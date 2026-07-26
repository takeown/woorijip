import type { Metadata } from "next";
import localFont from "next/font/local";
import "./globals.css";

const maruBuri = localFont({
  src: [
    {
      path: "./fonts/MaruBuri-Regular.otf",
      weight: "400",
      style: "normal",
    },
    {
      path: "./fonts/MaruBuri-SemiBold.otf",
      weight: "600",
      style: "normal",
    },
    {
      path: "./fonts/MaruBuri-Bold.otf",
      weight: "700",
      style: "normal",
    },
  ],
  display: "swap",
  variable: "--font-maru-buri",
});

export const metadata: Metadata = {
  title: "우리집",
  description: "부부가 함께 사용하는 비공개 가계부",
  icons: {
    icon: [{ url: "/icon.svg", type: "image/svg+xml" }],
  },
};

export default function RootLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <html
      lang="ko"
      className={`${maruBuri.variable} h-full antialiased`}
    >
      <body className="min-h-full flex flex-col">{children}</body>
    </html>
  );
}
