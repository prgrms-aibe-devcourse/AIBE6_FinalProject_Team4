"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import {
  AdminChargeProductInput,
  createAdminChargeProduct,
  getAdminChargeProducts,
  updateAdminChargeProduct,
} from "@/features/payment/admin-charge-product-api";
import { ChargeProduct } from "@/features/payment/api";
import { ApiError } from "@/lib/api";
import { useUI } from "@/lib/ui";

interface AdminChargeProductPanelProps {
  accessToken: string;
  adminUserId: number;
}

interface ChargeProductForm {
  name: string;
  price: string;
  pointAmount: string;
  isActive: boolean;
}

type FormField = "name" | "price" | "pointAmount";
type FormErrors = Partial<Record<FormField, string>>;

const EMPTY_FORM: ChargeProductForm = {
  name: "",
  price: "",
  pointAmount: "",
  isActive: true,
};

function formatNumber(value: number): string {
  return value.toLocaleString("ko-KR");
}

function createIdempotencyKey(): string {
  return (
    globalThis.crypto?.randomUUID?.() ??
    `charge-product-${Date.now()}-${Math.random().toString(16).slice(2)}`
  );
}

const CREATE_ATTEMPT_STORAGE_PREFIX = "kwb:admin-charge-product-create:";
const MIN_POINT_RATE = 1;
const MAX_POINT_RATE = 1.5;

function createAttemptStorageKey(
  adminUserId: number,
  signature: string,
): string {
  return `${CREATE_ATTEMPT_STORAGE_PREFIX}${adminUserId}:${signature}`;
}

function getStoredCreateAttempt(
  adminUserId: number,
  signature: string,
): string | null {
  if (typeof window === "undefined") return null;
  try {
    return sessionStorage.getItem(
      createAttemptStorageKey(adminUserId, signature),
    );
  } catch {
    return null;
  }
}

function storeCreateAttempt(
  adminUserId: number,
  signature: string,
  idempotencyKey: string,
): void {
  if (typeof window === "undefined") return;
  try {
    sessionStorage.setItem(
      createAttemptStorageKey(adminUserId, signature),
      idempotencyKey,
    );
  } catch {
    // 저장소가 차단된 환경에서도 현재 컴포넌트의 메모리 키로 재시도한다.
  }
}

function removeStoredCreateAttempt(
  adminUserId: number,
  signature: string,
): void {
  if (typeof window === "undefined") return;
  try {
    sessionStorage.removeItem(createAttemptStorageKey(adminUserId, signature));
  } catch {
    // 저장소 접근 실패는 이미 완료된 서버 요청 결과에 영향을 주지 않는다.
  }
}

export default function AdminChargeProductPanel({
  accessToken,
  adminUserId,
}: AdminChargeProductPanelProps) {
  const { askConfirm, showToast } = useUI();
  const [products, setProducts] = useState<ChargeProduct[]>([]);
  const [form, setForm] = useState<ChargeProductForm>(EMPTY_FORM);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editingVersion, setEditingVersion] = useState<number | null>(null);
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [busyId, setBusyId] = useState<number | null>(null);
  const [errorMessage, setErrorMessage] = useState("");
  const [formErrors, setFormErrors] = useState<FormErrors>({});
  const formRef = useRef<HTMLElement>(null);
  const nameInputRef = useRef<HTMLInputElement>(null);
  const loadGenerationRef = useRef(0);
  const mutationLockRef = useRef(false);
  const editingRef = useRef<{ id: number; version: number } | null>(null);
  const pendingCreateAttempts = useRef(new Map<string, string>());

  const resetForm = useCallback(() => {
    editingRef.current = null;
    setEditingId(null);
    setEditingVersion(null);
    setForm(EMPTY_FORM);
    setFormErrors({});
  }, []);

  const loadProducts = useCallback(
    async (signal?: AbortSignal) => {
      const generation = ++loadGenerationRef.current;
      setLoading(true);
      setErrorMessage("");
      try {
        const nextProducts = await getAdminChargeProducts(accessToken, signal);
        if (generation !== loadGenerationRef.current || signal?.aborted) return;

        const editing = editingRef.current;
        if (editing) {
          const latest = nextProducts.find(({ id }) => id === editing.id);
          if (!latest || latest.version !== editing.version) {
            resetForm();
            showToast(
              "편집 중인 상품이 변경되어 최신 목록을 불러왔어요. 다시 수정해 주세요.",
              "err",
            );
          }
        }
        setProducts(nextProducts);
      } catch (requestError) {
        if (generation !== loadGenerationRef.current) return;
        if (
          requestError instanceof DOMException &&
          requestError.name === "AbortError"
        ) {
          return;
        }
        setProducts([]);
        setErrorMessage(
          requestError instanceof ApiError
            ? requestError.message
            : "충전 상품을 불러오지 못했어요.",
        );
      } finally {
        if (generation === loadGenerationRef.current && !signal?.aborted) {
          setLoading(false);
        }
      }
    },
    [accessToken, resetForm, showToast],
  );

  useEffect(() => {
    const controller = new AbortController();
    void loadProducts(controller.signal);
    return () => controller.abort();
  }, [loadProducts]);

  const validateForm = (): AdminChargeProductInput | null => {
    const name = form.name.trim();
    const price = Number(form.price);
    const pointAmount = Number(form.pointAmount);

    const nextErrors: FormErrors = {};
    if (!name) nextErrors.name = "충전 상품명을 입력해 주세요.";
    else if (name.length > 50)
      nextErrors.name = "충전 상품명은 50자 이하로 입력해 주세요.";
    if (!Number.isSafeInteger(price) || price < 1)
      nextErrors.price = "결제 금액은 1 이상의 정수로 입력해 주세요.";
    if (!Number.isSafeInteger(pointAmount) || pointAmount < 1)
      nextErrors.pointAmount = "지급 포인트는 1 이상의 정수로 입력해 주세요.";
    else if (
      Number.isSafeInteger(price) &&
      price >= 1 &&
      (pointAmount < price || pointAmount / price > MAX_POINT_RATE)
    )
      nextErrors.pointAmount = `지급 포인트는 결제 금액의 ${MIN_POINT_RATE * 100}% 이상 ${MAX_POINT_RATE * 100}% 이하로 입력해 주세요.`;

    setFormErrors(nextErrors);
    const firstError = Object.values(nextErrors)[0];
    if (firstError) {
      showToast(firstError, "err");
      return null;
    }

    return { name, price, pointAmount, isActive: form.isActive };
  };

  const saveProduct = async () => {
    const payload = validateForm();
    if (!payload || mutationLockRef.current) return;

    mutationLockRef.current = true;
    setSubmitting(true);
    try {
      if (editingId === null) {
        const signature = JSON.stringify(payload);
        const idempotencyKey =
          pendingCreateAttempts.current.get(signature) ??
          getStoredCreateAttempt(adminUserId, signature) ??
          createIdempotencyKey();
        pendingCreateAttempts.current.set(signature, idempotencyKey);
        storeCreateAttempt(adminUserId, signature, idempotencyKey);
        await createAdminChargeProduct(accessToken, idempotencyKey, payload);
        pendingCreateAttempts.current.delete(signature);
        removeStoredCreateAttempt(adminUserId, signature);
        showToast("충전 상품을 추가했어요.");
      } else {
        if (editingVersion === null) return;
        await updateAdminChargeProduct(accessToken, editingId, {
          ...payload,
          version: editingVersion,
        });
        showToast("충전 상품을 수정했어요.");
      }
      resetForm();
      await loadProducts();
    } catch (requestError) {
      if (
        editingId === null &&
        requestError instanceof ApiError &&
        requestError.code === "COMMON_IDEMPOTENCY_CONFLICT"
      ) {
        const signature = JSON.stringify(payload);
        pendingCreateAttempts.current.delete(signature);
        removeStoredCreateAttempt(adminUserId, signature);
      }
      if (
        requestError instanceof ApiError &&
        requestError.code === "COMMON_OPTIMISTIC_LOCK_CONFLICT"
      ) {
        resetForm();
        showToast(
          "다른 관리자가 상품을 변경했어요. 최신 목록에서 다시 수정해 주세요.",
          "err",
        );
        await loadProducts();
        return;
      }
      showToast(
        requestError instanceof ApiError
          ? requestError.message
          : "충전 상품을 저장하지 못했어요.",
        "err",
      );
    } finally {
      mutationLockRef.current = false;
      setSubmitting(false);
    }
  };

  const editProduct = (product: ChargeProduct) => {
    if (mutationLockRef.current) return;
    editingRef.current = { id: product.id, version: product.version };
    setEditingId(product.id);
    setEditingVersion(product.version);
    setFormErrors({});
    setForm({
      name: product.name,
      price: String(product.price),
      pointAmount: String(product.pointAmount),
      isActive: product.isActive,
    });
    formRef.current?.scrollIntoView?.({ behavior: "smooth", block: "start" });
    window.setTimeout(() => nameInputRef.current?.focus(), 0);
  };

  const confirmStatusChange = (product: ChargeProduct) => {
    if (mutationLockRef.current) return;
    const nextActive = !product.isActive;
    askConfirm({
      icon: nextActive ? "visibility" : "visibility_off",
      title: nextActive
        ? "충전 상품을 판매할까요?"
        : "충전 상품 판매를 중지할까요?",
      body: nextActive
        ? `${product.name} 상품이 사용자 충전 페이지에 다시 표시됩니다.`
        : `${product.name} 상품이 사용자 충전 페이지에서 숨겨집니다. 기존 결제 내역은 유지됩니다.`,
      ok: nextActive ? "판매 시작" : "판매 중지",
      danger: !nextActive,
      onOk: async () => {
        if (mutationLockRef.current) return;
        mutationLockRef.current = true;
        setBusyId(product.id);
        try {
          await updateAdminChargeProduct(accessToken, product.id, {
            name: product.name,
            price: product.price,
            pointAmount: product.pointAmount,
            isActive: nextActive,
            version: product.version,
          });
          showToast(
            nextActive
              ? "충전 상품 판매를 시작했어요."
              : "충전 상품 판매를 중지했어요.",
          );
          await loadProducts();
        } catch (requestError) {
          if (
            requestError instanceof ApiError &&
            requestError.code === "COMMON_OPTIMISTIC_LOCK_CONFLICT"
          ) {
            showToast(
              "다른 관리자가 상품을 변경했어요. 최신 목록을 확인해 주세요.",
              "err",
            );
            await loadProducts();
            return;
          }
          showToast(
            requestError instanceof ApiError
              ? requestError.message
              : "충전 상품 상태를 변경하지 못했어요.",
            "err",
          );
        } finally {
          mutationLockRef.current = false;
          setBusyId(null);
        }
      },
    });
  };

  return (
    <div className="flex flex-col gap-5">
      <section
        ref={formRef}
        className="rounded-[18px] border border-line bg-white p-5 shadow-sm"
      >
        <h2 className="text-lg font-extrabold">
          {editingId === null
            ? "새 충전 상품 추가"
            : `충전 상품 #${editingId} 수정`}
        </h2>
        <p className="mt-1 text-sm text-sub">
          판매 중인 상품만 사용자 충전 페이지에 표시됩니다. 지급 포인트는 결제
          금액의 100~150%만 설정할 수 있습니다.
        </p>

        <form
          aria-label="충전 상품 입력"
          aria-busy={submitting || busyId !== null}
          className="mt-4"
          noValidate
          onSubmit={(event) => {
            event.preventDefault();
            void saveProduct();
          }}
        >
          <fieldset
            disabled={submitting || busyId !== null}
            className="grid gap-3 disabled:opacity-70 md:grid-cols-2 xl:grid-cols-4"
          >
            <label className="text-sm font-bold text-sub">
              상품명
              <input
                ref={nameInputRef}
                type="text"
                required
                maxLength={50}
                value={form.name}
                aria-invalid={Boolean(formErrors.name)}
                aria-describedby={
                  formErrors.name ? "charge-name-error" : undefined
                }
                onChange={(event) => {
                  setFormErrors((current) => ({ ...current, name: undefined }));
                  setForm((current) => ({
                    ...current,
                    name: event.target.value,
                  }));
                }}
                placeholder="예: 새싹 충전 1,000P"
                className="mt-1.5 w-full rounded-xl border border-line px-3 py-2.5 text-ink outline-none focus:border-brand"
              />
              {formErrors.name && (
                <span
                  id="charge-name-error"
                  className="mt-1 block text-xs text-danger"
                >
                  {formErrors.name}
                </span>
              )}
            </label>
            <label className="text-sm font-bold text-sub">
              결제 금액(원)
              <input
                type="number"
                required
                min={1}
                step={1}
                inputMode="numeric"
                aria-invalid={Boolean(formErrors.price)}
                aria-describedby={
                  formErrors.price ? "charge-price-error" : undefined
                }
                value={form.price}
                onChange={(event) => {
                  setFormErrors((current) => ({
                    ...current,
                    price: undefined,
                  }));
                  setForm((current) => ({
                    ...current,
                    price: event.target.value,
                  }));
                }}
                placeholder="1000"
                className="mt-1.5 w-full rounded-xl border border-line px-3 py-2.5 text-ink outline-none focus:border-brand"
              />
              {formErrors.price && (
                <span
                  id="charge-price-error"
                  className="mt-1 block text-xs text-danger"
                >
                  {formErrors.price}
                </span>
              )}
            </label>
            <label className="text-sm font-bold text-sub">
              지급 포인트(P)
              <input
                type="number"
                required
                min={1}
                step={1}
                inputMode="numeric"
                aria-invalid={Boolean(formErrors.pointAmount)}
                aria-describedby={
                  formErrors.pointAmount ? "charge-point-error" : undefined
                }
                value={form.pointAmount}
                onChange={(event) => {
                  setFormErrors((current) => ({
                    ...current,
                    pointAmount: undefined,
                  }));
                  setForm((current) => ({
                    ...current,
                    pointAmount: event.target.value,
                  }));
                }}
                placeholder="1000"
                className="mt-1.5 w-full rounded-xl border border-line px-3 py-2.5 text-ink outline-none focus:border-brand"
              />
              {formErrors.pointAmount && (
                <span
                  id="charge-point-error"
                  className="mt-1 block text-xs text-danger"
                >
                  {formErrors.pointAmount}
                </span>
              )}
            </label>
            <label className="text-sm font-bold text-sub">
              판매 상태
              <select
                value={form.isActive ? "ACTIVE" : "INACTIVE"}
                onChange={(event) =>
                  setForm((current) => ({
                    ...current,
                    isActive: event.target.value === "ACTIVE",
                  }))
                }
                className="mt-1.5 w-full rounded-xl border border-line bg-white px-3 py-2.5 text-ink outline-none focus:border-brand"
              >
                <option value="ACTIVE">판매 중</option>
                <option value="INACTIVE">판매 중지</option>
              </select>
            </label>
          </fieldset>

          <div className="mt-4 flex flex-wrap gap-2">
            <button
              type="submit"
              disabled={submitting || busyId !== null}
              className="rounded-xl bg-brand px-4 py-2.5 text-sm font-bold text-white disabled:cursor-not-allowed disabled:opacity-50"
            >
              {submitting
                ? "저장 중..."
                : editingId === null
                  ? "충전 상품 추가"
                  : "수정 저장"}
            </button>
            {editingId !== null && (
              <button
                type="button"
                disabled={submitting || busyId !== null}
                onClick={resetForm}
                className="rounded-xl border border-line bg-white px-4 py-2.5 text-sm font-bold text-sub disabled:opacity-50"
              >
                수정 취소
              </button>
            )}
          </div>
        </form>
      </section>

      <section className="overflow-hidden rounded-[18px] border border-line bg-white shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-line bg-[#fafbf7] px-5 py-4">
          <div>
            <h2 className="font-extrabold">충전 상품 목록</h2>
            <p className="mt-0.5 text-xs text-sub">
              활성·비활성 상품을 모두 표시합니다.
            </p>
          </div>
          <button
            type="button"
            disabled={loading || submitting || busyId !== null}
            onClick={() => void loadProducts()}
            className="rounded-lg border border-line bg-white px-3 py-2 text-xs font-bold text-sub disabled:opacity-40"
          >
            새로고침
          </button>
        </div>

        {loading ? (
          <div className="p-10 text-center text-sub">
            충전 상품을 불러오고 있어요.
          </div>
        ) : errorMessage ? (
          <div className="p-10 text-center">
            <p role="alert" className="text-danger">
              {errorMessage}
            </p>
            <button
              type="button"
              onClick={() => void loadProducts()}
              className="mt-3 rounded-lg bg-brand-soft px-3 py-2 text-sm font-bold text-brand-dark"
            >
              다시 시도
            </button>
          </div>
        ) : products.length === 0 ? (
          <div className="p-10 text-center text-sub">
            등록된 충전 상품이 없습니다. 위에서 첫 상품을 추가해 주세요.
          </div>
        ) : (
          <div className="grid gap-3 p-4 md:grid-cols-2 xl:grid-cols-3">
            {products.map((product) => (
              <article
                key={product.id}
                className="rounded-2xl border border-line bg-white p-4"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="min-w-0">
                    <h3 className="truncate font-extrabold">{product.name}</h3>
                    <p className="mt-1 text-xs text-sub">상품 #{product.id}</p>
                  </div>
                  <span
                    className={`shrink-0 rounded-full px-2.5 py-1 text-xs font-extrabold ${
                      product.isActive
                        ? "bg-brand-soft text-brand-dark"
                        : "bg-[#f0f1ea] text-sub"
                    }`}
                  >
                    {product.isActive ? "판매 중" : "판매 중지"}
                  </span>
                </div>
                <dl className="mt-4 grid grid-cols-2 gap-2 rounded-xl bg-[#f6f7f1] p-3 text-sm">
                  <div>
                    <dt className="text-xs text-sub">결제 금액</dt>
                    <dd className="mt-1 font-extrabold">
                      {formatNumber(product.price)}원
                    </dd>
                  </div>
                  <div>
                    <dt className="text-xs text-sub">지급 포인트</dt>
                    <dd className="mt-1 font-extrabold text-brand-text">
                      {formatNumber(product.pointAmount)}P
                    </dd>
                  </div>
                </dl>
                <div className="mt-4 flex gap-2">
                  <button
                    type="button"
                    disabled={submitting || busyId !== null}
                    onClick={() => editProduct(product)}
                    aria-label={`${product.name} 수정`}
                    className="flex-1 rounded-lg border border-line px-3 py-2 text-sm font-bold disabled:opacity-40"
                  >
                    수정
                  </button>
                  <button
                    type="button"
                    disabled={
                      submitting || busyId !== null || editingId === product.id
                    }
                    onClick={() => confirmStatusChange(product)}
                    aria-label={`${product.name} ${
                      product.isActive ? "판매 중지" : "판매 시작"
                    }`}
                    className={`flex-1 rounded-lg px-3 py-2 text-sm font-bold disabled:opacity-40 ${
                      product.isActive
                        ? "bg-danger-soft text-danger"
                        : "bg-brand-soft text-brand-dark"
                    }`}
                  >
                    {busyId === product.id
                      ? "처리 중..."
                      : product.isActive
                        ? "판매 중지"
                        : "판매 시작"}
                  </button>
                </div>
              </article>
            ))}
          </div>
        )}
      </section>
    </div>
  );
}
