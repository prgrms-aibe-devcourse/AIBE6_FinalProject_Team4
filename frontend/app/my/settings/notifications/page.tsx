'use client';
import { useCallback, useEffect, useState } from 'react';
import { ApiError } from '@/lib/api';
import { useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';
import {
  getNotificationSettings,
  NotificationSettingData,
  NotificationType,
  updateNotificationSetting,
} from '@/lib/notification-api';

const LABELS: Record<NotificationType, { icon: string; label: string }> = {
  DELIVERY: { icon: 'local_shipping', label: '배송 알림' },
  COMMUNITY: { icon: 'chat_bubble', label: '커뮤니티 알림' },
  POINT: { icon: 'light_mode', label: '포인트 알림' },
  NOTICE: { icon: 'campaign', label: '공지 알림' },
  INQUIRY: { icon: 'chat_bubble', label: '문의 답변 알림' },
  JOURNAL_REMINDER: { icon: 'eco', label: '케어 리마인더' },
};

// 화면에 보여줄 순서 — enum 선언 순서와 무관하게 사용자에게 익숙한 순서로 고정한다.
const ORDER: NotificationType[] = ['DELIVERY', 'COMMUNITY', 'POINT', 'NOTICE', 'INQUIRY', 'JOURNAL_REMINDER'];

export default function NotiSettings() {
  const { showToast } = useUI();
  const { state, hydrated } = useStore();
  const [settings, setSettings] = useState<NotificationSettingData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');
  const [savingType, setSavingType] = useState<NotificationType | null>(null);

  const load = useCallback(async () => {
    if (!state.accessToken) return;
    setLoading(true);
    setError('');
    try {
      const list = await getNotificationSettings(state.accessToken);
      setSettings(list);
    } catch (requestError) {
      setError(
        requestError instanceof ApiError
          ? requestError.message
          : '알림 설정을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
      );
    } finally {
      setLoading(false);
    }
  }, [state.accessToken]);

  useEffect(() => {
    if (!hydrated) return;
    void load();
  }, [hydrated, load]);

  const toggle = async (type: NotificationType, current: boolean) => {
    if (!state.accessToken || savingType) return;
    setSavingType(type);
    setSettings((prev) => prev.map((s) => (s.type === type ? { ...s, enabled: !current } : s)));
    try {
      await updateNotificationSetting(type, !current, state.accessToken);
      showToast('설정이 저장됐어요.');
    } catch (requestError) {
      setSettings((prev) => prev.map((s) => (s.type === type ? { ...s, enabled: current } : s)));
      showToast(
        requestError instanceof ApiError ? requestError.message : '설정 저장에 실패했어요.',
        'err',
      );
    } finally {
      setSavingType(null);
    }
  };

  if (!hydrated) return <div className="container max-w-[760px]" />;

  if (!state.accessToken) {
    return (
      <div className="container max-w-[760px]">
        <div className="rounded-[22px] bg-white px-5 py-14 text-center text-sub">
          알림 설정은 로그인 후 변경할 수 있어요.
        </div>
      </div>
    );
  }

  return (
    <div className="container max-w-[760px]">
      <h1 className="mb-5 text-2xl font-extrabold">알림 설정</h1>

      {loading ? (
        <div className="rounded-[22px] bg-white py-14 text-center text-sub">불러오고 있어요 🌱</div>
      ) : error ? (
        <div className="rounded-[22px] bg-white px-5 py-14 text-center text-sub">{error}</div>
      ) : (
        <div className="overflow-hidden rounded-[18px] bg-white shadow-card">
          {ORDER.map((type) => {
            const setting = settings.find((s) => s.type === type);
            const enabled = setting?.enabled ?? true;
            const meta = LABELS[type];
            return (
              <div key={type} className="flex items-center justify-between border-b border-[#f4f5ee] px-5 py-4 last:border-b-0">
                <div className="font-bold">
                  <span className="material-symbols-outlined mr-[5px] text-lg text-brand">{meta.icon}</span>
                  {meta.label}
                </div>
                <button
                  type="button"
                  disabled={savingType === type}
                  onClick={() => toggle(type, enabled)}
                  aria-pressed={enabled}
                  className={`relative h-7 w-12 cursor-pointer rounded-full transition-colors disabled:cursor-not-allowed disabled:opacity-60 ${
                    enabled ? 'bg-brand' : 'bg-[#d7dccd]'
                  }`}
                >
                  <span
                    className={`absolute top-[3px] h-[22px] w-[22px] rounded-full bg-white shadow-[0_1px_4px_rgba(0,0,0,.2)] transition-[left] ${
                      enabled ? 'left-[23px]' : 'left-[3px]'
                    }`}
                  />
                </button>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
