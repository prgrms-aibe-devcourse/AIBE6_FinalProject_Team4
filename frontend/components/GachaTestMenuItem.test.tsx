import { cleanup, fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import GachaTestMenuItem from "./GachaTestMenuItem";
import {
  createGachaQaDraw,
  createOneHundredGachaQaDraws,
} from "@/lib/gacha-api";

const navigation = vi.hoisted(() => ({ push: vi.fn() }));

vi.mock("next/navigation", () => ({
  useRouter: () => navigation,
}));

vi.mock("@/lib/gacha-api", () => ({
  createGachaQaDraw: vi.fn(),
  createOneHundredGachaQaDraws: vi.fn(),
}));

const mockedCreateDraw = vi.mocked(createGachaQaDraw);
const mockedCreateHundredDraws = vi.mocked(createOneHundredGachaQaDraws);

describe("GachaTestMenuItem", () => {
  afterEach(() => {
    cleanup();
    sessionStorage.clear();
    vi.unstubAllEnvs();
    vi.clearAllMocks();
  });

  it("100팩 테스트는 100개를 생성하고 다중팩 개봉 화면으로 이동한다", async () => {
    vi.stubEnv("NEXT_PUBLIC_ENABLE_GACHA_TEST_BUTTON", "true");
    mockedCreateHundredDraws.mockResolvedValue({
      drawIds: Array.from({ length: 100 }, (_, index) => index + 100),
      packCount: 100,
    });

    render(
      <GachaTestMenuItem accessToken="access-token" onNavigate={vi.fn()} />,
    );

    fireEvent.click(screen.getByRole("button", { name: "가챠 100팩 테스트" }));

    expect(
      await screen.findByText("100팩 개봉 준비 중..."),
    ).toBeInTheDocument();
    expect(mockedCreateHundredDraws).toHaveBeenCalledWith(
      "access-token",
      expect.any(String),
    );
    await vi.waitFor(() =>
      expect(navigation.push).toHaveBeenCalledWith(
        expect.stringMatching(/^\/gacha\/open\/batch\/.+/),
      ),
    );
    const target = vi.mocked(navigation.push).mock.calls[0][0] as string;
    const batchKey = target.split("/").at(-1);
    expect(
      JSON.parse(sessionStorage.getItem(`gacha-open-batch:${batchKey}`) ?? "{}")
        .drawIds,
    ).toEqual(Array.from({ length: 100 }, (_, index) => index + 100));
  });

  it("기능 설정이 켜지면 테스트 팩을 만들고 개봉 화면으로 이동한다", async () => {
    vi.stubEnv("NEXT_PUBLIC_ENABLE_GACHA_TEST_BUTTON", "true");
    mockedCreateDraw.mockResolvedValue({ drawId: 77, status: "PENDING" });

    render(
      <GachaTestMenuItem accessToken="access-token" onNavigate={vi.fn()} />,
    );

    fireEvent.click(screen.getByRole("button", { name: /가챠 테스트 버튼/ }));

    expect(await screen.findByText("테스트 팩 생성 중...")).toBeInTheDocument();
    expect(mockedCreateDraw).toHaveBeenCalledWith(
      "access-token",
      expect.any(String),
    );
    await vi.waitFor(() =>
      expect(navigation.push).toHaveBeenCalledWith("/gacha/open/77"),
    );
  });

  it("기능 설정이 꺼지면 테스트 링크를 제거한다", () => {
    vi.stubEnv("NEXT_PUBLIC_ENABLE_GACHA_TEST_BUTTON", "false");

    render(
      <GachaTestMenuItem accessToken="access-token" onNavigate={vi.fn()} />,
    );

    expect(
      screen.queryByRole("button", { name: /가챠 테스트 버튼/ }),
    ).not.toBeInTheDocument();
  });
});
