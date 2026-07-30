// Social login provider config — authorize-URL building only. The actual code→token
// exchange happens on the backend (see lib/api.ts#oauthLogin); the frontend never sees
// a provider access token.
export type OAuthProvider = "google" | "kakao" | "naver";

const STATE_KEY_PREFIX = "kwb_oauth_state_";

function redirectUri(provider: OAuthProvider): string {
  if (typeof window === "undefined") return "";
  return `${window.location.origin}/oauth/callback/${provider}`;
}

// A random per-attempt value stored client-side (survives the redirect round trip
// since it's the same tab/sessionStorage) and echoed back by the provider in the
// callback query string. The callback page must reject the attempt unless the
// returned state matches what's stored here — otherwise an attacker can send a
// victim a callback URL carrying the attacker's own authorization code and log
// the victim's browser into the attacker's account (login CSRF). Every provider
// needs this check, not just Naver: Google/Kakao's own OAuth servers don't verify
// state for us, so skipping it there leaves the same hole open.
function generateAndStoreState(provider: OAuthProvider): string {
  const state = crypto.randomUUID();
  sessionStorage.setItem(STATE_KEY_PREFIX + provider, state);
  return state;
}

export function consumeOAuthState(provider: OAuthProvider): string | null {
  const key = STATE_KEY_PREFIX + provider;
  const state = sessionStorage.getItem(key);
  sessionStorage.removeItem(key);
  return state;
}

export function buildAuthorizeUrl(provider: OAuthProvider): string {
  const redirect = redirectUri(provider);
  const state = generateAndStoreState(provider);

  switch (provider) {
    case "google": {
      const clientId = process.env.NEXT_PUBLIC_GOOGLE_CLIENT_ID || "";
      const params = new URLSearchParams({
        client_id: clientId,
        redirect_uri: redirect,
        response_type: "code",
        scope: "openid email profile",
        state,
      });
      return `https://accounts.google.com/o/oauth2/v2/auth?${params.toString()}`;
    }
    case "kakao": {
      const clientId = process.env.NEXT_PUBLIC_KAKAO_CLIENT_ID || "";
      const params = new URLSearchParams({
        client_id: clientId,
        redirect_uri: redirect,
        response_type: "code",
        state,
      });
      return `https://kauth.kakao.com/oauth/authorize?${params.toString()}`;
    }
    case "naver": {
      const clientId = process.env.NEXT_PUBLIC_NAVER_CLIENT_ID || "";
      // 네이버는 이 state를 토큰 교환 요청에도 그대로 실어 보내야 한다(백엔드
      // NaverOAuthClient 참고) — CSRF 검증 자체는 아래 콜백 페이지에서 한다.
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

export function startOAuthLogin(provider: OAuthProvider) {
  window.location.href = buildAuthorizeUrl(provider);
}
