// 시나리오 4-1: 카드 마켓 조회 부하 테스트 (읽기 전용, 인증 불필요)
// 목적: 조회 트래픽에 대한 응답시간/처리량 확인
// 실행: k6 run k6/market_browse.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL } from './lib/config.js';

export const options = {
  scenarios: {
    constant_market_browse: {
      executor: 'constant-vus',
      vus: 50,
      duration: '3m',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<300'],
    http_req_failed: ['rate<0.01'],
  },
};

export default function () {
  const res = http.get(
    `${BASE_URL}/api/v1/card/market/listings?page=0&size=20&sort=createdAt,DESC`,
  );

  check(res, { 'status is 200': (r) => r.status === 200 });

  sleep(1);
}
