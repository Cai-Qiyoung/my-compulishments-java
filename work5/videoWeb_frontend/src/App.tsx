import { ChangeEvent, FormEvent, KeyboardEvent, useEffect, useMemo, useRef, useState } from 'react';
import { api } from './lib/api';
import { dateInputToTimestamp, formatCount, formatDate } from './lib/format';
import { loadAuth, loadRememberedVideos, rememberVideos, saveAuth } from './lib/storage';
import type {
  AuthUser,
  ChatMessageItem,
  CommentItem,
  ContactItem,
  PublicUserHub,
  SessionItem,
  SocialUser,
  UserProfile,
  VideoAuditItem,
  VideoItem,
} from './types';

type TabKey = 'audit' | 'discover' | 'publish' | 'chat' | 'profile';

const DEFAULT_API_BASE = import.meta.env.VITE_API_BASE_URL ?? '/api';

const NAV_ITEMS: Array<{ key: TabKey; label: string; hint: string; adminOnly?: boolean }> = [
  { key: 'audit', label: '审核', hint: '视频审核', adminOnly: true },
  { key: 'discover', label: '主页', hint: '发现内容' },
  { key: 'publish', label: '投稿', hint: '发布视频' },
  { key: 'chat', label: '聊天', hint: '联系人与会话' },
  { key: 'profile', label: '我的', hint: '账号与关系' },
];

const TAB_META: Record<TabKey, { title: string; subtitle: string }> = {
  audit: { title: '视频审核', subtitle: '处理待审核的视频投稿。' },
  discover: { title: '主页', subtitle: '热门、最新和搜索结果都在这里。' },
  publish: { title: '投稿', subtitle: '发布你的视频和封面。' },
  chat: { title: '聊天', subtitle: '会话与联系人。' },
  profile: { title: '我的', subtitle: '管理账号、点赞、关注和资料。' },
};

const SEARCH_INIT = {
  keywords: '',
  username: '',
  fromDate: '',
  toDate: '',
};

const EMPTY_LIST = { items: [], total: 0 };
const HEVC_TAGS = ['hvc1', 'hev1'];
const TRANS_CODE_HEAD_BYTES = 512 * 1024;
const TRANS_CODE_POLL_MS = 3000;

function hasTag(buffer: string, tag: string) {
  return buffer.includes(tag);
}

function sleep(ms: number) {
  return new Promise<void>((resolve) => {
    window.setTimeout(resolve, ms);
  });
}

function buildWebSocketUrl(baseUrl: string, auth: AuthUser) {
  const wsPath = '/ws/chat';
  if (/^https?:\/\//i.test(baseUrl)) {
    const httpUrl = new URL(baseUrl);
    httpUrl.protocol = httpUrl.protocol === 'https:' ? 'wss:' : 'ws:';
    httpUrl.pathname = wsPath;
    httpUrl.search = `access_token=${encodeURIComponent(auth.accessToken)}`;
    return httpUrl.toString();
  }
  const url = new URL(wsPath, window.location.origin);
  url.protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
  url.search = `access_token=${encodeURIComponent(auth.accessToken)}`;
  return url.toString();
}

function upsertMessage(list: ChatMessageItem[], message: ChatMessageItem) {
  const existing = list.findIndex((item) => item.message_id === message.message_id);
  if (existing >= 0) {
    const next = list.slice();
    next[existing] = { ...next[existing], ...message };
    return next;
  }
  return [...list, message].sort((left, right) => parseTimeMs(left.sent_at) - parseTimeMs(right.sent_at));
}

function parseTimeMs(value?: string) {
  if (!value) {
    return 0;
  }
  const normalized = value.includes('T') ? value : value.replace(' ', 'T');
  const parsed = Date.parse(normalized);
  return Number.isFinite(parsed) ? parsed : 0;
}

function uniqueVideosById(items: VideoItem[]) {
  const map = new Map<string, VideoItem>();
  for (const item of items) {
    if (item?.id) {
      map.set(item.id, item);
    }
  }
  return Array.from(map.values());
}

function getVideoAuthor(video: VideoItem) {
  const id = video.publisher?.id || video.user_id;
  const username = video.publisher?.username || video.user_id || '匿名用户';
  const avatarUrl = video.publisher?.avatar_url || '';
  return { id, username, avatarUrl };
}

function mergeVideosById(current: VideoItem[], incoming: VideoItem[]) {
  if (!incoming.length) {
    return current;
  }
  const map = new Map(incoming.map((item) => [item.id, item]));
  return current.map((item) => {
    const next = map.get(item.id);
    if (!next) {
      return item;
    }
    return { ...item, ...next };
  });
}

function sameUserId(left?: string | number | null, right?: string | number | null) {
  const leftText = `${left ?? ''}`.trim();
  const rightText = `${right ?? ''}`.trim();
  if (!leftText || !rightText) {
    return false;
  }
  if (leftText === rightText) {
    return true;
  }
  const leftNum = Number(leftText);
  const rightNum = Number(rightText);
  return Number.isFinite(leftNum) && Number.isFinite(rightNum) && leftNum === rightNum;
}

async function isLikelyHevcVideo(file: File) {
  if (file.size <= 0) {
    return false;
  }

  const sampleSize = 2 * 1024 * 1024;
  const headBuffer = new TextDecoder('latin1').decode(
    await file.slice(0, Math.min(sampleSize, file.size)).arrayBuffer(),
  );
  if (HEVC_TAGS.some((tag) => hasTag(headBuffer, tag))) {
    return true;
  }

  if (file.size <= sampleSize) {
    return false;
  }

  const tailStart = Math.max(0, file.size - sampleSize);
  const tailBuffer = new TextDecoder('latin1').decode(
    await file.slice(tailStart, file.size).arrayBuffer(),
  );
  return HEVC_TAGS.some((tag) => hasTag(tailBuffer, tag));
}

function App() {
  const apiBaseUrl = DEFAULT_API_BASE;
  const [activeTab, setActiveTab] = useState<TabKey>('discover');
  const [auth, setAuth] = useState<AuthUser | null>(() => loadAuth());
  const [profile, setProfile] = useState<UserProfile | null>(null);
  const [heroMessage, setHeroMessage] = useState('欢迎回来。');
  const [globalError, setGlobalError] = useState('');
  const [busyKey, setBusyKey] = useState('');

  const [popularVideos, setPopularVideos] = useState<VideoItem[]>([]);
  const [latestVideos, setLatestVideos] = useState<VideoItem[]>(() => loadRememberedVideos());
  const [searchForm, setSearchForm] = useState(SEARCH_INIT);
  const [searchResults, setSearchResults] = useState<VideoItem[]>([]);
  const [searchTotal, setSearchTotal] = useState(0);
  const [selectedVideo, setSelectedVideo] = useState<VideoItem | null>(null);
  const [hideVideoPoster, setHideVideoPoster] = useState(false);
  const [videoPlayError, setVideoPlayError] = useState('');
  const [videoPlayHint, setVideoPlayHint] = useState('');
  const playerRef = useRef<HTMLVideoElement | null>(null);
  const commentSectionRef = useRef<HTMLDivElement | null>(null);
  const commentInputRef = useRef<HTMLTextAreaElement | null>(null);
  const hydratedDetailIdsRef = useRef<Set<string>>(new Set());
  const transcodeProgressTimerRef = useRef<number | null>(null);
  const copyNoticeTimerRef = useRef<number | null>(null);
  const [transcodeDialog, setTranscodeDialog] = useState({
    open: false,
    title: '',
    message: '',
    progress: 0,
  });
  const [copyNotice, setCopyNotice] = useState('');
  const [comments, setComments] = useState<CommentItem[]>([]);
  const [commentText, setCommentText] = useState('');

  const [loginForm, setLoginForm] = useState({ username: '', password: '' });
  const [registerForm, setRegisterForm] = useState({
    username: '',
    password: '',
    confirmPassword: '',
  });
  const [registerMode, setRegisterMode] = useState(false);

  const [publishForm, setPublishForm] = useState({ title: '', description: '' });
  const [videoFile, setVideoFile] = useState<File | null>(null);
  const [coverFile, setCoverFile] = useState<File | null>(null);
  const [avatarFile, setAvatarFile] = useState<File | null>(null);
  const [followTargetId, setFollowTargetId] = useState('');

  const [ownVideos, setOwnVideos] = useState<VideoItem[]>([]);
  const [likedVideos, setLikedVideos] = useState<VideoItem[]>([]);
  const [likedVideoIds, setLikedVideoIds] = useState<Set<string>>(new Set());
  const [likingVideoIds, setLikingVideoIds] = useState<Set<string>>(new Set());
  const [followers, setFollowers] = useState<SocialUser[]>([]);
  const [followings, setFollowings] = useState<SocialUser[]>([]);
  const [friends, setFriends] = useState<SocialUser[]>([]);
  const [blockedContacts, setBlockedContacts] = useState<ContactItem[]>([]);
  const [sessions, setSessions] = useState<SessionItem[]>([]);
  const [activeConversationId, setActiveConversationId] = useState('');
  const [chatView, setChatView] = useState<'overview' | 'conversation'>('overview');
  const [chatMessages, setChatMessages] = useState<ChatMessageItem[]>([]);
  const [chatMessageText, setChatMessageText] = useState('');
  const [auditItems, setAuditItems] = useState<VideoAuditItem[]>([]);
  const [auditReasonDrafts, setAuditReasonDrafts] = useState<Record<string, string>>({});
  const [inspectedUser, setInspectedUser] = useState<PublicUserHub | null>(null);
  const [userHubLoading, setUserHubLoading] = useState(false);
  const wsRef = useRef<WebSocket | null>(null);
  const activeConversationIdRef = useRef('');
  const chatMessageListRef = useRef<HTMLDivElement | null>(null);

  const authSummary = useMemo(() => `ID：${auth?.id || '-'}`, [auth?.id]);
  const activeSession = useMemo(
    () => sessions.find((item) => item.conversation_id === activeConversationId) ?? null,
    [sessions, activeConversationId],
  );

  const statusCards = [
    { label: '热门', value: popularVideos.length },
    { label: '搜索', value: searchTotal },
    { label: '点赞', value: likedVideos.length },
    { label: '会话', value: sessions.length },
  ];

  const followingLookup = useMemo(() => {
    const lookup = new Set<string>();
    for (const user of followings) {
      if (user.id) {
        lookup.add(user.id);
      }
      if (user.username) {
        lookup.add(user.username);
      }
    }
    return lookup;
  }, [followings]);

  const chatContacts = useMemo(() => {
    const merged = new Map<string, ContactItem>();
    for (const item of friends) {
      if (item.id) {
        merged.set(item.id, { ...item, blocked: false });
      }
    }
    for (const item of blockedContacts) {
      if (!item.id) {
        continue;
      }
      const current = merged.get(item.id);
      merged.set(item.id, current ? { ...current, blocked: true } : { ...item, blocked: true });
    }
    return Array.from(merged.values());
  }, [friends, blockedContacts]);

  function resolveLikedState(video: VideoItem) {
    if (video.is_liked === true) {
      return true;
    }
    if (video.is_liked === false) {
      return false;
    }
    return likedVideoIds.has(video.id);
  }

  function isVideoLiked(video: VideoItem) {
    return resolveLikedState(video);
  }

  function isLikingVideo(videoId: string) {
    return likingVideoIds.has(videoId);
  }

  function patchVideoAcrossLists(videoId: string, updater: (video: VideoItem) => VideoItem) {
    const patchList = (items: VideoItem[]) =>
      items.map((item) => (item.id === videoId ? updater(item) : item));
    setPopularVideos(patchList);
    setLatestVideos(patchList);
    setSearchResults(patchList);
    setOwnVideos(patchList);
    setLikedVideos(patchList);
    setSelectedVideo((current) => (current?.id === videoId ? updater(current) : current));
  }

  function focusCommentComposer() {
    commentSectionRef.current?.scrollIntoView({
      behavior: 'smooth',
      block: 'start',
    });
    commentInputRef.current?.focus();
  }

  function showCopyNotice(message: string) {
    setCopyNotice(message);
    if (copyNoticeTimerRef.current !== null) {
      window.clearTimeout(copyNoticeTimerRef.current);
    }
    copyNoticeTimerRef.current = window.setTimeout(() => {
      setCopyNotice('');
      copyNoticeTimerRef.current = null;
    }, 1000);
  }

  async function handleCopyAuthId() {
    const userId = auth?.id?.trim();
    if (!userId) {
      return;
    }

    try {
      if (navigator.clipboard?.writeText) {
        await navigator.clipboard.writeText(userId);
      } else {
        const textarea = document.createElement('textarea');
        textarea.value = userId;
        textarea.setAttribute('readonly', 'true');
        textarea.style.position = 'fixed';
        textarea.style.opacity = '0';
        document.body.appendChild(textarea);
        textarea.select();
        document.execCommand('copy');
        document.body.removeChild(textarea);
      }
      setHeroMessage('ID 已复制。');
      showCopyNotice('复制成功');
    } catch {
      setGlobalError('复制失败，请手动复制 ID。');
      showCopyNotice('复制失败');
    }
  }

  async function runTask<T>(key: string, message: string, task: () => Promise<T>) {
    setBusyKey(key);
    setGlobalError('');
    try {
      const result = await task();
      setHeroMessage(message);
      return result;
    } catch (error) {
      const nextMessage = error instanceof Error ? error.message : '请求失败';
      setGlobalError(nextMessage);
      throw error;
    } finally {
      setBusyKey('');
    }
  }

  async function safeList<T>(task: () => Promise<{ items: T[]; total?: number }>) {
    try {
      return await task();
    } catch {
      return EMPTY_LIST as { items: T[]; total?: number };
    }
  }

  function closeUserHub() {
    setInspectedUser(null);
    setUserHubLoading(false);
  }

  async function openUserHub(user: SocialUser) {
    if (!user.id && !user.username) {
      setGlobalError('暂时无法识别这个用户。');
      return;
    }

    setUserHubLoading(true);
    setInspectedUser({
      profile: user,
      uploads: [],
      likedVideos: [],
      followers: [],
      followings: [],
      isSelf: Boolean(auth && (user.id === auth.id || user.username === auth.username)),
      isFollowing: Boolean(user.id && (followingLookup.has(user.id) || followingLookup.has(user.username))),
    });

    await runTask('user-hub', `正在查看 ${user.username || '用户'}。`, async () => {
      const hub = await api.getPublicUserHub(apiBaseUrl, auth, user);
      const resolvedProfile = {
        ...hub.profile,
        avatar_url: hub.profile.avatar_url || user.avatar_url,
      };
      setInspectedUser({
        ...hub,
        profile: resolvedProfile,
        isSelf: Boolean(auth && (resolvedProfile.id === auth.id || resolvedProfile.username === auth.username)),
        isFollowing: Boolean(
          resolvedProfile.id && (followingLookup.has(resolvedProfile.id) || followingLookup.has(resolvedProfile.username)),
        ),
      });
    }).finally(() => {
      setUserHubLoading(false);
    });
  }

  function syncVideoToLocalState(nextVideo: VideoItem) {
    const merge = (items: VideoItem[]) =>
      items.map((item) => (item.id === nextVideo.id ? { ...item, ...nextVideo } : item));
    setPopularVideos(merge);
    setLatestVideos(merge);
    setSearchResults(merge);
    setOwnVideos(merge);
    setLikedVideos(merge);
    setSelectedVideo((current) =>
      current?.id === nextVideo.id ? { ...current, ...nextVideo } : current,
    );
  }

  async function loadHomeFeed(currentAuth?: AuthUser | null) {
    const login = currentAuth ?? auth ?? undefined;
    const [popular, latest] = await Promise.all([
      api.getPopularVideos(apiBaseUrl, 0, 6, login).catch(() => EMPTY_LIST),
      api
        .searchVideos(apiBaseUrl, {
          keywords: '',
          pageNum: 0,
          pageSize: 8,
        }, login)
        .catch(() => EMPTY_LIST),
    ]);

    const nextPopular = popular.items.length ? popular.items : latest.items.slice(0, 6);
    setPopularVideos(
      nextPopular.map((item) => ({
        ...item,
        is_liked: item.is_liked === undefined ? (likedVideoIds.has(item.id) ? true : undefined) : item.is_liked,
      })),
    );
    setLatestVideos(
      latest.items.map((item) => ({
        ...item,
        is_liked: item.is_liked === undefined ? (likedVideoIds.has(item.id) ? true : undefined) : item.is_liked,
      })),
    );
    rememberVideos([...popular.items, ...latest.items]);
  }

  async function loadComments(videoId: string) {
    const items = await api.getComments(apiBaseUrl, videoId, 0, 20, auth);
    setComments(items);
  }

  async function loadProfileHub(currentAuth: AuthUser) {
    let profileData: UserProfile | null = null;
    try {
      profileData = await api.getProfile(apiBaseUrl, currentAuth);
    } catch {
      profileData = null;
    }

    const [uploadsByUserData, uploadsFromFeedData, uploadsByNameData] = await Promise.all([
      safeList(() => api.getVideoList(apiBaseUrl, 0, 20, currentAuth.id, currentAuth)),
      safeList(() => api.getVideoList(apiBaseUrl, 0, 30, undefined, currentAuth)),
      safeList(() =>
        api.searchVideos(
          apiBaseUrl,
          {
            keywords: '',
            username: currentAuth.username,
            pageNum: 0,
            pageSize: 20,
          },
          currentAuth,
        ),
      ),
    ]);
    const uploadsData = {
      items: uniqueVideosById([
        ...uploadsByUserData.items,
        ...uploadsFromFeedData.items.filter((item) => item.user_id === currentAuth.id),
        ...uploadsByNameData.items,
      ]),
    };

    const [likesDirectData, likesByUserData] = await Promise.all([
      safeList(() => api.getLikedVideos(apiBaseUrl, currentAuth, 0, 30)),
      safeList(() => api.getLikedVideos(apiBaseUrl, currentAuth, 0, 30, currentAuth.id)),
    ]);
    const likesData = {
      items: uniqueVideosById([
        ...likesDirectData.items,
        ...likesByUserData.items,
      ]).filter((item) => Boolean(item.id && (item.video_url || item.cover_url || item.title))),
    };
    const likeIdSet = new Set(likesData.items.map((item) => item.id));

    const [followersData, followingsData, friendsData] = await Promise.all([
      safeList(() => api.getFollowers(apiBaseUrl, currentAuth, currentAuth.id, 0, 20)),
      safeList(() => api.getFollowings(apiBaseUrl, currentAuth, currentAuth.id, 0, 20)),
      safeList(() => api.getFriends(apiBaseUrl, currentAuth, 0, 20)),
    ]);

    if (profileData) {
      setProfile(profileData);
    }
    setOwnVideos(uploadsData.items.map((item) => ({ ...item, is_liked: likeIdSet.has(item.id) })));
    setLikedVideos(likesData.items.map((item) => ({ ...item, is_liked: true })));
    setLikedVideoIds(likeIdSet);
    setFollowers(followersData.items);
    setFollowings(followingsData.items);
    setFriends(friendsData.items);
    if (profileData && currentAuth.avatarUrl !== profileData.avatar_url) {
      const nextAuth = { ...currentAuth, avatarUrl: profileData.avatar_url };
      setAuth(nextAuth);
      saveAuth(nextAuth);
    }
    rememberVideos([...uploadsData.items, ...likesData.items]);
  }

  async function loadChatHub(currentAuth: AuthUser) {
    const [friendData, blockedData, sessionData] = await Promise.all([
      safeList(() => api.getFriends(apiBaseUrl, currentAuth, 1, 30)),
      safeList(() => api.getBlockedContacts(apiBaseUrl, currentAuth, 1, 30)),
      safeList(() => api.getSessionList(apiBaseUrl, currentAuth, 1, 50)),
    ]);

    setFriends(friendData.items);
    setBlockedContacts(blockedData.items);
    setSessions(sessionData.items);
    setActiveConversationId((current) => {
      if (current && sessionData.items.some((item) => item.conversation_id === current)) {
        return current;
      }
      return sessionData.items[0]?.conversation_id ?? '';
    });
  }

  async function loadPendingAudit(currentAuth: AuthUser) {
    if (currentAuth.role !== 'ADMIN') {
      setAuditItems([]);
      return;
    }
    const pending = await safeList(() => api.getPendingAuditVideos(apiBaseUrl, currentAuth, 1, 30));
    setAuditItems(pending.items);
  }

  async function loadConversationHistory(
    currentAuth: AuthUser,
    conversationId: string,
    options?: { start?: string; end?: string },
  ) {
    const history = await api.getMessageHistory(
      apiBaseUrl,
      currentAuth,
      conversationId,
      1,
      100,
      options?.start || undefined,
      options?.end || undefined,
    );
    const ordered = history.items.slice().sort((left, right) => parseTimeMs(left.sent_at) - parseTimeMs(right.sent_at));
    setChatMessages(ordered);
  }

  function updateSessionPreview(conversationId: string, message: ChatMessageItem) {
    setSessions((current) => {
      const next = current.map((item) =>
        item.conversation_id === conversationId
          ? {
              ...item,
              last_message: message.content,
              last_message_type: message.message_type,
              last_message_time: message.sent_at,
            }
          : item,
      );
      return next.slice().sort((left, right) => parseTimeMs(right.last_message_time) - parseTimeMs(left.last_message_time));
    });
  }

  useEffect(() => {
    const syncAuth = (event: Event) => {
      const detail = (event as CustomEvent<AuthUser | null>).detail ?? null;
      setAuth(detail);
    };
    window.addEventListener('video-web:auth-changed', syncAuth as EventListener);
    return () => {
      window.removeEventListener('video-web:auth-changed', syncAuth as EventListener);
    };
  }, []);

  useEffect(() => {
    if (!auth) {
      setProfile(null);
      setOwnVideos([]);
      setLikedVideos([]);
      setLikedVideoIds(new Set());
      setLikingVideoIds(new Set());
      setFollowers([]);
      setFollowings([]);
      setFriends([]);
      setBlockedContacts([]);
      setSessions([]);
      setActiveConversationId('');
      setChatView('overview');
      setChatMessages([]);
      setChatMessageText('');
      setAuditItems([]);
      setAuditReasonDrafts({});
      setPopularVideos([]);
      setLatestVideos([]);
      setSearchResults([]);
      setSearchTotal(0);
      setSelectedVideo(null);
      hydratedDetailIdsRef.current = new Set();
      setHideVideoPoster(false);
      setVideoPlayError('');
      setComments([]);
      setInspectedUser(null);
      if (wsRef.current) {
        wsRef.current.close();
        wsRef.current = null;
      }
      return;
    }

    runTask('dashboard-init', '内容已同步。', async () => {
      await loadHomeFeed(auth);
      await loadProfileHub(auth);
      await loadChatHub(auth);
      if (auth.role === 'ADMIN') {
        await loadPendingAudit(auth);
      }
    }).catch(() => undefined);
  }, [auth]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!selectedVideo?.video_url) {
      return;
    }

    const video = playerRef.current;
    if (!video) {
      return;
    }

    const playPromise = video.play();
    if (playPromise && typeof playPromise.catch === 'function') {
      playPromise.catch(() => undefined);
    }
    setVideoPlayHint('正在预缓冲视频流，请稍候…');
  }, [selectedVideo?.video_url]);

  useEffect(() => {
    return () => {
      stopTranscodeProgressTicker();
      if (copyNoticeTimerRef.current !== null) {
        window.clearTimeout(copyNoticeTimerRef.current);
        copyNoticeTimerRef.current = null;
      }
    };
  }, []);

  useEffect(() => {
    if (!auth || activeTab !== 'profile') {
      return;
    }
    loadProfileHub(auth).catch(() => undefined);
  }, [activeTab, auth?.id]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    activeConversationIdRef.current = activeConversationId;
  }, [activeConversationId]);

  useEffect(() => {
    if (chatView !== 'conversation') {
      return;
    }
    if (!activeConversationId) {
      setChatView('overview');
    }
  }, [chatView, activeConversationId]);

  useEffect(() => {
    if (chatView !== 'conversation') {
      return;
    }
    const container = chatMessageListRef.current;
    if (!container) {
      return;
    }
    container.scrollTop = container.scrollHeight;
  }, [chatMessages, chatView]);

  useEffect(() => {
    if (!auth) {
      return;
    }

    const socket = new WebSocket(buildWebSocketUrl(apiBaseUrl, auth));
    wsRef.current = socket;

    socket.onmessage = (event) => {
      try {
        const payload = JSON.parse(event.data);
        if (payload?.event === 'MESSAGE' || payload?.event === 'MESSAGE_ACK') {
          const message = {
            ...payload.data,
            self:
              payload?.event === 'MESSAGE_ACK'
                ? true
                : Boolean(payload?.data?.self),
          } as ChatMessageItem;
          setChatMessages((current) =>
            message.conversation_id === activeConversationIdRef.current ? upsertMessage(current, message) : current,
          );
          updateSessionPreview(message.conversation_id, message);
          return;
        }
        if (payload?.event === 'ERROR') {
          setGlobalError(payload?.message || '聊天消息发送失败。');
        }
      } catch {
        // Ignore malformed websocket payloads.
      }
    };

    socket.onclose = () => {
      if (wsRef.current === socket) {
        wsRef.current = null;
      }
    };

    return () => {
      if (wsRef.current === socket) {
        wsRef.current = null;
      }
      socket.close();
    };
  }, [auth?.id, auth?.accessToken, apiBaseUrl]);

  useEffect(() => {
    if (!auth || !activeConversationId) {
      return;
    }
    loadConversationHistory(auth, activeConversationId).catch(() => undefined);
  }, [auth?.id, activeConversationId]); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => {
    if (!auth || activeTab !== 'discover') {
      return;
    }
    const discoverFeed = searchResults.length ? searchResults : latestVideos;
    const targets = uniqueVideosById([...popularVideos, ...discoverFeed])
      .filter((video) => !hydratedDetailIdsRef.current.has(video.id))
      .filter((video) => !video.publisher?.username || !video.publisher?.avatar_url || video.is_liked === undefined)
      .slice(0, 14);
    if (!targets.length) {
      return;
    }
    for (const target of targets) {
      hydratedDetailIdsRef.current.add(target.id);
    }

    let cancelled = false;
    for (const target of targets) {
      api
        .getVideoDetail(apiBaseUrl, target.id, auth)
        .then((detail) => {
          if (cancelled || !detail?.id) {
            return;
          }
          const details = [detail];
          setLatestVideos((current) => mergeVideosById(current, details));
          setSearchResults((current) => mergeVideosById(current, details));
          setPopularVideos((current) => mergeVideosById(current, details));
          setOwnVideos((current) => mergeVideosById(current, details));
          setLikedVideos((current) => mergeVideosById(current, details));
          setLikedVideoIds((current) => {
            const next = new Set(current);
            if (detail.is_liked === true) {
              next.add(detail.id);
            }
            if (detail.is_liked === false) {
              next.delete(detail.id);
            }
            return next;
          });
        })
        .catch(() => undefined);
    }

    return () => {
      cancelled = true;
    };
  }, [activeTab, auth?.id, popularVideos, latestVideos, searchResults]); // eslint-disable-line react-hooks/exhaustive-deps

  async function handleLogin(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const nextAuth = await runTask('login', '登录成功。', () =>
      api.login(apiBaseUrl, loginForm.username, loginForm.password),
    );

    setAuth(nextAuth);
    saveAuth(nextAuth);
    setLoginForm({ username: '', password: '' });
  }

  async function handleRegister(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();

    if (registerForm.password !== registerForm.confirmPassword) {
      window.alert('两次密码输入不一致，请重新输入。');
      setRegisterForm((current) => ({
        ...current,
        password: '',
        confirmPassword: '',
      }));
      return;
    }

    await runTask('register', '注册成功，请登录。', () =>
      api.register(apiBaseUrl, registerForm.username, registerForm.password),
    );
    setLoginForm({ username: registerForm.username, password: '' });
    setRegisterForm({ username: '', password: '', confirmPassword: '' });
    setRegisterMode(false);
  }

  function handleLogout() {
    setAuth(null);
    saveAuth(null);
    setHeroMessage('已退出登录。');
  }

  async function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!auth) {
      return;
    }

    const data = await runTask('search', '搜索完成。', () =>
      api.searchVideos(
        apiBaseUrl,
        {
          keywords: searchForm.keywords,
          username: searchForm.username,
          pageNum: 0,
          pageSize: 12,
          fromDate: dateInputToTimestamp(searchForm.fromDate),
          toDate: dateInputToTimestamp(searchForm.toDate),
        },
        auth,
      ),
    );

    setSearchResults(
      data.items.map((item) => ({
        ...item,
        is_liked: item.is_liked === undefined ? (likedVideoIds.has(item.id) ? true : undefined) : item.is_liked,
      })),
    );
    setSearchTotal(data.total ?? data.items.length);
    rememberVideos(data.items);
  }

  async function openVideo(video: VideoItem, options?: { focusComments?: boolean }) {
    setSelectedVideo(video);
    setHideVideoPoster(false);
    setVideoPlayError('');
    setVideoPlayHint('正在预缓冲视频流，请稍候…');
    if (options?.focusComments) {
      window.setTimeout(() => {
        focusCommentComposer();
      }, 80);
    }
    rememberVideos([video]);
    await runTask('video-open', `已打开《${video.title}》。`, async () => {
      const detailVideo = await api.getVideoDetail(apiBaseUrl, video.id, auth);
      setSelectedVideo(detailVideo);
      setLikedVideoIds((current) => {
        if (detailVideo.is_liked === undefined) {
          return current;
        }
        const next = new Set(current);
        if (detailVideo.is_liked) {
          next.add(detailVideo.id);
        } else {
          next.delete(detailVideo.id);
        }
        return next;
      });
      rememberVideos([detailVideo]);
      syncVideoToLocalState(detailVideo);
      await loadComments(detailVideo.id);
      if (options?.focusComments) {
        window.setTimeout(() => {
          focusCommentComposer();
        }, 120);
      }
    });
  }

  function closeVideoDialog() {
    setSelectedVideo(null);
    setHideVideoPoster(false);
    setVideoPlayError('');
    setVideoPlayHint('');
  }

  async function handleLikeVideo(video: VideoItem) {
    if (!auth) {
      setGlobalError('请先登录后再点赞视频。');
      return;
    }
    if (!video.id || likingVideoIds.has(video.id)) {
      return;
    }

    setGlobalError('');
    setLikingVideoIds((current) => {
      const next = new Set(current);
      next.add(video.id);
      return next;
    });

    const currentlyLiked = isVideoLiked(video);
    const nextLiked = !currentlyLiked;
    const actionType: '1' | '2' = nextLiked ? '1' : '2';
    const currentCount = Number.isFinite(video.like_count) ? video.like_count : 0;
    const nextCount = Math.max(0, currentCount + (nextLiked ? 1 : -1));

    // Optimistic update: click immediately reflects in UI.
    setLikedVideoIds((current) => {
      const next = new Set(current);
      if (nextLiked) {
        next.add(video.id);
      } else {
        next.delete(video.id);
      }
      return next;
    });

    patchVideoAcrossLists(video.id, (item) => ({
      ...item,
      like_count: nextCount,
      is_liked: nextLiked,
    }));

    setLikedVideos((current) => {
      if (nextLiked) {
        if (current.some((item) => item.id === video.id)) {
          return current.map((item) =>
            item.id === video.id
              ? {
                  ...item,
                  like_count: nextCount,
                  is_liked: true,
                }
              : item,
          );
        }
        return [
          {
            ...video,
            like_count: nextCount,
            is_liked: true,
          },
          ...current,
        ];
      }
      return current.filter((item) => item.id !== video.id);
    });

    try {
      await api.likeVideo(apiBaseUrl, auth, video.id, actionType);
      setHeroMessage(nextLiked ? '点赞成功。' : '已取消点赞。');

      try {
        const freshDetail = await api.getVideoDetail(apiBaseUrl, video.id, auth);
        patchVideoAcrossLists(video.id, (item) => ({
          ...item,
          ...freshDetail,
          like_count: freshDetail.like_count,
          is_liked: freshDetail.is_liked ?? nextLiked,
        }));
        if (freshDetail.is_liked !== undefined) {
          setLikedVideoIds((current) => {
            const next = new Set(current);
            if (freshDetail.is_liked) {
              next.add(video.id);
            } else {
              next.delete(video.id);
            }
            return next;
          });
        }
      } catch {
        // Keep optimistic state if detail refresh fails.
      }
    } catch (error) {
      const previousCount = currentCount;
      // Rollback when server rejects the like/unlike request.
      setLikedVideoIds((current) => {
        const next = new Set(current);
        if (currentlyLiked) {
          next.add(video.id);
        } else {
          next.delete(video.id);
        }
        return next;
      });
      patchVideoAcrossLists(video.id, (item) => ({
        ...item,
        like_count: previousCount,
        is_liked: currentlyLiked,
      }));
      setLikedVideos((current) => {
        if (currentlyLiked) {
          if (current.some((item) => item.id === video.id)) {
            return current.map((item) =>
              item.id === video.id
                ? {
                    ...item,
                    like_count: previousCount,
                    is_liked: true,
                  }
                : item,
            );
          }
          return [
            {
              ...video,
              like_count: previousCount,
              is_liked: true,
            },
            ...current,
          ];
        }
        return current.filter((item) => item.id !== video.id);
      });
      const nextMessage = error instanceof Error ? error.message : '点赞请求失败';
      setGlobalError(nextMessage);
    } finally {
      setLikingVideoIds((current) => {
        const next = new Set(current);
        next.delete(video.id);
        return next;
      });
    }
  }

  async function handleSubmitComment(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!auth || !selectedVideo) {
      setGlobalError('请先登录并选择一个视频。');
      return;
    }
    if (!commentText.trim()) {
      setGlobalError('请输入评论内容。');
      return;
    }

    await runTask('comment-submit', '评论已发布。', () =>
      api.publishComment(apiBaseUrl, auth, selectedVideo.id, commentText.trim()),
    );
    setCommentText('');
    await loadComments(selectedVideo.id);
    setSelectedVideo((current) =>
      current ? { ...current, comment_count: current.comment_count + 1 } : current,
    );
  }

  async function handleDeleteComment(commentId: string) {
    if (!auth || !selectedVideo) {
      setGlobalError('请先登录后再删除评论。');
      return;
    }

    await runTask('comment-delete', '评论已删除。', () =>
      api.deleteComment(apiBaseUrl, auth, commentId),
    );
    await loadComments(selectedVideo.id);
    setSelectedVideo((current) =>
      current ? { ...current, comment_count: Math.max(0, current.comment_count - 1) } : current,
    );
  }

  async function handleLikeComment(commentId: string) {
    if (!auth || !selectedVideo) {
      setGlobalError('请先登录后再点赞评论。');
      return;
    }

    await runTask('comment-like', '评论已点赞。', () =>
      api.likeComment(apiBaseUrl, auth, commentId, '1'),
    );
    setComments((current) =>
      current.map((item) =>
        item.id === commentId ? { ...item, like_count: item.like_count + 1 } : item,
      ),
    );
  }

  function updateTranscodeDialog(progress: number, message: string) {
    setTranscodeDialog((current) => ({
      ...current,
      progress: Math.min(100, Math.max(current.progress, progress)),
      message,
    }));
  }

  function stopTranscodeProgressTicker() {
    if (transcodeProgressTimerRef.current !== null) {
      window.clearInterval(transcodeProgressTimerRef.current);
      transcodeProgressTimerRef.current = null;
    }
  }

  function startTranscodeProgressTicker() {
    stopTranscodeProgressTicker();
    transcodeProgressTimerRef.current = window.setInterval(() => {
      setTranscodeDialog((current) => {
        if (!current.open) {
          return current;
        }
        let step = 0.5;
        if (current.progress < 30) {
          step = 1.8;
        } else if (current.progress < 65) {
          step = 1.1;
        } else if (current.progress < 85) {
          step = 0.65;
        } else if (current.progress < 96) {
          step = 0.18;
        }
        const next = Math.min(96, current.progress + step);
        return {
          ...current,
          progress: next,
        };
      });
    }, 500);
  }

  function closeTranscodeDialog() {
    stopTranscodeProgressTicker();
    setTranscodeDialog({
      open: false,
      title: '',
      progress: 0,
      message: '',
    });
  }

  async function getRangeBytes(url: string, start: number, end: number) {
    const response = await fetch(url, {
      headers: {
        Range: `bytes=${start}-${end}`,
      },
    });
    if (!response.ok) {
      throw new Error(`无法读取视频片段(${response.status})`);
    }
    return new Uint8Array(await response.arrayBuffer());
  }

  async function sniffRemoteCodec(videoUrl: string) {
    const head = await getRangeBytes(videoUrl, 0, TRANS_CODE_HEAD_BYTES - 1);
    const headText = new TextDecoder('latin1').decode(head);
    if (hasTag(headText, 'avc1')) {
      return 'h264' as const;
    }
    if (HEVC_TAGS.some((tag) => hasTag(headText, tag))) {
      return 'hevc' as const;
    }

    const headResponse = await fetch(videoUrl, { method: 'HEAD' });
    const total = Number(headResponse.headers.get('content-length') ?? '0');
    if (!Number.isFinite(total) || total <= TRANS_CODE_HEAD_BYTES) {
      return 'unknown' as const;
    }

    const tailStart = Math.max(0, total - TRANS_CODE_HEAD_BYTES);
    const tail = await getRangeBytes(videoUrl, tailStart, total - 1);
    const tailText = new TextDecoder('latin1').decode(tail);
    if (hasTag(tailText, 'avc1')) {
      return 'h264' as const;
    }
    if (HEVC_TAGS.some((tag) => hasTag(tailText, tag))) {
      return 'hevc' as const;
    }
    return 'unknown' as const;
  }

  function chooseJustPublishedVideo(
    videos: VideoItem[],
    title: string,
    description: string,
    publishStartedAt: number,
  ) {
    if (!videos.length) {
      return null;
    }

    const normalizedTitle = title.trim();
    const normalizedDesc = description.trim();

    const recent = videos.filter((video) => parseTimeMs(video.created_at) >= publishStartedAt - 2 * 60 * 1000);
    const pool = recent.length ? recent : videos;

    const exact = pool.filter(
      (video) =>
        (!normalizedTitle || video.title === normalizedTitle) &&
        (!normalizedDesc || video.description === normalizedDesc),
    );
    const fuzzy = pool.filter(
      (video) =>
        (!normalizedTitle || video.title.includes(normalizedTitle)) &&
        (!normalizedDesc || video.description.includes(normalizedDesc)),
    );

    const ranked = (exact.length ? exact : fuzzy.length ? fuzzy : pool).sort(
      (left, right) => parseTimeMs(right.created_at) - parseTimeMs(left.created_at),
    );
    return ranked[0] ?? null;
  }

  async function waitForPublishedVideo(
    currentAuth: AuthUser,
    title: string,
    description: string,
    publishStartedAt: number,
  ) {
    const maxAttempts = 24;
    for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
      const progress = Math.min(42, 12 + Math.floor((attempt / maxAttempts) * 30));
      updateTranscodeDialog(progress, '视频已上传，正在获取发布记录…');

      try {
        const list = await api.getVideoList(apiBaseUrl, 0, 20, currentAuth.id, currentAuth);
        const picked = chooseJustPublishedVideo(list.items, title, description, publishStartedAt);
        if (picked) {
          return picked;
        }
      } catch {
        // Ignore short-lived API hiccups; polling continues.
      }
      await sleep(TRANS_CODE_POLL_MS);
    }
    throw new Error('发布成功，但暂未检索到视频记录，请稍后在主页刷新查看。');
  }

  async function waitForH264Video(currentAuth: AuthUser, publishedVideo: VideoItem) {
    const maxAttempts = 60;
    let latest = publishedVideo;

    for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
      const progress = Math.min(96, 45 + Math.floor((attempt / maxAttempts) * 50));
      updateTranscodeDialog(progress, '正在转码 HEVC -> H.264，请稍候…');

      try {
        latest = await api.getVideoDetail(apiBaseUrl, publishedVideo.id, currentAuth);
      } catch {
        // Ignore detail fetch failures during short transcode windows.
      }

      let codec: 'h264' | 'hevc' | 'unknown' = 'unknown';
      try {
        codec = await sniffRemoteCodec(latest.video_url);
      } catch {
        // Ignore temporary media probing failures and continue polling.
      }
      if (codec === 'h264') {
        return latest;
      }
      await sleep(TRANS_CODE_POLL_MS);
    }

    throw new Error('视频已发布，但转码仍在进行中。请稍后在主页打开该视频。');
  }

  async function handlePublish(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!auth) {
      setGlobalError('请先登录后再投稿。');
      return;
    }
    if (!videoFile) {
      setGlobalError('请选择要上传的视频文件。');
      return;
    }

    const publishTitle = publishForm.title.trim() || videoFile.name;
    const publishDescription = publishForm.description.trim();
    const publishStartedAt = Date.now();

    setTranscodeDialog({
      open: true,
      title: publishTitle,
      progress: 2,
      message: '正在上传视频，请稍候…',
    });
    startTranscodeProgressTicker();
    let publishCompleted = false;

    try {
      await runTask('video-publish', '视频已发布。', () =>
        api.publishVideo(apiBaseUrl, auth, {
          title: publishForm.title,
          description: publishForm.description,
          videoFile,
          coverFile: coverFile ?? undefined,
        }),
      );
      publishCompleted = true;

      setPublishForm({ title: '', description: '' });
      setVideoFile(null);
      setCoverFile(null);

      updateTranscodeDialog(10, '上传完成，正在进入转码队列…');
      const publishedVideo = await waitForPublishedVideo(
        auth,
        publishTitle,
        publishDescription,
        publishStartedAt,
      );
      const playableVideo = await waitForH264Video(auth, publishedVideo);
      updateTranscodeDialog(100, '转码完成，正在跳转主页并自动播放…');
      stopTranscodeProgressTicker();
      await sleep(420);

      await loadHomeFeed(auth);
      await loadProfileHub(auth);
      setActiveTab('discover');
      await openVideo(playableVideo);
      setHeroMessage('转码完成，已自动播放刚发布的视频。');
    } catch (error) {
      const message =
        error instanceof Error ? error.message : '发布失败，请稍后重试。';
      setGlobalError(message);
      if (publishCompleted) {
        await loadHomeFeed(auth);
        await loadProfileHub(auth);
        setActiveTab('discover');
      }
    } finally {
      closeTranscodeDialog();
    }
  }

  async function handleVideoFileChange(event: ChangeEvent<HTMLInputElement>) {
    const nextFile = event.target.files?.[0] ?? null;
    if (!nextFile) {
      setVideoFile(null);
      return;
    }

    const looksLikeVideo = nextFile.type.startsWith('video/') || /\.mp4$/i.test(nextFile.name);
    if (!looksLikeVideo) {
      setGlobalError('请选择视频文件（建议 MP4）。');
      setVideoFile(null);
      event.target.value = '';
      return;
    }

    try {
      const likelyHevc = await isLikelyHevcVideo(nextFile);
      if (likelyHevc) {
        setHeroMessage('检测到 HEVC 编码，已允许上传，服务器会自动转码为 H.264（可能需要几十秒）。');
      }
    } catch {
      // Ignore codec sniff failures; let backend handle upload validation.
    }

    setGlobalError('');
    setVideoFile(nextFile);
  }

  async function handleUploadAvatar(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!auth) {
      setGlobalError('请先登录。');
      return;
    }
    if (!avatarFile) {
      setGlobalError('请选择头像文件。');
      return;
    }

    const nextProfile = await runTask('avatar-upload', '头像已更新。', () =>
      api.uploadAvatar(apiBaseUrl, auth, avatarFile),
    );

    const nextAuth = { ...auth, avatarUrl: nextProfile.avatar_url };
    setAuth(nextAuth);
    saveAuth(nextAuth);
    setProfile(nextProfile);
    setAvatarFile(null);
  }

  async function handleFollowUser(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!auth) {
      setGlobalError('请先登录。');
      return;
    }
    if (!followTargetId.trim()) {
      setGlobalError('请输入目标用户 ID。');
      return;
    }

    await runTask('follow-user', '关系已更新。', () =>
      api.followUser(apiBaseUrl, auth, followTargetId.trim()),
    );
    setFollowTargetId('');
    await loadProfileHub(auth);
  }

  async function handleFollowAuthorFromCard(video: VideoItem) {
    if (!auth) {
      setGlobalError('请先登录。');
      return;
    }
    let author = getVideoAuthor(video);
    const needHydrateAuthor = !author.id || (author.username && author.id === author.username);
    if (needHydrateAuthor && video.id) {
      try {
        const detail = await api.getVideoDetail(apiBaseUrl, video.id, auth);
        patchVideoAcrossLists(video.id, (item) => ({ ...item, ...detail }));
        author = getVideoAuthor(detail);
      } catch {
        // Ignore detail hydrate failure and keep current author fallback.
      }
    }
    if (!author.id) {
      setGlobalError('缺少作者 ID，暂时无法关注。');
      return;
    }
    if (author.id === auth.id || author.username === auth.username) {
      setGlobalError('不能关注自己。');
      return;
    }
    const currentlyFollowing = Boolean(
      followingLookup.has(author.id) || (author.username && followingLookup.has(author.username)),
    );
    if (currentlyFollowing) {
      setHeroMessage('已关注该用户。');
      return;
    }
    await handleToggleUserFollow(
      {
        id: author.id,
        username: author.username,
        avatar_url: author.avatarUrl,
      },
      false,
    );
  }

  async function handleToggleUserFollow(user: SocialUser, currentlyFollowing: boolean) {
    if (!auth) {
      setGlobalError('请先登录。');
      return;
    }
    if (!user.id) {
      setGlobalError('缺少用户 ID，暂时无法操作。');
      return;
    }
    if (user.id === auth.id) {
      setGlobalError('不能关注自己。');
      return;
    }

    await runTask(
      currentlyFollowing ? 'unfollow-user' : 'follow-user',
      currentlyFollowing ? '已取消关注。' : '关注成功。',
      () => api.followUser(apiBaseUrl, auth, user.id),
    );

    setFollowings((current) => {
      if (currentlyFollowing) {
        return current.filter((item) => item.id !== user.id);
      }
      if (current.some((item) => item.id === user.id)) {
        return current;
      }
      return [user, ...current];
    });

    setInspectedUser((current) =>
      current && current.profile.id === user.id
        ? { ...current, isFollowing: !currentlyFollowing }
        : current,
    );
  }

  async function handleOpenSession(session: SessionItem) {
    if (!auth || !session.conversation_id) {
      return;
    }
    setActiveConversationId(session.conversation_id);
    setChatView('conversation');
    setChatMessageText('');
    await runTask('chat-history', `已打开会话 ${session.conversation_name || session.conversation_id}。`, () =>
      loadConversationHistory(auth, session.conversation_id),
    );
  }

  async function handleCreateSingleSession(targetUserId: string) {
    if (!auth) {
      setGlobalError('请先登录。');
      return;
    }
    const resolvedTargetId = targetUserId.trim();
    if (!resolvedTargetId) {
      setGlobalError('请输入目标用户 ID。');
      return;
    }
    const result = await runTask('chat-create-single', '单聊会话已创建。', () =>
      api.createSingleSession(apiBaseUrl, auth, resolvedTargetId),
    );
    await loadChatHub(auth);
    setActiveTab('chat');
    setActiveConversationId(result.conversation_id);
    setChatView('conversation');
    await loadConversationHistory(auth, result.conversation_id);
  }

  async function handleSendChatMessage(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!auth || !activeConversationId) {
      setGlobalError('请先选择一个会话。');
      return;
    }
    const content = chatMessageText.trim();
    if (!content) {
      setGlobalError('请输入消息内容。');
      return;
    }

    setGlobalError('');
    const socket = wsRef.current;
    if (socket && socket.readyState === WebSocket.OPEN) {
      socket.send(JSON.stringify({
        action: 'SEND',
        conversationId: activeConversationId,
        messageType: 'TEXT',
        content,
      }));
      setChatMessageText('');
      setHeroMessage('消息已发送。');
      return;
    }

    const message = await runTask('chat-send', '消息已发送。', () =>
      api.sendMessage(apiBaseUrl, auth, activeConversationId, 'TEXT', content),
    );
    setChatMessages((current) => upsertMessage(current, { ...message, self: true }));
    updateSessionPreview(activeConversationId, { ...message, self: true });
    setChatMessageText('');
  }

  function handleChatComposerKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    if (event.key !== 'Enter' || event.shiftKey) {
      return;
    }
    event.preventDefault();
    event.currentTarget.form?.requestSubmit();
  }

  function handleBackToChatOverview() {
    setChatView('overview');
  }

  async function handleOpenContactConversation(contact: ContactItem) {
    if (contact.blocked) {
      await handleToggleBlockUser(contact.id, true);
      return;
    }
    await handleCreateSingleSession(contact.id);
  }

  async function handleToggleBlockUser(targetUserId: string, blocked: boolean) {
    if (!auth) {
      setGlobalError('请先登录。');
      return;
    }
    if (!targetUserId) {
      setGlobalError('缺少目标用户 ID。');
      return;
    }
    await runTask(
      blocked ? 'contact-unblock' : 'contact-block',
      blocked ? '已取消屏蔽。' : '已屏蔽该联系人。',
      () => (blocked ? api.unblockContact(apiBaseUrl, auth, targetUserId) : api.blockContact(apiBaseUrl, auth, targetUserId)),
    );
    await loadChatHub(auth);
  }

  async function handleReviewVideo(videoId: string, auditStatus: 'APPROVED' | 'REJECTED') {
    if (!auth || auth.role !== 'ADMIN') {
      setGlobalError('只有管理员可以审核视频。');
      return;
    }
    await runTask(
      `audit-${videoId}`,
      auditStatus === 'APPROVED' ? '视频已通过审核。' : '视频已驳回。',
      () => api.reviewVideo(apiBaseUrl, auth, videoId, auditStatus, auditReasonDrafts[videoId]?.trim()),
    );
    setAuditItems((current) => current.filter((item) => item.video_id !== videoId));
    setAuditReasonDrafts((current) => {
      const next = { ...current };
      delete next[videoId];
      return next;
    });
  }

  if (!auth) {
    return (
      <div className="auth-page">
        <div className="auth-panel">
          <div className="brand-block auth-brand">
            <div className="brand-glyph">V</div>
            <div className="brand-copy">
              <strong>VideoWeb</strong>
              <span>先登录，再进入视频广场</span>
            </div>
          </div>

          {globalError ? (
            <div className="inline-banner error auth-banner">{globalError}</div>
          ) : (
            <div className="inline-banner auth-banner">
              {registerMode ? '注册后立即可以登录使用。' : '请输入账号密码登录。'}
            </div>
          )}

          {registerMode ? (
            <form className="stack-form auth-form" onSubmit={handleRegister}>
              <label>
                用户名
                <input
                  value={registerForm.username}
                  onChange={(event) =>
                    setRegisterForm((current) => ({ ...current, username: event.target.value }))
                  }
                  placeholder="输入新用户名"
                />
              </label>
              <label>
                密码
                <input
                  type="password"
                  value={registerForm.password}
                  onChange={(event) =>
                    setRegisterForm((current) => ({ ...current, password: event.target.value }))
                  }
                  placeholder="输入密码"
                />
              </label>
              <label>
                确认密码
                <input
                  type="password"
                  value={registerForm.confirmPassword}
                  onChange={(event) =>
                    setRegisterForm((current) => ({
                      ...current,
                      confirmPassword: event.target.value,
                    }))
                  }
                  placeholder="再次输入密码"
                />
              </label>
              <button className="primary-button" disabled={busyKey === 'register'}>
                注册
              </button>
              <button
                type="button"
                className="ghost-button"
                onClick={() => setRegisterMode(false)}
              >
                返回登录
              </button>
            </form>
          ) : (
            <form className="stack-form auth-form" onSubmit={handleLogin}>
              <label>
                用户名
                <input
                  value={loginForm.username}
                  onChange={(event) =>
                    setLoginForm((current) => ({ ...current, username: event.target.value }))
                  }
                  placeholder="输入用户名"
                />
              </label>
              <label>
                密码
                <input
                  type="password"
                  value={loginForm.password}
                  onChange={(event) =>
                    setLoginForm((current) => ({ ...current, password: event.target.value }))
                  }
                  placeholder="输入密码"
                />
              </label>
              <button className="primary-button" disabled={busyKey === 'login'}>
                登录
              </button>
            </form>
          )}

          {!registerMode ? (
            <button className="ghost-button auth-register-toggle" onClick={() => setRegisterMode(true)}>
              注册账号
            </button>
          ) : null}
        </div>
      </div>
    );
  }

  const activeMeta = TAB_META[activeTab];
  const searchFeed = searchResults.length ? searchResults : latestVideos;
  const navItems = NAV_ITEMS.filter((item) => !item.adminOnly || auth.role === 'ADMIN');

  useEffect(() => {
    if (activeTab === 'audit' && auth.role !== 'ADMIN') {
      setActiveTab('discover');
    }
  }, [activeTab, auth.role]);

  return (
    <div className="app-shell">
      <aside className="left-rail">
        <div className="brand-block">
          <div className="brand-glyph">V</div>
          <div className="brand-copy">
            <strong>VideoWeb</strong>
            <span>视频广场</span>
          </div>
        </div>

        <nav className="sidebar-nav">
          {navItems.map((item) => (
            <button
              key={item.key}
              className={item.key === activeTab ? 'sidebar-link active' : 'sidebar-link'}
              onClick={() => setActiveTab(item.key)}
            >
              <span>{item.label}</span>
              <small>{item.hint}</small>
            </button>
          ))}
        </nav>

        <button className="primary-button compose-button" onClick={() => setActiveTab('publish')}>
          发布视频
        </button>

        <section className="rail-card account-card">
          <div className="profile-inline">
            <AvatarChip name={auth.username} src={auth.avatarUrl} />
            <div className="profile-meta">
              <strong>{auth.username}</strong>
              <div className="id-copy-row">
                <button
                  type="button"
                  className="copy-icon-button"
                  onClick={handleCopyAuthId}
                  title="复制ID"
                  aria-label="复制ID"
                >
                  <svg className="copy-icon" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
                    <path d="M4.5 2A1.5 1.5 0 0 0 3 3.5v8A1.5 1.5 0 0 0 4.5 13h6A1.5 1.5 0 0 0 12 11.5v-8A1.5 1.5 0 0 0 10.5 2h-6zm0 1h6a.5.5 0 0 1 .5.5v8a.5.5 0 0 1-.5.5h-6a.5.5 0 0 1-.5-.5v-8a.5.5 0 0 1 .5-.5z" />
                    <path d="M6.5 0A1.5 1.5 0 0 0 5 1.5V2h1v-.5a.5.5 0 0 1 .5-.5h6a.5.5 0 0 1 .5.5v8a.5.5 0 0 1-.5.5H12v1h.5A1.5 1.5 0 0 0 14 9.5v-8A1.5 1.5 0 0 0 12.5 0h-6z" />
                  </svg>
                </button>
                <span className="id-copy-text" title={authSummary}>{authSummary}</span>
              </div>
            </div>
          </div>
          <button className="ghost-button small" onClick={handleLogout}>
            退出登录
          </button>
        </section>
      </aside>

      <main className="main-column">
        <header className="feed-header">
          <div>
            <h1>{activeMeta.title}</h1>
            <p>{activeMeta.subtitle}</p>
          </div>
          {activeTab === 'discover' ? (
              <button
                className="ghost-button small"
                onClick={() => runTask('refresh-home', '内容已刷新。', () => loadHomeFeed(auth))}
              >
                刷新
              </button>
          ) : null}
          {activeTab === 'audit' && auth.role === 'ADMIN' ? (
            <button
              className="ghost-button small"
              onClick={() => runTask('refresh-audit', '审核列表已刷新。', () => loadPendingAudit(auth))}
            >
              刷新
            </button>
          ) : null}
          {activeTab === 'profile' ? (
            <button
              className="ghost-button small"
              onClick={() => runTask('refresh-profile', '账号内容已同步。', () => loadProfileHub(auth))}
            >
              刷新
            </button>
          ) : null}
          {activeTab === 'chat' ? (
            <button
              className="ghost-button small"
              onClick={() => runTask('refresh-chat', '聊天列表已刷新。', () => loadChatHub(auth))}
            >
              刷新
            </button>
          ) : null}
        </header>

        {globalError ? (
          <div className="inline-banner error">{globalError}</div>
        ) : (
          <div className="inline-banner">{heroMessage}</div>
        )}

        <section className={activeTab === 'discover' ? 'panel active' : 'panel hidden'}>
          <section className="composer-card">
            <div className="composer-head">
              <AvatarChip name={auth.username} src={auth.avatarUrl} />
              <div>
                <strong>搜索视频</strong>
                <p>按标题、简介、作者和日期筛选内容。</p>
              </div>
            </div>

            <form className="search-grid" onSubmit={handleSearch}>
              <label>
                关键词
                <input
                  value={searchForm.keywords}
                  onChange={(event) =>
                    setSearchForm((current) => ({ ...current, keywords: event.target.value }))
                  }
                  placeholder="搜索视频"
                />
              </label>
              <label>
                作者
                <input
                  value={searchForm.username}
                  onChange={(event) =>
                    setSearchForm((current) => ({ ...current, username: event.target.value }))
                  }
                  placeholder="用户名"
                />
              </label>
              <label>
                开始时间
                <input
                  type="date"
                  value={searchForm.fromDate}
                  onChange={(event) =>
                    setSearchForm((current) => ({ ...current, fromDate: event.target.value }))
                  }
                />
              </label>
              <label>
                结束时间
                <input
                  type="date"
                  value={searchForm.toDate}
                  onChange={(event) =>
                    setSearchForm((current) => ({ ...current, toDate: event.target.value }))
                  }
                />
              </label>
              <div className="search-actions">
                <button className="primary-button" disabled={busyKey === 'search'}>
                  搜索
                </button>
                <button
                  type="button"
                  className="ghost-button"
                  onClick={() => {
                    setSearchForm(SEARCH_INIT);
                    setSearchResults([]);
                    setSearchTotal(0);
                  }}
                >
                  清空
                </button>
              </div>
            </form>
          </section>

          <section className="feed-panel">
            <div className="section-head compact">
              <div>
                <h2>热门</h2>
                <span>正在被更多人看到</span>
              </div>
            </div>
            <div className="feed-list">
              {popularVideos.length ? (
                popularVideos.map((video) => {
                  const author = getVideoAuthor(video);
                  const following = Boolean(
                    followingLookup.has(author.id) || (author.username && followingLookup.has(author.username)),
                  );
                  const canFollow = Boolean(
                    auth &&
                    (author.id || author.username) &&
                    author.id !== auth.id &&
                    author.username !== auth.username,
                  );
                  return (
                    <VideoCard
                      key={`popular-${video.id}`}
                      video={video}
                      onOpen={openVideo}
                      onOpenComments={(item) => openVideo(item, { focusComments: true })}
                      onLike={handleLikeVideo}
                      liked={isVideoLiked(video)}
                      commentMode
                      canFollow={canFollow}
                      following={following}
                      onFollow={handleFollowAuthorFromCard}
                      onOpenAuthor={(author) => openUserHub(author)}
                    />
                  );
                })
              ) : (
                <EmptyState text="这里暂时还没有内容。" />
              )}
            </div>
          </section>

          <section className="feed-panel">
            <div className="section-head compact">
              <div>
                <h2>{searchResults.length ? `搜索结果 ${searchTotal ? `· ${searchTotal}` : ''}` : '最新发布'}</h2>
                <span>{searchResults.length ? '与你的筛选条件匹配' : '最近更新的内容'}</span>
              </div>
            </div>
            <div className="feed-list">
              {searchFeed.length ? (
                searchFeed.map((video) => {
                  const author = getVideoAuthor(video);
                  const following = Boolean(
                    followingLookup.has(author.id) || (author.username && followingLookup.has(author.username)),
                  );
                  const canFollow = Boolean(
                    auth &&
                    (author.id || author.username) &&
                    author.id !== auth.id &&
                    author.username !== auth.username,
                  );
                  return (
                    <VideoCard
                      key={`feed-${video.id}`}
                      video={video}
                      onOpen={openVideo}
                      onOpenComments={(item) => openVideo(item, { focusComments: true })}
                      onLike={handleLikeVideo}
                      liked={isVideoLiked(video)}
                      commentMode
                      canFollow={canFollow}
                      following={following}
                      onFollow={handleFollowAuthorFromCard}
                      onOpenAuthor={(author) => openUserHub(author)}
                    />
                  );
                })
              ) : (
                <EmptyState text="试试换一个关键词或作者名。" />
              )}
            </div>
          </section>
        </section>

        <section className={activeTab === 'publish' ? 'panel active' : 'panel hidden'}>
          <section className="feed-panel">
            <div className="section-head compact">
              <div>
                <h2>发布视频</h2>
                <span>填写标题、简介并上传文件。</span>
              </div>
            </div>

            <form className="stack-form publish-form" onSubmit={handlePublish}>
              <label>
                标题
                <input
                  value={publishForm.title}
                  onChange={(event) =>
                    setPublishForm((current) => ({ ...current, title: event.target.value }))
                  }
                  placeholder="输入标题"
                />
              </label>
              <label>
                简介
                <textarea
                  value={publishForm.description}
                  onChange={(event) =>
                    setPublishForm((current) => ({
                      ...current,
                      description: event.target.value,
                    }))
                  }
                  rows={6}
                  placeholder="写点内容介绍"
                />
              </label>
              <div className="upload-grid">
                <label>
                  视频文件
                  <input
                    type="file"
                    accept="video/*"
                    onChange={handleVideoFileChange}
                  />
                </label>
                <label>
                  封面文件
                  <input
                    type="file"
                    accept="image/*"
                    onChange={(event) => setCoverFile(event.target.files?.[0] ?? null)}
                  />
                </label>
              </div>
              <button className="primary-button" disabled={busyKey === 'video-publish' || transcodeDialog.open}>
                发布
              </button>
            </form>
          </section>
        </section>

        <section className={activeTab === 'audit' ? 'panel active' : 'panel hidden'}>
          {auth.role === 'ADMIN' ? (
            <section className="feed-panel">
              <div className="section-head compact">
                <div>
                  <h2>视频审核</h2>
                  <span>处理待审核的视频投稿。</span>
                </div>
              </div>
              {auditItems.length ? (
                <div className="audit-list">
                  {auditItems.map((item) => (
                    <article key={item.video_id} className="audit-card">
                      <img src={item.cover_url} alt={item.title} />
                      <div className="audit-card-body">
                        <div className="audit-copy-block">
                          <span className="audit-copy-label">视频标题</span>
                          <h4>{item.title}</h4>
                        </div>
                        <div className="audit-copy-block">
                          <span className="audit-copy-label">视频描述</span>
                          <p>{item.description || '暂无简介。'}</p>
                        </div>
                        <div className="metric-row">
                          <span>作者 {item.author_name || item.author_id}</span>
                          <span>{formatDate(item.created_at)}</span>
                        </div>
                        <textarea
                          rows={2}
                          value={auditReasonDrafts[item.video_id] ?? ''}
                          onChange={(event) =>
                            setAuditReasonDrafts((current) => ({
                              ...current,
                              [item.video_id]: event.target.value,
                            }))
                          }
                          placeholder="填写审核备注（驳回时建议必填）"
                        />
                        <div className="card-actions">
                          <button
                            type="button"
                            className="primary-button"
                            onClick={() => handleReviewVideo(item.video_id, 'APPROVED')}
                          >
                            通过
                          </button>
                          <button
                            type="button"
                            className="ghost-button"
                            onClick={() => handleReviewVideo(item.video_id, 'REJECTED')}
                          >
                            驳回
                          </button>
                          <button
                            type="button"
                            className="ghost-button"
                            onClick={() =>
                              openVideo({
                                id: item.video_id,
                                user_id: item.author_id,
                                title: item.title,
                                description: item.description,
                                video_url: item.video_url,
                                cover_url: item.cover_url,
                                visit_count: 0,
                                like_count: 0,
                                comment_count: 0,
                                publisher: item.author_id || item.author_name
                                  ? {
                                      id: item.author_id,
                                      username: item.author_name || item.author_id,
                                      avatar_url: '',
                                    }
                                  : undefined,
                              })
                            }
                          >
                            预览
                          </button>
                        </div>
                      </div>
                    </article>
                  ))}
                </div>
              ) : (
                <EmptyState text="当前没有待审核视频。" />
              )}
            </section>
          ) : null}
        </section>

        <section className={activeTab === 'chat' ? 'panel active' : 'panel hidden'}>
          {chatView === 'conversation' && activeSession ? (
            <section className="feed-panel chat-conversation-card">
              <div className="chat-conversation-head">
                <div className="chat-conversation-title">
                  <button type="button" className="ghost-button small" onClick={handleBackToChatOverview}>
                    返回列表
                  </button>
                  <div>
                    <h2>{activeSession.conversation_name || '聊天窗口'}</h2>
                    {activeSession.conversation_type === 'GROUP' ? <span>群聊</span> : null}
                    {activeSession.conversation_type !== 'GROUP' && activeSession.blocked ? <span>已屏蔽</span> : null}
                  </div>
                </div>
                {activeSession.target_user_id ? (
                  <button
                    type="button"
                    className="ghost-button small"
                    onClick={() => handleToggleBlockUser(activeSession.target_user_id || '', Boolean(activeSession.blocked))}
                  >
                    {activeSession.blocked ? '取消屏蔽' : '屏蔽对方'}
                  </button>
                ) : null}
              </div>

              <div ref={chatMessageListRef} className="chat-message-list chat-message-list-page">
                {chatMessages.length ? (
                  chatMessages.map((message) => (
                    <article
                      key={message.message_id}
                      className={message.self ? 'chat-bubble self' : 'chat-bubble'}
                    >
                      <div className="chat-bubble-head">
                        <strong>{message.sender_name || message.sender_id}</strong>
                        <span>{formatDate(message.sent_at)}</span>
                      </div>
                      {message.message_type === 'IMAGE' ? (
                        <img className="chat-image" src={message.content} alt="聊天图片" />
                      ) : (
                        <p>{message.content}</p>
                      )}
                    </article>
                  ))
                ) : (
                  <EmptyState text="当前会话还没有消息。" compact />
                )}
              </div>

              <form className="chat-compose-row chat-compose-row-page" onSubmit={handleSendChatMessage}>
                <textarea
                  rows={2}
                  value={chatMessageText}
                  onChange={(event) => setChatMessageText(event.target.value)}
                  onKeyDown={handleChatComposerKeyDown}
                  placeholder={activeConversationId ? '输入消息，回车发送，Shift + 回车换行' : '先选择一个会话'}
                  disabled={!activeConversationId}
                />
                <button className="primary-button" disabled={!activeConversationId || busyKey === 'chat-send'}>
                  发送
                </button>
              </form>
            </section>
          ) : (
            <div className="chat-overview-grid">
              <section className="feed-panel">
                <div className="section-head compact">
                  <div>
                    <h2>会话列表</h2>
                    <span>点击会话进入对话页面。</span>
                  </div>
                </div>
                <div className="chat-session-list">
                  {sessions.length ? (
                    sessions.map((session) => (
                      <button
                        key={session.conversation_id}
                        type="button"
                        className={session.conversation_id === activeConversationId ? 'chat-session active' : 'chat-session'}
                        onClick={() => handleOpenSession(session)}
                      >
                        <AvatarChip name={session.conversation_name} src={session.conversation_avatar} size="sm" />
                        <div>
                          <strong>{session.conversation_name || session.conversation_id}</strong>
                          <span>{session.last_message || '暂无消息'}</span>
                          <small>{formatDate(session.last_message_time)}</small>
                        </div>
                      </button>
                    ))
                  ) : (
                    <EmptyState text="还没有会话。" compact />
                  )}
                </div>
              </section>

              <section className="feed-panel">
                <div className="section-head compact">
                  <div>
                    <h2>联系人</h2>
                    <span>仅展示互相关注的好友，点击即可进入对话。</span>
                  </div>
                </div>
                <div className="chat-contact-list">
                  {chatContacts.length ? (
                    chatContacts.map((contact) => (
                      <article key={contact.id} className="chat-contact-row">
                        <button
                          type="button"
                          className="social-user interactive"
                          onClick={() => handleOpenContactConversation(contact)}
                        >
                          <AvatarChip name={contact.username} src={contact.avatar_url} size="sm" />
                          <div className="chat-contact-meta">
                            <strong>{contact.username}</strong>
                            <span>ID {contact.id}</span>
                          </div>
                        </button>
                        <div className="chat-contact-actions">
                          {contact.blocked ? <span className="chat-contact-badge">已屏蔽</span> : null}
                          <button
                            type="button"
                            className="ghost-button small"
                            onClick={() => handleOpenContactConversation(contact)}
                          >
                            {contact.blocked ? '取消屏蔽' : '对话'}
                          </button>
                        </div>
                      </article>
                    ))
                  ) : (
                    <EmptyState text="还没有联系人。" compact />
                  )}
                </div>
              </section>
            </div>
          )}
        </section>

        <section className={activeTab === 'profile' ? 'panel active' : 'panel hidden'}>
          <section className="feed-panel">
            <div className="section-head compact">
              <div>
                <h2>账号资料</h2>
                <span>头像与基础信息。</span>
              </div>
            </div>

            <section className="sub-card">
              {profile ? (
                <div className="profile-card">
                  <img src={profile.avatar_url} alt={profile.username} className="avatar-large" />
                  <strong>{profile.username}</strong>
                  <span>ID {profile.id}</span>
                  <span>{formatDate(profile.created_at)}</span>
                </div>
              ) : (
                <EmptyState text="正在加载资料…" compact />
              )}

              <form className="stack-form" onSubmit={handleUploadAvatar}>
                <label>
                  上传头像
                  <input
                    type="file"
                    accept="image/*"
                    onChange={(event) => setAvatarFile(event.target.files?.[0] ?? null)}
                  />
                </label>
                <button className="ghost-button" disabled={busyKey === 'avatar-upload'}>
                  更新头像
                </button>
              </form>
            </section>
          </section>

          <section className="feed-panel">
            <div className="section-head compact">
              <div>
                <h2>我的投稿</h2>
                <span>你发布过的视频。</span>
              </div>
            </div>
            {ownVideos.length ? (
              <div className="video-list">
                {ownVideos.map((video) => (
                  <VideoRow
                    key={`mine-${video.id}`}
                    video={video}
                    onOpen={openVideo}
                    onLike={handleLikeVideo}
                    liked={isVideoLiked(video)}
                    onOpenAuthor={(author) => openUserHub(author)}
                  />
                ))}
              </div>
            ) : (
              <EmptyState text="还没有发布过视频。" />
            )}
          </section>

          <section className="feed-panel">
            <div className="section-head compact">
              <div>
                <h2>我的点赞</h2>
                <span>你点过赞的视频。</span>
              </div>
            </div>
            {likedVideos.length ? (
              <div className="video-list">
                {likedVideos.map((video) => (
                  <VideoRow
                    key={`like-${video.id}`}
                    video={video}
                    onOpen={openVideo}
                    onLike={handleLikeVideo}
                    liked={isVideoLiked(video)}
                    onOpenAuthor={(author) => openUserHub(author)}
                  />
                ))}
              </div>
            ) : (
              <EmptyState text="还没有点赞记录。" />
            )}
          </section>

          <section className="feed-panel">
            <div className="section-head compact">
              <div>
                <h2>关系</h2>
                <span>关注、粉丝和好友。</span>
              </div>
            </div>

            <form className="inline-form" onSubmit={handleFollowUser}>
              <input
                value={followTargetId}
                onChange={(event) => setFollowTargetId(event.target.value)}
                placeholder="输入用户 ID"
              />
              <button className="primary-button" disabled={busyKey === 'follow-user'}>
                提交
              </button>
            </form>

            <div className="social-grid">
              <SocialList title="关注" items={followings} onOpenUser={openUserHub} />
              <SocialList title="粉丝" items={followers} onOpenUser={openUserHub} />
              <SocialList title="好友" items={friends} onOpenUser={openUserHub} />
            </div>
          </section>

        </section>
      </main>

      <aside className="right-rail">
        <section className="rail-card">
          <h3>速览</h3>
          <div className="stat-list">
            {statusCards.map((card) => (
              <div key={card.label} className="stat-row">
                <span>{card.label}</span>
                <strong>{card.value}</strong>
              </div>
            ))}
          </div>
        </section>

        <section className="rail-card">
          <h3>热榜速览</h3>
          <div className="trend-list">
            {popularVideos.length ? (
              popularVideos.slice(0, 5).map((video, index) => (
                <button key={`trend-${video.id}`} className="trend-item" onClick={() => openVideo(video)}>
                  <span className="trend-rank">#{index + 1}</span>
                  <div>
                    <strong>{video.title}</strong>
                    <small>{formatCount(video.visit_count)} 播放</small>
                  </div>
                </button>
              ))
            ) : (
              <EmptyState text="热榜暂无数据" compact />
            )}
          </div>
        </section>

        {selectedVideo ? (
          <section className="rail-card">
            <h3>正在播放</h3>
            <button className="preview-card" onClick={() => openVideo(selectedVideo)}>
              <img src={selectedVideo.cover_url} alt={selectedVideo.title} />
              <div>
                <strong>{selectedVideo.title}</strong>
                <span>{formatCount(selectedVideo.comment_count)} 评论</span>
              </div>
            </button>
          </section>
        ) : null}
      </aside>

      {selectedVideo ? (
        <div className="dialog-backdrop" onClick={closeVideoDialog}>
          <div className="dialog-card" onClick={(event) => event.stopPropagation()}>
            <div className="dialog-head">
              <div>
                <h3>{selectedVideo.title}</h3>
                <span>{formatDate(selectedVideo.created_at)}</span>
              </div>
              <button className="ghost-button small" onClick={closeVideoDialog}>
                关闭
              </button>
            </div>

            <div className="player-wrap">
              <video
                ref={playerRef}
                key={selectedVideo.video_url}
                autoPlay
                controls
                playsInline
                preload="auto"
                poster={hideVideoPoster ? undefined : selectedVideo.cover_url}
                src={selectedVideo.video_url}
                onWaiting={() => setVideoPlayHint('视频缓冲中，正在加载更多数据…')}
                onCanPlay={() => setVideoPlayHint('')}
                onPlaying={() => setVideoPlayHint('')}
                onStalled={() => setVideoPlayHint('网络波动，正在恢复播放…')}
                onPlay={() => {
                  setHideVideoPoster(true);
                  setVideoPlayError('');
                  setVideoPlayHint('');
                }}
                onLoadedData={() => setHideVideoPoster(true)}
                onLoadedMetadata={(event) => {
                  setHideVideoPoster(true);
                  const { videoWidth, videoHeight } = event.currentTarget;
                  if (videoWidth === 0 || videoHeight === 0) {
                    setVideoPlayError('视频存在音频但无可解码画面，请上传 H.264 编码 MP4。');
                  }
                }}
                onError={() => {
                  setHideVideoPoster(true);
                  setVideoPlayHint('');
                  setVideoPlayError('视频播放失败，可能是文件损坏或编码不兼容（建议上传 H.264 编码 MP4）。');
                }}
              />
            </div>
            {videoPlayError ? <div className="inline-banner error">{videoPlayError}</div> : null}
            {videoPlayHint && !videoPlayError ? <div className="inline-banner">{videoPlayHint}</div> : null}

            <div className="detail-grid">
              <section className="sub-card">
                <p className="video-description">{selectedVideo.description || '暂无简介。'}</p>
                <div className="meta-row">
                  <button
                    type="button"
                    className="link-button"
                    onClick={() =>
                      openUserHub({
                        id: selectedVideo.publisher?.id || selectedVideo.user_id,
                        username: selectedVideo.publisher?.username || selectedVideo.user_id,
                        avatar_url: selectedVideo.publisher?.avatar_url || '',
                      })
                    }
                  >
                    作者 {selectedVideo.publisher?.username || selectedVideo.user_id}
                  </button>
                  <span>{formatDate(selectedVideo.created_at)}</span>
                </div>
                <div className="metric-row">
                  <strong>{formatCount(selectedVideo.visit_count)} 播放</strong>
                  <strong>{formatCount(selectedVideo.like_count)} 点赞</strong>
                  <strong>{formatCount(selectedVideo.comment_count)} 评论</strong>
                </div>
                <LikeToggleButton
                  liked={isVideoLiked(selectedVideo)}
                  count={selectedVideo.like_count}
                  onClick={() => handleLikeVideo(selectedVideo)}
                  disabled={isLikingVideo(selectedVideo.id)}
                  compact={false}
                />
              </section>

              <section className="sub-card" ref={commentSectionRef}>
                <div className="section-head compact">
                  <div>
                    <h3>评论</h3>
                    <span>参与讨论</span>
                  </div>
                </div>

                <form className="stack-form" onSubmit={handleSubmitComment}>
                  <textarea
                    rows={4}
                    value={commentText}
                    ref={commentInputRef}
                    onChange={(event) => setCommentText(event.target.value)}
                    placeholder="写下你的评论"
                  />
                  <button className="primary-button" disabled={busyKey === 'comment-submit'}>
                    发布评论
                  </button>
                </form>

                <div className="comment-list">
                  {comments.length ? (
                    comments.map((comment) => (
                      <article key={comment.id} className="comment-card">
                        <div className="comment-head">
                          <button
                            type="button"
                            className="author-link"
                            onClick={() =>
                              openUserHub({
                                id: comment.user_id,
                                username: `用户 ${comment.user_id}`,
                                avatar_url: '',
                              })
                            }
                          >
                            用户 {comment.user_id}
                          </button>
                          <span>{formatDate(comment.created_at)}</span>
                        </div>
                        <p>{comment.content}</p>
                        <div className="comment-actions">
                          <button
                            type="button"
                            className="ghost-button small"
                            onClick={() => handleLikeComment(comment.id)}
                          >
                            点赞 {formatCount(comment.like_count)}
                          </button>
                          {auth && sameUserId(comment.user_id, auth.id) ? (
                            <button
                              type="button"
                              className="ghost-button small"
                              onClick={() => handleDeleteComment(comment.id)}
                            >
                              删除
                            </button>
                          ) : null}
                        </div>
                      </article>
                    ))
                  ) : (
                    <EmptyState text="暂时还没有评论。" compact />
                  )}
                </div>
              </section>
            </div>
          </div>
        </div>
      ) : null}

      {transcodeDialog.open ? (
        <div className="dialog-backdrop transcode-backdrop">
          <div className="transcode-card" onClick={(event) => event.stopPropagation()}>
            <h3>正在处理《{transcodeDialog.title || '新视频'}》</h3>
            <p>{transcodeDialog.message}</p>
            <div className="transcode-progress-track">
              <div
                className="transcode-progress-fill"
                style={{ width: `${Math.min(100, Math.max(0, transcodeDialog.progress))}%` }}
              />
            </div>
            <strong>{Math.round(Math.min(100, Math.max(0, transcodeDialog.progress)))}%</strong>
          </div>
        </div>
      ) : null}

      {inspectedUser ? (
        <div className="dialog-backdrop" onClick={closeUserHub}>
          <div className="dialog-card user-hub-dialog" onClick={(event) => event.stopPropagation()}>
            <div className="dialog-head">
              <div className="user-hub-headline">
                <AvatarChip
                  name={inspectedUser.profile.username}
                  src={inspectedUser.profile.avatar_url}
                />
                <div>
                  <h3>{inspectedUser.profile.username || '未知用户'}</h3>
                  <span>ID {inspectedUser.profile.id || '暂无'}</span>
                </div>
              </div>
              <div className="user-hub-head-actions">
                {!inspectedUser.isSelf && auth ? (
                  <button
                    type="button"
                    className={inspectedUser.isFollowing ? 'follow-chip following' : 'follow-chip'}
                    onClick={() => handleToggleUserFollow(inspectedUser.profile, inspectedUser.isFollowing)}
                  >
                    {inspectedUser.isFollowing ? '取消关注' : '关注用户'}
                  </button>
                ) : null}
                {!inspectedUser.isSelf && auth && inspectedUser.profile.id ? (
                  <button
                    type="button"
                    className="ghost-button small"
                    onClick={() => handleCreateSingleSession(inspectedUser.profile.id)}
                  >
                    发消息
                  </button>
                ) : null}
                {!inspectedUser.isSelf && auth && inspectedUser.profile.id ? (
                  <button
                    type="button"
                    className="ghost-button small"
                    onClick={() =>
                      handleToggleBlockUser(
                        inspectedUser.profile.id,
                        blockedContacts.some((item) => item.id === inspectedUser.profile.id),
                      )
                    }
                  >
                    {blockedContacts.some((item) => item.id === inspectedUser.profile.id) ? '取消屏蔽' : '屏蔽'}
                  </button>
                ) : null}
                <button className="ghost-button small" onClick={closeUserHub}>
                  关闭
                </button>
              </div>
            </div>

            {userHubLoading ? <div className="inline-banner">正在加载用户主页…</div> : null}

            <div className="user-hub-metrics">
              <div className="stat-row">
                <span>投稿</span>
                <strong>{inspectedUser.uploads.length}</strong>
              </div>
              <div className="stat-row">
                <span>点赞</span>
                <strong>{inspectedUser.likedVideos.length}</strong>
              </div>
              <div className="stat-row">
                <span>关注</span>
                <strong>{inspectedUser.followings.length}</strong>
              </div>
              <div className="stat-row">
                <span>粉丝</span>
                <strong>{inspectedUser.followers.length}</strong>
              </div>
            </div>

            <div className="user-hub-grid">
              <section className="sub-card">
                <div className="section-head compact">
                  <div>
                    <h3>TA 的投稿</h3>
                    <span>公开视频列表</span>
                  </div>
                </div>
                {inspectedUser.uploads.length ? (
                  <div className="video-list">
                    {inspectedUser.uploads.map((video) => (
                      <VideoRow
                        key={`inspect-upload-${video.id}`}
                        video={video}
                        onOpen={openVideo}
                        onLike={handleLikeVideo}
                        liked={isVideoLiked(video)}
                        onOpenAuthor={(author) => openUserHub(author)}
                      />
                    ))}
                  </div>
                ) : (
                  <EmptyState text="暂时还没有公开投稿。" compact />
                )}
              </section>

              <section className="sub-card">
                <div className="section-head compact">
                  <div>
                    <h3>TA 的点赞</h3>
                    <span>后端已支持的喜欢列表</span>
                  </div>
                </div>
                {inspectedUser.likedVideos.length ? (
                  <div className="video-list">
                    {inspectedUser.likedVideos.map((video) => (
                      <VideoRow
                        key={`inspect-like-${video.id}`}
                        video={video}
                        onOpen={openVideo}
                        onLike={handleLikeVideo}
                        liked={isVideoLiked(video)}
                        onOpenAuthor={(author) => openUserHub(author)}
                      />
                    ))}
                  </div>
                ) : (
                  <EmptyState text="暂时还没有公开点赞记录。" compact />
                )}
              </section>

              <section className="sub-card">
                <div className="section-head compact">
                  <div>
                    <h3>TA 的关系</h3>
                    <span>关注与粉丝</span>
                  </div>
                </div>
                <div className="social-grid">
                  <SocialList title="关注" items={inspectedUser.followings} onOpenUser={openUserHub} />
                  <SocialList title="粉丝" items={inspectedUser.followers} onOpenUser={openUserHub} />
                </div>
              </section>
            </div>
          </div>
        </div>
      ) : null}

      {copyNotice ? <div className="copy-toast" role="status" aria-live="polite">{copyNotice}</div> : null}
    </div>
  );
}

type VideoCardProps = {
  video: VideoItem;
  onOpen: (video: VideoItem) => void | Promise<void>;
  onOpenComments: (video: VideoItem) => void | Promise<void>;
  onLike: (video: VideoItem) => void | Promise<void>;
  onOpenAuthor?: (user: SocialUser) => void | Promise<void>;
  liked: boolean;
  commentMode?: boolean;
  canFollow?: boolean;
  following?: boolean;
  onFollow?: (video: VideoItem) => void | Promise<void>;
};

function HeartIcon({ filled }: { filled: boolean }) {
  return (
    <svg className="heart-icon" viewBox="0 0 16 16" aria-hidden="true" focusable="false">
      {filled ? (
        <path d="M8 1.1C6.2-.6 3.3-.3 1.9 1.2.4 2.8.4 5.3 1.9 6.9l5.6 5.9a.7.7 0 0 0 1 0l5.6-5.9c1.5-1.6 1.5-4.1 0-5.7C12.7-.3 9.8-.6 8 1.1z" />
      ) : (
        <path d="M8 1.8c-1.4-1.4-3.7-1.3-5 .1-1.2 1.3-1.2 3.4 0 4.7L8 11.8l5-5.2c1.2-1.3 1.2-3.4 0-4.7-1.3-1.4-3.6-1.5-5-.1zm0 1.1a2.8 2.8 0 0 1 4.2-.1c.9 1 .9 2.5 0 3.5L8 10.8 3.8 6.3a2.5 2.5 0 0 1 0-3.5A2.8 2.8 0 0 1 8 2.9z" />
      )}
    </svg>
  );
}

function LikeToggleButton({
  liked,
  count,
  onClick,
  disabled = false,
  compact = true,
}: {
  liked: boolean;
  count: number;
  onClick: () => void | Promise<void>;
  disabled?: boolean;
  compact?: boolean;
}) {
  return (
    <button
      type="button"
      className={compact ? `ghost-button small like-toggle${liked ? ' active' : ''}` : `like-toggle large${liked ? ' active' : ''}`}
      onClick={() => onClick()}
      disabled={disabled}
    >
      <HeartIcon filled={liked} />
      <span>{formatCount(count)}</span>
    </button>
  );
}

function VideoCard({
  video,
  onOpen,
  onOpenComments,
  onLike,
  onOpenAuthor,
  liked,
  commentMode = false,
  canFollow = false,
  following = false,
  onFollow,
}: VideoCardProps) {
  const author = getVideoAuthor(video);
  const authorName =
    (author.username || '').trim() && author.username !== '匿名用户'
      ? author.username.trim()
      : `${video.user_id || video.id}`.trim();
  return (
    <article className="feed-card">
      <div className="feed-card-head">
        <AvatarChip name={author.username} src={author.avatarUrl} />
        <div className="feed-meta">
          <div className="feed-meta-top">
            <button
              type="button"
              className="author-link"
              onClick={() =>
                onOpenAuthor?.({
                  id: author.id,
                  username: author.username,
                  avatar_url: author.avatarUrl,
                })
              }
            >
              @{authorName}
            </button>
            {canFollow ? (
              <button
                type="button"
                className={following ? 'follow-chip following' : 'follow-chip'}
                onClick={() => onFollow?.(video)}
                disabled={following}
              >
                {following ? '已关注' : '关注'}
              </button>
            ) : null}
          </div>
          <p className="feed-copy">{video.description || '暂无简介。'}</p>
        </div>
      </div>

      <button className="media-button" onClick={() => onOpen(video)}>
        <img src={video.cover_url} alt={video.title} />
      </button>

      <div className="metric-row">
        <span>{formatCount(video.visit_count)} 播放</span>
        <span>{formatCount(video.like_count)} 点赞</span>
        <span>{formatCount(video.comment_count)} 评论</span>
      </div>

      <div className="card-actions">
        <button className="ghost-button small" onClick={() => (commentMode ? onOpenComments(video) : onOpen(video))}>
          {commentMode ? '评论' : '查看'}
        </button>
        <LikeToggleButton
          liked={liked}
          count={video.like_count}
          onClick={() => onLike(video)}
        />
      </div>
    </article>
  );
}

function VideoRow({
  video,
  onOpen,
  onLike,
  liked,
  onOpenAuthor,
}: {
  video: VideoItem;
  onOpen: (video: VideoItem) => void | Promise<void>;
  onLike: (video: VideoItem) => void | Promise<void>;
  liked: boolean;
  onOpenAuthor?: (user: SocialUser) => void | Promise<void>;
}) {
  const author = getVideoAuthor(video);
  return (
    <article className="video-row">
      <img src={video.cover_url} alt={video.title} />
      <div>
        <h4>{video.title}</h4>
        <p>{video.description || '暂无简介。'}</p>
        <button
          type="button"
          className="author-link subtle"
          onClick={() =>
            onOpenAuthor?.({
              id: author.id,
              username: author.username,
              avatar_url: author.avatarUrl,
            })
          }
        >
          @{author.username}
        </button>
        <div className="metric-row">
          <span>{formatCount(video.visit_count)} 播放</span>
          <span>{formatCount(video.like_count)} 点赞</span>
          <span>{formatCount(video.comment_count)} 评论</span>
        </div>
      </div>
      <div className="row-actions">
        <button type="button" className="ghost-button small" onClick={() => onOpen(video)}>
          打开
        </button>
        <LikeToggleButton
          liked={liked}
          count={video.like_count}
          onClick={() => onLike(video)}
        />
      </div>
    </article>
  );
}

function AvatarChip({ name, src, size = 'md' }: { name: string; src?: string; size?: 'sm' | 'md' }) {
  const [imgBroken, setImgBroken] = useState(false);
  const avatarClass = size === 'sm' ? 'avatar-image avatar-image-sm' : 'avatar-image';
  const badgeClass = size === 'sm' ? 'avatar-badge avatar-badge-sm' : 'avatar-badge';
  if (src && !imgBroken) {
    return <img className={avatarClass} src={src} alt={name} onError={() => setImgBroken(true)} />;
  }
  return <div className={badgeClass}>{(name || 'U').slice(0, 1).toUpperCase()}</div>;
}

function SocialList({
  title,
  items,
  onOpenUser,
}: {
  title: string;
  items: SocialUser[];
  onOpenUser?: (user: SocialUser) => void | Promise<void>;
}) {
  return (
    <div className="social-list">
      <h4>{title}</h4>
      {items.length ? (
        items.map((user) => (
          <button
            type="button"
            key={user.id}
            className="social-user interactive"
            onClick={() => onOpenUser?.(user)}
          >
            <img src={user.avatar_url} alt={user.username} />
            <div>
              <strong>{user.username}</strong>
              <span>ID {user.id}</span>
            </div>
          </button>
        ))
      ) : (
        <EmptyState text="暂无数据" compact />
      )}
    </div>
  );
}

function EmptyState({ text, compact = false }: { text: string; compact?: boolean }) {
  return <div className={compact ? 'empty-state compact' : 'empty-state'}>{text}</div>;
}

export default App;
