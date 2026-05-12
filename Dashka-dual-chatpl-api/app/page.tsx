"use client";

import { useCallback, useEffect, useState } from "react";
import { usePane } from "@/features/translator/usePane";
import { useTheme } from "@/features/translator/useTheme";
import { useTTS } from "@/features/translator/useTTS";
import Pane from "@/features/translator/Pane";
import {
  type LangCode,
  type TtsVoice,
  LANG_META,
} from "@/features/translator/types";
import { LEFT_PANE, RIGHT_PANE, PARTNER_LANG } from "@/features/translator/paneConfigs";

export default function Home() {
  const { theme, toggle: toggleTheme, mounted } = useTheme();
  const { play, stop, isPlaying, error: ttsError } = useTTS();
  const [autoTTS, setAutoTTS] = useState(true);
  const [activePlayingPane, setActivePlayingPane] = useState<"left" | "right" | null>(null);
  const [online, setOnline] = useState<boolean | null>(null);

  useEffect(() => {
    if (typeof window === "undefined") return;
    const saved = window.localStorage.getItem("dashka-auto-tts");
    if (saved === "0") setAutoTTS(false);
  }, []);
  useEffect(() => {
    if (typeof window === "undefined") return;
    window.localStorage.setItem("dashka-auto-tts", autoTTS ? "1" : "0");
  }, [autoTTS]);

  useEffect(() => {
    let mounted2 = true;
    const ping = async () => {
      try {
        const res = await fetch("/api/health", { cache: "no-store" });
        if (mounted2) setOnline(res.ok);
      } catch {
        if (mounted2) setOnline(false);
      }
    };
    ping();
    const id = window.setInterval(ping, 30_000);
    return () => {
      mounted2 = false;
      window.clearInterval(id);
    };
  }, []);

  const playFor = useCallback(
    (paneId: "left" | "right", text: string, lang: LangCode, voice: TtsVoice) => {
      setActivePlayingPane(paneId);
      void play({ text, language: lang, voice });
    },
    [play]
  );
  const onStop = useCallback(() => {
    stop();
    setActivePlayingPane(null);
  }, [stop]);

  useEffect(() => {
    if (!isPlaying) setActivePlayingPane(null);
  }, [isPlaying]);

  const leftPane = usePane({
    config: LEFT_PANE,
    autoTTS,
    onTranslated: (text, _lang, voice) => {
      playFor("left", text, LEFT_PANE.to, voice);
    },
  });

  const rightPane = usePane({
    config: RIGHT_PANE,
    autoTTS,
    onTranslated: (text, _lang, voice) => {
      playFor("right", text, RIGHT_PANE.to, voice);
    },
  });

  const partnerMeta = LANG_META[PARTNER_LANG];

  return (
    <div className="page-wrap">
      <div className="page-inner-dual">
        <header className="app-header">
          <div className="app-title-block">
            <h1 className="app-title">
              <span>{partnerMeta.flag}</span>
              <span>Dashka</span>
            </h1>
            <p className="app-subtitle">
              Dual · {partnerMeta.nativeName} ↔ Русский · v2.1
            </p>
          </div>
          <div className="app-actions">
            <label className="auto-tts-toggle" title="Авто-озвучка перевода">
              <input
                type="checkbox"
                checked={autoTTS}
                onChange={(e) => setAutoTTS(e.target.checked)}
              />
              <span>🔊 Auto</span>
            </label>
            {mounted && (
              <button
                type="button"
                onClick={toggleTheme}
                className="pill-btn"
                aria-label="Переключить тему"
                title={theme === "dark" ? "Светлая тема" : "Тёмная тема"}
              >
                {theme === "dark" ? "☀️" : "🌙"}
              </button>
            )}
            <span className="status-badge">
              <span className={`status-dot ${online ? "status-dot-on" : "status-dot-off"}`} />
              {online === null ? "…" : online ? "Online" : "Offline"}
            </span>
          </div>
        </header>

        {ttsError && <div className="tts-error">⚠ TTS: {ttsError}</div>}

        <div className="dual-grid">
          <Pane
            pane={leftPane}
            onPlay={(text, lang, voice) => playFor("left", text, lang, voice)}
            onStop={onStop}
            isPlaying={isPlaying && activePlayingPane === "left"}
          />
          <Pane
            pane={rightPane}
            onPlay={(text, lang, voice) => playFor("right", text, lang, voice)}
            onStop={onStop}
            isPlaying={isPlaying && activePlayingPane === "right"}
          />
        </div>

        <footer className="app-footer">
          Dashka · v2.1 Dual · {partnerMeta.flag} {partnerMeta.nativeName} · Grok TTS · Solar Team 🚀
        </footer>
      </div>
    </div>
  );
}
