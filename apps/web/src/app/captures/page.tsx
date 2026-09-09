"use client";

import { AuthenticatedShell } from "../authenticated-shell";
import { CapturePanel } from "../capture-panel";

export default function CapturesPage() {
  return <AuthenticatedShell>{(user) => <CapturePanel currentUserId={user.id} />}</AuthenticatedShell>;
}
