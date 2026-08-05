import {
  commerceAssetPreviewUrl,
  CommerceAssetPrefix,
  validateCommerceAssetKey,
} from "@/lib/commerce-asset";

interface AdminAssetKeyFieldProps {
  value: string;
  onChange: (value: string) => void;
  prefix: CommerceAssetPrefix;
  resourceId?: number | null;
  label?: string;
}

export default function AdminAssetKeyField({
  value,
  onChange,
  prefix,
  resourceId,
  label = "S3 이미지 경로",
}: AdminAssetKeyFieldProps) {
  const error = validateCommerceAssetKey(value, prefix, resourceId);
  const previewUrl = error ? null : commerceAssetPreviewUrl(value);

  return (
    <div className="flex flex-col gap-2">
      <label className="text-xs font-bold text-sub">{label}</label>
      <input
        value={value}
        onChange={(event) => onChange(event.target.value)}
        placeholder={`${prefix}/{id}/{uuid}.png`}
        maxLength={500}
        className={`rounded-xl border-[1.5px] px-[13px] py-2.5 text-sm outline-none ${
          error ? "border-danger" : "border-line"
        }`}
      />
      {error ? (
        <p className="text-xs font-semibold text-danger">{error}</p>
      ) : !resourceId ? (
        <p className="text-xs text-sub">
          새 항목은 먼저 이미지 없이 등록한 뒤, 발급된 ID 경로로 수정해 주세요.
        </p>
      ) : (
        <p className="text-xs text-sub">
          원본 확장자를 유지할 수 있습니다: png, jpg, jpeg, webp
        </p>
      )}
      {previewUrl && (
        <div className="overflow-hidden rounded-xl border border-line bg-brand-soft p-2">
          <div
            role="img"
            aria-label="S3 이미지 미리보기"
            className="h-36 bg-contain bg-center bg-no-repeat"
            style={{ backgroundImage: `url("${previewUrl}")` }}
          />
        </div>
      )}
    </div>
  );
}
