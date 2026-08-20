// 백엔드 PlantProfileRequest/PlantProfileUpdateRequest의 nickname @Pattern과
// 허용 문자 범위를 동일하게 맞춘다(한글/영문/숫자/공백만 허용).
export function nickValid(v: string) {
  if (!v) return { ok: false, msg: "별명을 입력해 주세요." };
  if (v.length > 50) return { ok: false, msg: "50자 이내로 지어주세요." };
  if (/[^가-힣a-zA-Z0-9 ]/.test(v))
    return { ok: false, msg: "특수문자 없이 예쁜 이름으로 지어주세요 🌱" };
  return { ok: true, msg: "좋은 이름이에요! 🌿" };
}

// 백엔드 PlantProfileRequest의 speciesName @Pattern과 허용 문자 범위를 동일하게 맞춘다
// (한글/영문/공백만 허용 — 숫자·특수문자 불가).
export function speciesNameValid(v: string) {
  if (!v.trim()) return { ok: false, msg: "식물 종을 입력해 주세요." };
  if (v.length > 100) return { ok: false, msg: "100자 이내로 입력해 주세요." };
  if (/[^가-힣a-zA-Z ]/.test(v))
    return { ok: false, msg: "한글/영문/공백만 사용할 수 있어요." };
  return { ok: true, msg: "" };
}
