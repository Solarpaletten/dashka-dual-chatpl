import { NextRequest, NextResponse } from "next/server";
import { UnifiedTranslationService } from "@/lib/translator";
import {
  verifyToken,
  isAllowedLang,
  ALLOWED_LANGS,
} from "@/lib/api-guards";

const translationService = new UnifiedTranslationService();

// REC-003 — hard cap on input text length (mirrors /api/tts cap)
const MAX_TEXT_LENGTH = 5000;

export async function POST(req: NextRequest) {
  // REC-001 — auth via X-Dashka-Token header
  const authError = verifyToken(req);
  if (authError) return authError;

  try {
    const body = await req.json();

    const {
      text,
      source_language,
      target_language,
      fromLang,
      toLang,
      from,
      to,
    } = body ?? {};

    if (!text || typeof text !== "string" || !text.trim()) {
      return NextResponse.json(
        {
          status: "error",
          message: "Текст не указан",
        },
        { status: 400 }
      );
    }

    // REC-003 — length cap
    if (text.length > MAX_TEXT_LENGTH) {
      return NextResponse.json(
        {
          status: "error",
          message: `Text too long (max ${MAX_TEXT_LENGTH} chars, got ${text.length})`,
        },
        { status: 400 }
      );
    }

    const sourceCodeRaw =
      (source_language || fromLang || from || "").toString().toUpperCase() || null;
    const targetCodeRaw =
      (target_language || toLang || to || "").toString().toUpperCase();

    // REC-005 — target_language is required and must be in the allowed set
    if (!targetCodeRaw) {
      return NextResponse.json(
        {
          status: "error",
          message: "target_language is required",
        },
        { status: 400 }
      );
    }

    if (!isAllowedLang(targetCodeRaw)) {
      return NextResponse.json(
        {
          status: "error",
          message: `Invalid target_language. Allowed: ${ALLOWED_LANGS.join(", ")}`,
        },
        { status: 400 }
      );
    }

    // REC-005 — source_language is optional (auto-detect), but if provided must be valid
    if (sourceCodeRaw && !isAllowedLang(sourceCodeRaw)) {
      return NextResponse.json(
        {
          status: "error",
          message: `Invalid source_language. Allowed: ${ALLOWED_LANGS.join(", ")}`,
        },
        { status: 400 }
      );
    }

    const sourceCode = sourceCodeRaw;
    const targetCode = targetCodeRaw;

    const result = await translationService.translateText(
      text.trim(),
      sourceCode,
      targetCode
    );

    return NextResponse.json({
      status: "success",
      original_text: result.originalText,
      translated_text: result.translatedText,
      source_language: (result.fromLanguage || sourceCode || "auto").toLowerCase(),
      target_language: targetCode.toLowerCase(),
      confidence: result.confidence,
      timestamp: new Date().toISOString(),
      processing_time: result.processingTime,
      provider: result.provider,
      from_cache: false,
    });
  } catch (error) {
    const message =
      error instanceof Error ? error.message : "Internal server error";

    return NextResponse.json(
      {
        status: "error",
        message,
      },
      { status: 500 }
    );
  }
}