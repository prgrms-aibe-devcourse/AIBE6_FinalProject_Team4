/** 실물 교환용 legacy 카드명을 사용자 화면에서 쿠폰으로 표시한다. */
export function couponName(name: string): string {
  return name.replace(/카드$/, "쿠폰");
}
