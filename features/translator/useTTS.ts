// features/translator/useTTS.ts
"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import type { LangCode, TtsVoice } from "./types";

interface TTSRequest {
  text: string;
  language: LangCode;
  voice: TtsVoice;
}

// Простой in-memory LRU-кэш blob URL на ключ text+voice+lang.
// Хэш собирается по содержимому — одинаковые фразы не обращаются к Grok API повторно.
const CACHE_SIZE = 50;
const cache = new Map<string, string>();

function cacheKey(r: TTSRequest): string {
  return `${r.language}|${r.voice}|${r.text}`;
}

async function fetchTts(r: TTSRequest): Promise<string> {
  const key = cacheKey(r);
  const hit = cache.get(key);
  if (hit) {
    // refresh LRU
    cache.delete(key);
    cache.set(key, hit);
    return hit;
  }

  const res = await fetch("/api/tts", {
    method: "POST",
    headers: (() => {
      // REC-001 — attach X-Dashka-Token header when configured.
      // NEXT_PUBLIC_DASHKA_TOKEN is exposed to the bundle on purpose: this
      // is an abuse-deterrent (blocks curl/scripts), not a real secret.
      const h: Record<string, string> = { "Content-Type": "application/json" };
      const token = process.env.NEXT_PUBLIC_DASHKA_TOKEN;
      if (token) h["X-Dashka-Token"] = token;
      return h;
    })(),
    body: JSON.stringify({
      text: r.text,
      language: r.language,
      voice: r.voice,
    }),
  });

  if (!res.ok) {
    let msg = `TTS failed: HTTP ${res.status}`;
    try {
      const data = await res.json();
      if (data?.message) msg = data.message;
    } catch {
      /* ignore */
    }
    throw new Error(msg);
  }

  const blob = await res.blob();
  const url = URL.createObjectURL(blob);

  // Вытеснить самую старую запись если cache переполнен
  if (cache.size >= CACHE_SIZE) {
    const firstKey = cache.keys().next().value;
    if (firstKey) {
      const oldUrl = cache.get(firstKey);
      if (oldUrl) URL.revokeObjectURL(oldUrl);
      cache.delete(firstKey);
    }
  }
  cache.set(key, url);
  return url;
}

export function useTTS() {
  const audioRef = useRef<HTMLAudioElement | null>(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // создаём singleton <audio>
  useEffect(() => {
    if (typeof window === "undefined") return;
    const el = new Audio();
    el.preload = "auto";
    audioRef.current = el;

    const onEnd = () => setIsPlaying(false);
    const onErr = () => {
      setIsPlaying(false);
      setError("Не удалось воспроизвести аудио");
    };
    el.addEventListener("ended", onEnd);
    el.addEventListener("error", onErr);

    return () => {
      el.pause();
      el.removeEventListener("ended", onEnd);
      el.removeEventListener("error", onErr);
      audioRef.current = null;
    };
  }, []);

  const stop = useCallback(() => {
    const el = audioRef.current;
    if (!el) return;
    el.pause();
    el.currentTime = 0;
    setIsPlaying(false);
  }, []);

  const play = useCallback(
    async (req: TTSRequest) => {
      const el = audioRef.current;
      if (!el || !req.text.trim()) return;
      setError(null);
      // Остановить предыдущее воспроизведение
      el.pause();
      el.currentTime = 0;
      try {
        const url = await fetchTts(req);
        el.src = url;
        await el.play();
        setIsPlaying(true);
      } catch (e) {
        setError(e instanceof Error ? e.message : "Ошибка TTS");
        setIsPlaying(false);
      }
    },
    []
  );

  return { play, stop, isPlaying, error };
}
