"use client";

// 0-indexed page 기준. 현재 페이지를 중심으로 최대 5개 번호를 보여주고, 양 끝에서는
// 창을 안쪽으로 당겨 항상 5개(또는 totalPages가 그보다 적으면 그 개수)를 채운다.
// 스크롤은 여기서 하지 않는다 — 클릭 즉시 스크롤하면 아직 이전 페이지가 보이는 채로
// 화면만 움직여 부자연스럽다. 새 페이지 데이터가 실제로 도착했을 때 스크롤하려면
// 호출하는 쪽에서 useScrollOnPageLoad를 같이 쓸 것.
const WINDOW_SIZE = 5;

export default function AdminPagination({
  page,
  totalPages,
  onChange,
  disabled = false,
}: {
  page: number;
  totalPages: number;
  onChange: (page: number) => void;
  // 부모가 다른 작업(제출 중 등)으로 목록 자체를 잠글 때 페이지 이동도 같이 막는다.
  disabled?: boolean;
}) {
  if (totalPages <= 1) return null;

  const start = Math.max(0, Math.min(page - Math.floor(WINDOW_SIZE / 2), totalPages - WINDOW_SIZE));
  const end = Math.min(totalPages, start + WINDOW_SIZE);
  const pages = Array.from({ length: end - start }, (_, i) => start + i);

  const go = (next: number) => {
    if (disabled || next < 0 || next >= totalPages || next === page) return;
    onChange(next);
  };

  return (
    <nav aria-label="페이지 이동" className="flex items-center justify-center gap-1.5">
      <button
        type="button"
        onClick={() => go(page - 1)}
        disabled={disabled || page === 0}
        className="rounded-lg border border-line px-3 py-1.5 text-sm font-bold text-sub disabled:cursor-not-allowed disabled:opacity-40"
      >
        이전
      </button>
      {pages.map((p) => (
        <button
          key={p}
          type="button"
          onClick={() => go(p)}
          disabled={disabled}
          aria-current={p === page ? "page" : undefined}
          className={`min-w-[34px] rounded-lg border px-2.5 py-1.5 text-sm font-bold disabled:cursor-not-allowed disabled:opacity-40 ${
            p === page
              ? "border-brand bg-brand-soft text-brand-dark"
              : "border-line text-sub hover:bg-[#f6f7f1]"
          }`}
        >
          {p + 1}
        </button>
      ))}
      <button
        type="button"
        onClick={() => go(page + 1)}
        disabled={disabled || page + 1 >= totalPages}
        className="rounded-lg border border-line px-3 py-1.5 text-sm font-bold text-sub disabled:cursor-not-allowed disabled:opacity-40"
      >
        다음
      </button>
    </nav>
  );
}
