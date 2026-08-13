import { request, SpringPage } from '@/lib/api';
import { PlantJournalData } from '@/lib/journal-api';

export type BoardCategory = 'NOTICE' | 'FREE' | 'PLANT_QNA';
export type BoardStatus = 'ACTIVE' | 'HIDDEN';

export interface BoardPostData {
  id: number;
  userId: number;
  nickname: string;
  category: BoardCategory;
  title: string;
  content: string;
  journalId: number | null;
  viewCount: number;
  likeCount: number;
  commentCount: number;
  status: BoardStatus;
  createdAt: string;
  updatedAt: string;
  likedByMe: boolean;
}

export interface BoardCommentData {
  id: number;
  postId: number;
  userId: number;
  nickname: string;
  content: string;
  parentCommentId: number | null;
  likeCount: number;
  createdAt: string;
  updatedAt: string;
  likedByMe: boolean;
}

export interface BoardPostCreatePayload {
  category: BoardCategory;
  title: string;
  content: string;
  journalId?: number | null;
}

export interface BoardPostUpdatePayload {
  title: string;
  content: string;
}

export interface BoardCommentCreatePayload {
  content: string;
  parentCommentId?: number | null;
}

export function getBoardPosts(
  category?: BoardCategory,
  page = 0,
  size = 20,
  accessToken?: string | null,
  signal?: AbortSignal,
): Promise<SpringPage<BoardPostData>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (category) query.set('category', category);
  return request<SpringPage<BoardPostData>>(`/api/v1/board/posts?${query.toString()}`, {
    accessToken,
    signal,
  });
}

export function getBoardPost(
  id: number,
  accessToken?: string | null,
  signal?: AbortSignal,
): Promise<BoardPostData> {
  return request<BoardPostData>(`/api/v1/board/posts/${id}`, { accessToken, signal });
}

// PLANT_QNA 게시글에 연동된 일지는 작성자가 아닌 다른 열람자도 볼 수 있다.
export function getBoardPostJournal(
  postId: number,
  signal?: AbortSignal,
): Promise<PlantJournalData> {
  return request<PlantJournalData>(`/api/v1/board/posts/${postId}/journal`, { signal });
}

export function createBoardPost(
  payload: BoardPostCreatePayload,
  accessToken: string,
): Promise<BoardPostData> {
  return request<BoardPostData>('/api/v1/board/posts', {
    method: 'POST',
    accessToken,
    body: JSON.stringify(payload),
  });
}

export function updateBoardPost(
  id: number,
  payload: BoardPostUpdatePayload,
  accessToken: string,
): Promise<BoardPostData> {
  return request<BoardPostData>(`/api/v1/board/posts/${id}`, {
    method: 'PATCH',
    accessToken,
    body: JSON.stringify(payload),
  });
}

export function deleteBoardPost(id: number, accessToken: string): Promise<void> {
  return request<void>(`/api/v1/board/posts/${id}`, { method: 'DELETE', accessToken });
}

export function likeBoardPost(id: number, accessToken: string): Promise<void> {
  return request<void>(`/api/v1/board/posts/${id}/likes`, { method: 'POST', accessToken });
}

export function unlikeBoardPost(id: number, accessToken: string): Promise<void> {
  return request<void>(`/api/v1/board/posts/${id}/likes`, { method: 'DELETE', accessToken });
}

export function getBoardComments(
  postId: number,
  accessToken?: string | null,
  signal?: AbortSignal,
): Promise<BoardCommentData[]> {
  return request<BoardCommentData[]>(`/api/v1/board/posts/${postId}/comments`, { accessToken, signal });
}

export function createBoardComment(
  postId: number,
  payload: BoardCommentCreatePayload,
  accessToken: string,
): Promise<BoardCommentData> {
  return request<BoardCommentData>(`/api/v1/board/posts/${postId}/comments`, {
    method: 'POST',
    accessToken,
    body: JSON.stringify(payload),
  });
}

export function updateBoardComment(
  id: number,
  content: string,
  accessToken: string,
): Promise<BoardCommentData> {
  return request<BoardCommentData>(`/api/v1/board/comments/${id}`, {
    method: 'PATCH',
    accessToken,
    body: JSON.stringify({ content }),
  });
}

export function deleteBoardComment(id: number, accessToken: string): Promise<void> {
  return request<void>(`/api/v1/board/comments/${id}`, { method: 'DELETE', accessToken });
}

export function likeBoardComment(id: number, accessToken: string): Promise<void> {
  return request<void>(`/api/v1/board/comments/${id}/likes`, { method: 'POST', accessToken });
}

export function unlikeBoardComment(id: number, accessToken: string): Promise<void> {
  return request<void>(`/api/v1/board/comments/${id}/likes`, { method: 'DELETE', accessToken });
}

export function getMyBoardPosts(
  accessToken: string,
  page = 0,
  size = 20,
): Promise<SpringPage<BoardPostData>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  return request<SpringPage<BoardPostData>>(`/api/v1/my/board/posts?${query.toString()}`, {
    accessToken,
  });
}

export function getMyBoardComments(
  accessToken: string,
  page = 0,
  size = 20,
): Promise<SpringPage<BoardCommentData>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  return request<SpringPage<BoardCommentData>>(`/api/v1/my/board/comments?${query.toString()}`, {
    accessToken,
  });
}
