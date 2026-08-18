import { ApiError } from "@/lib/api";

const pendingKeys = new Map<string, string>();

function storageKey(namespace: string, operation: string) {
  return `kwb:${namespace}:idempotency:${operation}`;
}

function pendingKey(namespace: string, operation: string) {
  const keyName = storageKey(namespace, operation);
  const cached = pendingKeys.get(keyName);
  if (cached) return cached;

  const stored =
    typeof window === "undefined"
      ? null
      : window.sessionStorage.getItem(keyName);
  const key = stored ?? crypto.randomUUID();
  pendingKeys.set(keyName, key);
  if (typeof window !== "undefined")
    window.sessionStorage.setItem(keyName, key);
  return key;
}

function clearPendingKey(namespace: string, operation: string) {
  const keyName = storageKey(namespace, operation);
  pendingKeys.delete(keyName);
  if (typeof window !== "undefined") window.sessionStorage.removeItem(keyName);
}

export async function runIdempotentMutation<T>(
  namespace: string,
  operation: string,
  execute: (idempotencyKey: string) => Promise<T>,
) {
  const key = pendingKey(namespace, operation);
  try {
    const response = await execute(key);
    clearPendingKey(namespace, operation);
    return response;
  } catch (error) {
    // 결과가 확정된 4xx만 폐기한다. 응답 유실·5xx·처리 중 응답은 같은 키로 재시도한다.
    if (
      error instanceof ApiError &&
      error.status < 500 &&
      error.code !== "COMMON_IDEMPOTENCY_IN_PROGRESS"
    ) {
      clearPendingKey(namespace, operation);
    }
    throw error;
  }
}
