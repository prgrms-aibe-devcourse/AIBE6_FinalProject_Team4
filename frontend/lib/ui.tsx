'use client';
// Lightweight toast + confirm hooks shared by pages.
import { createContext, useContext, useState, useCallback, ReactNode } from 'react';

interface Toast {
  msg: string;
  tone: 'ok' | 'err';
}

interface ConfirmOpts {
  icon?: string;
  title?: string;
  body?: string;
  ok?: string;
  danger?: boolean;
  onOk?: () => void;
}

interface UIContextValue {
  showToast: (msg: string, tone?: 'ok' | 'err') => void;
  askConfirm: (opts: ConfirmOpts) => void;
}

const UICtx = createContext<UIContextValue | null>(null);

export function UIProvider({ children }: { children: ReactNode }) {
  const [toast, setToast] = useState<Toast | null>(null);
  const [confirm, setConfirm] = useState<ConfirmOpts | null>(null);

  const showToast = useCallback((msg: string, tone: 'ok' | 'err' = 'ok') => {
    setToast({ msg, tone });
    setTimeout(() => setToast(null), 2600);
  }, []);

  const askConfirm = useCallback((opts: ConfirmOpts) => setConfirm(opts), []);

  return (
    <UICtx.Provider value={{ showToast, askConfirm }}>
      {children}
      {toast && (
        <div
          className={`fixed bottom-[88px] left-1/2 z-[70] max-w-[90vw] -translate-x-1/2 rounded-[14px] px-[22px] py-3.5 text-center font-bold shadow-[0_10px_30px_rgba(0,0,0,.18)] ${
            toast.tone === 'err' ? 'bg-[#fff4ef] text-danger' : 'bg-[#2f3a2b] text-white'
          }`}
        >
          {toast.msg}
        </div>
      )}
      {confirm && (
        <div onClick={() => setConfirm(null)} className="fixed inset-0 z-[60] flex items-center justify-center bg-[rgba(46,54,42,.4)] p-5">
          <div onClick={(e) => e.stopPropagation()} className="w-full max-w-[380px] rounded-[20px] bg-white p-6">
            <div className="mb-2 text-[38px]">
              <span className="material-symbols-outlined text-[40px]">{confirm.icon || 'eco'}</span>
            </div>
            <h3 className="mb-2 text-lg font-extrabold">{confirm.title}</h3>
            <p className="mb-5 text-sm leading-[1.6] text-[#6d7a68]">{confirm.body}</p>
            <div className="flex gap-2.5">
              <button
                type="button"
                onClick={() => { confirm.onOk && confirm.onOk(); setConfirm(null); }}
                className={`flex-1 cursor-pointer rounded-xl p-[13px] font-extrabold text-white ${confirm.danger ? 'bg-danger' : 'bg-brand'}`}
              >
                {confirm.ok || '확인'}
              </button>
              <button
                type="button"
                onClick={() => setConfirm(null)}
                className="cursor-pointer rounded-xl border-[1.5px] border-line bg-white px-5 py-[13px] font-bold text-sub"
              >
                취소
              </button>
            </div>
          </div>
        </div>
      )}
    </UICtx.Provider>
  );
}

export function useUI(): UIContextValue {
  const ctx = useContext(UICtx);
  if (!ctx) throw new Error('useUI must be used within <UIProvider>');
  return ctx;
}
