import { cleanup, fireEvent, render, screen, within } from "@testing-library/react";
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
          privacyConsent: consentStatus(true, true),
        }),
      ),
    );

    const { container } = render(
      <AuthenticatedShell>
        {() => <p>통계 콘텐츠</p>}
      </AuthenticatedShell>,
    );

    expect(await screen.findAllByText("테스트 사용자")).toHaveLength(2);
    expect(screen.getByText("통계 콘텐츠")).toBeDefined();
    const desktopNavigation = screen.getByRole("navigation", {
      name: "데스크톱 주요 메뉴",
    });
    const mobileNavigation = screen.getByRole("navigation", {
      name: "모바일 주요 메뉴",
    });
    for (const navigation of [desktopNavigation, mobileNavigation]) {
      expect(within(navigation).getByRole("link", { name: "거래" }).getAttribute("href")).toBe("/");
      const statsLink = within(navigation).getByRole("link", { name: "통계" });
      expect(statsLink.getAttribute("href")).toBe("/stats");
      expect(statsLink.getAttribute("aria-current")).toBeNull();
      const statementsLink = within(navigation).getByRole("link", { name: "명세서" });
      expect(statementsLink.getAttribute("href")).toBe("/statements");
      expect(statementsLink.getAttribute("aria-current")).toBe("page");
    }
    expect(screen.getAllByRole("link", { name: "우리집" })).toHaveLength(2);
    expect(container.querySelectorAll("svg")).toHaveLength(5);
  });

  test("requires the privacy policy before rendering app content", async () => {
    const fetchMock = vi.fn<typeof fetch>()
      .mockResolvedValueOnce(jsonResponse({
        id: 1,
        displayName: "테스트 사용자",
        householdId: 10,
        privacyConsent: consentStatus(false, false),
      }))
      .mockResolvedValueOnce(jsonResponse({ token: "csrf", headerName: "X-XSRF-TOKEN" }))
      .mockResolvedValueOnce(jsonResponse(consentStatus(true, false)));
    vi.stubGlobal("fetch", fetchMock);

    render(<AuthenticatedShell>{() => <p>가계부 콘텐츠</p>}</AuthenticatedShell>);

    expect(await screen.findByRole("heading", { name: "개인정보 동의" })).toBeDefined();
    expect(screen.queryByText("가계부 콘텐츠")).toBeNull();
    fireEvent.click(screen.getByRole("checkbox", { name: /개인정보 처리방침 동의/ }));
    fireEvent.click(screen.getByRole("button", { name: "동의하고 시작하기" }));

    expect(await screen.findByText("가계부 콘텐츠")).toBeDefined();
    expect(fetchMock).toHaveBeenLastCalledWith(
      "http://localhost:8080/auth/privacy-consents",
      expect.objectContaining({
        method: "PUT",
        body: JSON.stringify({ privacyPolicyAgreed: true, aiOverseasTransferAgreed: false }),
      }),
    );
  });
});

function consentStatus(privacyPolicyAgreed: boolean, aiOverseasTransferAgreed: boolean) {
  return {
    privacyPolicyVersion: "2026-09-09",
    privacyPolicyAgreed,
    privacyPolicyAgreedAt: privacyPolicyAgreed ? "2026-09-09T00:00:00Z" : null,
    aiOverseasTransferVersion: "2026-09-09",
    aiOverseasTransferAgreed,
    aiOverseasTransferAgreedAt: aiOverseasTransferAgreed ? "2026-09-09T00:00:00Z" : null,
  };
}

function jsonResponse(body: unknown): Response {
  return new Response(JSON.stringify(body), {
    headers: { "Content-Type": "application/json" },
    status: 200,
  });
}
