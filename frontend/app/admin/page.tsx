"use client";
import { ApiError } from "@/lib/api";
import AdminAssetKeyField from "@/components/admin/AdminAssetKeyField";
import AdminCouponPanel from "@/components/admin/AdminCouponPanel";
import AdminGachaOperationsPanel from "@/components/admin/AdminGachaOperationsPanel";
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
} from "@/lib/admin-product-api";
import {
  adminCancelExchange,
  deliverExchange,
  ExchangeOrderData,
  getExchangesForAdmin,
  prepareExchange,
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
import {
  createSpecies,
  getSpecies,
  PlantSpeciesData,
  updateSpecies,
} from "@/lib/species-api";
import { fmt, useStore } from "@/lib/store";
import { couponName } from "@/lib/coupon-label";
import { ProductCategory } from "@/lib/product-api";
import { useUI } from "@/lib/ui";
import { validateCommerceAssetKey } from "@/lib/commerce-asset";
import { useEffect, useState } from "react";

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
const PRODUCT_CATEGORY_LABEL: Record<ProductCategory, string> = {
  KIT: "재배 키트",
  SEEDLING: "모종",
  GACHA_PACK: "가챠 팩",
};

export default function Admin() {
  const { showToast, askConfirm } = useUI();
  const [tab, setTab] = useState("orders");
  const { state, hydrated } = useStore();
  const [orders, setOrders] = useState<OrderData[]>([]);
  const [orderItemsById, setOrderItemsById] = useState<
    Record<number, OrderItemData[]>
  >({});
  const [ordersLoading, setOrdersLoading] = useState(true);
  const [ordersError, setOrdersError] = useState("");

  useEffect(() => {
    if (!hydrated || !state.accessToken) return;
    const accessToken = state.accessToken;

    const controller = new AbortController();
    setOrdersLoading(true);
    setOrdersError("");

    getOrdersForAdmin(accessToken, undefined, 0, 50, controller.signal)
      .then((page) => {
        setOrders(page.content.map((detail) => detail.order));
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
  }, [hydrated, state.accessToken]);
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
  const [speciesForm, setSpeciesForm] = useState({
    name: "",
    category: "",
    careGuide: "",
  });
  const [speciesSubmitting, setSpeciesSubmitting] = useState(false);
  const [editingSpecies, setEditingSpecies] = useState<PlantSpeciesData | null>(
    null,
  );
  const [editForm, setEditForm] = useState({
    name: "",
    category: "",
    careGuide: "",
  });
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
        if (
          requestError instanceof DOMException &&
          requestError.name === "AbortError"
        )
          return;
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

  const [products, setProducts] = useState<AdminProduct[]>([]);
  const [productsLoading, setProductsLoading] = useState(true);
  const [productsError, setProductsError] = useState("");
  const [productSubmitting, setProductSubmitting] = useState(false);
  const [productBusyId, setProductBusyId] = useState<number | null>(null);
  const [stockDeltas, setStockDeltas] = useState<Record<number, string>>({});
  const [editingProductId, setEditingProductId] = useState<number | null>(null);
  const [productForm, setProductForm] = useState({
    name: "",
    category: "KIT" as ProductCategory,
    pointPrice: "",
    stock: "0",
    plantId: "",
    description: "",
    imageUrl: "",
  });

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
      showToast("배송 상태를 업데이트했어요. 고객에게 알림이 발송돼요 📦");
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
  const addSpecies = async () => {
    if (!state.accessToken) return;
    if (!speciesForm.name.trim())
      return showToast("종 이름을 입력해 주세요.", "err");

    setSpeciesSubmitting(true);
    try {
      const created = await createSpecies(
        {
          name: speciesForm.name.trim(),
          ...(speciesForm.category.trim()
            ? { category: speciesForm.category.trim() }
            : {}),
          ...(speciesForm.careGuide.trim()
            ? { careGuide: speciesForm.careGuide.trim() }
            : {}),
        },
        state.accessToken,
      );
      setSpeciesList([created, ...speciesList]);
      setSpeciesForm({ name: "", category: "", careGuide: "" });
      showToast(`'${created.name}' 종을 추가했어요 🌱`);
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError
          ? requestError.message
          : "종 추가에 실패했어요. 잠시 후 다시 시도해 주세요.",
        "err",
      );
    } finally {
      setSpeciesSubmitting(false);
    }
  };
  const openEditSpecies = (sp: PlantSpeciesData) => {
    setEditingSpecies(sp);
    setEditForm({
      name: sp.name,
      category: sp.category ?? "",
      careGuide: sp.careGuide ?? "",
    });
  };
  const closeEditSpecies = () => {
    if (editSubmitting) return;
    setEditingSpecies(null);
  };
  const saveEditSpecies = async () => {
    if (!state.accessToken || !editingSpecies) return;
    if (!editForm.name.trim())
      return showToast("종 이름을 입력해 주세요.", "err");

    setEditSubmitting(true);
    try {
      const updated = await updateSpecies(
        editingSpecies.id,
        {
          name: editForm.name.trim(),
          ...(editForm.category.trim()
            ? { category: editForm.category.trim() }
            : {}),
          ...(editForm.careGuide.trim()
            ? { careGuide: editForm.careGuide.trim() }
            : {}),
        },
        state.accessToken,
      );
      setSpeciesList(
        speciesList.map((sp) => (sp.id === updated.id ? updated : sp)),
      );
      showToast(`'${updated.name}' 정보를 수정했어요 🌱`);
      setEditingSpecies(null);
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError
          ? requestError.message
          : "종 수정에 실패했어요. 잠시 후 다시 시도해 주세요.",
        "err",
      );
    } finally {
      setEditSubmitting(false);
    }
  };
  const resetProductForm = () => {
    setEditingProductId(null);
    setProductForm({
      name: "",
      category: "KIT",
      pointPrice: "",
      stock: "0",
      plantId: "",
      description: "",
      imageUrl: "",
    });
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
    const imageError = validateCommerceAssetKey(
      productForm.imageUrl,
      "products",
      editingProductId,
    );
    if (imageError) {
      showToast(imageError, "err");
      return null;
    }
    const plantId = productForm.plantId ? Number(productForm.plantId) : null;
    return {
      name: productForm.name.trim(),
      category: productForm.category,
      pointPrice,
      stock: productForm.category === "GACHA_PACK" ? 0 : stock,
      plantId:
        productForm.category === "SEEDLING" && Number.isInteger(plantId)
          ? plantId
          : null,
      description: productForm.description.trim() || null,
      imageUrl: productForm.imageUrl.trim() || null,
    };
  };

  const saveProduct = async () => {
    if (!state.accessToken || productSubmitting) return;
    const input = productInput();
    if (!input) return;
    setProductSubmitting(true);
    try {
      const saved = editingProductId
        ? await updateAdminProduct(editingProductId, input, state.accessToken)
        : await createAdminProduct(input, state.accessToken);
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
      plantId: product.plantId ? String(product.plantId) : "",
      description: product.description ?? "",
      imageUrl: product.imageKey ?? "",
    });
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
    ["coupons", "쿠폰 관리"],
    ["gacha-operations", "가챠 장애 관리"],
    ["points", "포인트 관리"],
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
          <div className={`grid grid-cols-[1fr_1fr_1fr_1fr_1.4fr] ${HEAD}`}>
            <div>주문번호</div>
            <div>고객</div>
            <div>금액</div>
            <div>배송상태</div>
            <div className="text-right">처리</div>
          </div>
          {ordersLoading ? (
            <div className="px-[18px] py-10 text-center text-sm text-sub">
              주문을 불러오고 있어요 🌱
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
            orders.map((o) => {
              const m = delMeta[o.deliveryStatus];
              const cancelled = o.status === "CANCELLED";
              const advanceable =
                o.status === "PAID" &&
                (o.deliveryStatus === "PREPARING" ||
                  o.deliveryStatus === "SHIPPING");
              return (
                <div key={o.id} className="border-t border-[#f2f3ec]">
                  <div
                    className={`grid grid-cols-[1fr_1fr_1fr_1fr_1.4fr] ${ROW} border-t-0 pb-2`}
                  >
                    <div className="font-bold">주문 #{o.id}</div>
                    <div className="text-[#6d7a68]">{o.receiverName}</div>
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
                        {o.cancelledBy === "ADMIN" ? "관리자 취소" : "본인 취소"}
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
                              (item) => `${item.productName} × ${item.quantity}`,
                            )
                            .join(", ")}
                  </div>
                </div>
              );
            })
          )}
        </div>
      )}

      {tab === "exchanges" && (
        <div className={PANEL}>
          <div className={`grid grid-cols-[1.3fr_1fr_1fr_1.4fr] ${HEAD}`}>
            <div>실물상품</div>
            <div>쿠폰</div>
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
                    {couponName(x.cardName)} {x.usedCardCount}장
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
                      plantId:
                        event.target.value === "SEEDLING"
                          ? productForm.plantId
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
                연결 식물 종
                <select
                  value={productForm.plantId}
                  onChange={(event) =>
                    setProductForm({
                      ...productForm,
                      plantId: event.target.value,
                    })
                  }
                  className="mt-1.5 w-full rounded-xl border-[1.5px] border-line bg-white px-3 py-2.5 text-sm text-ink outline-none md:max-w-sm"
                >
                  <option value="">연결하지 않음</option>
                  {speciesList.map((species) => (
                    <option key={species.id} value={species.id}>
                      {species.name}
                    </option>
                  ))}
                </select>
              </label>
            )}

            <div className="mt-3 grid gap-3 md:grid-cols-2">
              <AdminAssetKeyField
                value={productForm.imageUrl}
                onChange={(imageUrl) =>
                  setProductForm({ ...productForm, imageUrl })
                }
                prefix="products"
                resourceId={editingProductId}
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

          <div className={`${PANEL} overflow-x-auto`}>
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
              {productsLoading ? (
                <div className="px-5 py-12 text-center text-sm text-sub">
                  상품을 불러오고 있어요.
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
                products.map((product) => {
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
                          <span className="grid h-10 w-10 flex-none place-items-center rounded-lg bg-brand-soft text-xl">
                            {product.category === "GACHA_PACK" ? "🎴" : "🌱"}
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
                              {product.soldOut ? "품절" : `${product.stock}개`}
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
            </div>
          </div>
        </div>
      )}

      {tab === "points" && (
        <AdminPointAdjustmentPanel accessToken={state.accessToken} />
      )}

      {tab === "coupons" && state.accessToken && (
        <AdminCouponPanel accessToken={state.accessToken} />
      )}

      {tab === "gacha-operations" && state.accessToken && (
        <AdminGachaOperationsPanel accessToken={state.accessToken} />
      )}

      {tab === "species" && (
        <div className="flex flex-col gap-5">
          <div className={`${PANEL} p-5`}>
            <div className="mb-3.5 text-sm font-extrabold">새 종 추가</div>
            <div className="grid gap-2.5 sm:grid-cols-3">
              <input
                value={speciesForm.name}
                onChange={(e) =>
                  setSpeciesForm({ ...speciesForm, name: e.target.value })
                }
                placeholder="이름 (예: 몬스테라)"
                maxLength={100}
                className="rounded-xl border-[1.5px] border-line px-[13px] py-2.5 text-sm outline-none"
              />
              <input
                value={speciesForm.category}
                onChange={(e) =>
                  setSpeciesForm({ ...speciesForm, category: e.target.value })
                }
                placeholder="카테고리 (선택)"
                maxLength={50}
                className="rounded-xl border-[1.5px] border-line px-[13px] py-2.5 text-sm outline-none"
              />
              <input
                value={speciesForm.careGuide}
                onChange={(e) =>
                  setSpeciesForm({ ...speciesForm, careGuide: e.target.value })
                }
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
              <div className="px-[18px] py-10 text-center text-sm text-sub">
                종 목록을 불러오고 있어요 🌱
              </div>
            ) : speciesError ? (
              <div className="px-[18px] py-10 text-center text-sm text-sub">
                {speciesError}
              </div>
            ) : speciesList.length === 0 ? (
              <div className="px-[18px] py-10 text-center text-sm text-sub">
                등록된 종이 없어요.
              </div>
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
                  <div className="truncate text-[#6d7a68]">
                    {sp.careGuide ?? "-"}
                  </div>
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
          <div
            onClick={(e) => e.stopPropagation()}
            className="w-full max-w-[460px] animate-pop rounded-[22px] bg-white p-[26px]"
          >
            <h3 className="mb-5 text-xl font-extrabold">종 정보 수정 🌿</h3>

            <label className="text-[13px] font-bold text-[#6d7a68]">이름</label>
            <input
              value={editForm.name}
              onChange={(e) =>
                setEditForm({ ...editForm, name: e.target.value })
              }
              maxLength={100}
              className="mb-4 mt-1.5 w-full rounded-xl border-[1.5px] border-line px-[13px] py-3 outline-none"
            />

            <label className="text-[13px] font-bold text-[#6d7a68]">
              카테고리
            </label>
            <input
              value={editForm.category}
              onChange={(e) =>
                setEditForm({ ...editForm, category: e.target.value })
              }
              maxLength={50}
              className="mb-4 mt-1.5 w-full rounded-xl border-[1.5px] border-line px-[13px] py-3 outline-none"
            />

            <label className="text-[13px] font-bold text-[#6d7a68]">
              관리 가이드
            </label>
            <textarea
              value={editForm.careGuide}
              onChange={(e) =>
                setEditForm({ ...editForm, careGuide: e.target.value })
              }
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
