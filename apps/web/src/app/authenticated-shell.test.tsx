import { cleanup, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, test, vi } from "vitest";
import { AuthenticatedShell } from "./authenticated-shell";

vi.mock("next/navigation", () => ({
  usePathname: () => "/statements",
}));

afterEach(() => {
  cleanup();
  vi.unstubAllGlobals();
});

describe("AuthenticatedShell", () => {
  test("shares the authenticated navigation and marks the current route", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn<typeof fetch>().mockResolvedValueOnce(
        jsonResponse({
          id: 1,
          displayName: "테스트 사용자",
          householdId: 10,
        }),
      ),
    );

    const { container } = render(
      <AuthenticatedShell>
        {() => <p>통계 콘텐츠</p>}
      </AuthenticatedShell>,
    );

    expect(await screen.findByText("테스트 사용자")).toBeDefined();
    expect(screen.getByText("통계 콘텐츠")).toBeDefined();
    expect(screen.getByRole("link", { name: "거래" }).getAttribute("href")).toBe("/");
    const statsLink = screen.getByRole("link", { name: "통계" });
    expect(statsLink.getAttribute("href")).toBe("/stats");
    expect(statsLink.getAttribute("aria-current")).toBeNull();
    const statementsLink = screen.getByRole("link", { name: "명세서" });
    expect(statementsLink.getAttribute("href")).toBe("/statements");
    expect(statementsLink.getAttribute("aria-current")).toBe("page");
    expect(screen.getByRole("link", { name: "우리집" }).querySelector("svg")).not.toBeNull();
    expect(container.querySelectorAll("svg")).toHaveLength(1);
  });
});

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json" },
    status: 200,
  });
}
