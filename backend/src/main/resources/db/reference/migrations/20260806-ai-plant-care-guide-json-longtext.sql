-- guide_json이 tinytext(255바이트)로 생성돼 한국어 가이드 JSON(수 KB)이 들어가지 못했다.
-- 매 저장이 "Data too long for column 'guide_json'"(MySQL 1406)으로 실패해 캐시가 통째로
-- 동작하지 않았고, 요청마다 AI를 새로 부른 뒤 409로 끝났다.
--
-- 원인은 @Lob + String이 Hibernate 6/MySQL에서 tinytext로 떨어지는 것이다. 엔티티에서
-- @Lob을 빼고 columnDefinition으로 타입을 못 박았으므로 실 DB도 같은 타입으로 맞춘다.
--
-- 잘린 데이터를 살릴 방법은 없지만, 애초에 저장에 성공한 행이 없어 유실될 것도 없다.
ALTER TABLE ai_plant_care_guides
    MODIFY COLUMN guide_json longtext NOT NULL;
