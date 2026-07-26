"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { ReactNode, useEffect, useState } from "react";
import { BrandLogo } from "./brand-logo";

export type CurrentUser = {
  id: number;
  displayName: string;
  householdId: number;
};

type CsrfToken = {
  token: string;
  headerName: string;
};

type AuthenticatedShellProps = {
  children: (currentUser: CurrentUser) => ReactNode;
};

const apiUrl = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export function AuthenticatedShell({ children }: AuthenticatedShellProps) {
  const pathname = usePathname();
  const [currentUser, setCurrentUser] = useState<CurrentUser | null>();
  const [error, setError] = useState<string | null>(() => {
    if (typeof window === "undefined") return null;
    const authError = new URL(window.location.href).searchParams.get("authError");
    return authError ? authErrorMessage(authError) : null;
  });

  useEffect(() => {
    let active = true;
    const url = new URL(window.location.href);
    const authError = url.searchParams.get("authError");
    if (authError) {
      url.searchParams.delete("authError");
      window.history.replaceState(null, "", `${url.pathname}${url.search}${url.hash}`);
    }

    fetch(`${apiUrl}/auth/me`, {
      credentials: "include",
      cache: "no-store",
    })
      .then(async (response) => {
        if (!active) return;
        if (response.status === 401) {
          setCurrentUser(null);
          return;
        }
        if (!response.ok) {
          throw new Error("로그인 상태를 확인하지 못했습니다.");
        }
        setCurrentUser(await response.json());
      })
      .catch((caughtError: unknown) => {
        if (!active) return;
        setCurrentUser(null);
        setError(
          caughtError instanceof Error
            ? caughtError.message
            : "API 연결을 확인해 주세요.",
        );
      });

    return () => {
      active = false;
    };
  }, []);

  async function logout() {
    try {
      const csrfResponse = await fetch(`${apiUrl}/auth/csrf`, {
        credentials: "include",
      });
      if (!csrfResponse.ok) {
        throw new Error("로그아웃 요청을 준비하지 못했습니다.");
      }
      const csrfToken: CsrfToken = await csrfResponse.json();
      const response = await fetch(`${apiUrl}/auth/logout`, {
        method: "POST",
        credentials: "include",
        headers: { [csrfToken.headerName]: csrfToken.token },
      });
      if (!response.ok) {
        throw new Error("로그아웃하지 못했습니다.");
      }
      setCurrentUser(null);
    } catch (caughtError) {
      setError(
        caughtError instanceof Error ? caughtError.message : "로그아웃하지 못했습니다.",
      );
    }
  }

  if (currentUser === undefined) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-stone-100 px-5 text-stone-700">
        <p>로그인 상태를 확인하고 있습니다.</p>
      </main>
    );
  }

  if (currentUser === null) {
    return (
      <main className="flex min-h-screen items-center justify-center bg-stone-100 px-5 text-stone-900">
        <section className="w-full max-w-md rounded-3xl border border-stone-200 bg-white p-8 text-center shadow-sm">
          <BrandLogo className="mx-auto h-20 w-20" />
          <p className="text-sm font-medium text-emerald-700">우리 둘의 생활 기록</p>
          <h1 className="mt-2 text-3xl font-semibold tracking-tight">우리집</h1>
          <p className="mt-4 text-sm leading-6 text-stone-600">
            허용된 Google 계정으로 로그인해 주세요.
          </p>
          {error ? (
            <p className="mt-5 rounded-xl bg-red-50 px-4 py-3 text-sm text-red-700" role="alert">
              {error}
            </p>
          ) : null}
          <a
            className="mt-7 block w-full rounded-xl bg-emerald-700 px-4 py-3 font-medium text-white transition hover:bg-emerald-800"
            href={`${apiUrl}/oauth2/authorization/google`}
          >
            Google 계정으로 로그인
          </a>
        </section>
      </main>
    );
  }

  return (
    <main className="min-h-screen bg-stone-100 px-5 py-8 text-stone-900 sm:px-8">
      <header className="mx-auto mb-8 flex w-full max-w-6xl flex-wrap items-center justify-between gap-4">
        <div className="flex items-center gap-6">
          <Link
            className="flex items-center gap-2.5 text-xl font-semibold tracking-tight text-stone-900"
            href="/"
          >
            <BrandLogo className="h-9 w-9" />
            <span>우리집</span>
          </Link>
          <nav className="flex rounded-full bg-white p-1 shadow-sm" aria-label="주요 메뉴">
            <NavigationLink active={pathname === "/"} href="/">
              거래
            </NavigationLink>
            <NavigationLink active={pathname === "/stats"} href="/stats">
              통계
            </NavigationLink>
          </nav>
        </div>
        <div className="flex items-center gap-4 text-sm text-stone-600">
          <span>{currentUser.displayName}</span>
          <button
            className="font-medium text-emerald-700 hover:text-emerald-800"
            onClick={logout}
            type="button"
          >
            로그아웃
          </button>
        </div>
      </header>
      {children(currentUser)}
    </main>
  );
}

function NavigationLink({
  active,
  children,
  href,
}: {
  active: boolean;
  children: ReactNode;
  href: string;
}) {
  return (
    <Link
      aria-current={active ? "page" : undefined}
      className={`rounded-full px-4 py-2 text-sm font-medium transition ${
        active
          ? "bg-emerald-700 text-white"
          : "text-stone-600 hover:bg-stone-100"
      }`}
      href={href}
    >
      {children}
    </Link>
  );
}

function authErrorMessage(authError: string): string {
  switch (authError) {
    case "not_allowed":
      return "허용되지 않은 Google 계정입니다.";
    case "session_expired":
      return "로그인 시간이 만료되었습니다. 다시 시도해 주세요.";
    default:
      return "Google 로그인을 완료하지 못했습니다. 다시 시도해 주세요.";
  }
}
