"use client";
import { ApiError, oauthLogin } from "@/lib/api";
import { consumeOAuthState, OAuthProvider } from "@/lib/oauth";
import { useStore } from "@/lib/store";
import { useRouter, useSearchParams, useParams } from "next/navigation";
import { Suspense, useEffect, useRef, useState } from "react";

const PROVIDER_LABEL: Record<OAuthProvider, string> = {
  google: "Google",
  kakao: "카카오",
  naver: "네이버",
};

function OAuthCallbackContent() {
  const router = useRouter();
  const params = useParams();
  const searchParams = useSearchParams();
  const { login } = useStore();
  const [error, setError] = useState("");
  const ranOnce = useRef(false);

  const provider = params.provider as OAuthProvider;
  const label = PROVIDER_LABEL[provider] || "소셜";

  useEffect(() => {
    // 콜백 URL이 두 번 마운트되는 환경(Strict Mode 등)에서 인가 코드를 두 번 소비하지
    // 않도록 가드 — provider의 인가 코드는 1회용이라 재사용하면 실패한다.
    if (ranOnce.current) return;
    ranOnce.current = true;

    const code = searchParams.get("code");
    const providerError = searchParams.get("error");
    if (providerError) {
      setError(`${label} 로그인이 취소되었어요.`);
      return;
    }
    if (!code) {
      setError("인가 코드를 받지 못했어요.");
      return;
    }

    const returnedState = searchParams.get("state");
    const expectedState = consumeOAuthState(provider);
    // Login CSRF guard: reject unless the state this tab generated before redirecting
    // matches what the provider echoed back. A forged callback URL (attacker's own
    // authorization code, no matching sessionStorage entry in the victim's tab) fails
    // here and never reaches the backend token exchange.
    if (!expectedState || !returnedState || returnedState !== expectedState) {
      setError("로그인 요청이 유효하지 않아요. 다시 시도해 주세요.");
      return;
    }

    oauthLogin(provider, code, returnedState)
      .then((res) => {
        login(res.accessToken, res.user);
        router.replace("/");
      })
      .catch((e) => {
        setError(e instanceof ApiError ? e.message : `${label} 로그인에 실패했어요.`);
      });
  }, [provider, searchParams, label, login, router]);

  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 px-5 text-center font-sans">
      {error ? (
        <>
          <p className="text-base font-bold text-danger">{error}</p>
          <button
            type="button"
            onClick={() => router.replace("/auth")}
            className="cursor-pointer rounded-xl bg-brand px-5 py-3 text-sm font-bold text-white transition-colors duration-150 hover:bg-brand-dark"
          >
            로그인 화면으로 돌아가기
          </button>
        </>
      ) : (
        <p className="text-sm text-sub">{label} 로그인 처리 중이에요...</p>
      )}
    </div>
  );
}

export default function OAuthCallback() {
  return (
    <Suspense fallback={null}>
      <OAuthCallbackContent />
    </Suspense>
  );
}
