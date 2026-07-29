"use client";
import {
  ApiError,
  login as apiLogin,
  signup as apiSignup,
  checkNicknameAvailability,
  confirmEmailVerification,
  confirmPasswordResetVerification,
  requestEmailVerification,
  requestPasswordResetVerification,
  resetPassword as apiResetPassword,
} from "@/lib/api";
import { startOAuthLogin } from "@/lib/oauth";
import { useStore } from "@/lib/store";
import { useUI } from "@/lib/ui";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";

const NICKNAME_MAX_LENGTH = 12;
const NAME_MAX_LENGTH = 10;
const PHONE_MAX_LENGTH = 11;
const PHONE_REGEX = /^(010|011)\d{7,8}$/;

const FIELD =
  "w-full rounded-xl border-[1.5px] border-line px-3.5 py-[13px] text-[15px] outline-none";
const LABEL = "text-[13px] font-bold text-[#6d7a68]";
const CARD =
  "w-full rounded-[22px] bg-white px-[30px] py-[34px] shadow-[0_10px_40px_rgba(124,179,66,.12)] animate-upIn";

interface AuthPanelProps {
  initialView?: "login" | "signup";
}

export default function AuthPanel({ initialView = "login" }: AuthPanelProps) {
  const router = useRouter();
  const { showToast } = useUI();
  const { login } = useStore();
  const [view, setView] = useState<"login" | "signup" | "reset">(initialView);
  const [submitting, setSubmitting] = useState(false);

  const [loginEmail, setLoginEmail] = useState("");
  const [loginPassword, setLoginPassword] = useState("");
  const [loginError, setLoginError] = useState("");

  // 비밀번호 찾기 단계 상태 — 이메일을 바꾸면 인증은 다시 받아야 하므로 resetVerified를 리셋한다.
  const [resetEmail, setResetEmail] = useState("");
  const [resetVerificationSent, setResetVerificationSent] = useState(false);
  const [resetCode, setResetCode] = useState("");
  const [resetVerified, setResetVerified] = useState(false);
  const [resetToken, setResetToken] = useState("");
  const [resetVerifying, setResetVerifying] = useState(false);
  const [resetVerificationError, setResetVerificationError] = useState("");
  const [resetNewPassword, setResetNewPassword] = useState("");
  const [resetNewPasswordConfirm, setResetNewPasswordConfirm] = useState("");
  const [resetError, setResetError] = useState("");

  const [signupEmail, setSignupEmail] = useState("");
  const [signupPassword, setSignupPassword] = useState("");
  const [signupPasswordConfirm, setSignupPasswordConfirm] = useState("");
  const [signupNickname, setSignupNickname] = useState("");
  const [signupName, setSignupName] = useState("");
  const [signupPhone, setSignupPhone] = useState("");
  const [signupError, setSignupError] = useState("");

  // 이메일 인증 단계 상태 — 이메일을 바꾸면 인증은 다시 받아야 하므로 emailVerified를 리셋한다.
  const [verificationSent, setVerificationSent] = useState(false);
  const [verificationCode, setVerificationCode] = useState("");
  const [emailVerified, setEmailVerified] = useState(false);
  const [verifying, setVerifying] = useState(false);
  const [verificationError, setVerificationError] = useState("");

  // 닉네임 중복확인 상태 — 닉네임을 바꾸면 다시 확인해야 하므로 checked를 리셋한다.
  const [nicknameChecked, setNicknameChecked] = useState(false);
  const [nicknameAvailable, setNicknameAvailable] = useState(false);
  const [checkingNickname, setCheckingNickname] = useState(false);
  const [nicknameError, setNicknameError] = useState("");

  const changeSignupEmail = (value: string) => {
    setSignupEmail(value);
    setVerificationSent(false);
    setVerificationCode("");
    setEmailVerified(false);
    setVerificationError("");
  };

  const changeSignupPhone = (value: string) => {
    setSignupPhone(value.replace(/\D/g, "").slice(0, PHONE_MAX_LENGTH));
  };

  const changeSignupNickname = (value: string) => {
    setSignupNickname(value.slice(0, NICKNAME_MAX_LENGTH));
    setNicknameChecked(false);
    setNicknameAvailable(false);
    setNicknameError("");
  };

  const checkNickname = async () => {
    setNicknameError("");
    setCheckingNickname(true);
    try {
      const res = await checkNicknameAvailability(signupNickname);
      setNicknameChecked(true);
      setNicknameAvailable(res.available);
      if (!res.available) {
        setNicknameError("이미 사용 중인 닉네임이에요.");
      }
    } catch (e) {
      setNicknameChecked(false);
      setNicknameError(
        e instanceof ApiError ? e.message : "중복확인에 실패했어요.",
      );
    } finally {
      setCheckingNickname(false);
    }
  };

  const sendVerificationCode = async () => {
    setVerificationError("");
    setVerifying(true);
    try {
      await requestEmailVerification(signupEmail);
      setVerificationSent(true);
      showToast("인증코드를 보냈어요. 5분 이내에 입력해 주세요 📮");
    } catch (e) {
      setVerificationError(
        e instanceof ApiError ? e.message : "인증코드 발송에 실패했어요.",
      );
    } finally {
      setVerifying(false);
    }
  };

  const confirmVerificationCode = async () => {
    setVerificationError("");
    setVerifying(true);
    try {
      await confirmEmailVerification(signupEmail, verificationCode);
      setEmailVerified(true);
      showToast("이메일 인증을 완료했어요 ✅");
    } catch (e) {
      setVerificationError(
        e instanceof ApiError ? e.message : "인증코드 확인에 실패했어요.",
      );
    } finally {
      setVerifying(false);
    }
  };

  const changeResetEmail = (value: string) => {
    setResetEmail(value);
    setResetVerificationSent(false);
    setResetCode("");
    setResetVerified(false);
    setResetToken("");
    setResetVerificationError("");
  };

  const resetToLoginView = () => {
    setView("login");
    setResetEmail("");
    setResetVerificationSent(false);
    setResetCode("");
    setResetVerified(false);
    setResetToken("");
    setResetVerificationError("");
    setResetNewPassword("");
    setResetNewPasswordConfirm("");
    setResetError("");
  };

  const sendResetVerificationCode = async () => {
    setResetVerificationError("");
    setResetVerifying(true);
    try {
      await requestPasswordResetVerification(resetEmail);
      setResetVerificationSent(true);
      showToast("인증코드를 보냈어요. 5분 이내에 입력해 주세요 📮");
    } catch (e) {
      setResetVerificationError(
        e instanceof ApiError ? e.message : "인증코드 발송에 실패했어요.",
      );
    } finally {
      setResetVerifying(false);
    }
  };

  const confirmResetVerificationCode = async () => {
    setResetVerificationError("");
    setResetVerifying(true);
    try {
      const { resetToken: token } = await confirmPasswordResetVerification(resetEmail, resetCode);
      setResetToken(token);
      setResetVerified(true);
      showToast("이메일 인증을 완료했어요 ✅");
    } catch (e) {
      setResetVerificationError(
        e instanceof ApiError ? e.message : "인증코드 확인에 실패했어요.",
      );
    } finally {
      setResetVerifying(false);
    }
  };

  const handleResetPassword = async () => {
    setResetError("");
    if (!resetVerified || !resetToken) {
      setResetError("이메일 인증을 먼저 완료해 주세요.");
      return;
    }
    if (resetNewPassword !== resetNewPasswordConfirm) {
      setResetError("비밀번호가 서로 달라요.");
      return;
    }
    setSubmitting(true);
    try {
      await apiResetPassword(resetEmail, resetNewPassword, resetToken);
      showToast("비밀번호를 변경했어요. 새 비밀번호로 로그인해 주세요 🌿");
      resetToLoginView();
    } catch (e) {
      setResetError(
        e instanceof ApiError ? e.message : "비밀번호 재설정에 실패했어요.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  const afterAuth = (msg: string) => {
    showToast(msg);
    setTimeout(() => router.push("/"), 1000);
  };

  const onLoginSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    handleLogin();
  };

  const handleLogin = async (email = loginEmail, password = loginPassword) => {
    setLoginError("");
    setSubmitting(true);
    try {
      const res = await apiLogin(email, password);
      login(res.accessToken, res.user);
      afterAuth("환영해요! 홈으로 데려다드릴게요 🌿");
    } catch (e) {
      setLoginError(
        e instanceof ApiError
          ? e.message
          : "로그인에 실패했어요. 다시 시도해 주세요.",
      );
    } finally {
      setSubmitting(false);
    }
  };

  const quickLogin = (email: string, password: string) => {
    setLoginEmail(email);
    setLoginPassword(password);
    handleLogin(email, password);
  };

  const handleSignup = async () => {
    setSignupError("");
    if (!emailVerified) {
      setSignupError("이메일 인증을 먼저 완료해 주세요.");
      return;
    }
    if (!nicknameChecked || !nicknameAvailable) {
      setSignupError("닉네임 중복확인을 먼저 완료해 주세요.");
      return;
    }
    if (signupPassword !== signupPasswordConfirm) {
      setSignupError("비밀번호가 서로 달라요.");
      return;
    }
    if (signupPhone && !PHONE_REGEX.test(signupPhone)) {
      setSignupError("전화번호는 010 또는 011로 시작하는 숫자 10~11자리여야 해요.");
      return;
    }
    setSubmitting(true);
    try {
      await apiSignup({
        email: signupEmail,
        password: signupPassword,
        nickname: signupNickname,
        name: signupName,
        phoneNumber: signupPhone || undefined,
      });
      const res = await apiLogin(signupEmail, signupPassword);
      login(res.accessToken, res.user);
      afterAuth("환영해요! 이제 첫 식물을 맞이해 볼까요? 🌱");
    } catch (e) {
      setSignupError(
        e instanceof ApiError
          ? e.message
          : "회원가입에 실패했어요. 다시 시도해 주세요.",
      );
    } finally {
      setSubmitting(false);
    }
  };


  const content = (
    <>
      {view === "login" && (
        <div className="flex flex-1 items-start justify-center px-5 pb-[60px] pt-2.5">
          <div className={`${CARD} max-w-[400px]`}>
            <h2 className="mb-1 text-2xl font-extrabold">다시 오셨네요 🌿</h2>
            <p className="mb-6 text-sm text-sub">
              오늘도 푸릇한 하루 보내세요.
            </p>

            <form onSubmit={onLoginSubmit}>
              <label className={LABEL}>이메일</label>
              <input
                value={loginEmail}
                onChange={(e) => setLoginEmail(e.target.value)}
                placeholder="hello@example.com"
                className={`${FIELD} mb-4 mt-1.5`}
              />
              <label className={LABEL}>비밀번호</label>
              <input
                type="password"
                value={loginPassword}
                onChange={(e) => setLoginPassword(e.target.value)}
                placeholder="••••••••"
                className={`${FIELD} mb-2 mt-1.5`}
              />
              <div className="mb-4 text-right">
                <button
                  type="button"
                  onClick={() => setView("reset")}
                  className="cursor-pointer text-xs text-[#a9b3a0]"
                >
                  비밀번호 찾기
                </button>
              </div>

              {loginError && (
                <div className="mb-3.5 rounded-[11px] bg-danger-soft px-[13px] py-[11px] text-[13px] font-semibold text-danger">
                  {loginError}
                </div>
              )}

              <button
                type="submit"
                disabled={submitting}
                className="w-full cursor-pointer rounded-xl bg-brand p-3.5 text-base font-bold text-white transition-colors duration-150 hover:bg-brand-dark disabled:opacity-60"
              >
                로그인
              </button>
            </form>

            <div className="my-[22px] flex items-center gap-3 text-xs text-[#c2c9b8]">
              <div className="h-px flex-1 bg-[#eceee5]" />
              또는
              <div className="h-px flex-1 bg-[#eceee5]" />
            </div>

            <button
              type="button"
              onClick={() => startOAuthLogin("google")}
              className="mb-2.5 w-full cursor-pointer rounded-xl border-[1.5px] border-[#e3e5df] bg-white p-[13px] font-bold text-[#3c4043] transition-colors duration-150 hover:bg-[#f5f6f2]"
            >
              Google로 계속하기
            </button>
            <button
              type="button"
              onClick={() => startOAuthLogin("kakao")}
              className="mb-2.5 w-full cursor-pointer rounded-xl bg-[#FEE500] p-[13px] font-bold text-[#3c1e1e] transition-colors duration-150 hover:brightness-95"
            >
              카카오로 계속하기
            </button>
            <button
              type="button"
              onClick={() => startOAuthLogin("naver")}
              className="w-full cursor-pointer rounded-xl bg-[#03C75A] p-[13px] font-extrabold text-white transition-colors duration-150 hover:brightness-95"
            >
              네이버로 계속하기
            </button>

            <p className="mt-[22px] text-center text-sm text-sub">
              아직 회원이 아니신가요?{" "}
              <button
                type="button"
                onClick={() => setView("signup")}
                className="cursor-pointer font-bold text-brand-dark"
              >
                회원가입
              </button>
            </p>

            <div className="mt-[22px] flex items-center gap-3 text-xs text-[#c2c9b8]">
              <div className="h-px flex-1 bg-[#eceee5]" />
              테스트 계정
              <div className="h-px flex-1 bg-[#eceee5]" />
            </div>
            <div className="mt-3.5 flex gap-2.5">
              <button
                type="button"
                disabled={submitting}
                onClick={() => quickLogin("test@test.com", "1234")}
                className="flex-1 cursor-pointer rounded-xl border-[1.5px] border-line bg-white p-3 text-sm font-bold text-[#5b6a54] transition-colors duration-150 hover:bg-brand-soft hover:text-brand-dark disabled:opacity-60"
              >
                테스트 유저로 로그인
              </button>
              <button
                type="button"
                disabled={submitting}
                onClick={() => quickLogin("admin@test.com", "1234")}
                className="flex-1 cursor-pointer rounded-xl border-[1.5px] border-line bg-white p-3 text-sm font-bold text-[#5b6a54] transition-colors duration-150 hover:bg-brand-soft hover:text-brand-dark disabled:opacity-60"
              >
                관리자로 로그인
              </button>
            </div>
          </div>
        </div>
      )}

      {view === "signup" && (
        <div className="flex flex-1 items-start justify-center px-5 pb-[60px] pt-2.5">
          <div className={`${CARD} max-w-[460px]`}>
            <h2 className="mb-1 text-2xl font-extrabold">
              첫 식물을 맞이할 준비 🌱
            </h2>
            <p className="mb-[22px] text-sm text-sub">
              몇 가지만 알려주시면 바로 시작할 수 있어요.
            </p>

            <div className="flex flex-col gap-3.5">
              <div>
                <label className={LABEL}>이메일</label>
                <div className="mt-1.5 flex gap-2">
                  <input
                    value={signupEmail}
                    onChange={(e) => changeSignupEmail(e.target.value)}
                    disabled={emailVerified}
                    placeholder="hello@example.com"
                    className={`${FIELD} disabled:bg-[#f5f6f2] disabled:text-sub`}
                  />
                  <button
                    type="button"
                    disabled={!signupEmail || verifying || emailVerified}
                    onClick={sendVerificationCode}
                    className="w-[104px] shrink-0 cursor-pointer whitespace-nowrap rounded-xl border-[1.5px] border-line bg-white text-[13px] font-bold text-[#5b6a54] transition-colors duration-150 hover:bg-brand-soft hover:text-brand-dark disabled:cursor-default disabled:opacity-60"
                  >
                    {emailVerified
                      ? "인증완료 ✅"
                      : verificationSent
                        ? "재전송"
                        : "인증코드 받기"}
                  </button>
                </div>

                {verificationSent && !emailVerified && (
                  <div className="mt-2 flex gap-2">
                    <input
                      value={verificationCode}
                      onChange={(e) =>
                        setVerificationCode(
                          e.target.value.replace(/\D/g, "").slice(0, 6),
                        )
                      }
                      placeholder="6자리 인증코드"
                      inputMode="numeric"
                      className={`${FIELD}`}
                    />
                    <button
                      type="button"
                      disabled={verifying || verificationCode.length !== 6}
                      onClick={confirmVerificationCode}
                      className="w-[104px] shrink-0 cursor-pointer whitespace-nowrap rounded-xl bg-brand-soft text-[13px] font-bold text-brand-dark transition-colors duration-150 hover:bg-brand hover:text-white disabled:cursor-default disabled:opacity-60"
                    >
                      확인
                    </button>
                  </div>
                )}
                {verificationError && (
                  <div className="mt-1.5 text-xs font-semibold text-danger">
                    {verificationError}
                  </div>
                )}
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className={LABEL}>비밀번호</label>
                  <input
                    type="password"
                    value={signupPassword}
                    onChange={(e) => setSignupPassword(e.target.value)}
                    placeholder="8자 이상"
                    className={`${FIELD} mt-1.5`}
                  />
                </div>
                <div>
                  <label className={LABEL}>비밀번호 확인</label>
                  <input
                    type="password"
                    value={signupPasswordConfirm}
                    onChange={(e) => setSignupPasswordConfirm(e.target.value)}
                    placeholder="다시 입력"
                    className={`${FIELD} mt-1.5`}
                  />
                </div>
              </div>
              <div className="-mt-1.5 text-xs text-[#a9b3a0]">
                영문과 숫자를 포함해 8자 이상으로 만들어 주세요.
              </div>

              <div>
                <label className={LABEL}>닉네임</label>
                <div className="mt-1.5 flex gap-2">
                  <input
                    value={signupNickname}
                    onChange={(e) => changeSignupNickname(e.target.value)}
                    maxLength={NICKNAME_MAX_LENGTH}
                    placeholder="특별한 이름을 지어주세요"
                    className={FIELD}
                  />
                  <button
                    type="button"
                    disabled={!signupNickname || checkingNickname}
                    onClick={checkNickname}
                    className="w-[104px] shrink-0 cursor-pointer whitespace-nowrap rounded-xl border-[1.5px] border-line bg-white text-[13px] font-bold text-[#5b6a54] transition-colors duration-150 hover:bg-brand-soft hover:text-brand-dark disabled:cursor-default disabled:opacity-60"
                  >
                    {nicknameChecked && nicknameAvailable
                      ? "사용가능 ✅"
                      : "중복확인"}
                  </button>
                </div>
                <div className="mt-1 flex items-center justify-between">
                  <span
                    className={`text-xs ${nicknameError ? "font-semibold text-danger" : "text-[#a9b3a0]"}`}
                  >
                    {nicknameError ||
                      (nicknameChecked && nicknameAvailable
                        ? "사용할 수 있는 닉네임이에요."
                        : "")}
                  </span>
                  <span className="text-xs text-[#a9b3a0]">
                    {signupNickname.length}/{NICKNAME_MAX_LENGTH}
                  </span>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className={LABEL}>이름</label>
                  <input
                    value={signupName}
                    onChange={(e) => setSignupName(e.target.value)}
                    maxLength={NAME_MAX_LENGTH}
                    placeholder="김초록"
                    className={`${FIELD} mt-1.5`}
                  />
                </div>
                <div>
                  <label className={LABEL}>전화번호</label>
                  <input
                    value={signupPhone}
                    onChange={(e) => changeSignupPhone(e.target.value)}
                    maxLength={PHONE_MAX_LENGTH}
                    inputMode="numeric"
                    placeholder="01012345678"
                    className={`${FIELD} mt-1.5`}
                  />
                </div>
              </div>
            </div>

            {signupError && (
              <div className="mt-4 rounded-[11px] bg-danger-soft px-[13px] py-[11px] text-[13px] font-semibold text-danger">
                {signupError}
              </div>
            )}

            <button
              type="button"
              disabled={
                submitting ||
                !emailVerified ||
                !nicknameChecked ||
                !nicknameAvailable
              }
              onClick={handleSignup}
              className="mt-6 w-full cursor-pointer rounded-xl bg-brand p-3.5 text-base font-bold text-white transition-colors duration-150 hover:bg-brand-dark disabled:opacity-60"
            >
              가입하고 시작하기
            </button>
            <p className="mt-5 text-center text-sm text-sub">
              이미 회원이신가요?{" "}
              <button
                type="button"
                onClick={() => setView("login")}
                className="cursor-pointer font-bold text-brand-dark"
              >
                로그인
              </button>
            </p>
          </div>
        </div>
      )}

      {view === "reset" && (
        <div className="flex flex-1 items-start justify-center px-5 pb-[60px] pt-2.5">
          <div className={`${CARD} max-w-[400px]`}>
            <h2 className="mb-1 text-2xl font-extrabold">비밀번호 찾기 🔑</h2>
            <p className="mb-6 text-sm text-sub">
              가입한 이메일로 인증코드를 보내드릴게요.
            </p>

            <div className="flex flex-col gap-3.5">
              <div>
                <label className={LABEL}>이메일</label>
                <div className="mt-1.5 flex gap-2">
                  <input
                    value={resetEmail}
                    onChange={(e) => changeResetEmail(e.target.value)}
                    disabled={resetVerified}
                    placeholder="hello@example.com"
                    className={`${FIELD} disabled:bg-[#f5f6f2] disabled:text-sub`}
                  />
                  <button
                    type="button"
                    disabled={!resetEmail || resetVerifying || resetVerified}
                    onClick={sendResetVerificationCode}
                    className="w-[104px] shrink-0 cursor-pointer whitespace-nowrap rounded-xl border-[1.5px] border-line bg-white text-[13px] font-bold text-[#5b6a54] transition-colors duration-150 hover:bg-brand-soft hover:text-brand-dark disabled:cursor-default disabled:opacity-60"
                  >
                    {resetVerified
                      ? "인증완료 ✅"
                      : resetVerificationSent
                        ? "재전송"
                        : "인증코드 받기"}
                  </button>
                </div>

                {resetVerificationSent && !resetVerified && (
                  <div className="mt-2 flex gap-2">
                    <input
                      value={resetCode}
                      onChange={(e) =>
                        setResetCode(
                          e.target.value.replace(/\D/g, "").slice(0, 6),
                        )
                      }
                      placeholder="6자리 인증코드"
                      inputMode="numeric"
                      className={`${FIELD}`}
                    />
                    <button
                      type="button"
                      disabled={resetVerifying || resetCode.length !== 6}
                      onClick={confirmResetVerificationCode}
                      className="w-[104px] shrink-0 cursor-pointer whitespace-nowrap rounded-xl bg-brand-soft text-[13px] font-bold text-brand-dark transition-colors duration-150 hover:bg-brand hover:text-white disabled:cursor-default disabled:opacity-60"
                    >
                      확인
                    </button>
                  </div>
                )}
                {resetVerificationError && (
                  <div className="mt-1.5 text-xs font-semibold text-danger">
                    {resetVerificationError}
                  </div>
                )}
              </div>

              {resetVerified && (
                <>
                  <div>
                    <label className={LABEL}>새 비밀번호</label>
                    <input
                      type="password"
                      value={resetNewPassword}
                      onChange={(e) => setResetNewPassword(e.target.value)}
                      placeholder="8자 이상"
                      className={`${FIELD} mt-1.5`}
                    />
                  </div>
                  <div>
                    <label className={LABEL}>새 비밀번호 확인</label>
                    <input
                      type="password"
                      value={resetNewPasswordConfirm}
                      onChange={(e) =>
                        setResetNewPasswordConfirm(e.target.value)
                      }
                      placeholder="다시 입력"
                      className={`${FIELD} mt-1.5`}
                    />
                  </div>
                  <div className="-mt-1.5 text-xs text-[#a9b3a0]">
                    영문과 숫자를 포함해 8자 이상으로 만들어 주세요.
                  </div>
                </>
              )}
            </div>

            {resetError && (
              <div className="mt-4 rounded-[11px] bg-danger-soft px-[13px] py-[11px] text-[13px] font-semibold text-danger">
                {resetError}
              </div>
            )}

            <button
              type="button"
              disabled={
                submitting ||
                !resetVerified ||
                !resetNewPassword ||
                !resetNewPasswordConfirm
              }
              onClick={handleResetPassword}
              className="mt-6 w-full cursor-pointer rounded-xl bg-brand p-3.5 text-base font-bold text-white transition-colors duration-150 hover:bg-brand-dark disabled:opacity-60"
            >
              비밀번호 변경하기
            </button>
            <p className="mt-5 text-center text-sm text-sub">
              <button
                type="button"
                onClick={resetToLoginView}
                className="cursor-pointer font-bold text-brand-dark"
              >
                로그인으로 돌아가기
              </button>
            </p>
          </div>
        </div>
      )}
    </>
  );

  return (
    <div className="flex min-h-screen flex-col font-sans">
      <div className="flex items-center justify-center p-[22px]">
        <Link href="/" className="text-[22px] font-extrabold text-brand-dark">
          키워볼래 🌱
        </Link>
      </div>
      {content}
    </div>
  );
}
