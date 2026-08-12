import { ApiError } from "@/lib/api";

const POINT_FORMATTER = new Intl.NumberFormat("ko-KR");
const DATE_TIME_FORMATTER = new Intl.DateTimeFormat("ko-KR", {
  month: "short",
  day: "numeric",
  hour: "2-digit",
  minute: "2-digit",
});

export function formatPoint(value: number) {
  return `${POINT_FORMATTER.format(value)}P`;
}

export function formatCommerceDateTime(value: string) {
  return DATE_TIME_FORMATTER.format(new Date(value));
}

export function isAbortError(error: unknown) {
  return error instanceof DOMException && error.name === "AbortError";
}

export function commerceErrorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError ? error.message : fallback;
}
