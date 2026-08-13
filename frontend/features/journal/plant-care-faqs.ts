export const PLANT_CARE_FAQ_CATEGORIES = [
  { id: "journal", label: "일지 기록", icon: "edit_note" },
  { id: "watering", label: "물주기", icon: "water_drop" },
  { id: "environment", label: "환경", icon: "wb_sunny" },
  { id: "symptom", label: "잎·병해충", icon: "eco" },
  { id: "growth", label: "성장 관리", icon: "psychiatry" },
] as const;

export type PlantCareFaqCategory =
  (typeof PLANT_CARE_FAQ_CATEGORIES)[number]["id"];

export interface PlantCareFaq {
  id: string;
  category: PlantCareFaqCategory;
  question: string;
  answer: string;
  recommendedActions: readonly string[];
  additionalChecks: readonly string[];
}

/**
 * 식물 종을 특정하지 않아도 안전하게 안내할 수 있는 관찰 중심의 준비 답변이다.
 * 정확한 물의 양이나 광량처럼 종·계절·화분 환경에 따라 달라지는 값은 단정하지 않고,
 * 사용자가 선택한 식물의 프로필과 일지를 사용하는 AI 질문으로 이어지게 한다.
 */
export const PLANT_CARE_FAQS: readonly PlantCareFaq[] = [
  {
    id: "journal-what-to-write",
    category: "journal",
    question: "오늘 일지에는 무엇을 적으면 좋을까요?",
    answer:
      "나중에 변화를 비교할 수 있도록 “보이는 모습”과 “오늘 한 관리”를 함께 적어보세요. 길게 쓰기보다 같은 항목을 꾸준히 남기는 것이 더 도움이 됩니다.",
    recommendedActions: [
      "가능하면 같은 위치와 각도에서 식물 전체 사진을 찍어주세요.",
      "흙의 마름 정도와 물·비료·분갈이 여부를 적어주세요.",
      "새잎, 잎 색, 처짐, 반점처럼 지난 기록과 달라진 점을 한 가지 이상 적어주세요.",
    ],
    additionalChecks: [
      "화분 위치나 날씨처럼 오늘 달라진 환경이 있었는지도 확인해 보세요.",
    ],
  },
  {
    id: "journal-record-a-problem",
    category: "journal",
    question: "이상한 부분을 발견했을 때 어떻게 기록하나요?",
    answer:
      "원인을 바로 단정하기보다 관찰한 사실을 구체적으로 남겨주세요. “병든 것 같다”보다 “아래쪽 잎 두 장이 노랗고 흙이 사흘째 젖어 있다”처럼 쓰면 이후 변화를 판단하기 쉽습니다.",
    recommendedActions: [
      "식물 전체 사진과 이상 부위의 가까운 사진을 각각 남겨주세요.",
      "처음 발견한 날짜, 위치, 색·크기, 번지는 속도를 적어주세요.",
      "물을 주거나 위치를 옮겼다면 한 번에 한 가지 조치만 하고 결과를 다음 일지에 적어주세요.",
    ],
    additionalChecks: [
      "잎 뒷면, 줄기와 잎이 만나는 곳, 흙 표면에 벌레나 곰팡이가 있는지 살펴보세요.",
    ],
  },
  {
    id: "watering-when",
    category: "watering",
    question: "물은 언제 주는 게 좋을까요?",
    answer:
      "정해진 요일보다 흙의 마름과 화분 무게를 기준으로 판단하는 편이 안전합니다. 필요한 건조 정도는 식물 종, 계절, 화분 크기와 통풍에 따라 달라집니다.",
    recommendedActions: [
      "손가락이나 나무 꼬챙이로 겉흙 아래까지 젖어 있는지 확인해 보세요.",
      "물을 준 직후와 마른 뒤의 화분 무게 차이를 익혀두세요.",
      "물을 줬다면 날짜와 흙 상태를 일지에 함께 기록해 다음 간격을 비교해 보세요.",
    ],
    additionalChecks: [
      "다육식물처럼 건조를 좋아하는 종은 흙이 더 깊이 마른 뒤 물을 줘야 할 수 있어요.",
    ],
  },
  {
    id: "watering-how",
    category: "watering",
    question: "물은 한 번에 얼마나 줘야 하나요?",
    answer:
      "배수구가 있는 일반 화분이라면 흙 전체가 고르게 젖도록 천천히 주고, 아래로 나온 물은 받침에 오래 고이지 않게 비워주세요. 정확한 양은 화분과 흙에 따라 달라 고정된 컵 수로 정하기 어렵습니다.",
    recommendedActions: [
      "한 곳에 붓기보다 흙 표면을 돌며 천천히 물을 주세요.",
      "물이 바로 옆으로 빠진다면 잠시 기다렸다가 나누어 주세요.",
      "받침에 고인 물은 뿌리가 계속 잠기지 않도록 비워주세요.",
    ],
    additionalChecks: [
      "배수구가 없거나 막혀 있다면 물의 양보다 배수 환경을 먼저 점검해 주세요.",
    ],
  },
  {
    id: "watering-too-much",
    category: "watering",
    question: "물을 너무 많이 줬는지 어떻게 알 수 있나요?",
    answer:
      "흙이 오래 젖어 있으면서 냄새가 나거나 아래쪽 잎이 노래지고 줄기가 물러진다면 과습이나 뿌리 스트레스 가능성을 살펴봐야 합니다. 증상 하나만으로 과습을 확정할 수는 없습니다.",
    recommendedActions: [
      "추가 물주기를 멈추고 흙이 마르는 속도를 관찰해 주세요.",
      "받침 물을 비우고 배수구와 통풍 상태를 확인해 주세요.",
      "며칠 동안 잎과 줄기의 변화를 같은 각도로 촬영해 비교해 주세요.",
    ],
    additionalChecks: [
      "줄기 밑동이나 뿌리가 검고 물렁하거나 악취가 난다면 분갈이 여부를 더 자세히 확인해 주세요.",
    ],
  },
  {
    id: "environment-light",
    category: "environment",
    question: "햇빛은 얼마나 보여줘야 하나요?",
    answer:
      "필요한 빛의 세기는 식물마다 다릅니다. 줄기가 가늘고 길어지거나 창 쪽으로 심하게 기울면 빛이 부족할 수 있고, 갑자기 옮긴 뒤 잎이 하얗게 바래거나 갈색 반점이 생기면 강한 빛 스트레스일 수 있습니다.",
    recommendedActions: [
      "현재 창 방향과 하루 중 빛이 드는 시간을 일지에 적어주세요.",
      "더 밝은 곳으로 옮길 때는 며칠에 걸쳐 노출 시간을 조금씩 늘려주세요.",
      "화분을 주기적으로 돌리되 방향을 바꾼 날짜를 기록해 성장을 비교해 보세요.",
    ],
    additionalChecks: [
      "창문 유리, 커튼, 계절 변화에 따라 실제 식물이 받는 빛이 크게 달라질 수 있어요.",
    ],
  },
  {
    id: "environment-temperature-air",
    category: "environment",
    question: "온도와 통풍은 어떻게 관리하나요?",
    answer:
      "대부분의 실내 식물은 급격한 온도 변화와 정체된 공기에 스트레스를 받습니다. 식물에 맞는 정확한 온도 범위는 종별로 다르므로, 우선 난방·냉방 바람과 밤낮의 큰 변화부터 피해주세요.",
    recommendedActions: [
      "에어컨, 히터, 뜨거운 창문 유리에 식물이 직접 닿지 않게 해주세요.",
      "밀폐된 공간은 약한 공기 흐름이 생기도록 환기하되 강풍은 피해주세요.",
      "위치를 바꿨다면 날짜와 이후 잎 상태를 일지에 남겨주세요.",
    ],
    additionalChecks: [
      "잎이 계속 젖어 있거나 화분 사이가 너무 빽빽해 공기가 막히지 않는지 확인해 보세요.",
    ],
  },
  {
    id: "symptom-yellow-leaves",
    category: "symptom",
    question: "잎이 노랗게 변하는 이유가 무엇인가요?",
    answer:
      "오래된 아래 잎의 자연스러운 교체일 수도 있고, 물주기·뿌리·빛·영양 스트레스일 수도 있습니다. 어느 위치의 잎부터 얼마나 빠르게 변하는지가 원인을 좁히는 중요한 단서입니다.",
    recommendedActions: [
      "노란 잎이 아래쪽 한두 장인지 새잎까지 번지는지 기록해 주세요.",
      "흙이 계속 젖어 있는지, 반대로 지나치게 말랐는지 먼저 확인해 주세요.",
      "물·빛·비료를 동시에 바꾸지 말고 가장 의심되는 조건 하나만 조정해 보세요.",
    ],
    additionalChecks: [
      "잎맥과 잎 사이의 색 차이, 반점, 줄기 무름, 벌레 유무를 함께 살펴보세요.",
    ],
  },
  {
    id: "symptom-brown-tips",
    category: "symptom",
    question: "잎 끝이 갈색으로 마르는 건 왜인가요?",
    answer:
      "불규칙한 물주기, 건조한 공기, 갑작스러운 강한 빛, 비료 염류나 뿌리 스트레스 등 여러 원인이 비슷한 모습으로 나타날 수 있습니다. 사진과 최근 관리 기록을 함께 비교해야 합니다.",
    recommendedActions: [
      "갈변이 시작된 날짜와 잎의 위치, 번지는 범위를 촬영해 주세요.",
      "최근 물주기 간격과 비료 사용, 화분 위치 변경 여부를 확인해 주세요.",
      "새잎에도 같은 증상이 생기는지 관찰하면서 한 번에 한 조건만 조정해 주세요.",
    ],
    additionalChecks: [
      "잎 가장자리만 마르는지, 가운데에도 반점이 생기는지 구분해 기록해 보세요.",
    ],
  },
  {
    id: "symptom-drooping",
    category: "symptom",
    question: "잎이 축 처졌을 때 바로 물을 줘야 하나요?",
    answer:
      "잎 처짐은 물 부족뿐 아니라 과습, 고온, 뿌리 문제에서도 나타날 수 있어 바로 물부터 주는 것은 위험할 수 있습니다. 먼저 흙 상태를 확인해 주세요.",
    recommendedActions: [
      "흙이 충분히 말랐다면 식물에 맞는 방법으로 물을 주고 회복 과정을 관찰해 주세요.",
      "흙이 젖어 있다면 추가로 물을 주지 말고 배수와 통풍을 확인해 주세요.",
      "한낮에만 처졌다가 저녁에 회복하는지 시간대별로 비교해 주세요.",
    ],
    additionalChecks: [
      "줄기 밑동의 무름, 뿌리 냄새, 잎 뒷면 벌레도 함께 살펴보세요.",
    ],
  },
  {
    id: "symptom-pests-spots",
    category: "symptom",
    question: "벌레나 이상한 반점이 보이면 어떻게 하나요?",
    answer:
      "다른 식물로 번질 가능성을 줄이고 정확한 모양을 기록하는 것이 먼저입니다. 사진만으로 병해충을 확정하기 어려우므로 정체를 모른 채 약제를 섞어 사용하지 마세요.",
    recommendedActions: [
      "해당 화분을 다른 식물과 잠시 떨어뜨려 놓아주세요.",
      "잎 앞뒤, 줄기, 흙 표면을 밝은 곳에서 촬영하고 움직임 여부를 기록해 주세요.",
      "식물을 만진 손과 도구를 씻고, 사용 제품이 있다면 이름과 사용 날짜를 적어주세요.",
    ],
    additionalChecks: [
      "반점이 물에 젖은 듯한지, 가루가 있는지, 잎을 닦았을 때 묻어나는지 확인해 보세요.",
    ],
  },
  {
    id: "growth-slow",
    category: "growth",
    question: "식물이 잘 자라지 않는 것 같아요.",
    answer:
      "성장 속도는 계절과 생육 단계에 따라 달라 하루 단위로 판단하기 어렵습니다. 새잎 수, 줄기 길이, 잎 크기를 같은 조건에서 주 단위로 비교해 보세요.",
    recommendedActions: [
      "일주일 간격으로 같은 각도에서 사진을 찍어 크기를 비교해 주세요.",
      "최근 빛, 온도, 물주기와 새잎 발생 여부를 함께 기록해 주세요.",
      "뿌리가 배수구 밖으로 많이 나왔는지와 화분이 지나치게 빨리 마르는지 확인해 주세요.",
    ],
    additionalChecks: [
      "휴면기나 적응 기간인지, 최근 분갈이·이동 같은 큰 변화가 있었는지 살펴보세요.",
    ],
  },
  {
    id: "growth-fertilizer",
    category: "growth",
    question: "비료는 언제 주는 게 좋을까요?",
    answer:
      "비료는 식물이 건강하게 자라는 시기에 보충하는 것이 기본이며, 약해진 식물을 즉시 회복시키는 치료제가 아닙니다. 종류와 농도는 식물 및 제품마다 달라 라벨을 우선 따라야 합니다.",
    recommendedActions: [
      "제품 라벨의 대상 식물, 희석 비율과 사용 간격을 확인해 주세요.",
      "처음에는 권장 범위를 넘기지 말고 사용 날짜와 양을 일지에 남겨주세요.",
      "심하게 마른 흙, 뿌리 이상, 병해충 스트레스가 있을 때는 비료보다 원인 점검을 먼저 해주세요.",
    ],
    additionalChecks: [
      "최근 분갈이한 흙에 이미 비료 성분이 포함되어 있는지 확인해 보세요.",
    ],
  },
  {
    id: "growth-repotting",
    category: "growth",
    question: "분갈이는 언제 해야 하나요?",
    answer:
      "뿌리가 화분을 가득 채워 배수구로 많이 나오거나, 물이 너무 빨리 빠지고 성장이 오래 멈춘 경우 분갈이를 검토할 수 있습니다. 큰 화분이 항상 좋은 것은 아닙니다.",
    recommendedActions: [
      "배수구의 뿌리와 흙이 마르는 속도를 먼저 확인해 주세요.",
      "분갈이가 필요하다면 보통 현재보다 한 단계 큰 배수 가능한 화분을 검토해 주세요.",
      "분갈이 날짜, 새 흙과 화분 크기를 기록하고 이후 며칠간 상태를 관찰해 주세요.",
    ],
    additionalChecks: [
      "꽃이 피거나 열매를 맺는 중인지, 식물이 이미 심한 스트레스를 받고 있는지 확인해 주세요.",
    ],
  },
];

export function getPlantCareFaqs(
  category: PlantCareFaqCategory,
): readonly PlantCareFaq[] {
  return PLANT_CARE_FAQS.filter((faq) => faq.category === category);
}
