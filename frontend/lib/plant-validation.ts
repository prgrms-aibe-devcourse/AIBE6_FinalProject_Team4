// 백엔드 PlantProfileRequest/PlantProfileUpdateRequest의 nickname @Pattern과
// 허용 문자 범위를 동일하게 맞춘다(한글/영문/숫자/공백만 허용).
export function nickValid(v: string) {
  if (!v) return { ok: false, msg: '별명을 입력해 주세요.' };
  if (v.length > 50) return { ok: false, msg: '50자 이내로 지어주세요.' };
  if (/[^가-힣a-zA-Z0-9 ]/.test(v)) return { ok: false, msg: '특수문자 없이 예쁜 이름으로 지어주세요 🌱' };
  return { ok: true, msg: '좋은 이름이에요! 🌿' };
}
