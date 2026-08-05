import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import GachaTitleBadge from "./GachaTitleBadge";

describe("GachaTitleBadge", () => {
  it.each([
    ["TITLE_SPROUT_COLLECTOR", "새싹 수집가", "sprout-glow"],
    ["TITLE_GARDEN_KEEPER", "정원의 수호자", "guardian-orbit"],
    ["TITLE_CARD_MASTER", "카드 마스터", "master-aurora"],
  ])("칭호 코드 %s에 전용 이펙트를 적용한다", (code, name, effect) => {
    render(<GachaTitleBadge code={code} name={name} />);

    expect(
      screen.getByText(name).closest("[data-cosmetic-title]"),
    ).toHaveAttribute("data-title-effect", effect);
  });

  it.each([
    ["TITLE_GARDEN_KEEPER", "정원의 수호자", 8],
    ["TITLE_CARD_MASTER", "카드 마스터", 24],
  ])("%s 칭호에 %i개 파티클을 표시한다", (code, name, count) => {
    const { container } = render(<GachaTitleBadge code={code} name={name} />);

    expect(container.querySelectorAll("[data-title-particle]")).toHaveLength(
      count,
    );
  });
});
