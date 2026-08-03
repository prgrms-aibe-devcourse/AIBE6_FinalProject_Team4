import {
  cleanup,
  fireEvent,
  render,
  screen,
  waitFor,
} from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import GachaPackStage from "./GachaPackStage";

vi.mock("next/image", () => ({
  default: ({ alt, src }: { alt: string; src: string }) => (
    <span role="img" aria-label={alt} data-src={src} />
  ),
}));

describe("GachaPackStage", () => {
  afterEach(cleanup);

  it("카드팩 앞면과 뒷면을 같은 규격으로 렌더링한다", async () => {
    const onOpen = vi.fn();
    render(<GachaPackStage onOpen={onOpen} />);

    expect(
      screen.getByRole("img", { name: "시즌 1 카드팩 앞면" }),
    ).toHaveAttribute(
      "data-src",
      "/cards/900001/0005fbe2-236e-5543-a4d4-69f8b57bd3f7.svg",
    );
    expect(
      screen.getByRole("img", { name: "시즌 1 카드팩 뒷면" }),
    ).toHaveAttribute(
      "data-src",
      "/cards/900003/ada07292-dc4b-58f0-ba69-1386fc040e56.svg",
    );

    fireEvent.click(screen.getByRole("button", { name: /팩을 눌러 개봉하기/ }));
    expect(screen.getByText("팩을 뜯는 중...")).toBeInTheDocument();
    await waitFor(() => expect(onOpen).toHaveBeenCalledOnce(), {
      timeout: 1800,
    });
  });
});
