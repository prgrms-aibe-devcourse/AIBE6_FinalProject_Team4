import { describe, expect, it } from "vitest";
import {
  getPlantCareFaqs,
  PLANT_CARE_FAQ_CATEGORIES,
  PLANT_CARE_FAQS,
} from "@/features/journal/plant-care-faqs";

describe("plant care faqs", () => {
  it("모든 분류에 둘 이상의 준비 답변을 제공한다", () => {
    PLANT_CARE_FAQ_CATEGORIES.forEach((category) => {
      expect(getPlantCareFaqs(category.id).length).toBeGreaterThanOrEqual(2);
    });
  });

  it("질문 식별자가 중복되지 않고 답변 항목이 비어 있지 않다", () => {
    const ids = PLANT_CARE_FAQS.map((faq) => faq.id);

    expect(new Set(ids).size).toBe(ids.length);
    PLANT_CARE_FAQS.forEach((faq) => {
      expect(faq.question.trim()).not.toBe("");
      expect(faq.answer.trim()).not.toBe("");
      expect(faq.recommendedActions.length).toBeGreaterThan(0);
      expect(
        faq.recommendedActions.every((action) => action.trim().length > 0),
      ).toBe(true);
      expect(
        faq.additionalChecks.every((check) => check.trim().length > 0),
      ).toBe(true);
    });
  });

  it("준비 답변은 최근 AI 대화 한 메시지 제한 안에서 요약할 수 있다", () => {
    PLANT_CARE_FAQS.forEach((faq) => {
      const context = [
        faq.answer,
        `권장 행동: ${faq.recommendedActions.join(" / ")}`,
        `추가 확인: ${faq.additionalChecks.join(" / ")}`,
      ].join("\n");

      expect(context.length).toBeLessThanOrEqual(1000);
    });
  });
});
