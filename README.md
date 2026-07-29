# 키워볼래 🌱

식물을 키우고 기록하면 포인트 보상을 받고, 그 포인트로 카드를 모아 실제 농작물로 교환하는
식물 재배 플랫폼입니다.

## 프로젝트 개요

- **핵심 루프**: 식물 등록 → 매일 성장 일지 작성(사진) → 포인트 보상 → 포인트로 상품/카드 구매
  → 카드를 모아 실물 농작물로 교환
- **부가 기능**: 회원가입/로그인, 마이페이지(프로필 수정, 배송지, 주문/포인트/카드 내역), 알림,
  1:1 문의, 신고, 결제/충전, 관리자 콘솔
- **서버 구성**: 단일 서버, 단일 MySQL 8 (운영은 AWS RDS)
- **저장소 구성**: `frontend/`(Next.js)와 `backend/`(Spring Boot)를 분리한 단일 모노레포

## 기술 스택

| 구분 | 스택 |
| --- | --- |
| 프론트엔드 | Next.js (App Router), TypeScript, React, Tailwind CSS |
| 백엔드 | Java 21, Spring Boot, Spring MVC, Spring Data JPA |
| 데이터베이스 | MySQL 8 |
| 인증 | JWT (Access Token) + httpOnly 쿠키 기반 Refresh Token |
| API 문서 | springdoc-openapi 기반 Swagger UI |
| 백엔드 빌드 | Gradle Wrapper |
| CI | GitHub Actions |

## 도메인 구성

| 도메인 | 책임 |
| --- | --- |
| auth / mypage / notification | 회원·인증·배송지·알림 |
| content (farm) | 식물 종·프로필·성장 일지·완료 기록 |
| 문의/신고 | 문의와 신고 처리 |
| commerce | 상품, 카드, 장바구니/주문, 실물 교환 |
| point / payment | 지갑·포인트 원장·충전·결제·환불 |
| common infrastructure | 멱등성 요청 관리 |

각 도메인은 화면이 아니라 데이터 소유권 기준으로 나뉘며, 다른 도메인의 테이블/Repository를
직접 갱신하지 않는 것을 원칙으로 합니다.

## 시작하기

- 백엔드 실행 방법: [`backend/README.md`](backend/README.md)
- 프론트엔드 실행 방법: [`frontend/README.md`](frontend/README.md)

## 문서

- 팀 공용 작업 지침(도메인 경계, API 컨벤션, 상태 모델, 보안 규칙 등): [`docs/AGENTS.md`](docs/AGENTS.md)
- ERD: [`docs/erd.dbml`](docs/erd.dbml) ([ERDCloud](https://www.erdcloud.com/d/qKJnD4xtHCCrPbtZs))
- 오류 코드 명세: [`docs/error-codes.md`](docs/error-codes.md)
- API 명세(정본): [Google Sheets — 키워볼래 API 명세](https://docs.google.com/spreadsheets/d/1u96yusnCEqrfxiIrY_oib-JuulkCIV6BwpT5cKBZwiA/edit?gid=0#gid=0)

## 저장소

[prgrms-aibe-devcourse/AIBE6_FinalProject_Team4](https://github.com/prgrms-aibe-devcourse/AIBE6_FinalProject_Team4)
