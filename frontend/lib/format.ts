export function formatDate(value: string): string {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return value;
  return new Intl.DateTimeFormat('ko-KR', { year: 'numeric', month: '2-digit', day: '2-digit' }).format(date).replace(/\s/g, '').replace(/\.$/, '');
}

// KST(Asia/Seoul) 기준 오늘 날짜를 YYYY-MM-DD로 반환한다. toISOString()은 UTC라 자정~오전 9시(KST) 사이 하루가 어긋난다.
export function localToday(): string {
  const parts = new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' }).formatToParts(new Date());
  const get = (type: string) => parts.find((p) => p.type === type)?.value ?? '';
  return `${get('year')}-${get('month')}-${get('day')}`;
}
