"use client";
import { fmt } from "@/lib/store";
import { useUI } from "@/lib/ui";
import { useState } from "react";

const DELSEQ = ["PREPARING", "SHIPPING", "DELIVERED"];
const EXSEQ = ["REQUESTED", "PREPARING", "SHIPPING", "DELIVERED"];
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
  const [exchanges, setExchanges] = useState([
    {
      id: 1,
      product: "진짜 수박",
      card: "수박 카드",
      count: 5,
      status: "REQUESTED",
    },
    {
      id: 2,
      product: "방울토마토 1kg",
      card: "토마토 카드",
      count: 4,
      status: "PREPARING",
    },
    {
      id: 3,
      product: "당근 1kg",
      card: "당근 카드",
      count: 3,
      status: "SHIPPING",
    },
  ]);
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
  const advEx = (id: number) => {
    setExchanges(
      exchanges.map((x) =>
        x.id === id && x.status !== "CANCELLED"
          ? {
              ...x,
              status:
                EXSEQ[Math.min(EXSEQ.length - 1, EXSEQ.indexOf(x.status) + 1)],
            }
          : x,
      ),
    );
    showToast("교환 상태를 업데이트했어요 🍉");
  };
  const cancelEx = (id: number) => {
    setExchanges(
      exchanges.map((x) => (x.id === id ? { ...x, status: "CANCELLED" } : x)),
    );
    showToast("교환을 취소하고 카드를 복원했어요.");
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
        서비스 운영 현황을 한눈에. (데모 — 실제 권한 검증은 서버에서 이뤄져요)
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
          {exchanges.map((x) => {
            const m = exMeta[x.status];
            return (
              <div
                key={x.id}
                className={`grid grid-cols-[1.3fr_1fr_1fr_1.4fr] ${ROW}`}
              >
                <div className="font-bold">{x.product}</div>
                <div className="text-[#6d7a68]">
                  {x.card} {x.count}장
                </div>
                <div>
                  <span className={`${CHIP} ${m[1]}`}>{m[0]}</span>
                </div>
                <div className="flex justify-end gap-1.5">
                  <button
                    type="button"
                    onClick={() => advEx(x.id)}
                    className="cursor-pointer rounded-[9px] bg-brand-soft px-3 py-[7px] text-[13px] font-bold text-brand-dark transition-colors duration-150 hover:bg-brand hover:text-white"
                  >
                    {m[2]}
                  </button>
                  <button
                    type="button"
                    onClick={() => cancelEx(x.id)}
                    className="cursor-pointer rounded-[9px] border-[1.5px] border-[#e8bdad] bg-white px-3 py-[7px] text-[13px] font-bold text-[#b5502f] transition-colors duration-150 hover:bg-danger-soft hover:border-[#e0a488]"
                  >
                    취소
                  </button>
                </div>
              </div>
            );
          })}
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
