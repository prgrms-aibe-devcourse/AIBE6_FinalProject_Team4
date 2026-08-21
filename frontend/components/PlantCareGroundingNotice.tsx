import { PlantCareGrounding } from "@/lib/plant-care-grounding";

interface PlantCareGroundingNoticeProps {
  grounding: PlantCareGrounding;
  compact?: boolean;
}

export default function PlantCareGroundingNotice({
  grounding,
  compact = false,
}: PlantCareGroundingNoticeProps) {
  if (grounding.status === "GENERAL_FALLBACK") {
    return (
      <div
        className={`rounded-xl border border-amber-200 bg-amber-50 text-amber-900 ${
          compact ? "mt-2 px-2.5 py-2 text-[11px]" : "px-3.5 py-3 text-[12px]"
        }`}
      >
        <div className="font-extrabold">공식 근거가 없는 일반 AI 안내예요</div>
        <p className="mt-1 leading-[1.55]">
          정확한 수치나 농약·비료 처방 대신 관찰 중심으로 보수적으로 안내합니다.
        </p>
      </div>
    );
  }

  const usesBaseSpecies =
    grounding.scope === "BASE_SPECIES" && grounding.resolvedSpeciesName;

  return (
    <div
      className={`rounded-xl border border-emerald-200 bg-emerald-50 text-emerald-900 ${
        compact ? "mt-2 px-2.5 py-2 text-[11px]" : "px-3.5 py-3 text-[12px]"
      }`}
    >
      <div className="font-extrabold">
        {usesBaseSpecies
          ? `${grounding.resolvedSpeciesName} 공통 재배 근거를 확인했어요`
          : "공식 문서 근거를 확인했어요"}
      </div>
      {usesBaseSpecies ? (
        <p className="mt-1 leading-[1.55]">
          입력한 품종 전용 자료가 아닌 {grounding.resolvedSpeciesName} 공통 재배
          자료를 참고했습니다. 품종별 특성은 다를 수 있어요.
        </p>
      ) : null}
      <ul className="mt-1 space-y-1 leading-[1.55]">
        {grounding.sources.map((source) => (
          <li key={`${source.sourceId}-${source.contentHash}`}>
            <a
              href={source.sourceUrl}
              target="_blank"
              rel="noreferrer"
              className="font-semibold underline decoration-emerald-400 underline-offset-2"
            >
              {source.sourceName}
            </a>{" "}
            <span className="text-emerald-700">({source.version})</span>
          </li>
        ))}
      </ul>
    </div>
  );
}
