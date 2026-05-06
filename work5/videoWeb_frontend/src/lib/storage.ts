import type { AuthUser, VideoItem } from '../types';

const AUTH_KEY = 'video-web.auth';
const API_BASE_KEY = 'video-web.api-base';
const VIDEO_CACHE_KEY = 'video-web.video-cache';

export function loadAuth(): AuthUser | null {
  const raw = window.localStorage.getItem(AUTH_KEY);
  if (!raw) {
    return null;
  }

  try {
    return JSON.parse(raw) as AuthUser;
  } catch {
    window.localStorage.removeItem(AUTH_KEY);
    return null;
  }
}

export function saveAuth(user: AuthUser | null) {
  if (!user) {
    window.localStorage.removeItem(AUTH_KEY);
    return;
  }

  window.localStorage.setItem(AUTH_KEY, JSON.stringify(user));
}

export function loadApiBaseUrl(defaultValue: string) {
  return window.localStorage.getItem(API_BASE_KEY) ?? defaultValue;
}

export function saveApiBaseUrl(value: string) {
  window.localStorage.setItem(API_BASE_KEY, value);
}

export function rememberVideos(videos: VideoItem[]) {
  const next = new Map<string, VideoItem>();
  const current = loadRememberedVideos();

  for (const video of current) {
    next.set(video.id, video);
  }

  for (const video of videos) {
    next.set(video.id, video);
  }

  window.localStorage.setItem(
    VIDEO_CACHE_KEY,
    JSON.stringify(Array.from(next.values()).slice(-80)),
  );
}

export function loadRememberedVideos(): VideoItem[] {
  const raw = window.localStorage.getItem(VIDEO_CACHE_KEY);
  if (!raw) {
    return [];
  }

  try {
    return JSON.parse(raw) as VideoItem[];
  } catch {
    window.localStorage.removeItem(VIDEO_CACHE_KEY);
    return [];
  }
}
