---
name: woorijip-design
description: Apply and preserve the Woorijip product design language in apps/web. Use for new screens, UI redesigns, styling or layout changes, responsive work, UX copy, accessibility audits, and visual reviews in this repository. Combine Hallmark's anti-AI-slop audit with Woorijip-specific typography, color, information hierarchy, mobile, interaction, and validation rules.
---

# Woorijip Design

Design a private household ledger for two people that feels warm, calm, and immediately understandable. Favor useful hierarchy and honest states over decoration.

## Start with context

1. Read the repository and `apps/web` instructions.
2. Read `docs/PLAN.md` before changing a feature or screen flow.
3. Inspect the current component, nearby screens, and relevant tests before proposing a change.
4. Use the `hallmark` skill when available. Treat it as the general audit method and this skill as the project-specific decision layer.
5. Explain concrete issues and obtain permission before editing, as required by the repository instructions.

## Hallmark dependency

- Treat `hallmark` as an optional external skill, not as content bundled with this repository.
- Expect the project skill to travel with the repository after commit, but do not assume a personal Hallmark installation travels with it.
- On each new computer, install Hallmark separately from `https://github.com/Nutlope/hallmark` with the skill installer before running the complete Hallmark audit.
- If Hallmark is unavailable, continue with the Woorijip rules in this skill and state explicitly that the Hallmark-specific audit was not run.

## Preserve the product character

- Keep user-facing copy in natural Korean household language. Prefer phrases such as “이번 달 우리집” and “지금까지 쓴 돈” over financial jargon.
- Keep MaruBuri for brand, headings, and prose. Use the local `font-ui` system sans stack for amounts, dates, counts, and dense data.
- Apply `tabular-nums` to values that users compare vertically or across repeated rows.
- Preserve the existing house-and-two-people brand meaning. Do not replace it with generic fintech imagery.
- Use real product data and honest loading, empty, error, success, and disabled states. Do not invent metrics or testimonials.

## Build the visual hierarchy

- Use the semantic tokens in `apps/web/src/app/globals.css`: paper-like background, subtly tinted surfaces, soft borders, green accent, and explicit focus color.
- Avoid pure black, pure white, and ad hoc palette expansion when a semantic token expresses the role.
- Prefer one containment layer. Remove card-in-card stacking unless both boundaries communicate distinct interaction or ownership.
- Use spacing, dividers, and typography before adding another rounded container or shadow.
- Keep decorative eyebrow labels rare. Retain only labels that add meaning not already present in the heading.
- Let the primary fact dominate each view. On the mobile home, prioritize monthly spending, quick entry, and recent transactions in that order.
- Keep important text safe at narrow widths with `min-w-0`, truncation for replaceable labels, and wrapping for essential copy.

## Design interaction states

- Preserve labels, roles, `aria-current`, dialog semantics, keyboard operation, Escape handling, focus movement and restoration, `inert`, and safe-area behavior.
- Keep touch targets at least 44px high.
- Give interactive controls visible hover, focus-visible, active, disabled, and loading states where applicable.
- Make focus indication immediate. Transition background, color, or transform explicitly instead of using broad `transition` on focus rings.
- Do not communicate status by color alone; pair it with text.
- Respect reduced motion and avoid motion without feedback value.

## Work in vertical slices

1. Audit the complete affected flow, including narrow mobile, tablet, and desktop layouts.
2. State the audience, primary task, hierarchy problem, and behavior that must not regress.
3. Change the smallest reviewable slice that produces a meaningful user improvement.
4. Preserve API contracts, authentication boundaries, data ownership, and existing form state behavior unless the task explicitly changes them.
5. Do not add a UI package or state library while the repository instructions prohibit it.
6. Extract shared primitives only after the same semantic pattern genuinely repeats in at least two places.

## Validate before handoff

- Run `nvm use` before every `pnpm` command.
- Run the closest tests first, then `pnpm lint:web`, `pnpm test:web`, and `pnpm build:web` for the affected web scope.
- Check keyboard navigation, visible focus, contrast, long Korean copy, loading/error/empty states, and horizontal overflow at 320, 375, 414, and 768px.
- Use a real browser for changed screens when the required application state is available. State clearly when authentication or API availability prevents visual verification.
- Report build or test failures separately from successful compilation. Do not attribute pre-existing failures to the design change.
- Confirm that only intended files changed and record any remaining Hallmark findings as follow-up scope rather than silently expanding the task.
