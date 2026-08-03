import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it } from "vitest";
import GoldenCelebrationEffects from "./GoldenCelebrationEffects";

describe("GoldenCelebrationEffects", () => {
  afterEach(cleanup);

  it("하이퍼와 분리된 골든 팡파레 무대를 렌더링한다", () => {
    render(<GoldenCelebrationEffects />);

    expect(screen.getByTestId("golden-celebration")).toBeInTheDocument();
    expect(screen.getByText("GOLDEN RARE")).toBeInTheDocument();
    expect(screen.getByText(/LEGENDARY DISCOVERY/)).toBeInTheDocument();
  });
});
