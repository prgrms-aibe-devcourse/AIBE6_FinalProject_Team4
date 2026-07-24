'use client';
import { useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { useUI } from '@/lib/ui';
import { useStore } from '@/lib/store';
import { login as apiLogin, signup as apiSignup, ApiError } from '@/lib/api';

const FIELD = 'w-full rounded-xl border-[1.5px] border-line px-3.5 py-[13px] text-[15px] outline-none';
const LABEL = 'text-[13px] font-bold text-[#6d7a68]';
const CARD = 'w-full rounded-[22px] bg-white px-[30px] py-[34px] shadow-[0_10px_40px_rgba(124,179,66,.12)] animate-upIn';

interface AuthPanelProps {
  initialView?: 'login' | 'signup';
}

export default function AuthPanel({ initialView = 'login' }: AuthPanelProps) {
  const router = useRouter();
  const { showToast } = useUI();
  const { login } = useStore();
  const [view, setView] = useState(initialView);
  const [submitting, setSubmitting] = useState(false);

  const [loginEmail, setLoginEmail] = useState('');
  const [loginPassword, setLoginPassword] = useState('');
  const [loginError, setLoginError] = useState('');

  const [signupEmail, setSignupEmail] = useState('');
  const [signupPassword, setSignupPassword] = useState('');
  const [signupPasswordConfirm, setSignupPasswordConfirm] = useState('');
  const [signupNickname, setSignupNickname] = useState('');
  const [signupName, setSignupName] = useState('');
  const [signupPhone, setSignupPhone] = useState('');
  const [signupError, setSignupError] = useState('');

  const afterAuth = (msg: string) => {
    showToast(msg);
    setTimeout(() => router.push('/'), 1000);
  };

  const handleLogin = async (email = loginEmail, password = loginPassword) => {
    setLoginError('');
    setSubmitting(true);
    try {
      const res = await apiLogin(email, password);
      login(res.accessToken, res.user);
      afterAuth('환영해요! 홈으로 데려다드릴게요 🌿');
    } catch (e) {
      setLoginError(e instanceof ApiError ? e.message : '로그인에 실패했어요. 다시 시도해 주세요.');
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
    setSignupError('');
    if (signupPassword !== signupPasswordConfirm) {
      setSignupError('비밀번호가 서로 달라요.');
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
      afterAuth('환영해요! 이제 첫 식물을 맞이해 볼까요? 🌱');
    } catch (e) {
      setSignupError(e instanceof ApiError ? e.message : '회원가입에 실패했어요. 다시 시도해 주세요.');
    } finally {
      setSubmitting(false);
    }
  };

  const notReady = () => showToast('소셜 로그인은 아직 준비 중이에요. 조금만 기다려 주세요 🌱');

  const content = (
    <>
      {view === 'login' && (
        <div className="flex flex-1 items-start justify-center px-5 pb-[60px] pt-2.5">
          <div className={`${CARD} max-w-[400px]`}>
            <h2 className="mb-1 text-2xl font-extrabold">다시 오셨네요 🌿</h2>
            <p className="mb-6 text-sm text-sub">오늘도 푸릇한 하루 보내세요.</p>

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
              <button type="button" onClick={() => showToast('비밀번호 찾기는 준비 중이에요. 조금만 기다려 주세요 🌱')} className="cursor-pointer text-xs text-[#a9b3a0]">
                비밀번호 찾기
              </button>
            </div>

            {loginError && (
              <div className="mb-3.5 rounded-[11px] bg-danger-soft px-[13px] py-[11px] text-[13px] font-semibold text-danger">
                {loginError}
              </div>
            )}

            <button type="button" disabled={submitting} onClick={handleLogin} className="w-full cursor-pointer rounded-xl bg-brand p-3.5 text-base font-bold text-white transition-colors duration-150 hover:bg-brand-dark disabled:opacity-60">
              로그인
            </button>

            <div className="my-[22px] flex items-center gap-3 text-xs text-[#c2c9b8]">
              <div className="h-px flex-1 bg-[#eceee5]" />또는<div className="h-px flex-1 bg-[#eceee5]" />
            </div>

            <button type="button" onClick={notReady} className="mb-2.5 w-full cursor-pointer rounded-xl border-[1.5px] border-[#e3e5df] bg-white p-[13px] font-bold text-[#3c4043] transition-colors duration-150 hover:bg-[#f5f6f2]">
              Google로 계속하기
            </button>
            <button type="button" onClick={notReady} className="mb-2.5 w-full cursor-pointer rounded-xl bg-[#FEE500] p-[13px] font-bold text-[#3c1e1e] transition-colors duration-150 hover:brightness-95">
              카카오로 계속하기
            </button>
            <button type="button" onClick={notReady} className="w-full cursor-pointer rounded-xl bg-[#03C75A] p-[13px] font-extrabold text-white transition-colors duration-150 hover:brightness-95">
              네이버로 계속하기
            </button>

            <p className="mt-[22px] text-center text-sm text-sub">
              아직 회원이 아니신가요?{' '}
              <button type="button" onClick={() => setView('signup')} className="cursor-pointer font-bold text-brand-dark">회원가입</button>
            </p>

            <div className="mt-[22px] flex items-center gap-3 text-xs text-[#c2c9b8]">
              <div className="h-px flex-1 bg-[#eceee5]" />테스트 계정<div className="h-px flex-1 bg-[#eceee5]" />
            </div>
            <div className="mt-3.5 flex gap-2.5">
              <button
                type="button"
                disabled={submitting}
                onClick={() => quickLogin('test@test.com', '1234')}
                className="flex-1 cursor-pointer rounded-xl border-[1.5px] border-line bg-white p-3 text-sm font-bold text-[#5b6a54] transition-colors duration-150 hover:bg-brand-soft hover:text-brand-dark disabled:opacity-60"
              >
                테스트 유저로 로그인
              </button>
              <button
                type="button"
                disabled={submitting}
                onClick={() => quickLogin('admin@test.com', '1234')}
                className="flex-1 cursor-pointer rounded-xl border-[1.5px] border-line bg-white p-3 text-sm font-bold text-[#5b6a54] transition-colors duration-150 hover:bg-brand-soft hover:text-brand-dark disabled:opacity-60"
              >
                관리자로 로그인
              </button>
            </div>
          </div>
        </div>
      )}

      {view === 'signup' && (
        <div className="flex flex-1 items-start justify-center px-5 pb-[60px] pt-2.5">
          <div className={`${CARD} max-w-[460px]`}>
            <h2 className="mb-1 text-2xl font-extrabold">첫 식물을 맞이할 준비 🌱</h2>
            <p className="mb-[22px] text-sm text-sub">몇 가지만 알려주시면 바로 시작할 수 있어요.</p>

            <div className="flex flex-col gap-3.5">
              <div>
                <label className={LABEL}>이메일</label>
                <input
                  value={signupEmail}
                  onChange={(e) => setSignupEmail(e.target.value)}
                  placeholder="hello@example.com"
                  className={`${FIELD} mt-1.5`}
                />
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
              <div className="-mt-1.5 text-xs text-[#a9b3a0]">영문과 숫자를 포함해 8자 이상으로 만들어 주세요.</div>

              <div>
                <label className={LABEL}>닉네임</label>
                <input
                  value={signupNickname}
                  onChange={(e) => setSignupNickname(e.target.value)}
                  placeholder="특별한 이름을 지어주세요"
                  className={`${FIELD} mt-1.5`}
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className={LABEL}>이름</label>
                  <input value={signupName} onChange={(e) => setSignupName(e.target.value)} placeholder="김초록" className={`${FIELD} mt-1.5`} />
                </div>
                <div>
                  <label className={LABEL}>전화번호</label>
                  <input value={signupPhone} onChange={(e) => setSignupPhone(e.target.value)} placeholder="010-0000-0000" className={`${FIELD} mt-1.5`} />
                </div>
              </div>
            </div>

            {signupError && (
              <div className="mt-4 rounded-[11px] bg-danger-soft px-[13px] py-[11px] text-[13px] font-semibold text-danger">
                {signupError}
              </div>
            )}

            <button type="button" disabled={submitting} onClick={handleSignup} className="mt-6 w-full cursor-pointer rounded-xl bg-brand p-3.5 text-base font-bold text-white transition-colors duration-150 hover:bg-brand-dark disabled:opacity-60">
              가입하고 시작하기
            </button>
            <p className="mt-5 text-center text-sm text-sub">
              이미 회원이신가요?{' '}
              <button type="button" onClick={() => setView('login')} className="cursor-pointer font-bold text-brand-dark">로그인</button>
            </p>
          </div>
        </div>
      )}
    </>
  );

  return (
    <div className="flex min-h-screen flex-col font-sans">
      <div className="flex items-center justify-center p-[22px]">
        <Link href="/" className="text-[22px] font-extrabold text-brand-dark">키워볼래 🌱</Link>
      </div>
      {content}
    </div>
  );
}
