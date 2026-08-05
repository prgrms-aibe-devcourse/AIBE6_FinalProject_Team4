import { describe, expect, it } from "vitest";
import {
  commerceAssetPreviewUrl,
  validateCommerceAssetKey,
} from "@/lib/commerce-asset";

describe("commerce asset key", () => {
  it("상품·쿠폰 S3 상대 경로를 검증한다", () => {
    expect(
      validateCommerceAssetKey(
        "products/11/f7573887-a33e-5690-b058-f32f7aa2a326.png",
        "products",
        11,
      ),
    ).toBeNull();
    expect(
      validateCommerceAssetKey(
        "coupons/1/5d085536-b249-56bf-b42f-82e56bd785dd.png",
        "products",
        1,
      ),
    ).not.toBeNull();
    expect(
      validateCommerceAssetKey(
        "products/12/f7573887-a33e-5690-b058-f32f7aa2a326.png",
        "products",
        11,
      ),
    ).not.toBeNull();
  });

  it("유효한 키만 미리보기 URL로 바꾼다", () => {
    expect(
      commerceAssetPreviewUrl(
        "coupons/1/5d085536-b249-56bf-b42f-82e56bd785dd.png",
      ),
    ).toContain("/coupons/1/5d085536-b249-56bf-b42f-82e56bd785dd.png");
    expect(commerceAssetPreviewUrl("https://example.com/image.png")).toBeNull();
  });
});
