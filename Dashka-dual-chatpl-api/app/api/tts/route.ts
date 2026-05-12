// app/api/tts/route.ts
// v2.1 — proxies text → Grok TTS → returns audio/mpeg
//
// Environment: XAI_API_KEY must be set in Vercel
//              DASHKA_API_TOKEN (optional, REC-001) — when set, requires
//              X-Dashka-Token header on every request

import { NextRequest, NextResponse } from "next/server";
import { verifyToken } from "@/lib/api-guards";

type Voice = "eve" | "leo" | "ara" | "rex" | "sal";
const DEFAULT_VOICE: Voice = "eve";
const ALLOWED_VOICES: Voice[] = ["eve", "leo", "ara", "rex", "sal"];

// REC-002 — full LANG_MAP covering every LangCode in features/translator/types.ts.
// Grok TTS expects ISO 639-1 primary tags. UA → "uk" (Grok uses ISO code, not
// country code). Both uppercase and lowercase keys are accepted to match the
// shape of payloads coming from Web (uppercase) and Mobile clients (lowercase).
const LANG_MAP: Record<string, string> = {
  RU: "ru", DE: "de", EN: "en", PL: "pl", ZH: "zh",
  FR: "fr", IT: "it", ES: "es", LV: "lv", LT: "lt", UA: "uk",
  ru: "ru", de: "de", en: "en", pl: "pl", zh: "zh",
  fr: "fr", it: "it", es: "es", lv: "lv", lt: "lt", ua: "uk",
};

export async function POST(req: NextRequest) {
  // REC-001 — auth via X-Dashka-Token header
  const authError = verifyToken(req);
  if (authError) return authError;

  try {
    const apiKey = process.env.XAI_API_KEY;
    if (!apiKey) {
      return NextResponse.json(
        { status: "error", message: "XAI_API_KEY is missing" },
        { status: 500 }
      );
    }

    const body = await req.json();
    const text = (body?.text ?? "").trim();
    const languageIn = (body?.language ?? body?.lang ?? "en").toString();
    const voiceIn = (body?.voice ?? body?.voice_id ?? DEFAULT_VOICE).toString().toLowerCase();

    if (!text) {
      return NextResponse.json(
        { status: "error", message: "Text is required" },
        { status: 400 }
      );
    }
    if (text.length > 5000) {
      return NextResponse.json(
        { status: "error", message: "Text too long (max 5000 chars)" },
        { status: 400 }
      );
    }

    const language = LANG_MAP[languageIn] ?? languageIn.toLowerCase().slice(0, 2);
    const voice: Voice = (ALLOWED_VOICES as string[]).includes(voiceIn)
      ? (voiceIn as Voice)
      : DEFAULT_VOICE;

    const grokResponse = await fetch("https://api.x.ai/v1/tts", {
      method: "POST",
      headers: {
        Authorization: `Bearer ${apiKey}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({
        text,
        voice_id: voice,
        language,
      }),
    });

    if (!grokResponse.ok) {
      const errText = await grokResponse.text().catch(() => "");
      return NextResponse.json(
        {
          status: "error",
          message: `Grok TTS error: ${grokResponse.status}`,
          details: errText.slice(0, 500),
        },
        { status: grokResponse.status }
      );
    }

    const audioBuffer = await grokResponse.arrayBuffer();
    return new Response(audioBuffer, {
      status: 200,
      headers: {
        "Content-Type": "audio/mpeg",
        "Cache-Control": "public, max-age=86400, immutable",
      },
    });
  } catch (error) {
    const message = error instanceof Error ? error.message : "Internal server error";
    return NextResponse.json(
      { status: "error", message },
      { status: 500 }
    );
  }
}
