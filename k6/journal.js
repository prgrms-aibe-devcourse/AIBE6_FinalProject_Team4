// 시나리오 2: 성장 일지 작성 부하 테스트
// 목적: 쓰기 요청의 처리 성능 확인 (images는 URL 참조 방식 — 실제 파일 업로드는 별도 S3 업로드 API가 처리)
// 실행: k6 run -e TEST_EMAIL=... -e TEST_PASSWORD=... -e PLANT_PROFILE_ID=... k6/journal.js
import http from 'k6/http';
import { check, sleep } from 'k6';
import { BASE_URL } from './lib/config.js';
import { login } from './lib/auth.js';

export const options = {
  scenarios: {
    constant_journal_write: {
      executor: 'constant-vus',
      vus: 30,
      duration: '3m',
    },
  },
  thresholds: {
    http_req_duration: ['p(95)<1000'],
    http_req_failed: ['rate<0.01'],
  },
};

const EMAIL = __ENV.TEST_EMAIL || 'loadtest@example.com';
const PASSWORD = __ENV.TEST_PASSWORD || 'password123!';
const PLANT_PROFILE_ID = Number(__ENV.PLANT_PROFILE_ID || 1);

// TODO: 더미 데이터 세팅 완료 후 실제 S3에 존재하는 이미지 URL/해시로 교체
const SAMPLE_IMAGE = {
  imageUrl:
    __ENV.SAMPLE_IMAGE_URL ||
    'https://example-bucket.s3.ap-northeast-2.amazonaws.com/journals/sample.jpg',
  imageHash: __ENV.SAMPLE_IMAGE_HASH || 'sample-hash-0001',
  representative: true,
};

export function setup() {
  return { accessToken: login(EMAIL, PASSWORD) };
}

export default function (data) {
  const payload = JSON.stringify({
    plantProfileId: PLANT_PROFILE_ID,
    content: `k6 load test journal ${Date.now()}-${__VU}-${__ITER}`,
    images: [SAMPLE_IMAGE],
  });

  const res = http.post(`${BASE_URL}/api/v1/journals`, payload, {
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${data.accessToken}`,
    },
  });

  check(res, { 'status is 201': (r) => r.status === 201 });

  sleep(1);
}
