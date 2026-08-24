"use client";
import { ApiError } from "@/lib/api";
import AdminAssetKeyField from "@/components/admin/AdminAssetKeyField";
import AdminCouponPanel from "@/components/admin/AdminCouponPanel";
import AdminGachaOperationsPanel from "@/components/admin/AdminGachaOperationsPanel";
import AdminInquiryPanel from "@/components/admin/AdminInquiryPanel";
import AdminReportPanel, {
  AdminReportTargetUserFilter,
} from "@/components/admin/AdminReportPanel";
import AdminBoardPanel from "@/components/admin/AdminBoardPanel";
import AdminUserPanel from "@/components/admin/AdminUserPanel";
import AdminCardMarketRevenuePanel from "@/components/admin/AdminCardMarketRevenuePanel";
import AdminPointAdjustmentPanel from "@/features/point/AdminPointAdjustmentPanel";
import { formatPhone } from "@/components/AddressForm";
import {
  adjustAdminProductStock,
  AdminProduct,
  AdminProductInput,
  changeAdminProductStatus,
  createAdminProduct,
  getAdminProducts,
  hideAdminProduct,
  updateAdminProduct,
  uploadAdminProductImage,
} from "@/lib/admin-product-api";
import {
  adminCancelExchange,
  deliverExchange,
  ExchangeOrderData,
  ExchangeStatus,
  getExchangesForAdmin,
  shipExchange,
} from "@/lib/exchange-api";
import {
  cancelOrderForAdmin,
  deliverOrderForAdmin,
  getOrdersForAdmin,
  OrderData,
  OrderItemData,
  shipOrderForAdmin,
} from "@/lib/order-api";
import { searchPlantCareGuideSpecies } from "@/lib/care-guide-api";
import { getAdminUsers } from "@/features/admin/user-api";
import { getReportsForAdmin } from "@/lib/report-api";
import { fmt, useStore } from "@/lib/store";
import { couponName } from "@/lib/coupon-label";
import { ProductCategory } from "@/lib/product-api";
import { useUI } from "@/lib/ui";
import { useEffect, useMemo, useRef, useState } from "react";
import { useRouter } from "next/navigation";
import AdminPagination from "@/components/admin/AdminPagination";
import { useScrollOnPageLoad } from "@/components/admin/use-scroll-on-page-load";

const CANCEL_REASON_OPTIONS = [
  "품절",
  "고객 요청",
  "배송 불가",
  "결제 오류",
  "기타",
];

const delMeta: Record<string, [string, string, string]> = {
  PREPARING: ["준비중", "bg-[#FFF3CC] text-gold-text", "배송 시작"],
  SHIPPING: ["배송중", "bg-[#E3F0FA] text-[#3a76a8]", "배송 완료"],
  DELIVERED: ["배송완료", "bg-[#E8F3D8] text-brand-text", "완료됨"],
};
const exMeta: Record<string, [string, string, string]> = {
  PREPARING: ["준비중", "bg-[#FFF3CC] text-gold-text", "배송 시작"],
  SHIPPING: ["배송중", "bg-[#E3F0FA] text-[#3a76a8]", "배송 완료"],
  DELIVERED: ["배송완료", "bg-[#E8F3D8] text-brand-text", "완료됨"],
  CANCELLED: ["취소됨", "bg-[#f0f1ea] text-[#8a8a8a]", "취소됨"],
};

interface DashboardKpis {
  todayOrders: number;
  yesterdayOrders: number;
  pendingExchanges: number;
  activeUsers: number;
  totalUsers: number;
  pendingReports: number;
}

const pad2 = (n: number) => String(n).padStart(2, "0");
// 브라우저 로컬 시각 기준 자정을 백엔드가 기대하는 ISO_LOCAL_DATE_TIME(오프셋 없음) 문자열로
// 만든다 — Date.toISOString()은 UTC로 변환돼 관리자가 보는 "오늘"과 날짜가 어긋날 수 있다.
function localMidnightIso(baseDate: Date, offsetDays: number): string {
  const d = new Date(
    baseDate.getFullYear(),
    baseDate.getMonth(),
    baseDate.getDate() + offsetDays,
  );
  return `${d.getFullYear()}-${pad2(d.getMonth() + 1)}-${pad2(d.getDate())}T00:00:00`;
}

function formatDateTime(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat("ko-KR", {
    year: "numeric", month: "2-digit", day: "2-digit", hour: "2-digit", minute: "2-digit", hour12: false,
  }).format(date);
}

const ORDER_STATUS_OPTIONS = [
  { key: "", label: "전체" },
  { key: "PREPARING", label: "준비중" },
  { key: "SHIPPING", label: "배송중" },
  { key: "DELIVERED", label: "배송완료" },
  { key: "PURCHASE_CONFIRMED", label: "구매확정" },
  { key: "CANCELLED", label: "취소됨" },
] as const;

// 배송 흐름(준비중/배송중/배송완료)과 주문 자체 상태(구매확정/취소됨)를 하나의 드롭다운으로
// 합쳐 보여주되, 실제 API 호출 시에는 어느 필드로 걸어야 하는지에 따라 나눠서 보낸다.
function buildOrderFilters(key: string) {
  if (!key) return undefined;
  if (key === "PURCHASE_CONFIRMED" || key === "CANCELLED") {
    return { status: key as "PURCHASE_CONFIRMED" | "CANCELLED" };
  }
  // 취소된 주문과 구매확정된 주문도 배송상태(취소/확정 시점 값)가 그대로 남아 있어
  // deliveryStatus만으로 걸면 함께 걸려 나온다. 서버가 페이지네이션까지 끝낸 뒤에
  // 클라이언트에서 걸러내면 뒷페이지가 통째로 비거나 totalPages가 틀어지므로,
  // status=PAID를 함께 보내 서버 쿼리 단계에서 제외한다.
  return { deliveryStatus: key as "PREPARING" | "SHIPPING" | "DELIVERED", status: "PAID" as const };
}

// 완료된(배송완료/구매확정) 건만 최신순으로, 그 외에는 아직 처리해야 할 오래된 것부터 보이게 한다.
function orderSort(filterKey: string) {
  return filterKey === "DELIVERED" || filterKey === "PURCHASE_CONFIRMED"
    ? "orderedAt,DESC"
    : "orderedAt,ASC";
}
function exchangeSort(filterKey: string) {
  return filterKey === "DELIVERED" ? "requestedAt,DESC" : "requestedAt,ASC";
}

const EXCHANGE_STATUS_OPTIONS = [
  { key: "", label: "전체" },
  { key: "PREPARING", label: "준비중" },
  { key: "SHIPPING", label: "배송중" },
  { key: "DELIVERED", label: "배송완료" },
  { key: "CANCELLED", label: "취소됨" },
] as const;

const ORDER_STATUS_PRIORITY: Record<string, number> = {
  PREPARING: 0, SHIPPING: 1, DELIVERED: 2, PURCHASE_CONFIRMED: 3, CANCELLED: 4,
};
const EXCHANGE_STATUS_PRIORITY: Record<string, number> = {
  PREPARING: 0, SHIPPING: 1, DELIVERED: 2, CANCELLED: 3,
};

function formatDelta(diff: number): string {
  if (diff > 0) return `▲ 어제 대비 +${diff}`;
  if (diff < 0) return `▼ 어제 대비 ${diff}`;
  return "어제와 동일";
}

const PANEL = "overflow-hidden rounded-[18px] bg-white shadow-card";
const HEAD =
  "bg-[#f6f7f1] px-[18px] py-3.5 text-[12.5px] font-extrabold text-sub";
const ROW = "items-center border-t border-[#f2f3ec] px-[18px] py-3.5 text-sm";
const CHIP = "rounded-full px-2.5 py-1 text-xs font-extrabold";
const BTN_SOFT =
  "cursor-pointer rounded-[9px] bg-brand-soft px-[13px] py-[7px] text-[13px] font-bold text-brand-dark transition-colors duration-150 hover:bg-brand hover:text-white";
const ADMIN_PAGE_SIZE = 10;
// "전체" 조회에서 상태 우선순위대로 전체 정렬하려면 서버가 한 번에 다 내려줘야 한다 —
// 서버는 필드 하나로만 정렬할 수 있어 커스텀 상태 순서를 페이지 단위로는 만들 수 없다.
const ADMIN_ALL_SIZE = 2000;

const PRODUCT_CATEGORY_LABEL: Record<ProductCategory, string> = {
  KIT: "재배 키트",
  SEEDLING: "모종",
  GACHA_PACK: "가챠 팩",
};

const ADMIN_TABS = [
  ["orders", "주문 관리"],
  ["exchanges", "교환 관리"],
  ["products", "상품 관리"],
  ["coupons", "쿠폰 관리"],
  ["gacha-operations", "가챠 장애 관리"],
  ["card-market-revenue", "거래소 수익"],
  ["points", "포인트 관리"],
  ["inquiries", "문의 관리"],
  ["reports", "신고 관리"],
  ["board", "게시판 관리"],
  ["users", "유저 관리"],
] as const;

export default function Admin({
  searchParams,
}: {
  searchParams?: { tab?: string | string[]; page?: string | string[] };
}) {
  const router = useRouter();
  const { showToast, askConfirm } = useUI();
  const requestedTab = Array.isArray(searchParams?.tab)
    ? searchParams?.tab[0]
    : searchParams?.tab;
  const requestedPage = Number(
    Array.isArray(searchParams?.page)
      ? searchParams?.page[0]
      : searchParams?.page,
  );
  const urlPage =
    Number.isInteger(requestedPage) && requestedPage > 0
      ? requestedPage - 1
      : 0;
  const [adminPage, setAdminPage] = useState(urlPage);
  const [tab, setTab] = useState(() =>
    ADMIN_TABS.some(([key]) => key === requestedTab) ? requestedTab! : "orders",
  );
  // 유저 관리 탭의 "누적 신고수"를 누르면 신고 관리 탭으로 전환하면서 이 값을 채운다.
  const [reportsUserFilter, setReportsUserFilter] =
    useState<AdminReportTargetUserFilter | null>(null);

  useEffect(() => {
    setTab(
      ADMIN_TABS.some(([key]) => key === requestedTab)
        ? requestedTab!
        : "orders",
    );
  }, [requestedTab]);
  useEffect(() => setAdminPage(urlPage), [urlPage]);
  const { state, hydrated } = useStore();

  const [kpis, setKpis] = useState<DashboardKpis | null>(null);
  const [kpisLoading, setKpisLoading] = useState(true);

  useEffect(() => {
    if (!state.accessToken) return;
    const accessToken = state.accessToken;
    const controller = new AbortController();
    setKpisLoading(true);
    const now = new Date();
    const todayStart = localMidnightIso(now, 0);
    const tomorrowStart = localMidnightIso(now, 1);
    const yesterdayStart = localMidnightIso(now, -1);

    Promise.all([
      getOrdersForAdmin(
        accessToken,
        { from: todayStart, to: tomorrowStart },
        0,
        1,
        controller.signal,
      ),
      getOrdersForAdmin(
        accessToken,
        { from: yesterdayStart, to: todayStart },
        0,
        1,
        controller.signal,
      ),
      getExchangesForAdmin(accessToken, "PREPARING", 0, 1, controller.signal),
      getAdminUsers({
        accessToken,
        status: "ACTIVE",
        page: 0,
        size: 1,
        signal: controller.signal,
      }),
      getAdminUsers({
        accessToken,
        page: 0,
        size: 1,
        signal: controller.signal,
      }),
      getReportsForAdmin(accessToken, "PENDING", 0, 1, controller.signal),
    ])
      .then(
        ([
          todayOrders,
          yesterdayOrders,
          exchanges,
          activeUsers,
          allUsers,
          reports,
        ]) => {
          setKpis({
            todayOrders: todayOrders.totalElements,
            yesterdayOrders: yesterdayOrders.totalElements,
            pendingExchanges: exchanges.totalElements,
            activeUsers: activeUsers.totalElements,
            totalUsers: allUsers.totalElements,
            pendingReports: reports.totalElements,
          });
        },
      )
      .catch((requestError) => {
        if (
          requestError instanceof DOMException &&
          requestError.name === "AbortError"
        )
          return;
        setKpis(null);
      })
      .finally(() => {
        if (!controller.signal.aborted) setKpisLoading(false);
      });

    return () => controller.abort();
  }, [state.accessToken]);

  const dashboardKpis = [
    {
      label: "오늘 주문",
      value: kpisLoading || !kpis ? "-" : `${kpis.todayOrders}건`,
      delta:
        kpisLoading || !kpis
          ? ""
          : formatDelta(kpis.todayOrders - kpis.yesterdayOrders),
      dc:
        !kpis || kpis.todayOrders >= kpis.yesterdayOrders
          ? "text-brand-text"
          : "text-[#b5502f]",
      tab: "orders",
    },
    {
      label: "교환 신청",
      value: kpisLoading || !kpis ? "-" : `${kpis.pendingExchanges}건`,
      delta: kpisLoading || !kpis ? "" : "배송 준비 중",
      dc: "text-gold-text",
      tab: "exchanges",
    },
    {
      label: "활성 사용자",
      value: kpisLoading || !kpis ? "-" : `${kpis.activeUsers}명`,
      delta: kpisLoading || !kpis ? "" : `전체 ${kpis.totalUsers}명 중`,
      dc: "text-brand-text",
      tab: "users",
    },
    {
      label: "미처리 신고",
      value: kpisLoading || !kpis ? "-" : `${kpis.pendingReports}건`,
      delta:
        kpisLoading || !kpis
          ? ""
          : kpis.pendingReports > 0
            ? "검토 필요"
            : "검토 완료",
      dc: "text-[#b5502f]",
      tab: "reports",
    },
  ];
  const ordersSectionRef = useRef<HTMLDivElement>(null);
  const exchangesSectionRef = useRef<HTMLDivElement>(null);
  const productsSectionRef = useRef<HTMLDivElement>(null);
  const [orders, setOrders] = useState<OrderData[]>([]);
  const [orderItemsById, setOrderItemsById] = useState<
    Record<number, OrderItemData[]>
  >({});
  const [ordersLoading, setOrdersLoading] = useState(true);
  const [ordersError, setOrdersError] = useState("");
  const [ordersTotalPages, setOrdersTotalPages] = useState(0);
  const [orderStatusFilter, setOrderStatusFilter] = useState("");

  useEffect(() => {
    if (!hydrated || !state.accessToken) return;
    const accessToken = state.accessToken;

    const controller = new AbortController();
    setOrdersLoading(true);
    setOrdersError("");

    getOrdersForAdmin(
      accessToken,
      buildOrderFilters(orderStatusFilter),
      orderStatusFilter ? adminPage : 0,
      orderStatusFilter ? ADMIN_PAGE_SIZE : ADMIN_ALL_SIZE,
      controller.signal,
      orderSort(orderStatusFilter),
    )
      .then((page) => {
        setOrders(page.content.map((detail) => detail.order));
        setOrdersTotalPages(
          orderStatusFilter
            ? page.totalPages
            : Math.ceil(page.totalElements / ADMIN_PAGE_SIZE),
        );
        const map: Record<number, OrderItemData[]> = {};
        page.content.forEach((detail) => {
          map[detail.order.id] = detail.items;
        });
        setOrderItemsById(map);
      })
      .catch((requestError) => {
        if (
          requestError instanceof DOMException &&
          requestError.name === "AbortError"
        )
          return;
        setOrders([]);
        setOrdersError(
          requestError instanceof ApiError
            ? requestError.message
            : "주문을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.",
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setOrdersLoading(false);
      });

    return () => controller.abort();
  }, [adminPage, orderStatusFilter, hydrated, state.accessToken]);
  const [exchanges, setExchanges] = useState<ExchangeOrderData[]>([]);
  const [exchangesLoading, setExchangesLoading] = useState(true);
  const [exchangesError, setExchangesError] = useState("");
  const [exchangesTotalPages, setExchangesTotalPages] = useState(0);
  const [exchangeStatusFilter, setExchangeStatusFilter] = useState("");

  useEffect(() => {
    if (!hydrated || !state.accessToken) return;
    const accessToken = state.accessToken;

    const controller = new AbortController();
    setExchangesLoading(true);
    setExchangesError("");

    getExchangesForAdmin(
      accessToken,
      (exchangeStatusFilter || undefined) as ExchangeStatus | undefined,
      exchangeStatusFilter ? adminPage : 0,
      exchangeStatusFilter ? ADMIN_PAGE_SIZE : ADMIN_ALL_SIZE,
      controller.signal,
      exchangeSort(exchangeStatusFilter),
    )
      .then((page) => {
        setExchanges(page.content);
        setExchangesTotalPages(
          exchangeStatusFilter
            ? page.totalPages
            : Math.ceil(page.totalElements / ADMIN_PAGE_SIZE),
        );
      })
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
  }, [adminPage, exchangeStatusFilter, hydrated, state.accessToken]);

  // SEEDLING 상품 종 이름 입력용 자동완성 — 이미 재배가이드가 생성된 종 이름만 검색된다.
  const [productSpeciesSuggestions, setProductSpeciesSuggestions] = useState<
    string[]
  >([]);

  const [products, setProducts] = useState<AdminProduct[]>([]);
  const [productsLoading, setProductsLoading] = useState(true);
  const [productsError, setProductsError] = useState("");

  // 주문/교환은 페이지마다 실제로 서버에 다시 조회하므로 응답이 온 뒤(loading===false)에
  // 스크롤한다. 상품은 전체를 한 번에 불러온 뒤 화면에서만 나눠 보여줘서(재요청 없음)
  // productsLoading이 페이지 전환에 반응하지 않는다 — 그래서 상품 목록만 아래
  // AdminPagination의 onChange에서 즉시 스크롤한다.
  useScrollOnPageLoad(adminPage, ordersLoading, ordersSectionRef, tab === "orders");
  useScrollOnPageLoad(adminPage, exchangesLoading, exchangesSectionRef, tab === "exchanges");

  // "전체" 조회일 때는 상태별로 준비중→배송중→배송완료→구매확정→취소됨 순서로 묶어서
  // 전체 데이터를 정렬한 뒤, 그 정렬된 전체 목록을 여기서 직접 페이지 단위로 잘라 보여준다
  // (서버는 필드 하나로만 정렬할 수 있어 커스텀 상태 순서로는 페이지네이션을 못 해준다 —
  // 그래서 "전체"일 때만 한 번에 다 받아와 클라이언트에서 정렬·페이지네이션한다).
  // 배송완료/구매확정은 이미 끝난 건이라 최신순, 나머지는 오래된순이 되도록 그룹 안에서
  // 필요한 그룹만 뒤집는다 — 기본 조회 자체가 orderedAt 오래된순이라 뒤집으면 최신순이 된다.
  const sortedAllOrders = useMemo(() => {
    if (orderStatusFilter) return orders;
    const buckets: Record<string, OrderData[]> = {
      PREPARING: [], SHIPPING: [], DELIVERED: [], PURCHASE_CONFIRMED: [], CANCELLED: [],
    };
    orders.forEach((o) => {
      const key = o.status === "CANCELLED" || o.status === "PURCHASE_CONFIRMED" ? o.status : o.deliveryStatus;
      buckets[key].push(o);
    });
    buckets.DELIVERED.reverse();
    buckets.PURCHASE_CONFIRMED.reverse();
    return Object.keys(ORDER_STATUS_PRIORITY)
      .sort((a, b) => ORDER_STATUS_PRIORITY[a] - ORDER_STATUS_PRIORITY[b])
      .flatMap((key) => buckets[key]);
  }, [orders, orderStatusFilter]);

  const displayOrders = orderStatusFilter
    ? sortedAllOrders
    : sortedAllOrders.slice(adminPage * ADMIN_PAGE_SIZE, (adminPage + 1) * ADMIN_PAGE_SIZE);

  const sortedAllExchanges = useMemo(() => {
    if (exchangeStatusFilter) return exchanges;
    const buckets: Record<string, ExchangeOrderData[]> = {
      PREPARING: [], SHIPPING: [], DELIVERED: [], CANCELLED: [],
    };
    exchanges.forEach((x) => buckets[x.status].push(x));
    buckets.DELIVERED.reverse();
    return Object.keys(EXCHANGE_STATUS_PRIORITY)
      .sort((a, b) => EXCHANGE_STATUS_PRIORITY[a] - EXCHANGE_STATUS_PRIORITY[b])
      .flatMap((key) => buckets[key]);
  }, [exchanges, exchangeStatusFilter]);

  const displayExchanges = exchangeStatusFilter
    ? sortedAllExchanges
    : sortedAllExchanges.slice(adminPage * ADMIN_PAGE_SIZE, (adminPage + 1) * ADMIN_PAGE_SIZE);

  const [productSubmitting, setProductSubmitting] = useState(false);
  const [productBusyId, setProductBusyId] = useState<number | null>(null);
  const [stockDeltas, setStockDeltas] = useState<Record<number, string>>({});
  const [editingProductId, setEditingProductId] = useState<number | null>(null);
  const [productImageFile, setProductImageFile] = useState<File | null>(null);
  const [productForm, setProductForm] = useState({
    name: "",
    category: "KIT" as ProductCategory,
    pointPrice: "",
    stock: "0",
    speciesName: "",
    description: "",
  });

  useEffect(() => {
    if (!hydrated || !state.accessToken) return;
    const trimmed = productForm.speciesName.trim();
    if (!trimmed) {
      setProductSpeciesSuggestions([]);
      return;
    }
    const accessToken = state.accessToken;
    const controller = new AbortController();
    const timer = setTimeout(() => {
      searchPlantCareGuideSpecies(trimmed, accessToken, controller.signal)
        .then((results) => setProductSpeciesSuggestions(results))
        .catch((requestError) => {
          if (
            requestError instanceof DOMException &&
            requestError.name === "AbortError"
          )
            return;
          setProductSpeciesSuggestions([]);
        });
    }, 300);

    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [hydrated, state.accessToken, productForm.speciesName]);

  useEffect(() => {
    if (!hydrated || !state.accessToken) return;
    const controller = new AbortController();
    setProductsLoading(true);
    setProductsError("");
    getAdminProducts(state.accessToken, controller.signal)
      .then(setProducts)
      .catch((requestError) => {
        if (
          requestError instanceof DOMException &&
          requestError.name === "AbortError"
        )
          return;
        setProducts([]);
        setProductsError(
          requestError instanceof ApiError
            ? requestError.message
            : "상품을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.",
        );
      })
      .finally(() => {
        if (!controller.signal.aborted) setProductsLoading(false);
      });
    return () => controller.abort();
  }, [hydrated, state.accessToken]);
  const advOrder = async (o: OrderData) => {
    if (!state.accessToken) return;
    try {
      let updated: OrderData;
      if (o.deliveryStatus === "PREPARING")
        updated = await shipOrderForAdmin(o.id, state.accessToken);
      else if (o.deliveryStatus === "SHIPPING")
        updated = await deliverOrderForAdmin(o.id, state.accessToken);
      else return;
      setOrders((prev) => prev.map((p) => (p.id === o.id ? updated : p)));
      showToast("배송 상태를 업데이트했어요. 고객에게 알림이 발송돼요");
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError
          ? requestError.message
          : "상태 변경에 실패했어요. 잠시 후 다시 시도해 주세요.",
        "err",
      );
    }
  };
  const [cancelOrderTargetId, setCancelOrderTargetId] = useState<number | null>(
    null,
  );
  const [cancelReasonOption, setCancelReasonOption] = useState(
    CANCEL_REASON_OPTIONS[0],
  );
  const [cancelReasonCustom, setCancelReasonCustom] = useState("");
  const [cancelSubmitting, setCancelSubmitting] = useState(false);

  const openCancelOrder = (id: number) => {
    setCancelOrderTargetId(id);
    setCancelReasonOption(CANCEL_REASON_OPTIONS[0]);
    setCancelReasonCustom("");
  };
  const closeCancelOrder = () => {
    if (cancelSubmitting) return;
    setCancelOrderTargetId(null);
  };
  const submitCancelOrder = async () => {
    if (!state.accessToken || cancelOrderTargetId == null) return;
    const reason =
      cancelReasonOption === "기타"
        ? cancelReasonCustom.trim()
        : cancelReasonOption;
    setCancelSubmitting(true);
    try {
      const updated = await cancelOrderForAdmin(
        cancelOrderTargetId,
        reason || undefined,
        state.accessToken,
      );
      setOrders((prev) =>
        prev.map((o) => (o.id === cancelOrderTargetId ? updated : o)),
      );
      showToast(
        "주문을 취소하고 재고·포인트를 복원했어요. 취소 사유가 고객에게 알림으로 전달돼요.",
      );
      setCancelOrderTargetId(null);
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError
          ? requestError.message
          : "취소에 실패했어요. 잠시 후 다시 시도해 주세요.",
        "err",
      );
    } finally {
      setCancelSubmitting(false);
    }
  };
  const advEx = async (x: ExchangeOrderData) => {
    if (!state.accessToken) return;
    try {
      let updated: ExchangeOrderData;
      if (x.status === "PREPARING")
        updated = await shipExchange(x.id, state.accessToken);
      else if (x.status === "SHIPPING")
        updated = await deliverExchange(x.id, state.accessToken);
      else return;
      setExchanges((prev) => prev.map((e) => (e.id === x.id ? updated : e)));
      showToast("교환 상태를 업데이트했어요");
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
      showToast("교환을 취소하고 쿠폰을 복원했어요.");
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError
          ? requestError.message
          : "취소에 실패했어요. 잠시 후 다시 시도해 주세요.",
        "err",
      );
    }
  };
  const resetProductForm = () => {
    setEditingProductId(null);
    setProductForm({
      name: "",
      category: "KIT",
      pointPrice: "",
      stock: "0",
      speciesName: "",
      description: "",
    });
    setProductSpeciesSuggestions([]);
    setProductImageFile(null);
  };

  const replaceProduct = (next: AdminProduct) => {
    setProducts((current) =>
      current.some((product) => product.id === next.id)
        ? current.map((product) => (product.id === next.id ? next : product))
        : [next, ...current],
    );
  };

  const productInput = (): AdminProductInput | null => {
    const pointPrice = Number(productForm.pointPrice);
    const stock = Number(productForm.stock);
    if (!productForm.name.trim()) {
      showToast("상품명을 입력해 주세요.", "err");
      return null;
    }
    if (!Number.isInteger(pointPrice) || pointPrice < 0) {
      showToast("가격은 0 이상의 정수로 입력해 주세요.", "err");
      return null;
    }
    if (productForm.category === "GACHA_PACK" && pointPrice < 1) {
      showToast("가챠 팩 가격은 1P 이상이어야 해요.", "err");
      return null;
    }
    if (!Number.isInteger(stock) || stock < 0) {
      showToast("재고는 0 이상의 정수로 입력해 주세요.", "err");
      return null;
    }
    const existingImageKey = editingProductId
      ? (products.find((product) => product.id === editingProductId)
          ?.imageKey ?? null)
      : null;
    const speciesName = productForm.speciesName.trim();
    return {
      name: productForm.name.trim(),
      category: productForm.category,
      pointPrice,
      stock: productForm.category === "GACHA_PACK" ? 0 : stock,
      speciesName:
        productForm.category === "SEEDLING" && speciesName ? speciesName : null,
      description: productForm.description.trim() || null,
      imageUrl: existingImageKey,
    };
  };

  const saveProduct = async () => {
    if (!state.accessToken || productSubmitting) return;
    const input = productInput();
    if (!input) return;
    setProductSubmitting(true);
    try {
      let saved = editingProductId
        ? await updateAdminProduct(editingProductId, input, state.accessToken)
        : await createAdminProduct(input, state.accessToken);
      if (productImageFile) {
        saved = await uploadAdminProductImage(
          saved.id,
          productImageFile,
          state.accessToken,
        );
      }
      replaceProduct(saved);
      showToast(
        editingProductId ? "상품 정보를 수정했어요." : "새 상품을 등록했어요.",
      );
      resetProductForm();
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError
          ? requestError.message
          : "상품을 저장하지 못했어요.",
        "err",
      );
    } finally {
      setProductSubmitting(false);
    }
  };

  const editProduct = (product: AdminProduct) => {
    setEditingProductId(product.id);
    setProductForm({
      name: product.name,
      category: product.category,
      pointPrice: String(product.pointPrice),
      stock: String(product.stock),
      speciesName: product.speciesName ?? "",
      description: product.description ?? "",
    });
    setProductImageFile(null);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  const toggleProduct = async (product: AdminProduct) => {
    if (!state.accessToken || productBusyId !== null) return;
    setProductBusyId(product.id);
    try {
      const next = await changeAdminProductStatus(
        product.id,
        product.status === "ACTIVE" ? "HIDDEN" : "ACTIVE",
        state.accessToken,
      );
      replaceProduct(next);
      showToast(
        next.status === "ACTIVE" ? "상품을 노출했어요." : "상품을 숨겼어요.",
      );
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError
          ? requestError.message
          : "노출 상태를 변경하지 못했어요.",
        "err",
      );
    } finally {
      setProductBusyId(null);
    }
  };

  const adjustProductStock = async (product: AdminProduct, delta: number) => {
    if (!state.accessToken || productBusyId !== null || product.unlimitedStock)
      return;
    setProductBusyId(product.id);
    try {
      const next = await adjustAdminProductStock(
        product.id,
        delta,
        state.accessToken,
      );
      replaceProduct(next);
      if (editingProductId === product.id) {
        setProductForm((current) => ({
          ...current,
          stock: String(next.stock),
        }));
      }
      showToast(`재고를 ${next.stock}개로 조정했어요.`);
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError
          ? requestError.message
          : "재고를 조정하지 못했어요.",
        "err",
      );
    } finally {
      setProductBusyId(null);
    }
  };

  const applyStockDelta = (product: AdminProduct) => {
    const delta = Number(stockDeltas[product.id]);
    if (!Number.isInteger(delta) || delta === 0) {
      showToast("재고 증감량을 0이 아닌 정수로 입력해 주세요.", "err");
      return;
    }
    void adjustProductStock(product, delta).then(() =>
      setStockDeltas((current) => ({ ...current, [product.id]: "" })),
    );
  };

  const confirmHideProduct = (product: AdminProduct) => {
    if (!state.accessToken || productBusyId !== null) return;
    askConfirm({
      icon: "visibility_off",
      title: "상품을 삭제할까요?",
      body: "실제 데이터는 삭제하지 않고 상점에서 숨깁니다. 언제든 다시 노출할 수 있어요.",
      ok: "숨김 처리",
      onOk: async () => {
        setProductBusyId(product.id);
        try {
          const next = await hideAdminProduct(product.id, state.accessToken!);
          replaceProduct(next);
          showToast("상품을 숨김 처리했어요.");
        } catch (requestError) {
          showToast(
            requestError instanceof ApiError
              ? requestError.message
              : "상품을 숨기지 못했어요.",
            "err",
          );
        } finally {
          setProductBusyId(null);
        }
      },
    });
  };
  const changeTab = (nextTab: string) => {
    setTab(nextTab);
    setAdminPage(0);
    router.replace(nextTab === "orders" ? "/admin" : `/admin?tab=${nextTab}`, {
      scroll: false,
    });
  };

  const changeAdminPage = (nextPage: number) => {
    setAdminPage(nextPage);
    const params = new URLSearchParams();
    if (tab !== "orders") params.set("tab", tab);
    params.set("page", String(nextPage + 1));
    router.replace(`/admin?${params}`, { scroll: false });
  };

  return (
    <div className="container">
      <div className="mb-1 flex items-center gap-2.5">
        <span className="material-symbols-outlined text-[26px] text-brand-dark">
          build
        </span>
        <h1 className="text-[26px] font-extrabold">관리자 콘솔</h1>
      </div>
      <p className="mb-[22px] text-sub">서비스 운영 현황을 한눈에.</p>

      <div className="mb-7 grid gap-3.5 [grid-template-columns:repeat(auto-fit,minmax(170px,1fr))]">
        {dashboardKpis.map((k) => (
          <button
            key={k.label}
            type="button"
            onClick={() => {
              if (k.tab === "reports") setReportsUserFilter(null);
              changeTab(k.tab);
            }}
            className="cursor-pointer rounded-2xl bg-white p-5 text-left shadow-card transition-shadow hover:shadow-[0_4px_16px_rgba(0,0,0,.08)]"
          >
            <div className="text-[13px] font-bold text-sub">{k.label}</div>
            <div className="mt-1.5 text-[26px] font-extrabold">{k.value}</div>
            <div className={`mt-1 text-xs font-bold ${k.dc}`}>{k.delta}</div>
          </button>
        ))}
      </div>

      <div className="mb-[22px] flex w-fit flex-wrap gap-1.5 rounded-[14px] bg-[#F0F2E8] p-[5px]">
        {ADMIN_TABS.map(([k, label]) => (
          <button
            key={k}
            type="button"
            onClick={() => changeTab(k)}
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
        <div ref={ordersSectionRef} className={PANEL}>
          <div className="flex flex-wrap items-center gap-2 border-b border-[#f2f3ec] px-[18px] py-3">
            <label className="text-xs font-bold text-sub">
              상태
              <select
                value={orderStatusFilter}
                onChange={(event) => {
                  setOrderStatusFilter(event.target.value);
                  changeAdminPage(0);
                }}
                className="ml-2 cursor-pointer rounded-lg border-[1.5px] border-line bg-white px-2.5 py-1.5 text-xs font-bold text-ink outline-none focus:border-brand"
              >
                {ORDER_STATUS_OPTIONS.map((option) => (
                  <option key={option.key} value={option.key}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <div className={`grid grid-cols-[.9fr_1fr_1.1fr_.9fr_1fr_1.4fr] ${HEAD}`}>
            <div>주문번호</div>
            <div>고객</div>
            <div>주문일시</div>
            <div>금액</div>
            <div>배송상태</div>
            <div className="text-right">처리</div>
          </div>
          {ordersLoading && orders.length === 0 ? (
            <div className="px-[18px] py-10 text-center text-sm text-sub">
              주문 내역을 불러오고 있어요.
            </div>
          ) : ordersError ? (
            <div className="px-[18px] py-10 text-center text-sm text-sub">
              {ordersError}
            </div>
          ) : orders.length === 0 ? (
            <div className="px-[18px] py-10 text-center text-sm text-sub">
              주문이 없어요.
            </div>
          ) : (
            displayOrders.map((o) => {
              const m = delMeta[o.deliveryStatus];
              const cancelled = o.status === "CANCELLED";
              const advanceable =
                o.status === "PAID" &&
                (o.deliveryStatus === "PREPARING" ||
                  o.deliveryStatus === "SHIPPING");
              return (
                <div key={o.id} className="border-t border-[#f2f3ec]">
                  <div
                    className={`grid grid-cols-[.9fr_1fr_1.1fr_.9fr_1fr_1.4fr] ${ROW} border-t-0 pb-2`}
                  >
                    <div className="font-bold">주문 #{o.id}</div>
                    <div className="text-[#6d7a68]">{o.receiverName}</div>
                    <div className="text-[12.5px] text-sub">{formatDateTime(o.orderedAt)}</div>
                    <div className="font-bold text-gold-text">
                      {fmt(o.totalPoint)}P
                    </div>
                    <div>
                      {cancelled ? (
                        <span className={`${CHIP} bg-[#f0f1ea] text-[#8a8a8a]`}>
                          취소됨
                        </span>
                      ) : (
                        <span className={`${CHIP} ${m[1]}`}>{m[0]}</span>
                      )}
                    </div>
                    <div className="flex justify-end gap-1.5">
                      {advanceable && (
                        <button
                          type="button"
                          onClick={() => advOrder(o)}
                          className={BTN_SOFT}
                        >
                          {m[2]}
                        </button>
                      )}
                      {o.status === "PAID" &&
                        o.deliveryStatus === "PREPARING" && (
                          <button
                            type="button"
                            onClick={() => openCancelOrder(o.id)}
                            className="cursor-pointer rounded-[9px] border-[1.5px] border-[#e8bdad] bg-white px-3 py-[7px] text-[13px] font-bold text-[#b5502f] transition-colors duration-150 hover:bg-danger-soft hover:border-[#e0a488]"
                          >
                            취소
                          </button>
                        )}
                    </div>
                  </div>
                  <div className="px-[18px] pb-1.5 text-[12.5px] text-sub">
                    {formatPhone(o.receiverPhone)} ·{" "}
                    {o.zipCode && `[${o.zipCode}] `}
                    {o.address} {o.addressDetail}
                    {cancelled && (o.cancelReason || o.cancelledBy) && (
                      <span className="ml-2 font-semibold text-[#b5502f]">
                        {o.cancelledBy === "ADMIN"
                          ? "관리자 취소"
                          : "본인 취소"}
                        {o.cancelReason && ` · 사유: ${o.cancelReason}`}
                      </span>
                    )}
                  </div>
                  <div className="px-[18px] pb-3.5 text-[12.5px] text-sub">
                    {!(o.id in orderItemsById)
                      ? "상품 정보를 불러오는 중…"
                      : orderItemsById[o.id].length === 0
                        ? "표시할 상품 정보가 없어요."
                        : orderItemsById[o.id]
                            .map(
                              (item) =>
                                `${item.productName} × ${item.quantity}`,
                            )
                            .join(", ")}
                  </div>
                </div>
              );
            })
          )}
          <div className="border-t border-line px-4 pt-4 pb-6">
            <AdminPagination
              page={adminPage}
              totalPages={ordersTotalPages}
              onChange={changeAdminPage}
            />
          </div>
        </div>
      )}

      {tab === "exchanges" && (
        <div ref={exchangesSectionRef} className={PANEL}>
          <div className="flex flex-wrap items-center gap-2 border-b border-[#f2f3ec] px-[18px] py-3">
            <label className="text-xs font-bold text-sub">
              상태
              <select
                value={exchangeStatusFilter}
                onChange={(event) => {
                  setExchangeStatusFilter(event.target.value);
                  changeAdminPage(0);
                }}
                className="ml-2 cursor-pointer rounded-lg border-[1.5px] border-line bg-white px-2.5 py-1.5 text-xs font-bold text-ink outline-none focus:border-brand"
              >
                {EXCHANGE_STATUS_OPTIONS.map((option) => (
                  <option key={option.key} value={option.key}>
                    {option.label}
                  </option>
                ))}
              </select>
            </label>
          </div>
          <div className={`grid grid-cols-[1.1fr_.9fr_1.1fr_.8fr_1.4fr] ${HEAD}`}>
            <div>실물상품</div>
            <div>쿠폰</div>
            <div>신청일시</div>
            <div>상태</div>
            <div className="text-right">처리</div>
          </div>
          {exchangesLoading && exchanges.length === 0 ? (
            <div className="px-[18px] py-10 text-center text-sm text-sub">
              교환 신청 내역을 불러오고 있어요.
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
            displayExchanges.map((x) => {
              const m = exMeta[x.status];
              const advanceable =
                x.status === "PREPARING" ||
                x.status === "SHIPPING";
              return (
                <div key={x.id} className="border-t border-[#f2f3ec]">
                  <div
                    className={`grid grid-cols-[1.1fr_.9fr_1.1fr_.8fr_1.4fr] ${ROW} border-t-0 pb-2`}
                  >
                    <div className="font-bold">{x.exchangeProductName}</div>
                    <div className="text-[#6d7a68]">
                      {couponName(x.cardName)} {x.usedCardCount}장
                    </div>
                    <div className="text-[12.5px] text-sub">{formatDateTime(x.requestedAt)}</div>
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
                      {x.status === "PREPARING" && (
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
                  <div className="px-[18px] pb-3.5 text-[12.5px] text-sub">
                    {x.receiverName} · {formatPhone(x.receiverPhone)} ·{" "}
                    {x.zipCode && `[${x.zipCode}] `}
                    {x.address} {x.addressDetail}
                  </div>
                </div>
              );
            })
          )}
          <div className="border-t border-line px-4 pt-4 pb-6">
            <AdminPagination
              page={adminPage}
              totalPages={exchangesTotalPages}
              onChange={changeAdminPage}
            />
          </div>
        </div>
      )}

      {tab === "products" && (
        <div className="flex flex-col gap-5">
          <div className={`${PANEL} p-5`}>
            <div className="mb-4 flex flex-wrap items-center justify-between gap-2">
              <div>
                <h2 className="font-extrabold">
                  {editingProductId ? "상품 수정" : "새 상품 추가"}
                </h2>
                <p className="mt-1 text-xs text-sub">
                  상점 상품과 무제한 재고 가챠 팩을 관리합니다.
                </p>
              </div>
              {editingProductId && (
                <button
                  type="button"
                  onClick={resetProductForm}
                  className={BTN_SOFT}
                >
                  신규 등록으로 전환
                </button>
              )}
            </div>

            <div className="grid gap-3 md:grid-cols-2 xl:grid-cols-4">
              <label className="text-xs font-bold text-sub">
                상품명
                <input
                  value={productForm.name}
                  onChange={(event) =>
                    setProductForm({
                      ...productForm,
                      name: event.target.value,
                    })
                  }
                  maxLength={100}
                  placeholder="상품명"
                  className="mt-1.5 w-full rounded-xl border-[1.5px] border-line px-3 py-2.5 text-sm text-ink outline-none focus:border-brand"
                />
              </label>

              <label className="text-xs font-bold text-sub">
                카테고리
                <select
                  value={productForm.category}
                  disabled={editingProductId !== null}
                  onChange={(event) =>
                    setProductForm({
                      ...productForm,
                      category: event.target.value as ProductCategory,
                      stock:
                        event.target.value === "GACHA_PACK"
                          ? "0"
                          : productForm.stock,
                      speciesName:
                        event.target.value === "SEEDLING"
                          ? productForm.speciesName
                          : "",
                    })
                  }
                  className="mt-1.5 w-full rounded-xl border-[1.5px] border-line bg-white px-3 py-2.5 text-sm text-ink outline-none disabled:bg-[#f3f4ef]"
                >
                  <option value="KIT">재배 키트</option>
                  <option value="SEEDLING">모종</option>
                  <option value="GACHA_PACK">가챠 팩</option>
                </select>
              </label>

              <label className="text-xs font-bold text-sub">
                가격(P)
                <input
                  type="number"
                  min={productForm.category === "GACHA_PACK" ? 1 : 0}
                  step={1}
                  value={productForm.pointPrice}
                  onChange={(event) =>
                    setProductForm({
                      ...productForm,
                      pointPrice: event.target.value,
                    })
                  }
                  className="mt-1.5 w-full rounded-xl border-[1.5px] border-line px-3 py-2.5 text-sm text-ink outline-none focus:border-brand"
                />
              </label>

              <label className="text-xs font-bold text-sub">
                {productForm.category === "GACHA_PACK"
                  ? "재고"
                  : editingProductId
                    ? "현재 재고"
                    : "초기 재고"}
                <input
                  type={
                    productForm.category === "GACHA_PACK" ? "text" : "number"
                  }
                  min={0}
                  step={1}
                  value={
                    productForm.category === "GACHA_PACK"
                      ? "무제한"
                      : productForm.stock
                  }
                  disabled={
                    productForm.category === "GACHA_PACK" ||
                    editingProductId !== null
                  }
                  onChange={(event) =>
                    setProductForm({
                      ...productForm,
                      stock: event.target.value,
                    })
                  }
                  className="mt-1.5 w-full rounded-xl border-[1.5px] border-line px-3 py-2.5 text-sm text-ink outline-none disabled:bg-[#f3f4ef]"
                />
              </label>
            </div>

            {productForm.category === "SEEDLING" && (
              <label className="mt-3 block text-xs font-bold text-sub">
                식물 종 이름
                <input
                  value={productForm.speciesName}
                  onChange={(event) =>
                    setProductForm({
                      ...productForm,
                      speciesName: event.target.value,
                    })
                  }
                  placeholder="예: 방울토마토 (한글/영문/공백만)"
                  className="mt-1.5 w-full rounded-xl border-[1.5px] border-line bg-white px-3 py-2.5 text-sm text-ink outline-none md:max-w-sm"
                />
                {productSpeciesSuggestions.length > 0 && (
                  <div className="mt-1.5 flex flex-wrap gap-1.5">
                    {productSpeciesSuggestions.map((name) => (
                      <button
                        key={name}
                        type="button"
                        onClick={() =>
                          setProductForm({ ...productForm, speciesName: name })
                        }
                        className="cursor-pointer rounded-lg border border-line bg-white px-2.5 py-1 text-xs font-semibold text-sub hover:border-brand hover:text-ink"
                      >
                        {name}
                      </button>
                    ))}
                  </div>
                )}
              </label>
            )}

            <div className="mt-3 grid gap-3 md:grid-cols-2">
              <AdminAssetKeyField
                file={productImageFile}
                onFileChange={setProductImageFile}
                previewUrl={
                  editingProductId
                    ? products.find(
                        (product) => product.id === editingProductId,
                      )?.imageUrl
                    : null
                }
                disabled={productSubmitting}
              />
              <label className="text-xs font-bold text-sub">
                설명
                <textarea
                  value={productForm.description}
                  onChange={(event) =>
                    setProductForm({
                      ...productForm,
                      description: event.target.value,
                    })
                  }
                  maxLength={2000}
                  rows={2}
                  className="mt-1.5 w-full resize-none rounded-xl border-[1.5px] border-line px-3 py-2.5 text-sm text-ink outline-none focus:border-brand"
                />
              </label>
            </div>

            <button
              type="button"
              disabled={productSubmitting}
              onClick={() => void saveProduct()}
              className="mt-4 cursor-pointer rounded-xl bg-brand px-5 py-2.5 text-sm font-extrabold text-white disabled:opacity-50"
            >
              {productSubmitting
                ? "저장 중..."
                : editingProductId
                  ? "수정 저장"
                  : "+ 상품 등록"}
            </button>
          </div>

          <div ref={productsSectionRef} className={`${PANEL} overflow-x-auto`}>
            <div className="min-w-[920px]">
              <div
                className={`grid grid-cols-[1.7fr_.8fr_.8fr_2.3fr_.7fr_1.4fr] ${HEAD}`}
              >
                <div>상품</div>
                <div>카테고리</div>
                <div>가격</div>
                <div>재고 관리</div>
                <div>노출</div>
                <div className="text-right">관리</div>
              </div>
              {productsLoading && products.length === 0 ? (
                <div className="px-5 py-12 text-center text-sm text-sub">
                  상품 목록을 불러오고 있어요.
                </div>
              ) : productsError ? (
                <div className="px-5 py-12 text-center text-sm text-danger">
                  {productsError}
                </div>
              ) : products.length === 0 ? (
                <div className="px-5 py-12 text-center text-sm text-sub">
                  등록된 상점 상품이 없어요.
                </div>
              ) : (
                products
                  .slice(
                    adminPage * ADMIN_PAGE_SIZE,
                    (adminPage + 1) * ADMIN_PAGE_SIZE,
                  )
                  .map((product) => {
                    const busy = productBusyId === product.id;
                    return (
                      <div
                        key={product.id}
                        className={`grid grid-cols-[1.7fr_.8fr_.8fr_2.3fr_.7fr_1.4fr] ${ROW}`}
                      >
                        <div className="flex min-w-0 items-center gap-2.5">
                          {product.imageUrl ? (
                            <div
                              className="h-10 w-10 flex-none rounded-lg bg-brand-soft bg-cover bg-center"
                              style={{
                                backgroundImage: `url("${product.imageUrl}")`,
                              }}
                            />
                          ) : (
                            <span className="material-symbols-outlined grid h-10 w-10 flex-none place-items-center rounded-lg bg-brand-soft text-xl">
                              {product.category === "GACHA_PACK" ? "style" : "potted_plant"}
                            </span>
                          )}
                          <div className="min-w-0">
                            <div className="truncate font-bold">
                              {product.name}
                            </div>
                            <div className="truncate text-xs text-sub">
                              {product.description || "설명 없음"}
                            </div>
                          </div>
                        </div>
                        <div className="text-xs font-bold text-sub">
                          {PRODUCT_CATEGORY_LABEL[product.category]}
                        </div>
                        <div className="font-bold text-gold-text">
                          {fmt(product.pointPrice)}P
                        </div>
                        <div>
                          {product.unlimitedStock ? (
                            <span className="font-extrabold text-brand-text">
                              무제한 재고 · 1회 1팩 구매
                            </span>
                          ) : (
                            <div className="flex items-center gap-1.5">
                              <button
                                type="button"
                                disabled={busy || product.stock < 1}
                                onClick={() =>
                                  void adjustProductStock(product, -1)
                                }
                                className="h-7 w-7 rounded-lg bg-[#f0f1ea] font-black disabled:opacity-35"
                              >
                                −
                              </button>
                              <span
                                className={`min-w-12 text-center font-extrabold ${product.soldOut ? "text-danger" : "text-brand-text"}`}
                              >
                                {product.soldOut
                                  ? "품절"
                                  : `${product.stock}개`}
                              </span>
                              <button
                                type="button"
                                disabled={busy}
                                onClick={() =>
                                  void adjustProductStock(product, 1)
                                }
                                className="h-7 w-7 rounded-lg bg-brand-soft font-black text-brand-dark disabled:opacity-35"
                              >
                                +
                              </button>
                              <input
                                type="number"
                                step={1}
                                value={stockDeltas[product.id] ?? ""}
                                onChange={(event) =>
                                  setStockDeltas((current) => ({
                                    ...current,
                                    [product.id]: event.target.value,
                                  }))
                                }
                                aria-label={`${product.name} 재고 증감량`}
                                placeholder="±수량"
                                className="ml-1 w-16 rounded-lg border border-line px-2 py-1 text-xs outline-none"
                              />
                              <button
                                type="button"
                                disabled={busy}
                                onClick={() => applyStockDelta(product)}
                                className="rounded-lg bg-[#f0f1ea] px-2 py-1 text-[11px] font-bold disabled:opacity-35"
                              >
                                적용
                              </button>
                              {product.stock > 0 && (
                                <button
                                  type="button"
                                  disabled={busy}
                                  onClick={() =>
                                    void adjustProductStock(
                                      product,
                                      -product.stock,
                                    )
                                  }
                                  className="ml-1 rounded-lg border border-danger/30 px-2 py-1 text-[11px] font-bold text-danger disabled:opacity-35"
                                >
                                  품절 처리
                                </button>
                              )}
                            </div>
                          )}
                        </div>
                        <div>
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() => void toggleProduct(product)}
                            aria-pressed={product.status === "ACTIVE"}
                            aria-label={`${product.name} ${product.status === "ACTIVE" ? "숨기기" : "노출하기"}`}
                            className={`relative inline-block h-[26px] w-[46px] cursor-pointer rounded-full transition-colors disabled:opacity-40 ${product.status === "ACTIVE" ? "bg-brand" : "bg-[#d7dccd]"}`}
                          >
                            <span
                              className={`absolute top-[3px] h-5 w-5 rounded-full bg-white transition-[left] ${product.status === "ACTIVE" ? "left-[23px]" : "left-[3px]"}`}
                            />
                          </button>
                        </div>
                        <div className="flex justify-end gap-1.5">
                          <button
                            type="button"
                            disabled={busy}
                            onClick={() => editProduct(product)}
                            className={BTN_SOFT}
                          >
                            수정
                          </button>
                          <button
                            type="button"
                            disabled={busy || product.status === "HIDDEN"}
                            onClick={() => confirmHideProduct(product)}
                            className="cursor-pointer rounded-[9px] bg-danger-soft px-3 py-[7px] text-[12px] font-bold text-danger disabled:cursor-not-allowed disabled:opacity-40"
                          >
                            삭제
                          </button>
                        </div>
                      </div>
                    );
                  })
              )}
              <div className="border-t border-line px-4 pt-4 pb-6">
                <AdminPagination
                  page={adminPage}
                  totalPages={Math.ceil(products.length / ADMIN_PAGE_SIZE)}
                  onChange={(next) => {
                    changeAdminPage(next);
                    if (typeof productsSectionRef.current?.scrollIntoView === "function") {
                      productsSectionRef.current.scrollIntoView({ behavior: "smooth", block: "start" });
                    }
                  }}
                />
              </div>
            </div>
          </div>
        </div>
      )}

      {tab === "points" && state.accessToken && state.user && (
        <AdminPointAdjustmentPanel
          accessToken={state.accessToken}
          adminUserId={state.user.id}
        />
      )}

      {tab === "coupons" && state.accessToken && (
        <AdminCouponPanel
          accessToken={state.accessToken}
          page={adminPage}
          onPageChange={changeAdminPage}
        />
      )}

      {tab === "gacha-operations" && state.accessToken && (
        <AdminGachaOperationsPanel accessToken={state.accessToken} />
      )}

      {tab === "inquiries" && state.accessToken && (
        <AdminInquiryPanel accessToken={state.accessToken} />
      )}

      {tab === "card-market-revenue" && state.accessToken && (
        <AdminCardMarketRevenuePanel accessToken={state.accessToken} />
      )}

      {cancelOrderTargetId != null && (
        <div
          onClick={closeCancelOrder}
          className="fixed inset-0 z-[60] flex items-start justify-center overflow-auto bg-[rgba(46,54,42,.4)] px-5 py-10"
        >
          <div
            onClick={(e) => e.stopPropagation()}
            className="w-full max-w-[420px] animate-pop rounded-[22px] bg-white p-[26px]"
          >
            <h3 className="mb-1.5 text-xl font-extrabold">주문 취소</h3>
            <p className="mb-5 text-[13px] text-sub">
              선택한 사유가 고객 알림에 그대로 표시돼요.
            </p>

            <label className="text-[13px] font-bold text-[#6d7a68]">
              취소 사유
            </label>
            <select
              value={cancelReasonOption}
              onChange={(e) => setCancelReasonOption(e.target.value)}
              className="mb-4 mt-1.5 w-full rounded-xl border-[1.5px] border-line px-[13px] py-3 outline-none"
            >
              {CANCEL_REASON_OPTIONS.map((reason) => (
                <option key={reason} value={reason}>
                  {reason}
                </option>
              ))}
            </select>

            {cancelReasonOption === "기타" && (
              <input
                value={cancelReasonCustom}
                onChange={(e) => setCancelReasonCustom(e.target.value)}
                maxLength={200}
                placeholder="사유를 입력해 주세요"
                className="mb-4 mt-1.5 w-full rounded-xl border-[1.5px] border-line px-[13px] py-3 outline-none"
              />
            )}

            <div className="flex gap-2">
              <button
                type="button"
                onClick={submitCancelOrder}
                disabled={
                  cancelSubmitting ||
                  (cancelReasonOption === "기타" && !cancelReasonCustom.trim())
                }
                className="flex-1 cursor-pointer rounded-[13px] bg-[#b5502f] p-3.5 text-base font-extrabold text-white disabled:cursor-not-allowed disabled:opacity-60"
              >
                {cancelSubmitting ? "취소 처리 중..." : "주문 취소"}
              </button>
              <button
                type="button"
                onClick={closeCancelOrder}
                disabled={cancelSubmitting}
                className="flex-1 cursor-pointer rounded-[13px] border-[1.5px] border-line bg-white p-3.5 text-base font-bold text-[#6d7a68] disabled:opacity-60"
              >
                닫기
              </button>
            </div>
          </div>
        </div>
      )}

      {tab === "board" && state.accessToken && (
        <AdminBoardPanel accessToken={state.accessToken} />
      )}

      {tab === "users" && state.accessToken && (
        <AdminUserPanel
          accessToken={state.accessToken}
          onViewReports={(userId, label) => {
            setReportsUserFilter({ id: userId, label });
            changeTab("reports");
          }}
        />
      )}

      {tab === "reports" && state.accessToken && (
        <AdminReportPanel
          accessToken={state.accessToken}
          targetUser={reportsUserFilter}
          onClearTargetUser={() => setReportsUserFilter(null)}
        />
      )}
    </div>
  );
}
