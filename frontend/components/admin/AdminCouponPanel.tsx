"use client";

import AdminAssetKeyField from "@/components/admin/AdminAssetKeyField";
import {
  AdminCard,
  AdminCardInput,
  AdminExchangeProductOption,
  changeAdminCardStatus,
  createAdminCard,
  getAdminCards,
  getAdminExchangeProductOptions,
  hideAdminCard,
  updateAdminCard,
  uploadAdminCardImage,
} from "@/lib/admin-card-api";
import { ApiError } from "@/lib/api";
import { useUI } from "@/lib/ui";
import { useCallback, useEffect, useState } from "react";

const EMPTY_FORM = {
  name: "",
  pointPrice: "",
  exchangeProductId: "",
  requiredCountForExchange: "",
  description: "",
};

export default function AdminCouponPanel({
  accessToken,
  page = 0,
  onPageChange,
}: {
  accessToken: string;
  page?: number;
  onPageChange?: (page: number) => void;
}) {
  const { askConfirm, showToast } = useUI();
  const [cards, setCards] = useState<AdminCard[]>([]);
  const [exchangeProducts, setExchangeProducts] = useState<
    AdminExchangeProductOption[]
  >([]);
  const [form, setForm] = useState(EMPTY_FORM);
  const [imageFile, setImageFile] = useState<File | null>(null);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [error, setError] = useState("");
  const pageSize = 10;
  const totalPages = Math.ceil(cards.length / pageSize);
  const visibleCards = cards.slice(page * pageSize, (page + 1) * pageSize);

  useEffect(() => {
    if (onPageChange && totalPages > 0 && page >= totalPages) {
      onPageChange(totalPages - 1);
    }
  }, [onPageChange, page, totalPages]);

  const load = useCallback(
    (signal?: AbortSignal) => {
      setLoading(true);
      setError("");
      return Promise.all([
        getAdminCards(accessToken, signal),
        getAdminExchangeProductOptions(accessToken, signal),
      ])
        .then(([nextCards, nextExchangeProducts]) => {
          setCards(nextCards);
          setExchangeProducts(nextExchangeProducts);
        })
        .catch((requestError) => {
          if (
            requestError instanceof DOMException &&
            requestError.name === "AbortError"
          )
            return;
          setError(
            requestError instanceof ApiError
              ? requestError.message
              : "쿠폰을 불러오지 못했어요.",
          );
        })
        .finally(() => {
          if (!signal?.aborted) setLoading(false);
        });
    },
    [accessToken],
  );

  useEffect(() => {
    const controller = new AbortController();
    void load(controller.signal);
    return () => controller.abort();
  }, [load]);

  const reset = () => {
    setEditingId(null);
    setForm(EMPTY_FORM);
    setImageFile(null);
  };

  const input = (): AdminCardInput | null => {
    const pointPrice = Number(form.pointPrice);
    const exchangeProductId = Number(form.exchangeProductId);
    const requiredCount = Number(form.requiredCountForExchange);
    if (!form.name.trim()) return fail("쿠폰명을 입력해 주세요.");
    if (!Number.isInteger(pointPrice) || pointPrice < 0)
      return fail("가격은 0 이상의 정수여야 합니다.");
    if (!Number.isInteger(exchangeProductId) || exchangeProductId < 1)
      return fail("교환 상품 ID를 입력해 주세요.");
    if (!Number.isInteger(requiredCount) || requiredCount < 1)
      return fail("교환 필요 수량은 1 이상의 정수여야 합니다.");
    const existingImageKey = editingId
      ? (cards.find((card) => card.id === editingId)?.imageKey ?? null)
      : null;
    return {
      name: form.name.trim(),
      pointPrice,
      exchangeProductId,
      requiredCountForExchange: requiredCount,
      description: form.description.trim() || null,
      imageUrl: existingImageKey,
      status:
        (editingId && cards.find((card) => card.id === editingId)?.status) ||
        "ON_SALE",
    };
  };

  const fail = (message: string): null => {
    showToast(message, "err");
    return null;
  };

  const submit = async () => {
    const values = input();
    if (!values || submitting) return;
    setSubmitting(true);
    try {
      let next = editingId
        ? await updateAdminCard(editingId, values, accessToken)
        : await createAdminCard(values, accessToken);
      if (imageFile) {
        next = await uploadAdminCardImage(next.id, imageFile, accessToken);
      }
      setCards((current) =>
        editingId
          ? current.map((card) => (card.id === next.id ? next : card))
          : [next, ...current],
      );
      showToast(editingId ? "쿠폰을 수정했어요." : "쿠폰을 등록했어요.");
      reset();
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError
          ? requestError.message
          : "쿠폰을 저장하지 못했어요.",
        "err",
      );
    } finally {
      setSubmitting(false);
    }
  };

  const edit = (card: AdminCard) => {
    setEditingId(card.id);
    setForm({
      name: card.name,
      pointPrice: String(card.pointPrice),
      exchangeProductId: String(card.exchangeProductId),
      requiredCountForExchange: String(card.requiredCountForExchange),
      description: card.description ?? "",
    });
    setImageFile(null);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const replace = (next: AdminCard) =>
    setCards((current) =>
      current.map((card) => (card.id === next.id ? next : card)),
    );

  const toggle = async (card: AdminCard) => {
    if (busyId !== null) return;
    setBusyId(card.id);
    try {
      replace(
        await changeAdminCardStatus(
          card.id,
          card.status === "ON_SALE" ? "HIDDEN" : "ON_SALE",
          accessToken,
        ),
      );
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError
          ? requestError.message
          : "노출 상태를 바꾸지 못했어요.",
        "err",
      );
    } finally {
      setBusyId(null);
    }
  };

  const hide = (card: AdminCard) =>
    askConfirm({
      icon: "visibility_off",
      title: "쿠폰을 숨길까요?",
      body: "기존 보유 내역은 유지되고 신규 목록과 구매에서만 숨겨집니다.",
      ok: "숨김 처리",
      onOk: async () => {
        setBusyId(card.id);
        try {
          replace(await hideAdminCard(card.id, accessToken));
          showToast("쿠폰을 숨겼어요.");
        } catch (requestError) {
          showToast(
            requestError instanceof ApiError
              ? requestError.message
              : "쿠폰을 숨기지 못했어요.",
            "err",
          );
        } finally {
          setBusyId(null);
        }
      },
    });

  return (
    <div className="flex flex-col gap-5">
      <section className="rounded-[18px] border border-line bg-white p-5 shadow-sm">
        <div className="mb-1 text-sm font-extrabold">
          {editingId ? `쿠폰 #${editingId} 수정` : "새 쿠폰 추가"}
        </div>
        <p className="mb-4 text-xs text-sub">
          교환 상품은 조회·연결만 하며 교환 주문 상태는 변경하지 않습니다.
        </p>
        <div className="grid gap-3 md:grid-cols-2">
          <input
            className="rounded-xl border border-line px-3 py-2.5"
            placeholder="쿠폰명"
            value={form.name}
            onChange={(e) => setForm({ ...form, name: e.target.value })}
          />
          <input
            className="rounded-xl border border-line px-3 py-2.5"
            type="number"
            min={0}
            placeholder="가격(P)"
            value={form.pointPrice}
            onChange={(e) => setForm({ ...form, pointPrice: e.target.value })}
          />
          <select
            className="rounded-xl border border-line px-3 py-2.5"
            value={form.exchangeProductId}
            onChange={(e) =>
              setForm({ ...form, exchangeProductId: e.target.value })
            }
          >
            <option value="">교환 상품 선택</option>
            {exchangeProducts.map((product) => (
              <option key={product.id} value={product.id}>
                {product.name} · 재고 {product.stock}개
              </option>
            ))}
          </select>
          <input
            className="rounded-xl border border-line px-3 py-2.5"
            type="number"
            min={1}
            placeholder="교환 필요 쿠폰 수"
            value={form.requiredCountForExchange}
            onChange={(e) =>
              setForm({ ...form, requiredCountForExchange: e.target.value })
            }
          />
          <textarea
            className="min-h-24 rounded-xl border border-line px-3 py-2.5 md:col-span-2"
            placeholder="설명"
            value={form.description}
            onChange={(e) => setForm({ ...form, description: e.target.value })}
          />
          <div className="md:col-span-2">
            <AdminAssetKeyField
              file={imageFile}
              onFileChange={setImageFile}
              previewUrl={
                editingId
                  ? cards.find((card) => card.id === editingId)?.imageUrl
                  : null
              }
              label="쿠폰 이미지"
              disabled={submitting}
            />
          </div>
        </div>
        <div className="mt-4 flex gap-2">
          <button
            type="button"
            disabled={submitting}
            onClick={() => void submit()}
            className="rounded-xl bg-brand px-4 py-2.5 text-sm font-bold text-white disabled:opacity-50"
          >
            {submitting
              ? "저장 중..."
              : editingId
                ? "수정 저장"
                : "+ 쿠폰 등록"}
          </button>
          {editingId && (
            <button
              type="button"
              onClick={reset}
              className="rounded-xl bg-brand-soft px-4 py-2.5 text-sm font-bold text-brand-dark"
            >
              취소
            </button>
          )}
        </div>
      </section>

      <section className="overflow-hidden rounded-[18px] border border-line bg-white shadow-sm">
        <div className="grid grid-cols-[1.5fr_.8fr_1fr_.7fr] gap-3 border-b border-line bg-[#fafbf7] px-4 py-3 text-xs font-extrabold text-sub">
          <div>쿠폰</div>
          <div>가격·필요량</div>
          <div>교환 상품</div>
          <div>관리</div>
        </div>
        {loading ? (
          <div className="p-10 text-center text-sub">
            쿠폰을 불러오고 있어요.
          </div>
        ) : error ? (
          <div className="p-10 text-center text-danger">{error}</div>
        ) : cards.length === 0 ? (
          <div className="p-10 text-center text-sub">등록된 쿠폰이 없어요.</div>
        ) : (
          visibleCards.map((card) => (
            <div
              key={card.id}
              className="grid grid-cols-[1.5fr_.8fr_1fr_.7fr] items-center gap-3 border-b border-line px-4 py-3 text-sm last:border-b-0"
            >
              <div className="flex min-w-0 items-center gap-3">
                <div
                  className="h-14 w-14 shrink-0 rounded-xl bg-brand-soft bg-contain bg-center bg-no-repeat"
                  style={
                    card.imageUrl
                      ? { backgroundImage: `url("${card.imageUrl}")` }
                      : undefined
                  }
                />
                <div className="min-w-0">
                  <div className="truncate font-extrabold">{card.name}</div>
                  <div className="text-xs text-sub">
                    #{card.id} ·{" "}
                    {card.status === "ON_SALE" ? "노출 중" : "숨김"}
                  </div>
                </div>
              </div>
              <div>
                <b>{card.pointPrice.toLocaleString()}P</b>
                <div className="text-xs text-sub">
                  {card.requiredCountForExchange}장 필요
                </div>
              </div>
              <div className="text-xs">
                <b>{card.exchangeProductName}</b>
                <div className="text-sub">ID {card.exchangeProductId}</div>
              </div>
              <div className="flex flex-wrap gap-1.5">
                <button
                  type="button"
                  disabled={busyId !== null}
                  onClick={() => void toggle(card)}
                  className="rounded-lg bg-brand-soft px-2.5 py-1.5 text-xs font-bold text-brand-dark"
                >
                  {card.status === "ON_SALE" ? "숨김" : "노출"}
                </button>
                <button
                  type="button"
                  onClick={() => edit(card)}
                  className="rounded-lg border border-line px-2.5 py-1.5 text-xs font-bold"
                >
                  수정
                </button>
                <button
                  type="button"
                  disabled={busyId !== null || card.status === "HIDDEN"}
                  onClick={() => hide(card)}
                  className="rounded-lg border border-line px-2.5 py-1.5 text-xs font-bold disabled:opacity-40"
                >
                  숨김
                </button>
              </div>
            </div>
          ))
        )}
        {onPageChange && totalPages > 1 ? (
          <div className="flex items-center justify-center gap-3 border-t border-line px-4 py-4">
            <button
              type="button"
              disabled={page <= 0}
              onClick={() => onPageChange(page - 1)}
              className="rounded-xl border border-line px-4 py-2 text-sm font-bold disabled:opacity-40"
            >
              이전
            </button>
            <span className="text-sm font-bold text-sub">
              {page + 1} / {totalPages}
            </span>
            <button
              type="button"
              disabled={page + 1 >= totalPages}
              onClick={() => onPageChange(page + 1)}
              className="rounded-xl border border-line px-4 py-2 text-sm font-bold disabled:opacity-40"
            >
              다음
            </button>
          </div>
        ) : null}
      </section>
    </div>
  );
}
