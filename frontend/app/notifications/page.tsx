'use client';
import { useUI } from '@/lib/ui';
import { useStore, NotificationItem, NotificationType } from '@/lib/store';

const NIC: Record<NotificationType, [string, string]> = {
  DELIVERY: ['local_shipping', 'bg-[#E3F0FA]'],
  POINT: ['light_mode', 'bg-gold-soft'],
  JOURNAL_REMINDER: ['eco', 'bg-brand-soft'],
  INQUIRY: ['chat_bubble', 'bg-[#F0ECF9]'],
  NOTICE: ['campaign', 'bg-[#FBEDE3]'],
};

export default function Notifications() {
  const { showToast } = useUI();
  const { state, markNotifRead, markAllNotifsRead } = useStore();
  const noti = state.notifications;

  const groups: Record<string, NotificationItem[]> = {};
  noti.forEach((n) => { (groups[n.date] = groups[n.date] || []).push(n); });

  const click = (n: NotificationItem) => {
    markNotifRead(n.id);
    if (n.broken) showToast('연결된 내용을 찾을 수 없어요.', 'err');
  };

  return (
    <div className="container max-w-[760px]">
      <div className="mb-5 flex items-center justify-between">
        <h1 className="text-2xl font-extrabold">알림</h1>
        <button
          type="button"
          onClick={() => { markAllNotifsRead(); showToast('모든 알림을 읽음으로 표시했어요.'); }}
          className="cursor-pointer rounded-[11px] bg-brand-soft px-4 py-[9px] font-bold text-brand-dark"
        >
          모두 읽음
        </button>
      </div>

      {Object.keys(groups).map((date) => (
        <div key={date} className="mb-[22px]">
          <div className="mb-2.5 text-[13px] font-extrabold text-faint">{date}</div>
          <div className="flex flex-col gap-2.5">
            {groups[date].map((n) => (
              <button
                key={n.id}
                type="button"
                onClick={() => click(n)}
                className={`flex cursor-pointer items-center gap-3 rounded-[14px] px-4 py-[15px] text-left shadow-[0_4px_20px_rgba(124,179,66,.05)] ${
                  n.unread ? 'bg-[#FFFBEB]' : 'bg-white'
                }`}
              >
                <div className={`flex h-10 w-10 items-center justify-center rounded-[11px] ${NIC[n.type][1]}`}>
                  <span className="material-symbols-outlined text-xl">{NIC[n.type][0]}</span>
                </div>
                <div className="flex-1">
                  <div className="text-[14.5px] font-bold">{n.title}</div>
                  <div className="mt-0.5 text-[12.5px] text-sub">{n.content}</div>
                </div>
                {n.unread && <div className="h-2 w-2 flex-none rounded-full bg-[#e5533b]" />}
              </button>
            ))}
          </div>
        </div>
      ))}
    </div>
  );
}
