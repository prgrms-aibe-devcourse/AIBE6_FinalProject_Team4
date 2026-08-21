// 실제 배포 EC2(t3.small)에는 절대 부하를 걸지 않는다 — 로컬 백엔드만 대상으로 한다.
export const BASE_URL = __ENV.BASE_URL || 'http://localhost:8080';
