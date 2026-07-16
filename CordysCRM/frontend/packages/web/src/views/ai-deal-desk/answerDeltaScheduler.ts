const defaultChunkSize = 36;
const defaultIntervalMs = 28;

export type AnswerDeltaMeta = {
  conversationId?: string;
  messageId?: string;
  taskId?: string;
};

type AnswerDeltaSchedulerOptions = {
  chunkSize?: number;
  intervalMs?: number;
};

function splitAnswerDelta(text: string, chunkSize: number) {
  const characters = Array.from(text);
  const chunks: string[] = [];
  for (let offset = 0; offset < characters.length; offset += chunkSize) {
    chunks.push(characters.slice(offset, offset + chunkSize).join(''));
  }
  return chunks;
}

export function createAnswerDeltaScheduler(
  onAnswerDelta?: (text: string, meta?: AnswerDeltaMeta) => void,
  signal?: AbortSignal,
  options: AnswerDeltaSchedulerOptions = {}
) {
  const chunkSize = Math.max(1, options.chunkSize ?? defaultChunkSize);
  const intervalMs = Math.max(0, options.intervalMs ?? defaultIntervalMs);
  let playback = Promise.resolve();
  let lastEmittedAt = 0;
  let stopped = signal?.aborted ?? false;

  const stop = () => {
    stopped = true;
  };
  signal?.addEventListener('abort', stop, { once: true });

  function enqueue(text: string, meta?: AnswerDeltaMeta) {
    if (!onAnswerDelta || !text || stopped) {
      return;
    }

    splitAnswerDelta(text, chunkSize).forEach((chunk) => {
      playback = playback.then(async () => {
        if (stopped) return;

        const elapsed = Date.now() - lastEmittedAt;
        const waitMs = lastEmittedAt ? Math.max(0, intervalMs - elapsed) : 0;
        if (waitMs) {
          await new Promise((resolve) => setTimeout(resolve, waitMs));
        }
        if (stopped) return;

        onAnswerDelta(chunk, meta);
        lastEmittedAt = Date.now();
      });
    });
  }

  async function flush() {
    await playback;
  }

  function dispose() {
    stop();
    signal?.removeEventListener('abort', stop);
  }

  return { enqueue, flush, dispose };
}
