import type { FastifyInstance } from 'fastify';
import { createHash } from 'node:crypto';
import { ServiceError } from './errors.js';
import type { OpenRouterModels } from './openrouter-config.js';
import type { TtsAudioCache } from './tts-audio-cache.js';

const validText = (value: unknown, max: number) =>
  typeof value === 'string' && value.trim() && value.length <= max && !/[\uD800-\uDFFF]/u.test(value);

/** Cached Kokoro TTS proxy for mural.chat / self-hosted API operators. */
export function registerOpenRouterRoutes(app: FastifyInstance, key: string, models: OpenRouterModels, cache: TtsAudioCache) {
  app.post('/v1/openrouter/tts', async (request) => {
    const body = request.body as Record<string, unknown>;
    const text = validText(body.text, 900) ? body.text as string : (() => { throw new ServiceError('invalid_request'); })();
    const voice = typeof body.voice === 'string' && body.voice.length <= 64 ? body.voice : models.ttsVoice;
    const cacheKey = cache.key(models.tts, voice, text);
    const hit = await cache.read(cacheKey);
    if (hit) return { cached: true, audioBase64: hit.toString('base64'), digest: cacheKey };
    const response = await fetch('https://openrouter.ai/api/v1/audio/speech', {
      method: 'POST',
      redirect: 'error',
      signal: AbortSignal.timeout(60_000),
      headers: {
        Authorization: `Bearer ${key}`,
        'Content-Type': 'application/json',
        'HTTP-Referer': 'https://mural.chat/',
        'X-OpenRouter-Title': 'Mural API',
      },
      body: JSON.stringify({ model: models.tts, input: text, voice }),
    });
    if (!response.ok) { await response.body?.cancel(); throw new ServiceError('openrouter_tts_unavailable', 502); }
    const audio = Buffer.from(await response.arrayBuffer());
    await cache.write(cacheKey, audio);
    return { cached: false, audioBase64: audio.toString('base64'), digest: cacheKey };
  });

  app.get('/v1/openrouter/models', async () => ({
    llm: models.llm,
    stt: models.stt,
    tts: models.tts,
    voiceBrevity: models.voiceBrevity,
    ttsVoice: models.ttsVoice,
    cache: 'disk',
    cacheDigestExample: createHash('sha256').update('demo').digest('hex'),
  }));
}
