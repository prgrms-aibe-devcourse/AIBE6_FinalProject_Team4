/** @type {import('next').NextConfig} */
const nextConfig = {
  images: {
    remotePatterns: [
      // 카드 일러스트가 저장된 S3 버킷(cards/ 프리픽스만 퍼블릭). CloudFront로 옮기면 이 hostname만 바꾸면 된다.
      {
        protocol: "https",
        hostname:
          "4team-storage-495264909330-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com",
        pathname: "/cards/**",
      },
    ],
  },
};
module.exports = nextConfig;
