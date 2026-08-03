import { ReactNode } from "react";

const FRAME_STYLE: Record<string, string> = {
  BORDER_SPROUT_VINE:
    "border-[#4c8a56] shadow-[0_0_0_3px_#d8ebcf,0_10px_28px_rgba(49,101,57,.2)]",
  BORDER_BLOOM_GARDEN:
    "border-[#b76ba1] shadow-[0_0_0_3px_#f3d7eb,0_0_22px_rgba(183,107,161,.28)]",
  BORDER_GOLDEN_HARVEST:
    "border-[#d3a629] shadow-[0_0_0_2px_#fff0a5,0_0_0_5px_#9d7014,0_0_25px_rgba(238,188,43,.35)]",
};

export default function CardCosmeticFrame({
  borderCode,
  children,
  className = "",
}: {
  borderCode?: string | null;
  children: ReactNode;
  className?: string;
}) {
  const style = borderCode
    ? `border-[3px] p-[3px] ${FRAME_STYLE[borderCode]}`
    : "border-0 p-0";
  return (
    <div
      data-cosmetic-border={borderCode ?? undefined}
      className={`relative rounded-[inherit] ${style} ${className}`}
    >
      {borderCode === "BORDER_SPROUT_VINE" ? (
        <span className="pointer-events-none absolute -left-1.5 -top-1.5 z-20 h-7 w-7 rotate-[-18deg]">
          <span className="absolute left-1 top-3 h-2.5 w-5 rotate-[-30deg] rounded-[100%_0_100%_0] bg-[#75a95d] shadow-sm" />
          <span className="absolute left-3 top-1 h-2.5 w-5 rotate-[24deg] rounded-[100%_0_100%_0] bg-[#98c477] shadow-sm" />
        </span>
      ) : null}
      {borderCode === "BORDER_BLOOM_GARDEN" ? (
        <>
          <span className="pointer-events-none absolute -left-1.5 -top-1.5 z-20 h-6 w-6 rounded-full bg-[#d88dbf] shadow-[6px_0_0_#b879c2,3px_5px_0_#efb6d7]" />
          <span className="pointer-events-none absolute -bottom-1 -right-1 z-20 h-4 w-4 rounded-full bg-[#c77db4] shadow-[-5px_0_0_#eaa9cd,-2px_-4px_0_#a978bf]" />
        </>
      ) : null}
      {borderCode === "BORDER_GOLDEN_HARVEST" ? (
        <>
          <span className="pointer-events-none absolute inset-0 z-10 rounded-[inherit] bg-[linear-gradient(115deg,transparent_20%,rgba(255,255,255,.38)_42%,transparent_64%)] opacity-60" />
          <span className="pointer-events-none absolute right-2 top-3 z-20 h-1.5 w-1.5 rotate-45 bg-[#fff0a5] shadow-[0_0_8px_#ffe36d]" />
          <span className="pointer-events-none absolute bottom-5 left-1 z-20 h-1 w-1 rotate-45 bg-white shadow-[0_0_7px_#ffe36d]" />
        </>
      ) : null}
      <div className="relative aspect-[1122/1402] h-full w-full overflow-hidden rounded-[18px]">
        {children}
      </div>
    </div>
  );
}
