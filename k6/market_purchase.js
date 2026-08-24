// 시나리오 4-2: 카드 마켓 구매 부하 테스트
// 목적: 동일 매물 동시 구매 시 경합(race condition) 처리 확인
// 실행: k6 run -e TEST_ACCOUNTS="a@x.com:pw1,b@x.com:pw2" -e LISTING_IDS="1,2,3" k6/market_purchase.js
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from './lib/config.js';
import { login, loginPool } from './lib/auth.js';

export const options = {
  scenarios: {
    market_purchase_spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 100 },
        { duration: '20s', target: 100 },
        { duration: '10s', target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<800'],
  },
};

const EMAIL = __ENV.TEST_EMAIL || 'loadtest@example.com';
const PASSWORD = __ENV.TEST_PASSWORD || 'password123!';

export function setup() {
  const accountsCsv = __ENV.TEST_ACCOUNTS || `${EMAIL}:${PASSWORD}`;
  const tokens = loginPool(accountsCsv);

  // TODO: 더미 데이터 세팅 완료 후 실제 판매 중인 listingId로 교체.
  // 매물은 구매되는 즉시 소진되므로 동시 요청 수보다 넉넉하게 준비해야 함.
  const listingIds = (__ENV.LISTING_IDS || '1,2,3').split(',').map(Number);

  return { tokens, listingIds };
}

export default function (data) {
  const token = data.tokens[__VU % data.tokens.length];
  const listingId = data.listingIds[(__VU + __ITER) % data.listingIds.length];

  const res = http.post(
    `${BASE_URL}/api/v1/card/market/listings/${listingId}/purchases`,
    null,
    {
      headers: {
        Authorization: `Bearer ${token}`,
        'Idempotency-Key': `${Date.now()}-${__VU}-${__ITER}-${Math.random()}`,
      },
    },
  );

  check(res, {
    // 409(이미 팔린 매물)는 경합에서 진 정상 케이스 — 서버 오류로 취급하지 않음
    'status is 200/201/409': (r) => [200, 201, 409].includes(r.status),
  });
}
