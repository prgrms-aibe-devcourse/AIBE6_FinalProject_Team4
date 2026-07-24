const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL || 'http://localhost:8080';

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

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const res = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers: {
      'Content-Type': 'application/json',
      ...options.headers,
    },
  });

  const body = await res.json().catch(() => null);

  if (!res.ok) {
    const code = body?.code || 'UNKNOWN_ERROR';
    const message = body?.message || '요청 처리 중 문제가 발생했어요.';
    throw new ApiError(code, message, res.status);
  }

  return body.data as T;
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
