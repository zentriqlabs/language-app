import { readFile } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export type OpenRouterModels = {
  llm: string;
  stt: string;
  tts: string;
  voiceBrevity: string;
  ttsVoice: string;
};

const defaults: OpenRouterModels = {
  llm: 'deepseek/deepseek-v4-pro-0813',
  stt: 'qwen/qwen3-asr-flash-2026-02-10',
  tts: 'hexgrad/kokoro-82m',
  voiceBrevity: 'Antworte im Sprachmodus stets kurz, direkt und in maximal zwei kurzen Sätzen.',
  ttsVoice: 'af_bella',
};

/** Loads shared/openrouter/models.json when present; env overrides win. */
export async function loadOpenRouterModels(env: NodeJS.ProcessEnv = process.env): Promise<OpenRouterModels> {
  let file = defaults;
  try {
    const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
    const raw = JSON.parse(await readFile(path.join(root, 'shared/openrouter/models.json'), 'utf8')) as Record<string, unknown>;
    const models = raw.models as Record<string, string> | undefined;
    const voicePrompt = raw.voicePrompt as Record<string, string> | undefined;
    const tts = raw.tts as Record<string, unknown> | undefined;
    file = {
      llm: models?.llm ?? defaults.llm,
      stt: models?.stt ?? defaults.stt,
      tts: models?.tts ?? defaults.tts,
      voiceBrevity: voicePrompt?.brevity ?? defaults.voiceBrevity,
      ttsVoice: (tts?.defaultVoice as string | undefined) ?? defaults.ttsVoice,
    };
  } catch { /* keep defaults */ }
  return {
    llm: env.OPENROUTER_LLM_MODEL ?? file.llm,
    stt: env.OPENROUTER_STT_MODEL ?? file.stt,
    tts: env.OPENROUTER_TTS_MODEL ?? file.tts,
    voiceBrevity: env.OPENROUTER_VOICE_BREVITY ?? file.voiceBrevity,
    ttsVoice: env.OPENROUTER_TTS_VOICE ?? file.ttsVoice,
  };
}
