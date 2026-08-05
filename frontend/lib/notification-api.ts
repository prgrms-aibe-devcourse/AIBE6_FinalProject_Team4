import { request, SpringPage } from '@/lib/api';

export type NotificationType = 'DELIVERY' | 'COMMUNITY' | 'POINT' | 'NOTICE' | 'INQUIRY' | 'JOURNAL_REMINDER' | 'TIMELAPSE';

export interface NotificationData {
  id: number;
  userId: number;
  type: NotificationType;
  title: string;
  content: string;
  linkUrl: string | null;
  refType: string | null;
  refId: number | null;
  isRead: boolean;
  readAt: string | null;
  createdAt: string;
}

export interface NotificationSettingData {
  id: number | null;
  userId: number;
  type: NotificationType;
  enabled: boolean;
  updatedAt: string | null;
}

// accessToken은 선택 인자다 — 생략하면 lib/api.ts의 store-synced 토큰을 쓰면서
// 401 발생 시 access token을 조용히 재발급받아 재시도한다(로그인 페이지 등 명시적
// 토큰 override가 필요한 극히 일부 호출에서만 넘긴다). store.tsx의 30초 배지 폴링이
// 매번 넘겼던 accessToken을 그대로 request()에 전달하면, 그 경로는 재발급-재시도를
// 건너뛰고 바로 401 → 로그아웃 처리로 빠져 유효한 refresh token이 있어도 폴링
// 도중 세션이 끊긴 것처럼 보였다.
export function getNotifications(
  accessToken?: string | null,
  type?: NotificationType,
  page = 0,
  size = 20,
  signal?: AbortSignal,
): Promise<SpringPage<NotificationData>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (type) query.set('type', type);
  return request<SpringPage<NotificationData>>(`/api/v1/notifications?${query.toString()}`, {
    accessToken,
    signal,
  });
}

export function getUnreadNotificationCount(
  accessToken?: string | null,
  signal?: AbortSignal,
): Promise<{ unreadCount: number }> {
  return request<{ unreadCount: number }>('/api/v1/notifications/unread-count', {
    accessToken,
    signal,
  });
}

export function markNotificationRead(
  notificationId: number,
  accessToken: string,
): Promise<NotificationData> {
  return request<NotificationData>(`/api/v1/notifications/${notificationId}/read`, {
    method: 'PATCH',
    accessToken,
  });
}

export function markAllNotificationsRead(accessToken: string): Promise<void> {
  return request<void>('/api/v1/notifications/read-all', {
    method: 'PATCH',
    accessToken,
  });
}

export function deleteNotification(notificationId: number, accessToken: string): Promise<void> {
  return request<void>(`/api/v1/notifications/${notificationId}`, {
    method: 'DELETE',
    accessToken,
  });
}

export function getNotificationSettings(
  accessToken: string,
  signal?: AbortSignal,
): Promise<NotificationSettingData[]> {
  return request<NotificationSettingData[]>('/api/v1/notifications/settings', {
    accessToken,
    signal,
  });
}

export function updateNotificationSetting(
  type: NotificationType,
  enabled: boolean,
  accessToken: string,
): Promise<NotificationSettingData> {
  return request<NotificationSettingData>('/api/v1/notifications/settings', {
    method: 'PATCH',
    accessToken,
    body: JSON.stringify({ type, enabled }),
  });
}
