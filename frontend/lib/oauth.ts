// Social login provider config — authorize-URL building only. The actual code→token
// exchange happens on the backend (see lib/api.ts#oauthLogin); the frontend never sees
// a provider access token.
export type OAuthProvider = "google" | "kakao" | "naver";

const NAVER_STATE_KEY = "kwb_naver_oauth_state";

function redirectUri(provider: OAuthProvider): string {
  if (typeof window === "undefined") return "";
  return `${window.location.origin}/oauth/callback/${provider}`;
}

export function buildAuthorizeUrl(provider: OAuthProvider): string {
  const redirect = redirectUri(provider);

  switch (provider) {
    case "google": {
      const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID || "";
      const params = new URLSearchParams({
        client_id: clientId,
        redirect_uri: redirect,
        response_type: "code",
        scope: "openid email profile",
      });
      return `https://accounts.google.com/o/oauth2/v2/auth?${params.toString()}`;
    }
    case "kakao": {
      const clientId = process.env.NEXT_PUBLIC_KAKAO_CLIENT_ID || "";
      const params = new URLSearchParams({
        client_id: clientId,
        redirect_uri: redirect,
        response_type: "code",
      });
      return `https://kauth.kakao.com/oauth/authorize?${params.toString()}`;
    }
    case "naver": {
      const clientId = process.env.NEXT_PUBLIC_NAVER_CLIENT_ID || "";
      // 네이버는 state를 CSRF 방지용으로 요구한다 — 여기서 생성해 세션스토리지에 저장해두고,
      // 콜백에서 꺼내 백엔드로 그대로 전달한다(토큰 교환 시 이 값을 함께 보내야 하기 때문).
      const state = crypto.randomUUID();
      sessionStorage.setItem(NAVER_STATE_KEY, state);
      const params = new URLSearchParams({
        client_id: clientId,
        redirect_uri: redirect,
        response_type: "code",
        state,
      });
      return `https://nid.naver.com/oauth2.0/authorize?${params.toString()}`;
    }
  }
}

export function consumeNaverState(): string | null {
  const state = sessionStorage.getItem(NAVER_STATE_KEY);
  sessionStorage.removeItem(NAVER_STATE_KEY);
  return state;
}

export function startOAuthLogin(provider: OAuthProvider) {
  window.location.href = buildAuthorizeUrl(provider);
}
