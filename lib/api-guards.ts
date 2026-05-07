// lib/api-guards.ts
// Shared API guards used by /api/translate and /api/tts route handlers.
//
// REC-001 — auth via X-Dashka-Token header (env: DASHKA_API_TOKEN)
// REC-005 — language code validation against the allowed LangCode set
//
// Single source of truth for ALLOWED_LANGS — must mirror LangCode in
// features/translator/types.ts. Kept here as a runtime tuple so that
// route handlers can validate without importing client-side modules.

import { NextRequest, NextResponse } from "next/server";

/* ------------------------------------------------------------------ */
/* Allowed languages — mirror of LangCode from features/translator/types.ts */
/* ------------------------------------------------------------------ */

export const ALLOWED_LANGS = [
  "RU", "DE", "EN", "PL", "ZH", "FR", "IT", "ES", "LV", "LT", "UA",
] as const;

export type AllowedLang = (typeof ALLOWED_LANGS)[number];

const ALLOWED_SET = new Set<string>(ALLOWED_LANGS);

export function isAllowedLang(code: unknown): code is AllowedLang {
  return typeof code === "string" && ALLOWED_SET.has(code.toUpperCase());
}

/* ------------------------------------------------------------------ */
/* REC-001 — token guard                                              */
/* ------------------------------------------------------------------ */

const TOKEN_HEADER = "x-dashka-token";

/**
 * Verifies the X-Dashka-Token header against env DASHKA_API_TOKEN.
 *
 * Behavior:
 *   - If DASHKA_API_TOKEN is not set in env -> guard is DISABLED (returns null).
 *     This keeps local dev / preview environments frictionless. Production
 *     deployments MUST set DASHKA_API_TOKEN in Vercel env.
 *   - If env token is set but request token is missing/wrong -> 401.
 *   - If tokens match -> returns null (request continues).
 *
 * Returns NextResponse on rejection, null on pass.
 */
export function verifyToken(req: NextRequest): NextResponse | null {
  const expected = process.env.DASHKA_API_TOKEN;
  if (!expected) {
    // Guard disabled (dev mode). Production must set the env var.
    return null;
  }

  const provided = req.headers.get(TOKEN_HEADER);
  if (!provided || provided !== expected) {
    return NextResponse.json(
      { status: "error", message: "Unauthorized" },
      { status: 401 }
    );
  }
  return null;
}

/* ------------------------------------------------------------------ */
/* REC-004 — fetch with AbortController timeout                       */
/* ------------------------------------------------------------------ */

/**
 * fetch() wrapped with a timeout. Throws an Error("Upstream timeout (...ms)")
 * if the upstream does not respond within timeoutMs.
 */
export async function fetchWithTimeout(
  input: string,
  init: RequestInit,
  timeoutMs: number
): Promise<Response> {
  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), timeoutMs);
  try {
    const res = await fetch(input, { ...init, signal: controller.signal });
    return res;
  } catch (err) {
    if (err instanceof Error && err.name === "AbortError") {
      throw new Error(`Upstream timeout (${timeoutMs}ms)`);
    }
    throw err;
  } finally {
    clearTimeout(timer);
  }
}
