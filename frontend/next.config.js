/** @type {import('next').NextConfig} */
const nextConfig = {
  images: {
    remotePatterns: [
      // 정적 상거래 자산 S3. CloudFront로 옮기면 hostname만 바꾸면 된다.
      {
        protocol: "https",
        hostname:
          "4team-storage-495264909330-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com",
        pathname: "/cards/**",
      },
      {
        protocol: "https",
        hostname:
          "4team-storage-495264909330-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com",
        pathname: "/products/**",
      },
      {
        protocol: "https",
        hostname:
          "4team-storage-495264909330-ap-northeast-2-an.s3.ap-northeast-2.amazonaws.com",
        pathname: "/coupons/**",
      },
    ],
  },
};
module.exports = nextConfig;
