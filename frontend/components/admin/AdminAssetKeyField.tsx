"use client";

import { useEffect, useState } from "react";

const MAX_FILE_SIZE = 5 * 1024 * 1024;
const ALLOWED_TYPES = new Set(["image/jpeg", "image/png", "image/webp"]);

interface AdminAssetKeyFieldProps {
  file: File | null;
  onFileChange: (file: File | null) => void;
  previewUrl?: string | null;
  label?: string;
  disabled?: boolean;
}

export default function AdminAssetKeyField({
  file,
  onFileChange,
  previewUrl,
  label = "상품 이미지",
  disabled = false,
}: AdminAssetKeyFieldProps) {
  const [localPreview, setLocalPreview] = useState<string | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!file) {
      setLocalPreview(null);
      return;
    }
    const objectUrl = URL.createObjectURL(file);
    setLocalPreview(objectUrl);
    return () => URL.revokeObjectURL(objectUrl);
  }, [file]);

  const selectedPreview = localPreview ?? previewUrl ?? null;

  return (
    <div className="flex flex-col gap-2">
      <label className="text-xs font-bold text-sub">{label}</label>
      <label
        className={`flex min-h-12 items-center justify-center rounded-xl border-[1.5px] border-dashed px-4 py-3 text-sm font-extrabold transition ${
          disabled
            ? "cursor-not-allowed border-line bg-[#f5f5f0] text-faint"
            : "cursor-pointer border-brand bg-brand-soft text-brand-dark hover:bg-[#e8f1dc]"
        }`}
      >
        <input
          type="file"
          accept="image/png,image/jpeg,image/webp"
          disabled={disabled}
          className="sr-only"
          onChange={(event) => {
            const next = event.target.files?.[0] ?? null;
            event.target.value = "";
            if (!next) return;
            if (!ALLOWED_TYPES.has(next.type) || next.size > MAX_FILE_SIZE) {
              setError("5MB 이하의 jpg, png, webp 이미지만 선택할 수 있어요.");
              onFileChange(null);
              return;
            }
            setError("");
            onFileChange(next);
          }}
        />
        {file ? `${file.name} 선택됨` : "이미지 파일 선택"}
      </label>
      <p className="text-xs text-sub">
        업로드하면 UUID 파일명으로 S3에 저장되고 상품에 자동 연결됩니다.
      </p>
      {error ? (
        <p className="text-xs font-semibold text-danger">{error}</p>
      ) : null}
      {selectedPreview ? (
        <div className="overflow-hidden rounded-xl border border-line bg-white p-2">
          <div
            role="img"
            aria-label="업로드 이미지 미리보기"
            className="h-36 bg-contain bg-center bg-no-repeat"
            style={{ backgroundImage: `url("${selectedPreview}")` }}
          />
        </div>
      ) : null}
    </div>
  );
}
