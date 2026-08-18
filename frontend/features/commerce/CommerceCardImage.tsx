type CommerceCardImageProps = {
  src: string | null;
  name: string;
  className?: string;
};

export default function CommerceCardImage({
  src,
  name,
  className = "h-full w-full object-contain",
}: CommerceCardImageProps) {
  return src ? (
    // S3/CDN 원본 비율을 유지해야 하므로 Next 이미지 최적화 대신 원본 URL을 사용한다.
    // eslint-disable-next-line @next/next/no-img-element
    <img src={src} alt={name} className={className} />
  ) : (
    <div className="flex h-full items-center justify-center bg-[#e8ece4] text-[#788173]">
      <span className="material-symbols-outlined text-4xl">playing_cards</span>
    </div>
  );
}
