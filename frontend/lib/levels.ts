// 레벨(1~5)별 표시 타이틀. 서버는 순수 숫자 level만 내려주고, 타이틀 문구는 프론트에서 관리한다.
export const LEVEL_TITLES: Record<number, string> = {
  1: '씨앗 새내기',
  2: '떡잎 정원사',
  3: '새싹 정원사',
  4: '푸른잎 재배자',
  5: '열매 마스터',
};

export function levelTitle(level: number): string {
  return LEVEL_TITLES[level] ?? LEVEL_TITLES[5];
}
