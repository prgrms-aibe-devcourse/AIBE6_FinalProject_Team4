import { grads } from './theme';

// Shared sample data — mirrors the prototype seed data.
export const SPECIES = [
  { id: 1, name: '방울토마토', emoji: '🍅' }, { id: 2, name: '바질', emoji: '🌿' },
  { id: 3, name: '상추', emoji: '🥬' }, { id: 4, name: '딸기', emoji: '🍓' },
  { id: 5, name: '고추', emoji: '🌶️' }, { id: 6, name: '수박', emoji: '🍉' },
  { id: 7, name: '당근', emoji: '🥕' }, { id: 8, name: '청경채', emoji: '🥬' },
];

export const PLANTS = [
  { id: 1, nickname: '토실이', species: '방울토마토', emoji: '🍅', grad: grads.tomato, startDate: '2026.06.09', dplus: 42, status: 'GROWING', archived: false, careGuide: '햇빛을 아주 좋아해요. 하루 6시간 이상 볕을 쬐어 주세요.\n흙 표면이 마르면 듬뿍 물을 주고, 열매가 맺히면 물 양을 조금 줄여 당도를 높여요.\n곁순은 부지런히 따주면 열매가 더 튼튼해져요.' },
  { id: 2, nickname: '바질이', species: '바질', emoji: '🌿', grad: grads.basil, startDate: '2026.07.06', dplus: 15, status: 'GROWING', archived: false, careGuide: '따뜻하고 볕이 잘 드는 곳을 좋아해요.\n윗잎을 자주 따주면 옆으로 풍성하게 자라요.' },
  { id: 3, nickname: '쌈싸리', species: '상추', emoji: '🥬', grad: grads.lettuce, startDate: '2026.07.13', dplus: 8, status: 'GROWING', archived: false, careGuide: '서늘한 반그늘을 좋아해요. 겉잎부터 하나씩 따서 오래 즐길 수 있어요.' },
  { id: 4, nickname: '딸기공주', species: '딸기', emoji: '🍓', grad: grads.strawberry, startDate: '2026.03.23', dplus: 120, status: 'HARVESTED', archived: false, careGuide: '볕과 통풍이 중요해요. 수확 후에는 러너를 정리해 새 포기를 준비해요.' },
  { id: 5, nickname: '매콤이', species: '고추', emoji: '🌶️', grad: grads.pepper, startDate: '2026.06.21', dplus: 30, status: 'FAILED', archived: false, careGuide: '물빠짐이 좋은 흙에서 키워요. 과습에 약하니 주의해 주세요.' },
];

export const JOURNALS = [
  { id: 1, plantId: 1, plantNickname: '토실이', emoji: '🍅', grad: grads.tomato, date: '2026.07.21', content: '드디어 첫 열매가 붉게 물들기 시작했어요! 아침마다 조금씩 색이 진해지는 게 신기해요.', rewarded: true, today: true },
  { id: 2, plantId: 2, plantNickname: '바질이', emoji: '🌿', grad: grads.basil, date: '2026.07.20', content: '잎이 부쩍 커졌어요. 손끝을 스치면 향이 확 퍼져요.', rewarded: true, today: false },
  { id: 3, plantId: 3, plantNickname: '쌈싸리', emoji: '🥬', grad: grads.lettuce, date: '2026.07.19', content: '새잎이 세 장이나 났어요. 곧 첫 수확 할 수 있을 것 같아요.', rewarded: false, today: false },
  { id: 4, plantId: 1, plantNickname: '토실이', emoji: '🍅', grad: grads.sun, date: '2026.07.18', content: '지지대를 세워줬어요. 이제 곧게 잘 자라겠죠?', rewarded: true, today: false },
  { id: 5, plantId: 2, plantNickname: '바질이', emoji: '🪴', grad: grads.mint, date: '2026.07.17', content: '분갈이 완료! 뿌리가 튼튼하게 자리 잡았어요.', rewarded: true, today: false },
  { id: 6, plantId: 3, plantNickname: '쌈싸리', emoji: '🌱', grad: grads.sprout, date: '2026.07.15', content: '씨앗에서 떡잎이 올라왔어요. 작고 여린 초록빛이 사랑스러워요.', rewarded: true, today: false },
];

export const PRODUCTS = [
  { id: 1, name: '새싹 재배 키트', cat: 'KIT', price: 800, emoji: '🌱', grad: grads.sprout, stock: 24, description: '흙, 씨앗, 화분이 한 번에. 오늘 바로 시작할 수 있는 올인원 키트예요.' },
  { id: 2, name: '방울토마토 모종', cat: 'SEEDLING', price: 1200, emoji: '🍅', grad: grads.tomato, stock: 12, description: '튼튼하게 자란 방울토마토 모종이에요.', speciesGuide: '햇빛을 좋아하고 물을 자주 주면 달콤한 열매가 많이 맺혀요.' },
  { id: 3, name: '바질 모종', cat: 'SEEDLING', price: 900, emoji: '🌿', grad: grads.basil, stock: 18, description: '향긋한 바질 모종. 요리에 바로 활용할 수 있어요.', speciesGuide: '따뜻하고 볕이 잘 드는 곳을 좋아해요.' },
  { id: 4, name: '미니 화분 세트', cat: 'KIT', price: 1500, emoji: '🪴', grad: grads.mint, stock: 7, description: '아담한 테라코타 화분 3종 세트.' },
  { id: 5, name: '상추 모종', cat: 'SEEDLING', price: 700, emoji: '🥬', grad: grads.lettuce, stock: 0, description: '쌈 채소의 기본, 상추 모종이에요.', speciesGuide: '서늘한 반그늘을 좋아하고 겉잎부터 오래 즐길 수 있어요.' },
  { id: 6, name: '딸기 모종', cat: 'SEEDLING', price: 1800, emoji: '🍓', grad: grads.strawberry, stock: 5, description: '달콤한 설향 딸기 모종.', speciesGuide: '볕과 통풍이 중요해요.' },
];

export const CARDS = [
  { id: 1, name: '수박 카드', price: 300, emoji: '🍉', grad: grads.basil, owned: 5, required: 5, realName: '진짜 수박', realEmoji: '🍉', realGrad: 'linear-gradient(135deg,#C8E6C9,#81C784)', realDesc: '제철 수박 한 통', stock: 8 },
  { id: 2, name: '토마토 카드', price: 200, emoji: '🍅', grad: grads.tomato, owned: 2, required: 4, realName: '방울토마토 1kg', realEmoji: '🍅', realGrad: 'linear-gradient(135deg,#FFE0B2,#FFAB91)', realDesc: '농장 직송 1kg', stock: 12 },
  { id: 3, name: '딸기 카드', price: 350, emoji: '🍓', grad: grads.strawberry, owned: 1, required: 6, realName: '딸기 한 팩', realEmoji: '🍓', realGrad: 'linear-gradient(135deg,#FCE4EC,#F48FB1)', realDesc: '설향 500g', stock: 5 },
  { id: 4, name: '당근 카드', price: 150, emoji: '🥕', grad: grads.carrot, owned: 3, required: 3, realName: '당근 1kg', realEmoji: '🥕', realGrad: 'linear-gradient(135deg,#FFE0B2,#FFB74D)', realDesc: '유기농 1kg', stock: 0 },
  { id: 5, name: '감자 카드', price: 180, emoji: '🥔', grad: grads.potato, owned: 0, required: 5, realName: '감자 2kg', realEmoji: '🥔', realGrad: 'linear-gradient(135deg,#EFEBE9,#BCAAA4)', realDesc: '수미감자 2kg', stock: 20 },
];

export const ADDRESSES = [
  { id: 1, name: '김초록', phone: '010-1234-5678', addr: '서울 마포구 양화로 45, 302호', isDefault: true },
  { id: 2, name: '김초록 (회사)', phone: '010-1234-5678', addr: '서울 강남구 테헤란로 20, 8층', isDefault: false },
];

export const BADGE = {
  GROWING: { label: '재배중', bg: '#E8F3D8', color: '#4b7a1e' },
  HARVESTED: { label: '수확완료', bg: '#FFF3CC', color: '#8a6d00' },
  FAILED: { label: '실패', bg: '#ECECEC', color: '#8a8a8a' },
};
