import { request, SpringPage } from '@/lib/api';
import { PlantJournalData } from '@/lib/journal-api';

// 게시글 상세의 "목록으로"가 router.back() 대신 이 값으로 돌아가, 새로고침/직접 진입으로
// 히스토리가 없는 경우에도 방금 보던 탭·페이지 그대로 목록에 복귀할 수 있게 한다.
export const BOARD_LIST_URL_KEY = 'kwb_board_list_url';

export type BoardCategory = 'NOTICE' | 'FREE' | 'PLANT_QNA';
export type BoardStatus = 'ACTIVE' | 'HIDDEN';
export type BoardSearchType = 'TITLE_CONTENT' | 'TITLE' | 'CONTENT' | 'AUTHOR' | 'COMMENT';

export interface BoardPostData {
  id: number;
  userId: number;
  nickname: string;
  category: BoardCategory;
  title: string;
  content: string;
  journalId: number | null;
  imageUrls: string[];
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
  imageUrls?: string[];
}

export interface BoardPostUpdatePayload {
  title: string;
  content: string;
  imageUrls?: string[];
}

export interface BoardImageUploadData {
  imageUrl: string;
}

export function uploadBoardImage(file: File, accessToken: string): Promise<BoardImageUploadData> {
  const formData = new FormData();
  formData.append('file', file);
  return request<BoardImageUploadData>('/api/v1/board/images', {
    method: 'POST',
    accessToken,
    body: formData,
  });
}

// 업로드는 성공했지만 뒤이은 작성/수정이 실패해 게시글에 연결되지 못한 이미지를 정리한다.
// best-effort 정리이므로 실패해도 호출부의 에러 처리를 방해하지 않도록 별도로 호출한다.
export function deleteBoardImage(imageUrl: string, accessToken: string): Promise<void> {
  return request<void>(`/api/v1/board/images?imageUrl=${encodeURIComponent(imageUrl)}`, {
    method: 'DELETE',
    accessToken,
  });
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
  keyword?: string,
  searchType?: BoardSearchType,
): Promise<SpringPage<BoardPostData>> {
  const query = new URLSearchParams({ page: String(page), size: String(size) });
  if (category) query.set('category', category);
  if (keyword && keyword.trim()) {
    query.set('keyword', keyword.trim());
    if (searchType) query.set('searchType', searchType);
  }
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
