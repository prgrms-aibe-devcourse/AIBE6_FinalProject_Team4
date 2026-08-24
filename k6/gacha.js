// 시나리오 3: 가챠 뽑기 부하 테스트
// 목적: 이벤트성 트래픽 몰림 시 포인트 차감 동시성 처리 확인
// 실행: k6 run -e TEST_ACCOUNTS="a@x.com:pw1,b@x.com:pw2" -e GACHA_PRODUCT_ID=... k6/gacha.js
import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from './lib/config.js';
import { login, loginPool } from './lib/auth.js';

export const options = {
  scenarios: {
    gacha_spike: {
      executor: 'ramping-vus',
      startVUs: 0,
      stages: [
        { duration: '10s', target: 200 }, // 짧은 시간 내 급격한 몰림
        { duration: '30s', target: 200 },
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
const PRODUCT_ID = Number(__ENV.GACHA_PRODUCT_ID || 1);

export function setup() {
  // TODO: 더미 데이터 세팅 완료 후 포인트 잔액이 넉넉한 계정들로 TEST_ACCOUNTS 채우기
  const accountsCsv = __ENV.TEST_ACCOUNTS || `${EMAIL}:${PASSWORD}`;
  return { tokens: loginPool(accountsCsv) };
}

export default function (data) {
  const token = data.tokens[__VU % data.tokens.length];

  const payload = JSON.stringify({
    productId: PRODUCT_ID,
    // 컨트롤러 쪽 @Max(1) 제약으로 1 고정
    quantity: 1,
  });

  const res = http.post(`${BASE_URL}/api/v1/card/gacha/purchases`, payload, {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      // sleep 없이 초당 수백 회 도는 spike 시나리오라 Date.now()만으로는 같은 밀리초에 충돌할 수
      // 있어 난수를 더한다.
      'Idempotency-Key': `${Date.now()}-${__VU}-${__ITER}-${Math.random()}`,
    },
  });

  check(res, {
    'status is 200/201': (r) => r.status === 200 || r.status === 201,
  });
  check(res, {
    // 422 POINT_INSUFFICIENT_BALANCE는 서버 오류가 아니라 테스트 계정 잔액 소진에 따른 정상
    // 비즈니스 거절이므로 별도 체크로 분리해서 관찰한다 (위 체크는 실패로 잡히는 게 맞음 — 실행
    // 결과 요약에서 이 체크와 함께 보고 실제 장애인지 잔액 소진인지 구분한다).
    'status is 200/201/422(insufficient balance)': (r) =>
      r.status === 200 || r.status === 201 || r.status === 422,
  });
}
