type TranslateResult = {
  originalText: string;
  translatedText: string;
  fromLanguage: string;
  confidence: number;
  provider: string;
  processingTime: number;
};

// REC-004 — upstream timeout for OpenAI fetch (10 seconds)
const OPENAI_TIMEOUT_MS = 10_000;

export class UnifiedTranslationService {
  async translateText(
    text: string,
    sourceCode: string | null,
    targetCode: string
  ): Promise<TranslateResult> {
    const started = Date.now();
    const apiKey = process.env.OPENAI_API_KEY;

    if (!apiKey) {
      throw new Error("OPENAI_API_KEY is missing");
    }

    const targetName = targetCode.toUpperCase() === "DE" ? "German" : targetCode;
    const sourceHint = sourceCode ? `Source language: ${sourceCode}.` : "Detect source language automatically.";

    // REC-004: AbortController with 10s timeout to prevent serverless function
    // from hanging on a stalled OpenAI response.
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), OPENAI_TIMEOUT_MS);

    let response: Response;
    try {
      response = await fetch("https://api.openai.com/v1/chat/completions", {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${apiKey}`,
        },
        body: JSON.stringify({
          model: "gpt-4.1-mini",
          temperature: 0.2,
          messages: [
            {
              role: "system",
              content:
                "You are a translation engine. Return only the translated text, without explanations.",
            },
            {
              role: "user",
              content: `${sourceHint} Translate this text to ${targetName}: ${text}`,
            },
          ],
        }),
        signal: controller.signal,
      });
    } catch (err) {
      if (err instanceof Error && err.name === "AbortError") {
        throw new Error(`OpenAI timeout after ${OPENAI_TIMEOUT_MS}ms`);
      }
      throw err;
    } finally {
      clearTimeout(timer);
    }

    if (!response.ok) {
      const errText = await response.text();
      throw new Error(`OpenAI error: ${response.status} ${errText}`);
    }

    const data = await response.json();
    const translatedText =
      data?.choices?.[0]?.message?.content?.trim();

    if (!translatedText) {
      throw new Error("Empty translation response");
    }

    return {
      originalText: text,
      translatedText,
      fromLanguage: sourceCode?.toLowerCase() || "auto",
      confidence: 0.95,
      provider: "openai",
      processingTime: Date.now() - started,
    };
  }
}