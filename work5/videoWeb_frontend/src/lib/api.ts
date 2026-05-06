import type {
  ApiEnvelope,
  AuthUser,
  ChatMessageItem,
  CommentItem,
  ContactItem,
  ListPayload,
  PublicUserHub,
  SocialUser,
  SessionItem,
  UserProfile,
  VideoItem,
  VideoAuditItem,
} from '../types';
import { saveAuth } from './storage';

type RequestOptions = {
  method?: 'GET' | 'POST' | 'PUT' | 'DELETE';
  query?: Record<string, string | number | undefined>;
  formData?: Record<string, string | number | File | Array<string | number> | undefined>;
  auth?: AuthUser | null;
  headers?: Record<string, string | undefined>;
};

const SUCCESS_CODES = new Set([10000, 200]);

function pickValue<T>(...values: Array<T | undefined | null>) {
  return values.find((value) => value !== undefined && value !== null);
}

function normalizeMediaUrl(input: unknown) {
  if (typeof input !== 'string' || !input) {
    return '';
  }

  try {
    const parsed = new URL(input, window.location.origin);
    if (
      parsed.pathname.startsWith('/upload/') &&
      (
        parsed.hostname === window.location.hostname ||
        parsed.hostname === '127.0.0.1' ||
        parsed.hostname === 'localhost' ||
        parsed.port === '9090'
      )
    ) {
      return `${parsed.pathname}${parsed.search}${parsed.hash}`;
    }
    return parsed.toString();
  } catch {
    if (input.startsWith('/upload/')) {
      return input;
    }
    return input;
  }
}

function toNumber(value: unknown) {
  if (typeof value === 'number') {
    return value;
  }
  if (typeof value === 'string' && value.trim()) {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }
  return 0;
}

function mapVideoItem(raw: any): VideoItem {
  const source = pickValue(raw?.video, raw?.videoInfo, raw?.video_detail, raw) ?? raw;
  const publisher = pickValue(source?.publisher, raw?.publisher);
  const likedRaw = pickValue(source?.is_liked, source?.isLiked, raw?.is_liked, raw?.isLiked);
  const publisherId = `${pickValue(publisher?.id, source?.publisher_id, source?.user_id, raw?.user_id) ?? ''}`;
  const publisherUsername = `${pickValue(
    publisher?.username,
    source?.publisher_username,
    source?.username,
    raw?.username,
  ) ?? ''}`;
  const publisherAvatar = normalizeMediaUrl(
    pickValue(publisher?.avatar_url, publisher?.avatarUrl, source?.publisher_avatar_url, raw?.publisher_avatar_url),
  );
  const isLiked =
    typeof likedRaw === 'boolean'
      ? likedRaw
      : likedRaw === 1 || likedRaw === '1'
        ? true
        : likedRaw === 0 || likedRaw === '0'
          ? false
          : undefined;

  return {
    id: `${pickValue(source?.id, raw?.video_id, raw?.id) ?? ''}`,
    user_id: `${pickValue(source?.user_id, source?.userId, source?.publisher?.id, raw?.user_id, raw?.userId) ?? ''}`,
    video_url: normalizeMediaUrl(pickValue(source?.video_url, source?.videoUrl, raw?.video_url, raw?.videoUrl)),
    cover_url: normalizeMediaUrl(pickValue(source?.cover_url, source?.coverUrl, raw?.cover_url, raw?.coverUrl)),
    publisher:
      publisherId || publisherUsername || publisherAvatar
        ? {
            id: publisherId,
            username: publisherUsername,
            avatar_url: publisherAvatar,
          }
        : undefined,
    is_liked: isLiked,
    title: `${pickValue(source?.title, raw?.title, '') ?? ''}`,
    description: `${pickValue(source?.description, raw?.description, '') ?? ''}`,
    visit_count: toNumber(pickValue(source?.visit_count, source?.visitCount, raw?.visit_count, raw?.visitCount)),
    like_count: toNumber(pickValue(source?.like_count, source?.likeCount, raw?.like_count, raw?.likeCount)),
    comment_count: toNumber(
      pickValue(source?.comment_count, source?.commentCount, raw?.comment_count, raw?.commentCount),
    ),
    created_at: pickValue(source?.created_at, source?.createdAt, raw?.created_at, raw?.createdAt) ?? undefined,
    updated_at: pickValue(source?.updated_at, source?.updatedAt, raw?.updated_at, raw?.updatedAt) ?? undefined,
    deleted_at: pickValue(source?.deleted_at, source?.deletedAt, raw?.deleted_at, raw?.deletedAt) ?? undefined,
  };
}

function mapVideoDetail(raw: any): VideoItem {
  return mapVideoItem(raw);
}

function mapCommentItem(raw: any): CommentItem {
  return {
    id: `${pickValue(raw?.id, raw?.comment_id) ?? ''}`,
    user_id: `${pickValue(raw?.user_id, raw?.userId) ?? ''}`,
    video_id: `${pickValue(raw?.video_id, raw?.videoId) ?? ''}`,
    parent_id: `${pickValue(raw?.parent_id, raw?.parentId, '0') ?? '0'}`,
    like_count: toNumber(pickValue(raw?.like_count, raw?.likeCount)),
    child_count: toNumber(pickValue(raw?.child_count, raw?.childCount)),
    content: `${pickValue(raw?.content, '') ?? ''}`,
    created_at: pickValue(raw?.created_at, raw?.createdAt) ?? undefined,
    updated_at: pickValue(raw?.updated_at, raw?.updatedAt) ?? undefined,
    deleted_at: pickValue(raw?.deleted_at, raw?.deletedAt) ?? undefined,
  };
}

function mapSocialUser(raw: any): SocialUser {
  return {
    id: `${pickValue(raw?.id, raw?.user_id, raw?.userId) ?? ''}`,
    username: `${pickValue(raw?.username, raw?.name, '') ?? ''}`,
    avatar_url: normalizeMediaUrl(pickValue(raw?.avatar_url, raw?.avatarUrl)),
    blocked:
      pickValue(raw?.blocked, raw?.is_blocked) === true ||
      pickValue(raw?.blocked, raw?.is_blocked) === 1 ||
      pickValue(raw?.blocked, raw?.is_blocked) === '1',
  };
}

function mapContactItem(raw: any): ContactItem {
  const user = mapSocialUser(raw);
  return {
    ...user,
    blocked:
      pickValue(raw?.blocked, raw?.is_blocked) === true ||
      pickValue(raw?.blocked, raw?.is_blocked) === 1 ||
      pickValue(raw?.blocked, raw?.is_blocked) === '1',
  };
}

function mapSessionItem(raw: any): SessionItem {
  return {
    conversation_id: `${pickValue(raw?.conversation_id, raw?.conversationId, raw?.id) ?? ''}`,
    conversation_type: `${pickValue(raw?.conversation_type, raw?.conversationType, '') ?? ''}`,
    conversation_name: `${pickValue(raw?.conversation_name, raw?.conversationName, raw?.name, '') ?? ''}`,
    conversation_avatar: normalizeMediaUrl(
      pickValue(raw?.conversation_avatar, raw?.conversationAvatar, raw?.avatar_url, raw?.avatarUrl),
    ),
    target_user_id: `${pickValue(raw?.target_user_id, raw?.targetUserId, '') ?? ''}`,
    last_message: `${pickValue(raw?.last_message, raw?.lastMessage, '') ?? ''}`,
    last_message_type: `${pickValue(raw?.last_message_type, raw?.lastMessageType, '') ?? ''}`,
    last_message_time: pickValue(raw?.last_message_time, raw?.lastMessageTime) ?? undefined,
    blocked:
      pickValue(raw?.blocked, raw?.is_blocked) === true ||
      pickValue(raw?.blocked, raw?.is_blocked) === 1 ||
      pickValue(raw?.blocked, raw?.is_blocked) === '1',
  };
}

function mapChatMessageItem(raw: any): ChatMessageItem {
  return {
    message_id: `${pickValue(raw?.message_id, raw?.messageId, raw?.id) ?? ''}`,
    conversation_id: `${pickValue(raw?.conversation_id, raw?.conversationId) ?? ''}`,
    sender_id: `${pickValue(raw?.sender_id, raw?.senderId, raw?.user_id) ?? ''}`,
    sender_name: `${pickValue(raw?.sender_name, raw?.senderName, raw?.username, '') ?? ''}`,
    sender_avatar: normalizeMediaUrl(
      pickValue(raw?.sender_avatar, raw?.senderAvatar, raw?.avatar_url, raw?.avatarUrl),
    ),
    message_type: `${pickValue(raw?.message_type, raw?.messageType, 'TEXT') ?? 'TEXT'}`,
    content: `${pickValue(raw?.content, '') ?? ''}`,
    sent_at: pickValue(raw?.sent_at, raw?.sentAt, raw?.created_at) ?? undefined,
    self:
      pickValue(raw?.self, raw?.is_self) === true ||
      pickValue(raw?.self, raw?.is_self) === 1 ||
      pickValue(raw?.self, raw?.is_self) === '1',
  };
}

function mapVideoAuditItem(raw: any): VideoAuditItem {
  return {
    video_id: `${pickValue(raw?.video_id, raw?.videoId, raw?.id) ?? ''}`,
    title: `${pickValue(raw?.title, '') ?? ''}`,
    description: `${pickValue(raw?.description, '') ?? ''}`,
    video_url: normalizeMediaUrl(pickValue(raw?.video_url, raw?.videoUrl)),
    cover_url: normalizeMediaUrl(pickValue(raw?.cover_url, raw?.coverUrl)),
    author_id: `${pickValue(raw?.author_id, raw?.authorId, raw?.user_id) ?? ''}`,
    author_name: `${pickValue(raw?.author_name, raw?.authorName, raw?.username, '') ?? ''}`,
    audit_status: `${pickValue(raw?.audit_status, raw?.auditStatus, '') ?? ''}`,
    audit_reason: `${pickValue(raw?.audit_reason, raw?.auditReason, '') ?? ''}`,
    audit_by: `${pickValue(raw?.audit_by, raw?.auditBy, '') ?? ''}`,
    audit_at: pickValue(raw?.audit_at, raw?.auditAt) ?? undefined,
    created_at: pickValue(raw?.created_at, raw?.createdAt) ?? undefined,
  };
}

function mapListPayload<T>(raw: any, mapper: (item: any) => T): ListPayload<T> {
  const list = Array.isArray(raw?.items)
    ? raw.items
    : Array.isArray(raw?.records)
      ? raw.records
      : [];
  const totalRaw = pickValue(raw?.total, raw?.count);
  return {
    items: list.map(mapper),
    total: typeof totalRaw === 'number' ? totalRaw : undefined,
  };
}

function buildUrl(baseUrl: string, path: string, query?: RequestOptions['query']) {
  const normalizedBase = baseUrl.replace(/\/$/, '');
  const url = new URL(`${normalizedBase}${path}`, window.location.origin);

  if (query) {
    for (const [key, value] of Object.entries(query)) {
      if (value === undefined || value === '') {
        continue;
      }
      url.searchParams.set(key, `${value}`);
    }
  }

  return url.toString();
}

function broadcastAuthChanged(auth: AuthUser | null) {
  saveAuth(auth);
  window.dispatchEvent(new CustomEvent('video-web:auth-changed', { detail: auth }));
}

function isJsonLike(contentType: string, raw: string) {
  if (contentType.includes('application/json')) {
    return true;
  }
  const trimmed = raw.trim();
  return trimmed.startsWith('{') || trimmed.startsWith('[');
}

function isSuccessCode(code: unknown) {
  const value = Number(code);
  return Number.isFinite(value) && SUCCESS_CODES.has(value);
}

async function request<T>(
  baseUrl: string,
  path: string,
  options: RequestOptions = {},
  allowRefresh = true,
) {
  const headers = new Headers();
  if (options.auth?.accessToken) {
    headers.set('Access-Token', options.auth.accessToken);
  }
  if (options.auth?.refreshToken) {
    headers.set('Refresh-Token', options.auth.refreshToken);
  }
  if (options.headers) {
    for (const [key, value] of Object.entries(options.headers)) {
      if (value) {
        headers.set(key, value);
      }
    }
  }

  let body: FormData | undefined;
  if (options.formData) {
    body = new FormData();
    for (const [key, value] of Object.entries(options.formData)) {
      if (value === undefined || value === '') {
        continue;
      }
      if (Array.isArray(value)) {
        for (const item of value) {
          body.append(key, `${item}`);
        }
        continue;
      }
      body.append(key, value instanceof File ? value : `${value}`);
    }
  }

  const response = await fetch(buildUrl(baseUrl, path, options.query), {
    method: options.method ?? 'GET',
    headers,
    body,
  });

  const raw = await response.text();
  const contentType = (response.headers.get('content-type') ?? '').toLowerCase();

  if (response.status === 403 && allowRefresh && options.auth?.refreshToken && path !== '/user/refreshToken') {
    try {
      const nextAccessToken = await refreshAccessToken(baseUrl, options.auth.refreshToken);
      const nextAuth = { ...options.auth, accessToken: nextAccessToken };
      broadcastAuthChanged(nextAuth);
      return request<T>(baseUrl, path, { ...options, auth: nextAuth }, false);
    } catch {
      broadcastAuthChanged(null);
      throw new Error('登录已过期，请重新登录。');
    }
  }

  let payload: ApiEnvelope<T> | null = null;
  if (raw && isJsonLike(contentType, raw)) {
    try {
      payload = JSON.parse(raw) as ApiEnvelope<T>;
    } catch {
      payload = null;
    }
  }

  if (!payload) {
    if (response.status === 413) {
      throw new Error('上传文件过大，请压缩视频后重试（或联系管理员调大上传限制）。');
    }
    if (!response.ok) {
      throw new Error(`请求失败: ${response.status}`);
    }
    throw new Error('服务端返回了无法识别的数据格式，请稍后重试。');
  }

  if (!response.ok || !isSuccessCode(payload.code)) {
    throw new Error(payload.msg || `请求失败: ${response.status}`);
  }

  return payload;
}

async function refreshAccessToken(baseUrl: string, refreshToken: string) {
  const payload = await request<any>(
    baseUrl,
    '/user/refreshToken',
    {
      method: 'POST',
      headers: {
        'Refresh-Token': refreshToken,
      },
    },
    false,
  );

  const nextToken = pickValue(
    payload.data?.accessToken,
    payload.data?.access_token,
  );
  if (typeof nextToken !== 'string' || !nextToken) {
    throw new Error('刷新登录状态失败');
  }
  return nextToken;
}

type AuthPayload = {
  id?: string | number;
  user_id?: string | number;
  username?: string;
  access_token?: string;
  refresh_token?: string;
  avatar_url?: string;
  role?: string;
};

export const api = {
  register(baseUrl: string, username: string, password: string) {
    return request<undefined>(baseUrl, '/user/register', {
      method: 'POST',
      formData: { username, password },
    });
  },
  async login(baseUrl: string, username: string, password: string) {
    const payload = await request<AuthPayload>(baseUrl, '/user/login', {
      method: 'POST',
      formData: { username, password },
    });

    const data = payload.data;
    if (!data) {
      throw new Error('登录成功但未返回用户信息');
    }

    const id = `${pickValue(data.id, data.user_id) ?? ''}`;
    const accessToken = `${pickValue(data.access_token, '') ?? ''}`;
    const refreshToken = `${pickValue(data.refresh_token, '') ?? ''}`;
    if (!id || !accessToken || !refreshToken) {
      throw new Error('登录返回数据不完整，请稍后重试');
    }

    return {
      id,
      username: `${pickValue(data.username, '') ?? ''}`,
      accessToken,
      refreshToken,
      avatarUrl: normalizeMediaUrl(data.avatar_url),
      role: `${pickValue(data.role, 'USER') ?? 'USER'}`,
    } satisfies AuthUser;
  },
  async getProfile(baseUrl: string, auth: AuthUser) {
    const payload = await request<any>(baseUrl, '/user/info', { auth });
    if (!payload.data) {
      throw new Error('未获取到用户信息');
    }
    return {
      id: `${pickValue(payload.data.id, payload.data.user_id) ?? ''}`,
      username: `${pickValue(payload.data.username, '') ?? ''}`,
      avatar_url: normalizeMediaUrl(pickValue(payload.data.avatar_url, payload.data.avatarUrl)),
      created_at: pickValue(payload.data.created_at, payload.data.createdAt) ?? undefined,
      updated_at: pickValue(payload.data.updated_at, payload.data.updatedAt) ?? undefined,
      deleted_at: pickValue(payload.data.deleted_at, payload.data.deletedAt) ?? undefined,
    } satisfies UserProfile;
  },
  async uploadAvatar(baseUrl: string, auth: AuthUser, file: File) {
    const payload = await request<any>(
      baseUrl,
      '/user/avatar/upload',
      {
        method: 'PUT',
        formData: { data: file },
        auth,
      },
    );

    if (!payload.data) {
      throw new Error('头像上传成功但未返回用户信息');
    }

    return {
      id: `${pickValue(payload.data.id, payload.data.user_id) ?? ''}`,
      username: `${pickValue(payload.data.username, '') ?? ''}`,
      avatar_url: normalizeMediaUrl(pickValue(payload.data.avatar_url, payload.data.avatarUrl)),
      created_at: pickValue(payload.data.created_at, payload.data.createdAt) ?? undefined,
      updated_at: pickValue(payload.data.updated_at, payload.data.updatedAt) ?? undefined,
      deleted_at: pickValue(payload.data.deleted_at, payload.data.deletedAt) ?? undefined,
    } satisfies UserProfile;
  },
  async getVideoList(baseUrl: string, pageNum: number, pageSize: number, userId?: string, auth?: AuthUser | null) {
    const payload = await request<any>(baseUrl, '/video/list', {
      query: { page_num: pageNum, page_size: pageSize, user_id: userId },
      auth,
    });
    return mapListPayload(payload.data, mapVideoItem);
  },
  async getPopularVideos(baseUrl: string, pageNum: number, pageSize: number, auth?: AuthUser | null) {
    const payload = await request<any>(baseUrl, '/video/popular', {
      query: { page_num: pageNum, page_size: pageSize },
      auth,
    });
    return mapListPayload(payload.data, mapVideoItem);
  },
  async getVideoDetail(baseUrl: string, videoId: string, auth?: AuthUser | null) {
    let payload: ApiEnvelope<any>;
    try {
      payload = await request<any>(baseUrl, '/video/detail', {
        query: { video_id: videoId },
        auth,
      });
    } catch (error) {
      if (!auth) {
        throw error;
      }
      payload = await request<any>(baseUrl, '/video/detail', {
        query: { video_id: videoId },
      });
    }

    if (!payload.data) {
      throw new Error('未获取到视频详情');
    }

    return mapVideoDetail(payload.data);
  },
  async searchVideos(
    baseUrl: string,
    params: {
      keywords: string;
      pageNum: number;
      pageSize: number;
      fromDate?: number;
      toDate?: number;
      username?: string;
    },
    auth?: AuthUser | null,
  ) {
    const payload = await request<any>(baseUrl, '/video/search', {
      method: 'POST',
      formData: {
        keywords: params.keywords,
        page_num: params.pageNum,
        page_size: params.pageSize,
        from_date: params.fromDate,
        to_date: params.toDate,
        username: params.username,
      },
      auth,
    });
    return mapListPayload(payload.data, mapVideoItem);
  },
  publishVideo(
    baseUrl: string,
    auth: AuthUser,
    values: {
      title: string;
      description: string;
      videoFile: File;
      coverFile?: File;
    },
  ) {
    return request<undefined>(baseUrl, '/video/publish', {
      method: 'POST',
      formData: {
        title: values.title,
        description: values.description,
        videoFile: values.videoFile,
        coverFile: values.coverFile,
      },
      auth,
    });
  },
  async getComments(baseUrl: string, videoId: string, pageNum: number, pageSize: number, auth?: AuthUser | null) {
    const payload = await request<any>(baseUrl, '/comment/list', {
      query: { video_id: videoId, page_num: pageNum, page_size: pageSize },
      auth,
    });
    const list = Array.isArray(payload.data?.items)
      ? payload.data.items
      : Array.isArray(payload.data?.records)
        ? payload.data.records
        : [];
    return list.map(mapCommentItem);
  },
  publishComment(baseUrl: string, auth: AuthUser, videoId: string, content: string, parentId = '0') {
    return request<undefined>(baseUrl, '/comment/publish', {
      method: 'POST',
      formData: { video_id: videoId, content, parent_id: parentId },
      auth,
    });
  },
  deleteComment(baseUrl: string, auth: AuthUser, commentId: string) {
    return request<undefined>(baseUrl, '/comment/delete', {
      method: 'DELETE',
      formData: { comment_id: commentId },
      auth,
    });
  },
  likeVideo(baseUrl: string, auth: AuthUser, videoId: string, actionType: '1' | '2') {
    return request<undefined>(baseUrl, '/like/video', {
      method: 'POST',
      formData: { video_id: videoId, action_type: actionType },
      auth,
    });
  },
  likeComment(baseUrl: string, auth: AuthUser, commentId: string, type: '1' | '2') {
    return request<undefined>(baseUrl, '/like/comment', {
      method: 'POST',
      query: { comment_id: commentId, type },
      auth,
    });
  },
  async getLikedVideos(baseUrl: string, auth: AuthUser, pageNum: number, pageSize: number, userId?: string) {
    const payload = await request<any>(baseUrl, '/like/list', {
      query: { user_id: userId, page_num: pageNum, page_size: pageSize },
      auth,
    });
    return mapListPayload(payload.data, mapVideoItem);
  },
  followUser(baseUrl: string, auth: AuthUser, toUserId: string) {
    return request<undefined>(baseUrl, '/relation/action', {
      method: 'POST',
      formData: { to_user_id: toUserId },
      auth,
    });
  },
  async getFollowers(baseUrl: string, auth: AuthUser, userId: string, pageNum: number, pageSize: number) {
    const payload = await request<any>(baseUrl, '/relation/fans/list', {
      query: { user_id: userId, page_num: pageNum, page_size: pageSize },
      auth,
    });
    return mapListPayload(payload.data, mapSocialUser);
  },
  async getFollowings(baseUrl: string, auth: AuthUser, userId: string, pageNum: number, pageSize: number) {
    const payload = await request<any>(baseUrl, '/relation/following/list', {
      query: { user_id: userId, page_num: pageNum, page_size: pageSize },
      auth,
    });
    return mapListPayload(payload.data, mapSocialUser);
  },
  async getFriends(baseUrl: string, auth: AuthUser, pageNum: number, pageSize: number) {
    const payload = await request<any>(baseUrl, '/relation/friends/list', {
      query: { page_num: pageNum, page_size: pageSize },
      auth,
    });
    return mapListPayload(payload.data, mapSocialUser);
  },
  async getContacts(baseUrl: string, auth: AuthUser, pageNum: number, pageSize: number) {
    const payload = await request<any>(baseUrl, '/contact/list', {
      query: { page_num: pageNum, page_size: pageSize },
      auth,
    });
    return mapListPayload(payload.data, mapContactItem);
  },
  async getBlockedContacts(baseUrl: string, auth: AuthUser, pageNum: number, pageSize: number) {
    const payload = await request<any>(baseUrl, '/contact/blocked/list', {
      query: { page_num: pageNum, page_size: pageSize },
      auth,
    });
    return mapListPayload(payload.data, mapContactItem);
  },
  blockContact(baseUrl: string, auth: AuthUser, targetUserId: string) {
    return request<undefined>(baseUrl, '/contact/block', {
      method: 'POST',
      formData: { target_user_id: targetUserId },
      auth,
    });
  },
  unblockContact(baseUrl: string, auth: AuthUser, targetUserId: string) {
    return request<undefined>(baseUrl, '/contact/unblock', {
      method: 'POST',
      formData: { target_user_id: targetUserId },
      auth,
    });
  },
  async createSingleSession(baseUrl: string, auth: AuthUser, targetUserId: string) {
    const payload = await request<any>(baseUrl, '/session/single', {
      method: 'POST',
      formData: { target_user_id: targetUserId },
      auth,
    });
    return {
      conversation_id: `${pickValue(payload.data?.conversation_id, payload.data?.conversationId) ?? ''}`,
      existed: Boolean(pickValue(payload.data?.existed, false)),
    };
  },
  async createGroupSession(baseUrl: string, auth: AuthUser, groupName: string, memberIds: string[]) {
    const payload = await request<any>(baseUrl, '/session/group', {
      method: 'POST',
      formData: { group_name: groupName, member_ids: memberIds },
      auth,
    });
    return {
      conversation_id: `${pickValue(payload.data?.conversation_id, payload.data?.conversationId) ?? ''}`,
      existed: Boolean(pickValue(payload.data?.existed, false)),
    };
  },
  async getSessionList(baseUrl: string, auth: AuthUser, pageNum: number, pageSize: number) {
    const payload = await request<any>(baseUrl, '/session/list', {
      query: { page_num: pageNum, page_size: pageSize },
      auth,
    });
    return mapListPayload(payload.data, mapSessionItem);
  },
  async getMessageHistory(
    baseUrl: string,
    auth: AuthUser,
    conversationId: string,
    pageNum: number,
    pageSize: number,
    startTime?: string,
    endTime?: string,
  ) {
    const payload = await request<any>(baseUrl, '/message/history', {
      query: {
        conversation_id: conversationId,
        page_num: pageNum,
        page_size: pageSize,
        start_time: startTime,
        end_time: endTime,
      },
      auth,
    });
    return mapListPayload(payload.data, mapChatMessageItem);
  },
  async sendMessage(
    baseUrl: string,
    auth: AuthUser,
    conversationId: string,
    messageType: 'TEXT' | 'IMAGE',
    content: string,
  ) {
    const payload = await request<any>(baseUrl, '/message/send', {
      method: 'POST',
      formData: {
        conversation_id: conversationId,
        message_type: messageType,
        content,
      },
      auth,
    });
    if (!payload.data) {
      throw new Error('消息发送成功但未返回消息体');
    }
    return mapChatMessageItem(payload.data);
  },
  async getPendingAuditVideos(baseUrl: string, auth: AuthUser, pageNum: number, pageSize: number) {
    const payload = await request<any>(baseUrl, '/video/audit/pending', {
      query: { page_num: pageNum, page_size: pageSize },
      auth,
    });
    return mapListPayload(payload.data, mapVideoAuditItem);
  },
  reviewVideo(
    baseUrl: string,
    auth: AuthUser,
    videoId: string,
    auditStatus: 'APPROVED' | 'REJECTED',
    auditReason?: string,
  ) {
    return request<undefined>(baseUrl, '/video/audit/review', {
      method: 'POST',
      formData: {
        video_id: videoId,
        audit_status: auditStatus,
        audit_reason: auditReason,
      },
      auth,
    });
  },
  async getPublicUserHub(baseUrl: string, auth: AuthUser | null, user: SocialUser) {
    const [uploadsById, uploadsByName, likedVideos, followers, followings] = await Promise.all([
      user.id
        ? request<any>(baseUrl, '/video/list', {
            query: { user_id: user.id, page_num: 1, page_size: 20 },
            auth,
          }).then((payload) => mapListPayload(payload.data, mapVideoItem))
        : Promise.resolve({ items: [], total: 0 }),
      user.username
        ? request<any>(baseUrl, '/video/search', {
            method: 'POST',
            formData: { keywords: '', username: user.username, page_num: 1, page_size: 20 },
            auth,
          }).then((payload) => mapListPayload(payload.data, mapVideoItem))
        : Promise.resolve({ items: [], total: 0 }),
      user.id
        ? request<any>(baseUrl, '/like/list', {
            query: { user_id: user.id, page_num: 1, page_size: 20 },
            auth,
          }).then((payload) => mapListPayload(payload.data, mapVideoItem))
        : Promise.resolve({ items: [], total: 0 }),
      user.id
        ? request<any>(baseUrl, '/relation/fans/list', {
            query: { user_id: user.id, page_num: 1, page_size: 20 },
            auth,
          }).then((payload) => mapListPayload(payload.data, mapSocialUser))
        : Promise.resolve({ items: [], total: 0 }),
      user.id
        ? request<any>(baseUrl, '/relation/following/list', {
            query: { user_id: user.id, page_num: 1, page_size: 20 },
            auth,
          }).then((payload) => mapListPayload(payload.data, mapSocialUser))
        : Promise.resolve({ items: [], total: 0 }),
    ]);

    const uniqueVideoMap = new Map<string, VideoItem>();
    for (const item of [...uploadsById.items, ...uploadsByName.items]) {
      if (item.id) {
        uniqueVideoMap.set(item.id, item);
      }
    }

    const profileFromVideos = Array.from(uniqueVideoMap.values()).find((item) => item.publisher?.id || item.publisher?.username);
    const profile: SocialUser = {
      id: user.id || profileFromVideos?.publisher?.id || '',
      username: user.username || profileFromVideos?.publisher?.username || '未知用户',
      avatar_url: user.avatar_url || profileFromVideos?.publisher?.avatar_url || '',
    };

    return {
      profile,
      uploads: Array.from(uniqueVideoMap.values()),
      likedVideos: likedVideos.items,
      followers: followers.items,
      followings: followings.items,
      isSelf: Boolean(auth && profile.id && auth.id === profile.id),
      isFollowing: false,
    } satisfies PublicUserHub;
  },
};
