import http from 'k6/http';
import { check } from 'k6';
import { BASE_URL } from './config.js';

export function login(email, password) {
  const res = http.post(
    `${BASE_URL}/api/v1/auth/login`,
    JSON.stringify({ email, password }),
    { headers: { 'Content-Type': 'application/json' } },
  );

  check(res, { 'login succeeded': (r) => r.status === 200 });

  return res.json('data.accessToken');
}

// "email:password,email2:password2" 형태의 TEST_ACCOUNTS 환경변수를 로그인해 토큰 배열로 반환.
// 계정을 여러 개 쓰는 이유: 가챠/마켓 구매처럼 포인트·매물을 소모하는 테스트를 단일 계정으로 돌리면
// 몇 번 안 가 잔액/재고가 바닥나 실패율이 서버 성능이 아니라 데이터 부족 때문에 튄다.
export function loginPool(accountsCsv) {
  return accountsCsv
    .split(',')
    .map((pair) => pair.split(':'))
    .map(([email, password]) => login(email, password));
}
