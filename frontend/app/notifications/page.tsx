'use client';
import { useCallback, useEffect, useState, type MouseEvent as ReactMouseEvent } from 'react';
import { useRouter } from 'next/navigation';
import { ApiError } from '@/lib/api';
import { useStore } from '@/lib/store';
import { useUI } from '@/lib/ui';
import {
  deleteNotification,
  getNotifications,
  markNotificationRead,
  NotificationData,
  NotificationType,
} from '@/lib/notification-api';

const NIC: Record<NotificationType, [string, string]> = {
  DELIVERY: ['local_shipping', 'bg-[#E3F0FA]'],
  COMMUNITY: ['chat_bubble', 'bg-[#F0ECF9]'],
  POINT: ['light_mode', 'bg-gold-soft'],
  JOURNAL_REMINDER: ['eco', 'bg-brand-soft'],
  INQUIRY: ['chat_bubble', 'bg-[#F0ECF9]'],
  NOTICE: ['campaign', 'bg-[#FBEDE3]'],
  TIMELAPSE: ['movie', 'bg-[#E6F4EA]'],
  CARD_MARKET: ['handshake', 'bg-[#F7EBC9]'],
};

const TYPE_TABS: { label: string; type?: NotificationType }[] = [
  { label: '전체' },
  { label: '배송', type: 'DELIVERY' },
  { label: '커뮤니티', type: 'COMMUNITY' },
  { label: '재화', type: 'POINT' },
  { label: '거래소', type: 'CARD_MARKET' },
  { label: '공지', type: 'NOTICE' },
];

// 알림은 시:분까지 필요 없고 날짜 구분만 있으면 되므로, 오늘/어제만 상대 표기하고
// 나머지는 YYYY.MM.DD로 표시한다.
function dateGroupLabel(iso: string): string {
  const created = new Date(iso);
  const today = new Date();
  const startOf = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate()).getTime();
  const diffDays = Math.round((startOf(today) - startOf(created)) / 86_400_000);
  if (diffDays === 0) return '오늘';
  if (diffDays === 1) return '어제';
  return new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' })
    .format(created)
    .replace(/\s/g, '')
    .replace(/\.$/, '');
}

export default function Notifications() {
  const router = useRouter();
  const { showToast } = useUI();
  const { state, hydrated, refreshNotifications, markAllNotifsRead } = useStore();
  const [type, setType] = useState<NotificationType | undefined>(undefined);
  const [items, setItems] = useState<NotificationData[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState('');

  const load = useCallback(async () => {
    if (!state.accessToken) return;
    setLoading(true);
    setError('');
    try {
      const page = await getNotifications(state.accessToken, type, 0, 50);
      setItems(page.content);
    } catch (requestError) {
      setError(
        requestError instanceof ApiError
          ? requestError.message
          : '알림을 불러오지 못했어요. 잠시 후 다시 시도해 주세요.',
      );
    } finally {
      setLoading(false);
    }
  }, [state.accessToken, type]);

  useEffect(() => {
    if (!hydrated) return;
    void load();
  }, [hydrated, load]);

  const click = async (n: NotificationData) => {
    if (state.accessToken && !n.isRead) {
      setItems((prev) => prev.map((item) => (item.id === n.id ? { ...item, isRead: true } : item)));
      try {
        await markNotificationRead(n.id, state.accessToken);
      } finally {
        void refreshNotifications();
      }
    }
    if (n.linkUrl) router.push(n.linkUrl);
  };

  const remove = async (event: ReactMouseEvent, n: NotificationData) => {
    event.stopPropagation();
    if (!state.accessToken) return;
    setItems((prev) => prev.filter((item) => item.id !== n.id));
    try {
      await deleteNotification(n.id, state.accessToken);
      void refreshNotifications();
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError ? requestError.message : '알림 삭제에 실패했어요.',
        'err',
      );
      void load();
    }
  };

  const markAll = async () => {
    setItems((prev) => prev.map((item) => ({ ...item, isRead: true })));
    try {
      await markAllNotifsRead();
      showToast('모든 알림을 읽음으로 표시했어요.');
    } catch (requestError) {
      showToast(
        requestError instanceof ApiError ? requestError.message : '처리에 실패했어요.',
        'err',
      );
      void load();
    }
  };

  if (!hydrated) return <div className="container" />;

  if (!state.accessToken) {
    return (
      <div className="container max-w-[760px]">
        <div className="rounded-[22px] bg-white px-5 py-14 text-center text-sub">
          알림은 로그인 후 확인할 수 있어요.
        </div>
      </div>
    );
  }

  const groups: Record<string, NotificationData[]> = {};
  items.forEach((n) => {
    const label = dateGroupLabel(n.createdAt);
    (groups[label] = groups[label] || []).push(n);
  });

  return (
    <div className="container max-w-[760px]">
      <div className="mb-4 flex items-center justify-between">
        <h1 className="text-2xl font-extrabold">알림</h1>
        <button
          type="button"
          onClick={() => void markAll()}
          className="cursor-pointer rounded-[11px] bg-brand-soft px-4 py-[9px] font-bold text-brand-dark"
        >
          모두 읽음
        </button>
      </div>

      <div className="mb-5 flex flex-wrap gap-2">
        {TYPE_TABS.map((tab) => (
          <button
            key={tab.label}
            type="button"
            onClick={() => setType(tab.type)}
            className={`cursor-pointer rounded-full px-3.5 py-1.5 text-[13px] font-bold ${
              type === tab.type ? 'bg-brand text-white' : 'bg-white text-sub'
            }`}
          >
            {tab.label}
          </button>
        ))}
      </div>

      {loading ? (
        <div className="rounded-[22px] bg-white py-14 text-center text-sub">알림을 불러오고 있어요 🌱</div>
      ) : error ? (
        <div className="rounded-[22px] bg-white px-5 py-14 text-center text-sub">{error}</div>
      ) : items.length === 0 ? (
        <div className="rounded-[22px] bg-white py-14 text-center text-sub">알림이 없어요.</div>
      ) : (
        Object.keys(groups).map((label) => (
          <div key={label} className="mb-[22px]">
            <div className="mb-2.5 text-[13px] font-extrabold text-faint">{label}</div>
            <div className="flex flex-col gap-2.5">
              {groups[label].map((n) => (
                <div
                  key={n.id}
                  className={`flex items-center gap-3 rounded-[14px] px-4 py-[15px] shadow-[0_4px_20px_rgba(124,179,66,.05)] ${
                    !n.isRead ? 'bg-[#FFFBEB]' : 'bg-white'
                  }`}
                >
                  <button type="button" onClick={() => click(n)} className="flex flex-1 cursor-pointer items-center gap-3 text-left">
                    <div className={`flex h-10 w-10 flex-none items-center justify-center rounded-[11px] ${NIC[n.type][1]}`}>
                      <span className="material-symbols-outlined text-xl">{NIC[n.type][0]}</span>
                    </div>
                    <div className="min-w-0 flex-1">
                      <div className="text-[14.5px] font-bold">{n.title}</div>
                      <div className="mt-0.5 text-[12.5px] text-sub">{n.content}</div>
                    </div>
                    {!n.isRead && <div className="h-2 w-2 flex-none rounded-full bg-[#e5533b]" />}
                  </button>
                  <button
                    type="button"
                    onClick={(event) => remove(event, n)}
                    className="material-symbols-outlined flex-none cursor-pointer p-1 text-lg text-faint hover:text-danger"
                  >
                    close
                  </button>
                </div>
              ))}
            </div>
          </div>
        ))
      )}
    </div>
  );
}
