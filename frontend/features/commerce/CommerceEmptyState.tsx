export default function CommerceEmptyState({ text }: { text: string }) {
  return (
    <div className="rounded-2xl border border-dashed border-[#cfd8ca] bg-white/60 px-5 py-12 text-center text-sm font-bold text-[#7a8476]">
      {text}
    </div>
  );
}
