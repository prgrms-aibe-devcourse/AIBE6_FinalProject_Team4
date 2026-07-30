import { useRouter } from "next/navigation";
import { useState } from "react";
import {
  createGachaQaDraw,
  createOneHundredGachaQaDraws,
} from "@/lib/gacha-api";
import { saveGachaBatch } from "@/features/gacha/batch-session";

interface GachaTestMenuItemProps {
  accessToken: string;
  onNavigate: () => void;
}

function isGachaTestButtonEnabled() {
  const configured = process.env.NEXT_PUBLIC_ENABLE_GACHA_TEST_BUTTON;
  if (configured === "true") return true;
  if (configured === "false") return false;
  return process.env.NODE_ENV === "development";
}

/**
 * 임시 QA 진입점입니다.
 * 제거할 때는 Navbar의 import/렌더링을 삭제하거나
 * NEXT_PUBLIC_ENABLE_GACHA_TEST_BUTTON=false로 설정합니다.
 */
export default function GachaTestMenuItem({
  accessToken,
  onNavigate,
}: GachaTestMenuItemProps) {
  const router = useRouter();
  const [creating, setCreating] = useState<"one" | "hundred" | null>(null);
  const [failed, setFailed] = useState<"one" | "hundred" | null>(null);

  if (!isGachaTestButtonEnabled()) return null;

  const createTestDraw = async (mode: "one" | "hundred") => {
    if (creating) return;
    setCreating(mode);
    setFailed(null);
    try {
      const clientKey = crypto.randomUUID();
      const target =
        mode === "one"
          ? `/gacha/open/${(await createGachaQaDraw(accessToken, clientKey)).drawId}`
          : `/gacha/open/batch/${saveGachaBatch(
              (await createOneHundredGachaQaDraws(accessToken, clientKey))
                .drawIds,
            )}`;
      onNavigate();
      router.push(target);
    } catch {
      setFailed(mode);
      setCreating(null);
    }
  };

  return (
    <div className="border-t border-[#F2ECDD] bg-[#f8f5ff]">
      <button
        type="button"
        onClick={() => void createTestDraw("one")}
        disabled={creating !== null}
        title={
          failed === "one"
            ? "테스트 팩 생성에 실패했습니다. 다시 눌러 주세요."
            : undefined
        }
        className="flex w-full items-center gap-2 px-4 py-2.5 text-left text-[14px] font-bold text-[#6750a4] transition-colors duration-150 hover:bg-[#eee7ff] hover:text-[#513b8c] disabled:cursor-wait disabled:opacity-60"
      >
        <span
          className="material-symbols-outlined text-[18px]"
          aria-hidden="true"
        >
          casino
        </span>
        <span aria-live="polite">
          {creating === "one"
            ? "테스트 팩 생성 중..."
            : failed === "one"
              ? "가챠 테스트 다시 시도"
              : "가챠 테스트 버튼"}
        </span>
      </button>
      <button
        type="button"
        onClick={() => void createTestDraw("hundred")}
        disabled={creating !== null}
        title={
          failed === "hundred"
            ? "100팩 생성에 실패했습니다. 다시 눌러 주세요."
            : "카드 500장이 실제 보유 수량에 반영됩니다."
        }
        className="flex w-full items-center gap-2 border-t border-[#e8e0fa] px-4 py-2.5 text-left text-[14px] font-bold text-[#7b4a86] transition-colors duration-150 hover:bg-[#f2e8f5] hover:text-[#61376c] disabled:cursor-wait disabled:opacity-60"
      >
        <span
          className="material-symbols-outlined text-[18px]"
          aria-hidden="true"
        >
          stacks
        </span>
        <span aria-live="polite">
          {creating === "hundred"
            ? "100팩 개봉 준비 중..."
            : failed === "hundred"
              ? "100팩 테스트 다시 시도"
              : "가챠 100팩 테스트"}
        </span>
      </button>
    </div>
  );
}
