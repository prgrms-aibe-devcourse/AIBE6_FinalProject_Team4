"use client";
import { ApiError } from "@/lib/api";
import {
  adminCancelExchange,
  deliverExchange,
  ExchangeOrderData,
  getExchangesForAdmin,
  prepareExchange,
  shipExchange,
} from "@/lib/exchange-api";
import { createSpecies, getSpecies, PlantSpeciesData, updateSpecies } from "@/lib/species-api";
import { fmt, useStore } from "@/lib/store";
import { useUI } from "@/lib/ui";
import { useEffect, useState } from "react";

const DELSEQ = ["PREPARING", "SHIPPING", "DELIVERED"];
const delMeta: Record<string, [string, string, string]> = {
  PREPARING: ["준비중", "bg-[#FFF3CC] text-gold-text", "배송 시작"],
  SHIPPING: ["배송중", "bg-[#E3F0FA] text-[#3a76a8]", "배송 완료"],
  DELIVERED: ["배송완료", "bg-[#E8F3D8] text-brand-text", "완료됨"],
};
const exMeta: Record<string, [string, string, string]> = {
  REQUESTED: ["신청됨", "bg-[#F0ECF9] text-[#7a5ea8]", "준비 시작"],
  PREPARING: ["준비중", "bg-[#FFF3CC] text-gold-text", "배송 시작"],
  SHIPPING: ["배송중", "bg-[#E3F0FA] text-[#3a76a8]", "배송 완료"],
  DELIVERED: ["배송완료", "bg-[#E8F3D8] text-brand-text", "완료됨"],
  CANCELLED: ["취소됨", "bg-[#f0f1ea] text-[#8a8a8a]", "취소됨"],
};

const KPIS = [
  {
    label: "오늘 주문",
    value: "12건",
    delta: "▲ 어제 대비 +3",
    dc: "text-brand-text",
  },
  { label: "교환 신청", value: "5건", delta: "대기 2건", dc: "text-gold-text" },
  {
    label: "활성 사용자",
    value: "184명",
    delta: "▲ +12",
    dc: "text-brand-text",
  },
  {
    label: "미처리 신고",
    value: "2건",
    delta: "검토 필요",
    dc: "text-[#b5502f]",
  },
];

const PANEL = "overflow-hidden rounded-[18px] bg-white shadow-card";
const HEAD =
  "bg-[#f6f7f1] px-[18px] py-3.5 text-[12.5px] font-extrabold text-sub";
const ROW = "items-center border-t border-[#f2f3ec] px-[18px] py-3.5 text-sm";
const CHIP = "rounded-full px-2.5 py-1 text-xs font-extrabold";
const BTN_SOFT =
  "cursor-pointer rounded-[9px] bg-brand-soft px-[13px] py-[7px] text-[13px] font-bold text-brand-dark transition-colors duration-150 hover:bg-brand hover:text-white";

export default function Admin() {
  const { showToast } = useUI();
  const [tab, setTab] = useState("orders");
  const [orders, setOrders] = useState([
    {
      id: 1,
      no: "ORD-...0043",
      user: "초록",
      amount: 2100,
      delivery: "PREPARING",
    },
    {
      id: 2,
      no: "ORD-...0031",
      user: "민트",
      amount: 1500,
      delivery: "SHIPPING",
    },
    {
      id: 3,
      no: "ORD-...0022",
      user: "단풍",
      amount: 1200,
      delivery: "DELIVERED",
    },
    {
      id: 4,
      no: "ORD-...0018",
      user: "노을",
      amount: 800,
      delivery: "PREPARING",
    },
  ]);
  const { state, hydrated } = useStore();
  const [exchanges, setExchanges] = useState<ExchangeOrderData[]>([]);
  const [exchangesLoading, setExchangesLoading] = useState(true);
  const [exchangesError, setExchangesError] = useState("");

  useEffect(() => {
    if (!hydrated || !state.accessToken) return;
    const accessToken = state.accessToken;

    const controller = new AbortController();
    setExchangesLoading(true);
    setExchangesError("");

    getExchangesForAdmin(accessToken, undefined, 0, 50, controller.signal)
      .then((page) => setExchanges(page.content))
      .catch((requestError) => {
        if (
          requestError instanceof DOMException &&
          requestError.name === "AbortError"
        )
          return;
        setExchanges([]);
        setExchangesError(
          requestError instanceof ApiError
            ? requestError.message
            : "교환 신청을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.",
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setExchangesLoading(false);
      });

    return () => controller.abort();
  }, [hydrated, state.accessToken]);

  const [speciesList, setSpeciesList] = useState<PlantSpeciesData[]>([]);
  const [speciesLoading, setSpeciesLoading] = useState(true);
  const [speciesError, setSpeciesError] = useState("");
  const [speciesForm, setSpeciesForm] = useState({ name: "", category: "", careGuide: "" });
  const [speciesSubmitting, setSpeciesSubmitting] = useState(false);
  const [editingSpecies, setEditingSpecies] = useState<PlantSpeciesData | null>(null);
  const [editForm, setEditForm] = useState({ name: "", category: "", careGuide: "" });
  const [editSubmitting, setEditSubmitting] = useState(false);

  useEffect(() => {
    if (!hydrated || !state.accessToken) return;
    const accessToken = state.accessToken;
    const controller = new AbortController();
    setSpeciesLoading(true);
    setSpeciesError("");

    getSpecies(accessToken, controller.signal)
      .then((data) => setSpeciesList(data))
      .catch((requestError) => {
        if (requestError instanceof DOMException && requestError.name === "AbortError") return;
        setSpeciesList([]);
        setSpeciesError(
          requestError instanceof ApiError
            ? requestError.message
            : "종 목록을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.",
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setSpeciesLoading(false);
      });

    return () => controller.abort();
  }, [hydrated, state.accessToken]);

  const [products, setProducts] = useState([
    {
      id: 1,
      name: "새싹 재배 키트",
      emoji: "🌱",
      price: 800,
      stock: 24,
      hidden: false,
    },
    {
      id: 2,
      name: "방울토마토 모종",
      emoji: "🍅",
      price: 1200,
      stock: 12,
      hidden: false,
    },
    {
      id: 3,
      name: "상추 모종",
      emoji: "🥬",
      price: 700,
      stock: 0,
      hidden: false,
    },
    {
      id: 4,
      name: "딸기 모종",
      emoji: "🍓",
      price: 1800,
      stock: 5,
      hidden: true,
    },
  ]);
  const [reports, setReports] = useState([
    {
      id: 1,
      target: "토실이 · 2026.07.21 일지",
      emoji: "🍅",
      grad: "linear-gradient(135deg,#FFCC80,#FF8A65)",
      reason: "사진 도용",
      reporter: "민트",
      date: "오늘",
      status: "PENDING",
    },
    {
      id: 2,
      target: "바질이 · 2026.07.19 일지",
      emoji: "🌿",
      grad: "linear-gradient(135deg,#AED581,#7CB342)",
      reason: "스팸/광고",
      reporter: "단풍",
      date: "어제",
      status: "PENDING",
    },
    {
      id: 3,
      target: "매콤이 · 2026.07.12 일지",
      emoji: "🌶️",
      grad: "linear-gradient(135deg,#EF9A9A,#E57373)",
      reason: "부적절한 콘텐츠",
      reporter: "노을",
      date: "2026.07.13",
      status: "RESOLVED",
    },
  ]);

  const advOrder = (id: number) => {
    setOrders(
      orders.map((o) =>
        o.id === id
          ? {
              ...o,
              delivery:
                DELSEQ[
                  Math.min(DELSEQ.length - 1, DELSEQ.indexOf(o.delivery) + 1)
                ],
            }
          : o,
      ),
    );
    showToast("배송 상태를 업데이트했어요. 고객에게 알림이 발송돼요 📦");
  };
  const advEx = async (x: ExchangeOrderData) => {
    if (!state.accessToken) return;
    try {
      let updated: ExchangeOrderData;
      if (x.status === "REQUESTED")
        updated = await prepareExchange(x.id, state.accessToken);
      else if (x.status === "PREPARING")
        updated = await shipExchange(x.id, state.accessToken);
      else if (x.status === "SHIPPING")
        updated = await deliverExchange(x.id, state.accessToken);
      else return;
      setExchanges((prev) => prev.map((e) => (e.id === x.id ? updated : e)));
      showToast("교환 상태를 업데이트했어요 🍉");
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError
          ? requestError.message
          : "상태 변경에 실패했어요. 잠시 후 다시 시도해 주세요.",
        "err",
      );
    }
  };
  const cancelEx = async (id: number) => {
    if (!state.accessToken) return;
    try {
      const updated = await adminCancelExchange(
        id,
        "관리자 취소",
        state.accessToken,
      );
      setExchanges((prev) => prev.map((e) => (e.id === id ? updated : e)));
      showToast("교환을 취소하고 카드를 복원했어요.");
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError
          ? requestError.message
          : "취소에 실패했어요. 잠시 후 다시 시도해 주세요.",
        "err",
      );
    }
  };
  const addSpecies = async () => {
    if (!state.accessToken) return;
    if (!speciesForm.name.trim()) return showToast("종 이름을 입력해 주세요.", "err");

    setSpeciesSubmitting(true);
    try {
      const created = await createSpecies(
        {
          name: speciesForm.name.trim(),
          ...(speciesForm.category.trim() ? { category: speciesForm.category.trim() } : {}),
          ...(speciesForm.careGuide.trim() ? { careGuide: speciesForm.careGuide.trim() } : {}),
        },
        state.accessToken,
      );
      setSpeciesList([created, ...speciesList]);
      setSpeciesForm({ name: "", category: "", careGuide: "" });
      showToast(`'${created.name}' 종을 추가했어요 🌱`);
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError ? requestError.message : "종 추가에 실패했어요. 잠시 후 다시 시도해 주세요.",
        "err",
      );
    } finally {
      setSpeciesSubmitting(false);
    }
  };
  const openEditSpecies = (sp: PlantSpeciesData) => {
    setEditingSpecies(sp);
    setEditForm({ name: sp.name, category: sp.category ?? "", careGuide: sp.careGuide ?? "" });
  };
  const closeEditSpecies = () => {
    if (editSubmitting) return;
    setEditingSpecies(null);
  };
  const saveEditSpecies = async () => {
    if (!state.accessToken || !editingSpecies) return;
    if (!editForm.name.trim()) return showToast("종 이름을 입력해 주세요.", "err");

    setEditSubmitting(true);
    try {
      const updated = await updateSpecies(
        editingSpecies.id,
        {
          name: editForm.name.trim(),
          ...(editForm.category.trim() ? { category: editForm.category.trim() } : {}),
          ...(editForm.careGuide.trim() ? { careGuide: editForm.careGuide.trim() } : {}),
        },
        state.accessToken,
      );
      setSpeciesList(speciesList.map((sp) => (sp.id === updated.id ? updated : sp)));
      showToast(`'${updated.name}' 정보를 수정했어요 🌱`);
      setEditingSpecies(null);
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError ? requestError.message : "종 수정에 실패했어요. 잠시 후 다시 시도해 주세요.",
        "err",
      );
    } finally {
      setEditSubmitting(false);
    }
  };
  const toggleProd = (id: number) => {
    setProducts(
      products.map((p) => (p.id === id ? { ...p, hidden: !p.hidden } : p)),
    );
    showToast("상품 노출 상태를 변경했어요.");
  };
  const resolveReport = (id: number, msg: string) => {
    setReports(
      reports.map((r) => (r.id === id ? { ...r, status: "RESOLVED" } : r)),
    );
    showToast(msg);
  };

  const TABS = [
    ["orders", "주문 관리"],
    ["exchanges", "교환 관리"],
    ["products", "상품 관리"],
    ["reports", "신고 관리"],
    ["species", "종 관리"],
  ];

  return (
    <div className="container">
      <div className="mb-1 flex items-center gap-2.5">
        <span className="material-symbols-outlined text-[26px] text-brand-dark">
          build
        </span>
        <h1 className="text-[26px] font-extrabold">관리자 콘솔</h1>
      </div>
      <p className="mb-[22px] text-sub">
        서비스 운영 현황을 한눈에. (데모 화면)
      </p>

      <div className="mb-7 grid gap-3.5 [grid-template-columns:repeat(auto-fit,minmax(170px,1fr))]">
        {KPIS.map((k) => (
          <div key={k.label} className="rounded-2xl bg-white p-5 shadow-card">
            <div className="text-[13px] font-bold text-sub">{k.label}</div>
            <div className="mt-1.5 text-[26px] font-extrabold">{k.value}</div>
            <div className={`mt-1 text-xs font-bold ${k.dc}`}>{k.delta}</div>
          </div>
        ))}
      </div>

      <div className="mb-[22px] flex w-fit flex-wrap gap-1.5 rounded-[14px] bg-[#F0F2E8] p-[5px]">
        {TABS.map(([k, label]) => (
          <button
            key={k}
            type="button"
            onClick={() => setTab(k)}
            className={`cursor-pointer rounded-[11px] px-[18px] py-[9px] text-sm font-bold transition-colors duration-150 ${
              tab === k
                ? "bg-white text-ink shadow-[0_2px_8px_rgba(0,0,0,.06)]"
                : "bg-transparent text-sub hover:bg-white/70 hover:text-ink"
            }`}
          >
            {label}
          </button>
        ))}
      </div>

      {tab === "orders" && (
        <div className={PANEL}>
          <div className={`grid grid-cols-[1.4fr_1fr_1fr_1fr_1.2fr] ${HEAD}`}>
            <div>주문번호</div>
            <div>고객</div>
            <div>금액</div>
            <div>배송상태</div>
            <div className="text-right">처리</div>
          </div>
          {orders.map((o) => {
            const m = delMeta[o.delivery];
            return (
              <div
                key={o.id}
                className={`grid grid-cols-[1.4fr_1fr_1fr_1fr_1.2fr] ${ROW}`}
              >
                <div className="font-bold">{o.no}</div>
                <div className="text-[#6d7a68]">{o.user}</div>
                <div className="font-bold text-gold-text">{fmt(o.amount)}P</div>
                <div>
                  <span className={`${CHIP} ${m[1]}`}>{m[0]}</span>
                </div>
                <div className="text-right">
                  <button
                    type="button"
                    onClick={() => advOrder(o.id)}
                    className={BTN_SOFT}
                  >
                    {m[2]}
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {tab === "exchanges" && (
        <div className={PANEL}>
          <div className={`grid grid-cols-[1.3fr_1fr_1fr_1.4fr] ${HEAD}`}>
            <div>실물상품</div>
            <div>카드</div>
            <div>상태</div>
            <div className="text-right">처리</div>
          </div>
          {exchangesLoading ? (
            <div className="px-[18px] py-10 text-center text-sm text-sub">
              교환 신청을 불러오고 있어요 🍉
            </div>
          ) : exchangesError ? (
            <div className="px-[18px] py-10 text-center text-sm text-sub">
              {exchangesError}
            </div>
          ) : exchanges.length === 0 ? (
            <div className="px-[18px] py-10 text-center text-sm text-sub">
              교환 신청이 없어요.
            </div>
          ) : (
            exchanges.map((x) => {
              const m = exMeta[x.status];
              const advanceable =
                x.status === "REQUESTED" ||
                x.status === "PREPARING" ||
                x.status === "SHIPPING";
              return (
                <div
                  key={x.id}
                  className={`grid grid-cols-[1.3fr_1fr_1fr_1.4fr] ${ROW}`}
                >
                  <div className="font-bold">{x.exchangeProductName}</div>
                  <div className="text-[#6d7a68]">
                    {x.cardName} {x.usedCardCount}장
                  </div>
                  <div>
                    <span className={`${CHIP} ${m[1]}`}>{m[0]}</span>
                  </div>
                  <div className="flex justify-end gap-1.5">
                    {advanceable && (
                      <button
                        type="button"
                        onClick={() => advEx(x)}
                        className="cursor-pointer rounded-[9px] bg-brand-soft px-3 py-[7px] text-[13px] font-bold text-brand-dark transition-colors duration-150 hover:bg-brand hover:text-white"
                      >
                        {m[2]}
                      </button>
                    )}
                    {x.status === "REQUESTED" && (
                      <button
                        type="button"
                        onClick={() => cancelEx(x.id)}
                        className="cursor-pointer rounded-[9px] border-[1.5px] border-[#e8bdad] bg-white px-3 py-[7px] text-[13px] font-bold text-[#b5502f] transition-colors duration-150 hover:bg-danger-soft hover:border-[#e0a488]"
                      >
                        취소
                      </button>
                    )}
                  </div>
                </div>
              );
            })
          )}
        </div>
      )}

      {tab === "products" && (
        <div className={PANEL}>
          <div className={`grid grid-cols-[1.6fr_1fr_1fr_1.2fr] ${HEAD}`}>
            <div>상품명</div>
            <div>가격</div>
            <div>재고</div>
            <div className="text-right">노출</div>
          </div>
          {products.map((p) => (
            <div
              key={p.id}
              className={`grid grid-cols-[1.6fr_1fr_1fr_1.2fr] ${ROW}`}
            >
              <div className="flex items-center gap-[9px] font-bold">
                <span className="text-xl">{p.emoji}</span>
                {p.name}
              </div>
              <div className="font-bold text-gold-text">{fmt(p.price)}P</div>
              <div
                className={`font-bold ${p.stock === 0 ? "text-[#b5502f]" : "text-brand-text"}`}
              >
                {p.stock === 0 ? "품절" : p.stock + "개"}
              </div>
              <div className="text-right">
                <button
                  type="button"
                  onClick={() => toggleProd(p.id)}
                  aria-pressed={!p.hidden}
                  className={`relative inline-block h-[26px] w-[46px] cursor-pointer rounded-full transition-colors duration-150 ${p.hidden ? "bg-[#d7dccd] hover:bg-[#c5cbb9]" : "bg-brand hover:bg-brand-dark"}`}
                >
                  <span
                    className={`absolute top-[3px] h-5 w-5 rounded-full bg-white transition-[left] ${p.hidden ? "left-[3px]" : "left-[23px]"}`}
                  />
                </button>
              </div>
            </div>
          ))}
        </div>
      )}

      {tab === "species" && (
        <div className="flex flex-col gap-5">
          <div className={`${PANEL} p-5`}>
            <div className="mb-3.5 text-sm font-extrabold">새 종 추가</div>
            <div className="grid gap-2.5 sm:grid-cols-3">
              <input
                value={speciesForm.name}
                onChange={(e) => setSpeciesForm({ ...speciesForm, name: e.target.value })}
                placeholder="이름 (예: 몬스테라)"
                maxLength={100}
                className="rounded-xl border-[1.5px] border-line px-[13px] py-2.5 text-sm outline-none"
              />
              <input
                value={speciesForm.category}
                onChange={(e) => setSpeciesForm({ ...speciesForm, category: e.target.value })}
                placeholder="카테고리 (선택)"
                maxLength={50}
                className="rounded-xl border-[1.5px] border-line px-[13px] py-2.5 text-sm outline-none"
              />
              <input
                value={speciesForm.careGuide}
                onChange={(e) => setSpeciesForm({ ...speciesForm, careGuide: e.target.value })}
                placeholder="관리 가이드 (선택)"
                maxLength={500}
                className="rounded-xl border-[1.5px] border-line px-[13px] py-2.5 text-sm outline-none"
              />
            </div>
            <button
              type="button"
              onClick={addSpecies}
              disabled={speciesSubmitting}
              className="mt-3.5 cursor-pointer rounded-[11px] bg-brand px-4 py-2.5 text-sm font-bold text-white disabled:opacity-60"
            >
              {speciesSubmitting ? "추가 중..." : "+ 종 추가"}
            </button>
          </div>

          <div className={PANEL}>
            <div className={`grid grid-cols-[1.2fr_1fr_2fr] ${HEAD}`}>
              <div>이름</div>
              <div>카테고리</div>
              <div>관리 가이드</div>
            </div>
            {speciesLoading ? (
              <div className="px-[18px] py-10 text-center text-sm text-sub">종 목록을 불러오고 있어요 🌱</div>
            ) : speciesError ? (
              <div className="px-[18px] py-10 text-center text-sm text-sub">{speciesError}</div>
            ) : speciesList.length === 0 ? (
              <div className="px-[18px] py-10 text-center text-sm text-sub">등록된 종이 없어요.</div>
            ) : (
              speciesList.map((sp) => (
                <button
                  key={sp.id}
                  type="button"
                  onClick={() => openEditSpecies(sp)}
                  className={`grid w-full grid-cols-[1.2fr_1fr_2fr] ${ROW} cursor-pointer text-left transition-colors duration-150 hover:bg-[#f9faf6]`}
                >
                  <div className="font-bold">{sp.name}</div>
                  <div className="text-[#6d7a68]">{sp.category ?? "-"}</div>
                  <div className="truncate text-[#6d7a68]">{sp.careGuide ?? "-"}</div>
                </button>
              ))
            )}
          </div>
        </div>
      )}

      {editingSpecies && (
        <div
          onClick={closeEditSpecies}
          className="fixed inset-0 z-[60] flex items-start justify-center overflow-auto bg-[rgba(46,54,42,.4)] px-5 py-10"
        >
          <div onClick={(e) => e.stopPropagation()} className="w-full max-w-[460px] animate-pop rounded-[22px] bg-white p-[26px]">
            <h3 className="mb-5 text-xl font-extrabold">종 정보 수정 🌿</h3>

            <label className="text-[13px] font-bold text-[#6d7a68]">이름</label>
            <input
              value={editForm.name}
              onChange={(e) => setEditForm({ ...editForm, name: e.target.value })}
              maxLength={100}
              className="mb-4 mt-1.5 w-full rounded-xl border-[1.5px] border-line px-[13px] py-3 outline-none"
            />

            <label className="text-[13px] font-bold text-[#6d7a68]">카테고리</label>
            <input
              value={editForm.category}
              onChange={(e) => setEditForm({ ...editForm, category: e.target.value })}
              maxLength={50}
              className="mb-4 mt-1.5 w-full rounded-xl border-[1.5px] border-line px-[13px] py-3 outline-none"
            />

            <label className="text-[13px] font-bold text-[#6d7a68]">관리 가이드</label>
            <textarea
              value={editForm.careGuide}
              onChange={(e) => setEditForm({ ...editForm, careGuide: e.target.value })}
              maxLength={500}
              rows={5}
              className="mb-5 mt-1.5 w-full resize-none rounded-xl border-[1.5px] border-line px-[13px] py-3 outline-none"
            />

            <div className="flex gap-2">
              <button
                type="button"
                onClick={saveEditSpecies}
                disabled={editSubmitting}
                className="flex-1 cursor-pointer rounded-[13px] bg-brand p-3.5 text-base font-extrabold text-white disabled:opacity-60"
              >
                {editSubmitting ? "저장 중..." : "저장"}
              </button>
              <button
                type="button"
                onClick={closeEditSpecies}
                disabled={editSubmitting}
                className="flex-1 cursor-pointer rounded-[13px] border-[1.5px] border-line bg-white p-3.5 text-base font-bold text-[#6d7a68] disabled:opacity-60"
              >
                취소
              </button>
            </div>
          </div>
        </div>
      )}

      {tab === "reports" && (
        <div className="flex flex-col gap-3">
          {reports.map((r) => {
            const pending = r.status === "PENDING";
            return (
              <div
                key={r.id}
                className="flex flex-wrap items-center gap-3.5 rounded-2xl bg-white px-5 py-[18px] shadow-card"
              >
                <div
                  className="flex h-12 w-12 items-center justify-center rounded-xl text-2xl"
                  style={{ background: r.grad }}
                >
                  {r.emoji}
                </div>
                <div className="min-w-[180px] flex-1">
                  <div className="font-bold">{r.target}</div>
                  <div className="mt-0.5 text-[13px] text-sub">
                    사유: {r.reason} · 신고자 {r.reporter} · {r.date}
                  </div>
                </div>
                <span
                  className={`${CHIP} ${pending ? "bg-[#FBEDE3] text-[#b5771a]" : "bg-[#E8F3D8] text-brand-text"}`}
                >
                  {pending ? "검토 대기" : "처리 완료"}
                </span>
                {pending && (
                  <div className="flex gap-1.5">
                    <button
                      type="button"
                      onClick={() =>
                        resolveReport(r.id, "콘텐츠를 숨김 처리했어요.")
                      }
                      className="cursor-pointer rounded-[9px] bg-danger-soft px-[13px] py-2 text-[13px] font-bold text-[#b5502f] transition-colors duration-150 hover:bg-danger hover:text-white"
                    >
                      숨김 처리
                    </button>
                    <button
                      type="button"
                      onClick={() => resolveReport(r.id, "신고를 반려했어요.")}
                      className="cursor-pointer rounded-[9px] bg-brand-soft px-[13px] py-2 text-[13px] font-bold text-brand-dark transition-colors duration-150 hover:bg-brand hover:text-white"
                    >
                      반려
                    </button>
                  </div>
                )}
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
