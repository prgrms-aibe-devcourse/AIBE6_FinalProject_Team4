-- 상품·쿠폰 PNG를 S3에 업로드한 뒤 적용한다.
-- DB에는 ASSET_BASE_URL을 제외한 상대 객체 key만 저장한다.
-- 실행 전 각 id와 name이 아래 매핑과 일치하는지 반드시 확인한다.

UPDATE products SET image_url = 'products/1/e9f8dd6d-7692-5c67-9172-7c9a2eeae96b.png' WHERE id = 1 AND name = '방울토마토 홈가드닝 키트';
UPDATE products SET image_url = 'products/2/07e36a18-4d17-5b91-b3bd-9a9a13e75516.png' WHERE id = 2 AND name = '향긋한 바질 씨앗 키트';
UPDATE products SET image_url = 'products/3/55ae1ce1-1ff1-5371-8b86-70e2bdd7a50e.png' WHERE id = 3 AND name = '청상추 미니 텃밭 키트';
UPDATE products SET image_url = 'products/4/b9bdb189-9f98-5636-86ce-d5c9852cf082.png' WHERE id = 4 AND name = '루꼴라 스타터 키트';
UPDATE products SET image_url = 'products/5/4a0eeb55-c63e-5f9c-8657-de18de80d689.png' WHERE id = 5 AND name = '해바라기 성장 관찰 키트';
UPDATE products SET image_url = 'products/6/26484928-4c05-592b-a3fc-cb3e76ad3578.png' WHERE id = 6 AND name = '스위트 바질 모종';
UPDATE products SET image_url = 'products/7/682bcb8b-1814-52a4-ba81-d513ea2fcbeb.png' WHERE id = 7 AND name = '방울토마토 모종';
UPDATE products SET image_url = 'products/8/64645cda-841a-55f3-893c-cab4715edc87.png' WHERE id = 8 AND name = '아삭한 청상추 모종';
UPDATE products SET image_url = 'products/9/86294710-c193-5d62-8f4c-2dc50356355f.png' WHERE id = 9 AND name = '향긋한 로즈마리 모종';
UPDATE products SET image_url = 'products/10/15e4d877-7091-5900-842e-6365fc9892c2.png' WHERE id = 10 AND name = '설향 딸기 모종';
UPDATE products SET image_url = 'products/11/f7573887-a33e-5690-b058-f32f7aa2a326.png' WHERE id = 11 AND name = '시즌 1 가챠 카드팩';

UPDATE cards SET image_url = 'coupons/1/5d085536-b249-56bf-b42f-82e56bd785dd.png' WHERE id = 1 AND name = '수박 쿠폰';
UPDATE cards SET image_url = 'coupons/2/a4d206ed-e57c-57aa-b347-6a633f1f08b4.png' WHERE id = 2 AND name = '방울토마토 쿠폰';
UPDATE cards SET image_url = 'coupons/3/817208ec-104b-5e78-8d90-84ebeab76ffe.png' WHERE id = 3 AND name = '설향 딸기 쿠폰';
UPDATE cards SET image_url = 'coupons/4/81c3e0a4-e115-5cc9-a21e-5accc4504cbd.png' WHERE id = 4 AND name = '유기농 당근 쿠폰';
UPDATE cards SET image_url = 'coupons/5/64303f95-e437-5ee9-8fb0-d9bccefe3a1b.png' WHERE id = 5 AND name = '수미감자 쿠폰';
UPDATE cards SET image_url = 'coupons/6/0a0fac0b-b987-5240-8802-e13c719b6475.png' WHERE id = 6 AND name = '샤인머스캣 쿠폰';
UPDATE cards SET image_url = 'coupons/7/c14efdb8-491e-546d-b234-15ee6ef863b1.png' WHERE id = 7 AND name = '초당옥수수 쿠폰';
UPDATE cards SET image_url = 'coupons/8/6ef1c07a-be33-5e1d-a6c2-75427e43e13e.png' WHERE id = 8 AND name = '꿀고구마 쿠폰';
UPDATE cards SET image_url = 'coupons/9/cda323a0-8b66-5071-9730-34c3cad1ea16.png' WHERE id = 9 AND name = '부사 사과 쿠폰';
UPDATE cards SET image_url = 'coupons/10/a5a1f56f-5b64-5df6-b955-9df23797ca9f.png' WHERE id = 10 AND name = '제주 감귤 쿠폰';
