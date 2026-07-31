export const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080';
export const AUTH_EXPIRED_EVENT = 'kwb:auth-expired';

// Server stores/returns image paths host-relative (e.g. "/api/v1/journals/images/...")
// so a DB row never bakes in a specific environment's host — see JournalImageUploadService.
// Local blob:/data: URLs (unsaved file picker previews) are already display-ready, pass through.
export function resolveImageUrl(path: string): string {
  if (!path || /^(https?:|blob:|data:)/.test(path)) return path;
  return API_BASE_URL + path;
}

// Backend error codes/messages: see docs/error-codes.md. The server already returns
// user-facing Korean messages, so no client-side translation table is needed here.
export class ApiError extends Error {
  code: string;
  status: number;

  constructor(code: string, message: string, status: number) {
    super(message);
    this.code = code;
    this.status = status;
  }
}

// The access token lives only in memory (never localStorage) — the store sets this
// whenever it changes so plain fetch calls elsewhere can still attach it. Feature
// modules that already hold their own token (see features/point/api.ts etc.) can
// instead pass `accessToken` explicitly via ApiRequestOptions to override this.
let currentAccessToken: string | null = null;

export function setAccessToken(token: string | null) {
  currentAccessToken = token;
}

// The store registers a handler that calls /auth/reissue (using the httpOnly refresh
// cookie) and updates in-memory state. Returns the new access token, or null if the
// refresh itself failed (session is over — caller should treat this like a 401).
type UnauthorizedHandler = () => Promise<string | null>;
let onUnauthorized: UnauthorizedHandler | null = null;

export function setUnauthorizedHandler(handler: UnauthorizedHandler | null) {
  onUnauthorized = handler;
}

interface ApiRequestOptions extends RequestInit {
  accessToken?: string | null;
}

export async function request<T>(path: string, options: ApiRequestOptions = {}, isRetry = false): Promise<T> {
  const { accessToken, ...requestOptions } = options;
  // Explicit accessToken (feature-module style) wins; otherwise fall back to the
  // store-synced in-memory token so older calls in this file keep working unchanged.
  const tokenForThisCall = accessToken !== undefined ? accessToken : currentAccessToken;

  // FormData bodies (file uploads) must NOT get an explicit Content-Type — the
  // browser sets it itself with the multipart boundary fetch needs.
  const isFormData = requestOptions.body instanceof FormData;
  const headers: Record<string, string> = {
    ...(isFormData ? {} : { 'Content-Type': 'application/json' }),
    ...(requestOptions.headers as Record<string, string> | undefined),
  };
  if (tokenForThisCall && !headers.Authorization) {
    headers.Authorization = `Bearer ${tokenForThisCall}`;
  }

  const res = await fetch(`${API_BASE_URL}${path}`, {
    ...requestOptions,
    headers,
    credentials: 'include', // send/receive the httpOnly refresh_token cookie
  });

  // signup/login/reissue/logout never go through the refresh-and-retry flow — a 401
  // there means "bad credentials" or "no session", not "expired token". /auth/me is a
  // regular authenticated endpoint (like any other protected resource), so it's excluded
  // from this list and does get the retry-after-refresh treatment.
  const isCredentialEndpoint = ['/api/v1/auth/signup', '/api/v1/auth/login', '/api/v1/auth/reissue', '/api/v1/auth/logout'].includes(path);
  // Silent-refresh-and-retry only applies to calls using the store-synced token — a
  // caller that already passed its own accessToken explicitly gets AUTH_EXPIRED_EVENT
  // below instead, since retrying with a rotated token wouldn't reach it anyway.
  if (res.status === 401 && !isRetry && onUnauthorized && !isCredentialEndpoint && accessToken === undefined) {
    const newToken = await onUnauthorized();
    if (newToken) {
      return request<T>(path, options, true);
    }
  }

  const body = await res.json().catch(() => null);

  if (!res.ok) {
    if (res.status === 401 && !isCredentialEndpoint && typeof window !== 'undefined') {
      window.dispatchEvent(new Event(AUTH_EXPIRED_EVENT));
    }
    const code = body?.code || 'UNKNOWN_ERROR';
    const message = body?.message || '요청 처리 중 문제가 발생했어요.';
    throw new ApiError(code, message, res.status);
  }

  // 204 No Content (delete/cancel/confirm 등)처럼 응답 본문이 없는 성공 응답은 body가 null이라
  // body.data에 접근하면 TypeError가 나서 성공한 요청이 실패로 보인다.
  return (body ? body.data : undefined) as T;
}

// Shape of Spring Data's Page<T> as Jackson serializes it by default (content/number/
// totalElements/... alongside a nested pageable/sort object we don't need on the client).
export interface SpringPage<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
  first: boolean;
  last: boolean;
  numberOfElements: number;
  empty: boolean;
}

export interface UserResponse {
  id: number;
  email: string;
  nickname: string;
  name: string;
  phoneNumber: string | null;
  provider: string;
  role: string;
  level: number;
  experience: number;
  status: string;
  suspendedReason: string | null;
  withdrawnAt: string | null;
  createdAt: string;
}

export interface LoginResponse {
  accessToken: string;
  tokenType: string;
  user: UserResponse;
}

export interface AccessTokenResponse {
  accessToken: string;
  tokenType: string;
  user: UserResponse;
}

export interface SignupPayload {
  email: string;
  password: string;
  nickname: string;
  name: string;
  phoneNumber?: string;
}

export function login(email: string, password: string): Promise<LoginResponse> {
  return request<LoginResponse>('/api/v1/auth/login', {
    method: 'POST',
    body: JSON.stringify({ email, password }),
  });
}

export function signup(payload: SignupPayload): Promise<UserResponse> {
  return request<UserResponse>('/api/v1/auth/signup', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function requestEmailVerification(email: string): Promise<void> {
  return request<void>('/api/v1/auth/signup/email-verification', {
    method: 'POST',
    body: JSON.stringify({ email }),
  });
}

export function confirmEmailVerification(email: string, code: string): Promise<void> {
  return request<void>('/api/v1/auth/signup/email-verification/confirm', {
    method: 'POST',
    body: JSON.stringify({ email, code }),
  });
}

export function requestPasswordResetVerification(email: string): Promise<void> {
  return request<void>('/api/v1/auth/password/reset/email-verification', {
    method: 'POST',
    body: JSON.stringify({ email }),
  });
}

export interface PasswordResetTicketResponse {
  resetToken: string;
}

// Returned resetToken must be echoed back into resetPassword() below — the
// server binds this single-use ticket to the confirm-code call so a reset
// can't be completed by anyone other than whoever verified the code.
export function confirmPasswordResetVerification(email: string, code: string): Promise<PasswordResetTicketResponse> {
  return request<PasswordResetTicketResponse>('/api/v1/auth/password/reset/email-verification/confirm', {
    method: 'POST',
    body: JSON.stringify({ email, code }),
  });
}

export function resetPassword(email: string, newPassword: string, resetToken: string): Promise<void> {
  return request<void>('/api/v1/auth/password/reset', {
    method: 'POST',
    body: JSON.stringify({ email, newPassword, resetToken }),
  });
}

export interface NicknameAvailabilityResponse {
  available: boolean;
}

export function checkNicknameAvailability(nickname: string): Promise<NicknameAvailabilityResponse> {
  return request<NicknameAvailabilityResponse>(
    `/api/v1/auth/signup/nickname-check?nickname=${encodeURIComponent(nickname)}`,
  );
}

export function oauthLogin(provider: string, code: string, state?: string): Promise<LoginResponse> {
  return request<LoginResponse>(`/api/v1/auth/oauth/${provider}`, {
    method: 'POST',
    body: JSON.stringify({ code, state: state || null }),
  });
}

// Silent refresh: relies solely on the httpOnly refresh_token cookie, no body needed.
export function reissue(): Promise<AccessTokenResponse> {
  return request<AccessTokenResponse>('/api/v1/auth/reissue', { method: 'POST' });
}

export function logout(): Promise<void> {
  return request<void>('/api/v1/auth/logout', { method: 'POST' });
}

export interface ProfileUpdatePayload {
  nickname?: string;
  name?: string;
  phoneNumber?: string;
}

export function getMe(): Promise<UserResponse> {
  return request<UserResponse>('/api/v1/auth/me');
}

export function updateProfile(payload: ProfileUpdatePayload): Promise<UserResponse> {
  return request<UserResponse>('/api/v1/auth/me', {
    method: 'PATCH',
    body: JSON.stringify(payload),
  });
}

export function changePassword(currentPassword: string, newPassword: string): Promise<void> {
  return request<void>('/api/v1/auth/me/password', {
    method: 'PATCH',
    body: JSON.stringify({ currentPassword, newPassword }),
  });
}

// Re-checks the current password without changing anything — used to gate access
// to sensitive actions like editing the profile.
export function verifyPassword(password: string): Promise<void> {
  return request<void>('/api/v1/auth/me/password/verify', {
    method: 'POST',
    body: JSON.stringify({ password }),
  });
}

// Soft delete: server flips status to WITHDRAWN and revokes every refresh token —
// nothing is physically removed. password is required for LOCAL accounts only.
export function withdraw(password?: string): Promise<void> {
  return request<void>('/api/v1/auth/me/withdraw', {
    method: 'POST',
    body: JSON.stringify({ password: password || null }),
  });
}

export interface UserAddress {
  id: number;
  userId: number;
  receiverName: string;
  receiverPhone: string;
  zipCode: string;
  address: string;
  addressDetail: string | null;
  isDefault: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface UserAddressPayload {
  receiverName: string;
  receiverPhone: string;
  zipCode: string;
  address: string;
  addressDetail?: string;
  isDefault: boolean;
}

export function getAddresses(): Promise<UserAddress[]> {
  return request<UserAddress[]>('/api/v1/mypage/addresses');
}

export function createAddress(payload: UserAddressPayload): Promise<UserAddress> {
  return request<UserAddress>('/api/v1/mypage/addresses', {
    method: 'POST',
    body: JSON.stringify(payload),
  });
}

export function updateAddress(addressId: number, payload: UserAddressPayload): Promise<UserAddress> {
  return request<UserAddress>(`/api/v1/mypage/addresses/${addressId}`, {
    method: 'PATCH',
    body: JSON.stringify(payload),
  });
}

export function deleteAddress(addressId: number): Promise<void> {
  return request<void>(`/api/v1/mypage/addresses/${addressId}`, {
    method: 'DELETE',
  });
}

export function setDefaultAddress(addressId: number): Promise<UserAddress> {
  return request<UserAddress>(`/api/v1/mypage/addresses/${addressId}/default`, {
    method: 'PATCH',
  });
}
