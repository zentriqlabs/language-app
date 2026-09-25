import { createHash } from 'node:crypto';
import { mkdir, readFile, writeFile } from 'node:fs/promises';
import path from 'node:path';

/**
 * Enterprise-style TTS cache: content-addressed audio blobs on disk.
 * Redis can front this layer later; the API surface stays stable.
 */
export class TtsAudioCache {
  constructor(private readonly directory: string) {}

  static async open(directory: string) {
    await mkdir(directory, { recursive: true });
    return new TtsAudioCache(directory);
  }

  key(model: string, voice: string, text: string) {
    return createHash('sha256').update(`${model}\0${voice}\0${text.trim()}`).digest('hex');
  }

  pathFor(key: string) {
    return path.join(this.directory, `${key}.mp3`);
  }

  async read(key: string): Promise<Buffer | null> {
    try {
      const data = await readFile(this.pathFor(key));
      return data.length > 0 && data.length <= 2_000_000 ? data : null;
    } catch { return null; }
  }

  async write(key: string, audio: Buffer) {
    if (!audio.length || audio.length > 2_000_000) return;
    await writeFile(this.pathFor(key), audio);
  }
}
