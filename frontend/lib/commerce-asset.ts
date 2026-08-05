export type CommerceAssetPrefix = "products" | "coupons";

const DEFAULT_ASSET_BASE_URL =
  "https://4team-storage-495264909330-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com";
const ASSET_BASE_URL = (
  process.env.NEXT_PUBLIC_ASSET_BASE_URL || DEFAULT_ASSET_BASE_URL
).replace(/\/+$/, "");

const ASSET_KEY_PATTERN =
  /^(products|coupons)\/(\d+)\/([0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12})\.(png|jpe?g|webp)$/i;

export function validateCommerceAssetKey(
  value: string,
  prefix: CommerceAssetPrefix,
  resourceId?: number | null,
): string | null {
  if (!value.trim()) return null;
  const match = value.trim().match(ASSET_KEY_PATTERN);
  if (!match || match[1] !== prefix) {
    return `${prefix}/{id}/{uuid}.png 형식으로 입력해 주세요.`;
  }
  if (resourceId && Number(match[2]) !== resourceId) {
    return `경로의 id는 ${resourceId}이어야 합니다.`;
  }
  return null;
}

export function commerceAssetPreviewUrl(key: string): string | null {
  if (!key.trim() || !ASSET_KEY_PATTERN.test(key.trim())) return null;
  return `${ASSET_BASE_URL}/${key.trim()}`;
}
