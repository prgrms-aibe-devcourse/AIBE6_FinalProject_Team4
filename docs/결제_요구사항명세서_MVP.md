## 결제 `payment`

### 도메인 설명
현금 결제를 통한 포인트 충전과 환불을 처리합니다. 사용자는 정액 충전 상품(ChargeProduct)을 선택해 결제하며, 결제 로직은 특정 결제사에 종속되지 않도록 `PaymentProvider` 인터페이스로 추상화합니다. 현재 구현체는 `MockPaymentProvider`이며, 결제 승인이 성공하면 `point` 도메인을 호출해 유상 포인트를 적립하고 환불 시 유상 포인트를 차감합니다. **실결제는 발생하지 않으며 테스트 모드로 동작합니다.**

> **다른 도메인과의 관계 (단방향 의존):** payment는 point에만 의존한다.
> - 충전 승인 성공 → point 유상 적립 호출 (POINT-05 credit)
> - 환불 완료 → point 유상 차감 호출 (POINT-06 refund-deduct)
> - point는 payment에 역으로 의존하지 않는다.

> **결제 연동:** 개발·자동테스트는 `MockPaymentProvider`(성공/실패/취소 강제)를 사용합니다.
> `TOSS` enum은 향후 실제 PG 구현을 위한 예약 값이며 현재 `TossProvider` 구현은 없습니다.
> API 중복 요청 방지는 공통 `idempotency_keys` 테이블을 사용합니다.

### 주요 Entity (MVP 확정)

| Entity | 설명 | 주요 속성 |
| --- | --- | --- |
| ChargeProduct | 정액 충전 상품 | id, name, price, pointAmount, isActive, version |
| Payment | 충전(결제) 이력 | id, userId, chargeProductId, chargeProductName, cashAmount, pointAmount, status, provider, providerOrderId, paymentKey, approvedAt, createdAt, updatedAt |
| PaymentRefund | 결제 건별 전액 환불 이력 | id, paymentId, cashAmount, pointAmount, status, reason, refundKey, createdAt, completedAt |
| PaymentRefundAttempt | PG 환불 호출 전 별도 커밋하는 감사·대조 기록 | id, paymentId, userId, cashAmount, pointAmount, status, reason, startedAt, settledAt |

- `payment.status`: PENDING / PAID / FAILED / CANCELED / REFUNDED
- `payment.provider`: MOCK / TOSS
- `paymentRefund.status`: REQUESTED / COMPLETED / FAILED
- `paymentRefundAttempt.status`: STARTED / SETTLED
- `providerOrderId`: 우리가 PG에 넘기는 주문번호(webhook·대조용) · `paymentKey`: PG가 승인 후 주는 결제키
- `payment_refund_attempts.payment_id`, `user_id`는 논리 참조이며, 별도 트랜잭션 기록의 생존을 위해
  물리 FK를 두지 않습니다.

---

### 충전 상품 목록 조회
> PAY-01

1. 사용자 행위
    - 사용자가 포인트 결제 페이지에서 충전 가능한 상품 목록을 조회합니다.
2. 시스템 처리
    - 판매 중(isActive=true)인 정액 충전 상품(가격·지급 포인트)만 반환합니다.
3. 처리 결과
    - **`성공`**
        - 충전 상품 목록이 조회됩니다.
    - **`실패`**
        - 실패 사유를 제공합니다.
            - 인증되지 않은 요청

---

### 포인트 충전 요청
> PAY-02 · 결제 시작(Payment PENDING 생성)

1. 사용자 행위
    - 사용자가 정액 충전 상품을 선택해 결제를 요청합니다.
2. 시스템 처리
    - 선택한 상품의 존재·판매 여부(isActive)를 검증합니다.
    - **결제 금액(cashAmount)·지급 포인트(pointAmount)를 서버가 상품 정보에서 확정**합니다. (클라이언트 값 불신)
    - 결제 당시 상품명을 `chargeProductName`으로 복사해 이후 관리자 상품명 수정과 무관하게 과거 내역을 보존합니다.
    - 우리 주문번호(providerOrderId)를 생성하고 Payment를 `PENDING`으로 저장합니다.
    - 현재는 `MockPaymentProvider`를 통해 결제 요청을 진행합니다.
    - 공통 `idempotency_keys`로 동일 요청의 중복 생성을 방지합니다.
3. 처리 결과
    - **`성공`**
        - Payment가 `PENDING`으로 생성되고 결제 진행 단계로 넘어갑니다.
    - **`실패`**
        - 결제가 시작되지 않습니다.
        - 실패 사유를 제공합니다.
            - 없거나 판매 중지된 충전 상품
            - 유효하지 않은 요청
            - 중복 요청

---

### 결제 승인 확정
> PAY-03 · 승인 후 point 유상 적립

1. 사용자 행위
    - 사용자가 결제창에서 결제를 완료하고 돌아옵니다. (Mock은 성공/실패/취소 시뮬레이션)
2. 시스템 처리
    - providerOrderId·paymentKey와 **승인 금액이 우리 결제 금액과 일치하는지** 검증합니다. (금액 조작 방지)
    - `PaymentProvider`로 승인(confirm)을 확정합니다. 승인 결과를 정본으로 삼습니다.
    - 승인 성공 시 Payment를 `PAID`로 갱신(approvedAt·paymentKey 저장)하고, **point 적립 API(POINT-05 credit)를 호출해 유상 포인트를 적립**합니다.
    - Payment 갱신 + 포인트 적립을 단일 트랜잭션으로 처리합니다.
    - 동일 결제 건의 중복 승인을 방지합니다.
3. 처리 결과
    - **`성공`**
        - Payment가 `PAID`가 되고 유상 포인트가 충전됩니다.
    - **`실패`**
        - 포인트가 적립되지 않고 트랜잭션이 롤백됩니다.
        - Payment 상태가 `FAILED` 또는 `CANCELED`로 기록됩니다.
        - 실패 사유를 제공합니다.
            - 결제 승인 실패
            - 승인 금액 불일치
            - 결제 취소
            - 이미 처리된 결제

---

### 환불
> PAY-04 · payment_refund 생성 + point 유상 차감

- API: `POST /api/v1/payments/{paymentId}/refund`
- 필수 헤더: `Idempotency-Key`(1~64자)
- 요청: `{ "reason": "환불 사유" }`(공백 제외 필수, 최대 200자)
- 성공 응답: 환불 ID·결제 ID·원결제 `cashAmount`·`pointAmount`·상태·사유·PG 환불키·처리 시각

1. 사용자 행위
    - 사용자가 결제 내역에서 충전 취소(환불)를 요청합니다.
2. 시스템 처리
    - 부분 환불과 다중 환불은 지원하지 않으며, 대상 결제가 `PAID`일 때 원결제 전체만 환불합니다.
    - **payment 행을 잠근 뒤(SELECT ... FOR UPDATE) 멱등키를 검증**해 동일 결제의 동시 환불을
      직렬화합니다.
    - 환불 `cashAmount`와 회수 `pointAmount`는 각각 원결제의 `cashAmount`, `pointAmount` 전액으로
      서버에서 확정합니다.
    - **현재 유상 포인트 잔액(paidPoint)이 원결제 pointAmount 이상인지 검증**합니다. 충전 후 유상
      포인트를 사용해 전액 회수할 수 없으면 환불하지 않으며, 무상 포인트는 회수하거나 환불하지 않습니다.
    - PaymentRefund를 원결제 금액 전체로 `REQUESTED` 생성합니다.
    - **point 환불 차감 API(POINT-06 refund-deduct)를 호출해 유상 포인트만 차감**합니다. (무상 제외)
    - PG 호출 직전에 `PaymentRefundAttempt.STARTED`를 별도 `REQUIRES_NEW` 트랜잭션으로 커밋합니다.
      이후 본 환불 트랜잭션이 롤백되어도 이 시도 기록은 남아 PG 대조 근거가 됩니다.
    - 이전 `STARTED` 시도가 남아 있으면 PG 결과를 확인하기 전까지 자동 재환불을 거절합니다.
    - `PaymentProvider`로 환불을 처리하고 성공 시 `COMPLETED`(completedAt) 처리합니다.
    - Payment.status를 `PAID → REFUNDED`로 조건부 변경합니다.
    - PaymentRefund 완료, 유상 포인트 회수·원장, Payment 상태 변경,
      PaymentRefundAttempt `STARTED → SETTLED`는 본 환불 트랜잭션에서 함께 확정합니다.
    - `STARTED` 생성만 별도 트랜잭션이고 나머지 내부 변경은 본 환불 트랜잭션입니다.
    - 동일 멱등키·동일 요청은 최초 200 응답을 재사용하고, 동일 키에 다른 결제 ID 또는 사유가 오면
      `409 COMMON_IDEMPOTENCY_CONFLICT`로 거절합니다.
    - 결제·환불 멱등키와 최초 결과는 7일간 보관합니다. 7일은 환불 가능 기간이 아니라 네트워크 단절이나
      PG 응답 지연 후 동일 요청을 안전하게 재시도하기 위한 결과 보관 기간입니다.
    - 멱등 결과가 만료된 뒤에도 `PAID → REFUNDED` 상태 조건으로 중복 환불을 차단합니다.
3. 처리 결과
    - **`성공`**
        - 환불이 처리되고 유상 포인트가 차감되며 Payment 상태가 갱신됩니다.
    - **`실패`**
        - PG 호출 전에 실패하면 환불·포인트 내부 변경이 롤백되고 시도 기록도 생성되지 않습니다.
        - PG 호출 후 실패하거나 결과가 불명확하면 내부 변경은 롤백되지만 `STARTED` 시도 기록은
          유지되어 자동 재환불을 차단하고 운영 대조 대상으로 남습니다.
        - 실패 사유를 제공합니다.
            - 원결제 포인트 전액을 회수할 수 없는 유상 포인트 잔액
            - 결제 건 없음
            - 환불 불가 상태 (이미 취소·환불되었거나 결제 완료 전)
            - PG 환불 거절

> 이 환불은 **현금 충전분의 취소**입니다. 상점 주문의 포인트 원복(취소)은 `order` 도메인 소관으로 point 원복 API(POINT-04)를 사용하며 본 환불과 별개입니다.
>
> 현재 `STARTED/SETTLED`만 구현되어 명확한 PG 거절도 `STARTED`로 남습니다. 명확한 실패
> `FAILED`와 결과 불명 `UNKNOWN`, 자동 대조·복구는 후속 리팩토링 범위입니다.

---

### 결제 / 환불 내역 조회
> PAY-05

1. 사용자 행위
    - 사용자가 마이페이지에서 자신의 결제·환불 내역을 조회합니다.
2. 시스템 처리
    - 본인 Payment 목록과 각 결제에 연결된 PaymentRefund 이력을 반환합니다.
3. 처리 결과
    - **`성공`**
        - 결제·환불 내역이 조회됩니다.
    - **`실패`**
        - 실패 사유를 제공합니다.
            - 인증되지 않은 요청

---

### 충전 상품 관리 (관리자)
> PAY-06(등록) / PAY-07(수정) / PAY-08(판매 중지) / PAY-11(전체 목록)

1. 사용자 행위
    - 관리자가 정액 충전 상품을 조회/등록/수정/판매 중지합니다.
2. 시스템 처리
    - 관리자 권한(Role: ADMIN)을 검증합니다.
    - 등록·수정: 지급 포인트는 결제 금액의 100% 이상 150% 이하만 허용합니다.
    - 등록: 상품명·가격·지급 포인트를 입력받아 생성합니다. `Idempotency-Key`는 필수이며,
      같은 관리자·키·요청은 최초 `201` 응답을 재사용하고 다른 요청은 `409`로 거부합니다.
    - 전체 목록: 활성·비활성 상품을 가격·ID 오름차순으로 조회합니다.
    - 수정: 상품명·가격·지급 포인트·판매 여부(isActive)와 조회한 `version`을 함께 전송합니다.
      서버는 JPA 낙관적 락으로 오래된 화면의 덮어쓰기를 `409 COMMON_OPTIMISTIC_LOCK_CONFLICT`로 거부합니다.
    - 삭제: 구매 이력 보존을 위해 소프트 삭제(isActive=false)로 처리합니다.
3. 처리 결과
    - **`성공`**
        - 상품이 등록/수정/삭제(비활성)됩니다.
    - **`실패`**
        - 실패 사유를 제공합니다.
            - 권한 없음
            - 유효하지 않은 값(가격·포인트 등)
            - 등록 멱등키 누락·진행 중·요청 충돌
            - 오래된 version 또는 동시 수정 충돌

---

### (선택) 결제 상태 Webhook 수신
> PAY-10 · 카드+Mock MVP면 생략 가능

1. 사용자 행위
    - 별도 행위 없이, 가상계좌 입금·비동기 취소 등 상태 변경 시 PG가 웹훅을 보냅니다.
2. 시스템 처리
    - 웹훅 전송 ID로 중복 수신을 방지하고, `paymentKey`·`orderId`·금액을 내부 결제와 대조합니다.
    - 결제 웹훅 본문만으로 포인트를 변경하지 않고 Toss 결제 조회로 현재 상태를 재검증한 뒤
      조건부 상태 전이를 수행합니다.
    - 공식 서명 헤더는 모든 결제 웹훅에 공통 제공되지 않으므로 결제 웹훅에 서명이 항상 존재한다고
      가정하지 않습니다.
3. 처리 결과
    - **`성공`**
        - 결제 상태가 최신으로 반영됩니다.
    - **`실패`**
        - 처리되지 않습니다(로그로 관리).
        - 실패 사유: 웹훅 전송 ID 중복, 결제 식별자·금액 불일치, Toss 조회 검증 실패,
          유효하지 않은 payload

> 카드 정액 충전만 지원하는 MVP에서는 생략 가능. 가상계좌/이체를 도입할 때 함께 구현.

---

### Toss Payments API 도입

> 상세 개발 순서와 복구·테스트 계약은
> [`Toss_Payments_API_도입_개발계획.md`](Toss_Payments_API_도입_개발계획.md)를 정본으로 사용합니다.

- 프론트는 Toss Payments SDK V2로 결제 인증을 진행하고, 백엔드는 서버에 저장한 주문번호와 금액을
  대조한 뒤 `POST /v1/payments/confirm`으로 승인합니다.
- 승인·환불 결과가 불명확하면 `GET /v1/payments/{paymentKey}` 또는
  `GET /v1/payments/orders/{orderId}`로 조회해 복구합니다.
- 전액 환불은 `POST /v1/payments/{paymentKey}/cancel`에 `cancelReason`만 보내고
  `cancelAmount`를 생략합니다.
- Toss POST 요청에는 서버가 생성·저장한 별도 `Idempotency-Key`를 사용합니다. 내부 API 멱등키
  7일 보관과 Toss 멱등키 15일 유효 정책은 서로 독립적으로 관리합니다.
- Toss 시크릿 키는 백엔드 시크릿으로만 관리하고 프론트·저장소·로그에 노출하지 않습니다.
- 외부 승인·취소 호출은 DB 트랜잭션 밖에서 실행하고, 호출 전 작업 선점과 호출 후 결제·포인트 확정은
  각각 짧은 트랜잭션으로 처리합니다.

---

### 확정된 정책 (2026-07-31 MVP 기준)

| # | 항목 | 결정 |
| --- | --- | --- |
| 1 | 결제 방식 | PaymentProvider 추상화, 현재 Mock 구현만 제공, 실결제 미발생(테스트) |
| 2 | 충전 단위 | 정액 상품(ChargeProduct) — 자유 금액 미사용 |
| 3 | 금액 신뢰 | cashAmount·pointAmount는 서버가 상품 기준 확정(클라이언트 불신) |
| 4 | 승인 정본 | confirm 결과 기준 + 승인 금액 일치 검증 |
| 5 | 환불 | `PAID` 결제 건별 1회 전액 환불, 원결제 포인트만큼 유상 포인트 전액 회수, 무상 포인트 제외 |
| 6 | 멱등 | 공통 idempotency_keys 테이블(API 레벨), 일반 24시간·결제/환불 7일 결과 보관 |
| 7 | provider 기록 | MOCK/TOSS 구분 저장 (개발/운영 DB 미분리라 유지) |

### 미결정 사항

| # | 항목 | 내용 |
| --- | --- | --- |
| 1 | 정액 상품 구성 | 금액대·개수 (예: 1,000/5,000/10,000P) |
| 2 | Webhook 도입 | 가상계좌/이체 지원 시 필요 (카드 MVP는 생략) |
| 3 | 환불 기간 정책 | 환불 가능 기간 제한 둘지 여부 (현재 없음) |
| 4 | 실제 PG 환불 | Toss 등 실제 Provider 승인·환불 및 복구 계약 |

### 구현·검증 상태 (2026-07-31)

- 백엔드 환불 API, 유상 포인트 전액 회수, Mock Provider 환불, 조건부 상태 전이 구현 완료
- PG 호출 전 `payment_refund_attempts.STARTED` 별도 커밋과 미확정 시도 재환불 차단 구현 완료
- 결제 내역의 전액 환불 UI와 성공 후 지갑 재조회 연결 완료
- 단위·컨트롤러 테스트와 MySQL 8 동시 환불 통합 테스트 완료
- 결제·환불 멱등 결과 7일 보관 및 만료 후 상태 조건 중복 차단 반영
- 실제 Toss Provider, 환불 시도 `FAILED/UNKNOWN`, 자동 대조·복구와 환불 가능 기간 정책은
  미구현·미확정
