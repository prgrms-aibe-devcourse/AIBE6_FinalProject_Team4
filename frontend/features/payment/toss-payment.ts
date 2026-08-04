const TOSS_PAYMENTS_SDK_URL = "https://js.tosspayments.com/v2/standard";
export const TOSS_PENDING_ORDER_STORAGE_KEY = "kwb_toss_pending_order_id";

interface TossPaymentRequest {
  method: "CARD";
  amount: {
    currency: "KRW";
    value: number;
  };
  orderId: string;
  orderName: string;
  successUrl: string;
  failUrl: string;
  customerEmail?: string;
  customerName?: string;
}

interface TossPayment {
  requestPayment(request: TossPaymentRequest): Promise<void>;
}

interface TossPaymentsInstance {
  payment(options: { customerKey: string }): TossPayment;
}

interface TossPaymentsFactory {
  (clientKey: string): TossPaymentsInstance;
  ANONYMOUS: string;
}

declare global {
  interface Window {
    TossPayments?: TossPaymentsFactory;
  }
}

export interface RequestTossPaymentInput {
  clientKey: string;
  orderId: string;
  orderName: string;
  amount: number;
  customerEmail?: string;
  customerName?: string;
}

const FALLBACK_PAYMENT_ERROR_CODE = "PAYMENT_WINDOW_ERROR";

export function getTossPaymentErrorCode(error: unknown): string {
  if (
    typeof error === "object" &&
    error !== null &&
    "code" in error &&
    typeof error.code === "string" &&
    error.code.trim().length > 0 &&
    error.code.length <= 100
  ) {
    return error.code;
  }
  return FALLBACK_PAYMENT_ERROR_CODE;
}

let sdkPromise: Promise<TossPaymentsFactory> | null = null;

function loadTossPaymentsSdk(): Promise<TossPaymentsFactory> {
  if (typeof window === "undefined") {
    return Promise.reject(new Error("결제창은 브라우저에서만 열 수 있어요."));
  }
  if (window.TossPayments) return Promise.resolve(window.TossPayments);
  if (sdkPromise) return sdkPromise;

  sdkPromise = new Promise((resolve, reject) => {
    const existingScript = document.querySelector<HTMLScriptElement>(
      `script[src="${TOSS_PAYMENTS_SDK_URL}"]`,
    );
    const script = existingScript ?? document.createElement("script");

    const handleLoad = () => {
      if (window.TossPayments) {
        resolve(window.TossPayments);
        return;
      }
      sdkPromise = null;
      reject(new Error("Toss Payments 결제창을 불러오지 못했어요."));
    };
    const handleError = () => {
      sdkPromise = null;
      reject(new Error("Toss Payments 결제창을 불러오지 못했어요."));
    };

    script.addEventListener("load", handleLoad, { once: true });
    script.addEventListener("error", handleError, { once: true });
    if (!existingScript) {
      script.src = TOSS_PAYMENTS_SDK_URL;
      script.async = true;
      document.head.appendChild(script);
    }
  });

  return sdkPromise;
}

export async function requestTossPayment(
  input: RequestTossPaymentInput,
): Promise<void> {
  if (!input.clientKey.startsWith("test_")) {
    throw new Error("Toss Payments 테스트 클라이언트 키를 설정해 주세요.");
  }

  const TossPayments = await loadTossPaymentsSdk();
  const payment = TossPayments(input.clientKey).payment({
    customerKey: TossPayments.ANONYMOUS,
  });
  const origin = window.location.origin;

  sessionStorage.setItem(TOSS_PENDING_ORDER_STORAGE_KEY, input.orderId);
  await payment.requestPayment({
    method: "CARD",
    amount: { currency: "KRW", value: input.amount },
    orderId: input.orderId,
    orderName: input.orderName,
    successUrl: `${origin}/my/points/charge/success`,
    failUrl: `${origin}/my/points/charge/fail`,
    customerEmail: input.customerEmail,
    customerName: input.customerName,
  });
}
