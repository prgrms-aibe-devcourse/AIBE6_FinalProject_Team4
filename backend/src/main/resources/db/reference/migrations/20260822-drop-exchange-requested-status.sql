-- 교환(exchange_orders) 상태에서 REQUESTED("신청됨") 단계를 없애고 일반 주문(Order)과 동일하게
-- 바로 PREPARING으로 시작하도록 통일한다. 취소 가능 창구도 주문처럼 PREPARING까지로 넓혔다.
-- ddl-auto: update는 enum 컬럼(문자열)의 허용 값 자체를 검증하지 않지만, 애플리케이션 코드에서
-- ExchangeStatus.REQUESTED를 제거하면 기존에 그 값으로 저장된 행을 읽을 때
-- IllegalArgumentException(No enum constant)이 나므로, 배포 전에 반드시 실행해야 한다.
UPDATE exchange_orders
SET status = 'PREPARING'
WHERE status = 'REQUESTED';
