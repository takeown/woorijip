import { cleanup, render, screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, expect, test, vi } from "vitest";
import { CapturePanel } from "./capture-panel";

afterEach(() => { cleanup(); vi.unstubAllGlobals(); });
const entry = { merchant: "테스트마트", amount: 12000, occurredAt: "2026-09-08T08:56:00+09:00", category: "LIVING", status: "APPROVAL" };
const json = (data: unknown) => new Response(JSON.stringify(data), { headers: { "Content-Type": "application/json" } });

function setup({ duplicate = false, failSave = false, review = false } = {}) {
  let attempts = 0;
  const fetchMock = vi.fn<typeof fetch>(async (url) => {
    const path = String(url);
    if (path.endsWith("/members")) return json([{ userId: 1, displayName: "나" }]);
    if (path.endsWith("/auth/csrf")) return json({ headerName: "X-CSRF", token: "test-token" });
    if (path.includes("/analyze")) return json({ entries: [{ ...entry, status: review ? "REVIEW" : "APPROVAL" }] });
    if (path.endsWith("/check")) return json([{ duplicate, category: null }]);
    if (path.endsWith("/apply")) { if (failSave && attempts++ === 0) throw new TypeError("Network failed"); return json({ savedCount: 1 }); }
    throw new Error("Unexpected request");
  });
  vi.stubGlobal("fetch", fetchMock);
  render(<CapturePanel currentUserId={1} />);
  return fetchMock;
}

async function analyze() {
  const user = userEvent.setup();
  await screen.findByRole("option", { name: "나" });
  await user.upload(screen.getByLabelText(/캡처 선택/), new File(["image"], "capture.png", { type: "image/png" }));
  await user.click(screen.getByRole("button", { name: "1장 분석하기" }));
  await screen.findByLabelText("가맹점");
  return user;
}

test("uploads through our server and saves reviewed edits only after duplicate check", async () => {
  const fetchMock = setup();
  const user = await analyze();
  expect(screen.queryByRole("button", { name: /확인하고 저장/ })).toBeNull();
  await user.clear(screen.getByLabelText("가맹점"));
  await user.type(screen.getByLabelText("가맹점"), "수정한 가맹점");
  await user.click(screen.getByRole("button", { name: "중복·분류 확인" }));
  await user.click(await screen.findByRole("button", { name: "1건 확인하고 저장" }));
  await screen.findByText("1건을 저장했습니다.");
  const saveCall = fetchMock.mock.calls.find(([url]) => String(url).endsWith("/apply"));
  expect(JSON.parse(String(saveCall?.[1]?.body)).candidates[0].merchant).toBe("수정한 가맹점");
  expect(fetchMock.mock.calls.every(([url]) => !String(url).includes("api.openai.com"))).toBe(true);
});

test("requires explicit duplicate confirmation and rechecks after edits", async () => {
  setup({ duplicate: true });
  const user = await analyze();
  await user.click(screen.getByRole("button", { name: "중복·분류 확인" }));
  const save = await screen.findByRole("button", { name: "1건 확인하고 저장" });
  expect(save).toHaveProperty("disabled", true);
  await user.click(screen.getByLabelText(/별도 결제임을 확인/));
  expect(save).toHaveProperty("disabled", false);
  await user.type(screen.getByLabelText("금액(원)"), "0");
  expect(screen.queryByRole("button", { name: /확인하고 저장/ })).toBeNull();
});

test("retries the identical request after a lost response", async () => {
  const fetchMock = setup({ failSave: true });
  const user = await analyze();
  await user.click(screen.getByRole("button", { name: "중복·분류 확인" }));
  await user.click(await screen.findByRole("button", { name: "1건 확인하고 저장" }));
  await screen.findByRole("alert");
  await user.click(screen.getByRole("button", { name: "같은 요청으로 다시 저장" }));
  await screen.findByText("1건을 저장했습니다.");
  const calls = fetchMock.mock.calls.filter(([url]) => String(url).endsWith("/apply"));
  expect(calls).toHaveLength(2);
  expect(calls[0][1]?.body).toEqual(calls[1][1]?.body);
});

test("excludes unsupported entries and rejects more than five files", async () => {
  setup({ review: true });
  const user = await analyze();
  expect(screen.getByRole("checkbox")).toHaveProperty("disabled", true);
  expect(screen.getByRole("button", { name: "중복·분류 확인" })).toHaveProperty("disabled", true);
  await user.click(screen.getByRole("button", { name: "다른 캡처 선택" }));
  await user.upload(screen.getByLabelText(/캡처 선택/), Array.from({ length: 6 }, (_, index) => new File(["image"], `${index}.png`, { type: "image/png" })));
  await waitFor(() => expect(screen.getByRole("alert").textContent).toContain("최대 5장"));
});
