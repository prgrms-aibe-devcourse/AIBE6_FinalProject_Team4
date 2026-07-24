'use client';
import { useState } from 'react';
import { useUI } from '@/lib/ui';

const INITIAL = [
  { type: 'DELIVERY', icon: 'local_shipping', label: '배송 알림', on: true },
  { type: 'COMMUNITY', icon: 'chat_bubble', label: '커뮤니티 알림', on: true },
  { type: 'POINT', icon: 'light_mode', label: '포인트 알림', on: true },
  { type: 'NOTICE', icon: 'campaign', label: '공지 알림', on: false },
  { type: 'INQUIRY', icon: 'chat_bubble', label: '문의 답변 알림', on: true },
  { type: 'REMINDER', icon: 'eco', label: '케어 리마인더', on: true },
];

export default function NotiSettings() {
  const { showToast } = useUI();
  const [settings, setSettings] = useState(INITIAL);

  const toggle = (i: number) => {
    setSettings(settings.map((x, j) => j === i ? { ...x, on: !x.on } : x));
    showToast('설정이 저장됐어요.');
  };

  return (
    <div className="container max-w-[760px]">
      <h1 className="mb-5 text-2xl font-extrabold">알림 설정</h1>
      <div className="overflow-hidden rounded-[18px] bg-white shadow-card">
        {settings.map((st, i) => (
          <div key={st.type} className="flex items-center justify-between border-b border-[#f4f5ee] px-5 py-4">
            <div className="font-bold">
              <span className="material-symbols-outlined mr-[5px] text-lg text-brand">{st.icon}</span>
              {st.label}
            </div>
            <button
              type="button"
              onClick={() => toggle(i)}
              aria-pressed={st.on}
              className={`relative h-7 w-12 cursor-pointer rounded-full transition-colors ${st.on ? 'bg-brand' : 'bg-[#d7dccd]'}`}
            >
              <span
                className={`absolute top-[3px] h-[22px] w-[22px] rounded-full bg-white shadow-[0_1px_4px_rgba(0,0,0,.2)] transition-[left] ${
                  st.on ? 'left-[23px]' : 'left-[3px]'
                }`}
              />
            </button>
          </div>
        ))}
      </div>
    </div>
  );
}
