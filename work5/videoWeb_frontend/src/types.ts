export type ApiEnvelope<T> = {
  code: number;
  msg: string;
  data?: T;
};

export type AuthUser = {
  id: string;
  username: string;
  accessToken: string;
  refreshToken: string;
  avatarUrl: string;
  role?: string;
};

export type VideoItem = {
  id: string;
  user_id: string;
  video_url: string;
  cover_url: string;
  publisher?: {
    id: string;
    username: string;
    avatar_url: string;
  };
  is_liked?: boolean;
  title: string;
  description: string;
  visit_count: number;
  like_count: number;
  comment_count: number;
  created_at?: string;
  updated_at?: string;
  deleted_at?: string | null;
};

export type CommentItem = {
  id: string;
  user_id: string;
  video_id: string;
  parent_id: string;
  like_count: number;
  child_count: number;
  content: string;
  created_at?: string;
  updated_at?: string;
  deleted_at?: string | null;
};

export type UserProfile = {
  id: string;
  username: string;
  avatar_url: string;
  created_at?: string;
  updated_at?: string;
  deleted_at?: string | null;
};

export type SocialUser = {
  id: string;
  username: string;
  avatar_url: string;
  blocked?: boolean;
};

export type ListPayload<T> = {
  items: T[];
  total?: number;
};

export type PublicUserHub = {
  profile: SocialUser;
  uploads: VideoItem[];
  likedVideos: VideoItem[];
  followers: SocialUser[];
  followings: SocialUser[];
  isSelf: boolean;
  isFollowing: boolean;
};

export type ContactItem = SocialUser & {
  blocked: boolean;
};

export type SessionItem = {
  conversation_id: string;
  conversation_type: string;
  conversation_name: string;
  conversation_avatar?: string;
  target_user_id?: string;
  last_message?: string;
  last_message_type?: string;
  last_message_time?: string;
  blocked?: boolean;
};

export type ChatMessageItem = {
  message_id: string;
  conversation_id: string;
  sender_id: string;
  sender_name: string;
  sender_avatar?: string;
  message_type: string;
  content: string;
  sent_at?: string;
  self?: boolean | null;
};

export type VideoAuditItem = {
  video_id: string;
  title: string;
  description: string;
  video_url: string;
  cover_url: string;
  author_id: string;
  author_name?: string;
  audit_status: string;
  audit_reason?: string;
  audit_by?: string;
  audit_at?: string;
  created_at?: string;
};
