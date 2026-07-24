# 키워볼래 🌱 — Next.js (App Router)

식물을 키우고, 매일 기록하고, 카드를 모아 실물로 교환하는 서비스의 프론트엔드 프로토타입입니다.
HTML 프로토타입(`.dc.html`)을 Next.js App Router 구조로 이식했습니다.

## 실행

```bash
cd nextjs
npm install
npm run dev
# http://localhost:3000
```

> ⚠️ 이 코드는 프론트엔드 UI/UX 프로토타입입니다. 실제 API·DB·인증·결제는 연결돼 있지 않고,
> 상태는 브라우저 `localStorage`에 저장됩니다. 백엔드 연동 시 `lib/store.jsx`와 각 페이지의
> 목(mock) 데이터(`lib/data.js`, 각 페이지 상단의 `INITIAL` 상수)를 API 호출로 교체하세요.

## 기술 스택
- **Next.js 14 (App Router)** · React 18
- 스타일: **Tailwind CSS v3** — 디자인 토큰은 `tailwind.config.js`의 `theme.extend`에 정의
  (`bg-brand`, `text-ink`, `border-line`, `shadow-card` 등). 데이터에 들어있는 그라디언트
  (`grad` 필드)만 인라인 `style`로 남아 있어요.
- 전역 상태: **React Context**(`lib/store.jsx`) + localStorage 영속화
- 폰트: Pretendard (CDN) · 아이콘: Google Material Symbols Outlined (CDN)
- 확장자 규칙: JSX를 포함한 파일은 `.jsx`, 그 외(`lib/data.js`, `lib/theme.js`, 설정 파일)는 `.js`

## 폴더 구조

```
nextjs/
├─ app/
│  ├─ layout.jsx                         # 루트 레이아웃(Store/UI Provider + Navbar)
│  ├─ globals.css                        # Tailwind 지시어 + base/components 레이어
│  ├─ page.jsx                           # / 홈 대시보드
│  ├─ plants/
│  │  ├─ page.jsx                        # /plants 목록 + 등록 모달
│  │  └─ [id]/page.jsx                   # /plants/:id 상세·수정·보관
│  ├─ journals/
│  │  ├─ page.jsx                        # /journals 목록(마소너리)
│  │  ├─ new/page.jsx                    # /journals/new ⭐ 보상 판정 엔진
│  │  └─ [id]/page.jsx                   # /journals/:id 상세·수정·삭제·신고
│  ├─ shop/
│  │  ├─ page.jsx                        # /shop 상품 목록(키트·모종 필터·정렬)
│  │  └─ [id]/page.jsx                   # /shop/:id 상품 상세
│  ├─ cards/
│  │  ├─ page.jsx                        # /cards 카드 목록(수집중·교환가능 필터·정렬)
│  │  └─ [id]/page.jsx                   # /cards/:id ⭐ 카드 구매·축하
│  ├─ cart/page.jsx                      # /cart 장바구니
│  ├─ checkout/page.jsx                  # /checkout 주문·결제
│  ├─ exchange/new/page.jsx              # /exchange/new ⭐ 실물 교환 신청
│  ├─ notifications/page.jsx             # /notifications 알림
│  ├─ admin/page.jsx                     # /admin 관리자 콘솔(선택 기능)
│  └─ my/
│     ├─ page.jsx                        # /my 마이페이지·배송지
│     ├─ orders/page.jsx                 # /my/orders 주문 내역
│     ├─ cards/page.jsx                  # /my/cards 내 카드·구매이력
│     ├─ exchanges/page.jsx              # /my/exchanges 교환 내역·상태 스텝퍼
│     ├─ inquiries/page.jsx              # /my/inquiries 1:1 문의 + 신고
│     ├─ points/
│     │  ├─ page.jsx                     # /my/points 포인트 홈·내역
│     │  ├─ charge/page.jsx              # /my/points/charge 충전(모의결제)
│     │  └─ payments/page.jsx            # /my/points/payments 결제·환불
│     └─ settings/notifications/page.jsx # /my/settings/notifications 알림 설정
├─ components/
│  ├─ Navbar.jsx                         # 상단바(장바구니·포인트·알림) + 모바일 하단 탭바
│  ├─ Footer.jsx                         # 데스크톱 푸터
│  ├─ FilterBar.jsx                      # /shop · /cards 공용 필터 탭 + 정렬 칩
│  └─ PointPrice.jsx                     # 노란 P 배지 + 금액 표기
├─ tailwind.config.js                    # 디자인 토큰·키프레임 정의
├─ postcss.config.js
└─ lib/
   ├─ store.jsx                          # 전역 상태(지갑·카운터) Context
   ├─ ui.jsx                             # 공용 토스트 + 확인 다이얼로그
   ├─ theme.js                           # 그라디언트 프리셋(grads) — 데이터에서 참조
   └─ data.js                            # 공용 샘플 데이터
```

## 도메인 간 실제 연동 (핵심)
`lib/store.js`의 단일 지갑(무상 `free` + 충전 `paid`)과 카운터를 모든 페이지가 공유합니다.
- 일지 보상(+30) → 잔액·"오늘 기록" 갱신, 당일 삭제 시 회수
- 상점/카드 구매 → 무상 포인트 먼저 차감
- 충전/환불 → 잔액 반영
- 식물 등록/보관 → 재배중 수 갱신 / 카드 완성·교환 → 교환가능 수 갱신
- 상단바 포인트 칩은 항상 실시간 잔액 표시. `↺` 버튼으로 데모 초기화.

## MVP 우선순위 (⭐)
1. **일지 작성 보상** — `app/journals/new/page.jsx`
   보상 판정: 이미 오늘 받음 → 중립 / 최근 30일 중복 사진(🔁) → 중립 / 신규 사진 → +30P 지급.
2. **실물 교환** — `app/cards/[id]` → `app/exchange/new` → `app/my/exchanges`
   카드 구매→진행률→완성 축하→신청→상태 스텝퍼→취소(복원).

## 백엔드 연동 시 참고 (엔티티)
프로토타입 기준 도메인 모델: PlantSpecies, Plant, Journal, JournalCompletionLog,
PointTransaction(CHARGE/JOURNAL_REWARD/PURCHASE/REFUND/CLAWBACK/ADMIN_ADJUST),
Payment(PAID/FAILED/CANCELED/REFUNDED), Product, Cart, Order, Card, UserCard,
CardPurchaseLog, ExchangeProduct, ExchangeOrder(REQUESTED→PREPARING→SHIPPING→DELIVERED/CANCELLED),
UserAddress, Notification, Inquiry, Report.

## 참고: 인증 화면
로그인/회원가입/랜딩은 원본 HTML 프로토타입(`01-인증.dc.html`)에 있습니다.
Next.js에서는 인증 공급자(NextAuth 등)에 맞춰 별도 구현을 권장하여 이식 대상에서 제외했습니다.
