// 카카오(다음) 우편번호 서비스 — 무료 공개 스크립트, API 키가 필요 없다.
// https://postcode.map.daum.net
const SCRIPT_SRC = "https://t1.daumcdn.net/mapjsapi/bundle/postcode/prod/postcode.v2.js";

export interface DaumPostcodeResult {
  zonecode: string;
  address: string;
  roadAddress: string;
  jibunAddress: string;
  addressType: "R" | "J";
  bname: string;
  buildingName: string;
}

declare global {
  interface Window {
    daum?: {
      Postcode: new (options: {
        oncomplete: (data: DaumPostcodeResult) => void;
        width?: string | number;
        height?: string | number;
      }) => { embed: (container: HTMLElement) => void };
    };
  }
}

let loadPromise: Promise<void> | null = null;

function loadScript(): Promise<void> {
  if (typeof window === "undefined") return Promise.reject(new Error("no window"));
  if (window.daum?.Postcode) return Promise.resolve();
  if (loadPromise) return loadPromise;

  loadPromise = new Promise((resolve, reject) => {
    const script = document.createElement("script");
    script.src = SCRIPT_SRC;
    script.async = true;
    script.onload = () => resolve();
    script.onerror = () => {
      loadPromise = null;
      reject(new Error("주소 검색 스크립트를 불러오지 못했어요."));
    };
    document.head.appendChild(script);
  });
  return loadPromise;
}

// 팝업(.open()) 대신 지정한 컨테이너 안에 검색창을 그대로 그려 넣는 임베드 방식을 쓴다 —
// .open()은 window.open()을 호출하는데, 스크립트 로드를 기다리는 동안 사용자 클릭 시점의
// 제스처 컨텍스트가 끊겨 브라우저 팝업 차단에 걸리기 때문.
export async function embedAddressSearch(
  container: HTMLElement,
  onComplete: (result: { zipCode: string; address: string }) => void,
): Promise<void> {
  await loadScript();
  if (!window.daum?.Postcode) {
    throw new Error("주소 검색 스크립트를 불러오지 못했어요.");
  }
  new window.daum.Postcode({
    oncomplete: (data) => {
      // 도로명 주소(있으면)를 우선 쓰고, 없으면 지번 주소로 대체한다.
      const address = data.roadAddress || data.jibunAddress || data.address;
      onComplete({ zipCode: data.zonecode, address });
    },
    width: "100%",
    height: "100%",
  }).embed(container);
}
