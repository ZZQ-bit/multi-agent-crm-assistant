import { buildProcessSummaryText, normalizeProcessEvents } from '../adapters/dealDeskProcessFallback';
import { formatDealDeskClockTime } from '../dealDeskTime';
import type { DealDeskAssistantReplyPayload, DealDeskProcessBlock, DealDeskReference, DealDeskTurn } from '../types';

const SYNTHETIC_PROCESS_EVENT_IDS = new Set(['pending-status', 'live-thinking']);

function buildCompletedProcess(process?: DealDeskProcessBlock) {
  const realEvents = process?.events.filter((event) => !SYNTHETIC_PROCESS_EVENT_IDS.has(event.id)) || [];

  if (!realEvents.length) {
    return undefined;
  }

  const hasRunningEvent = realEvents.some((event) => event.status === 'running');
  const completedEvents = normalizeProcessEvents(
    realEvents.map((event) => ({
      ...event,
      status: event.status === 'running' ? ('completed' as const) : event.status,
      text: event.text,
    }))
  );

  return {
    ...process,
    summary: hasRunningEvent ? buildProcessSummaryText(completedEvents) : process?.summary || buildProcessSummaryText(completedEvents),
    expanded: false,
    events: completedEvents,
  } satisfies DealDeskProcessBlock;
}

function resolveFinalProcess(replyProcess?: DealDeskProcessBlock): DealDeskProcessBlock | undefined {
  if (replyProcess?.events?.length) {
    return buildCompletedProcess(replyProcess);
  }

  return replyProcess;
}

export function createPendingAssistantTurn(id: string, _boundObject: DealDeskReference | null): DealDeskTurn {
  return {
    id,
    role: 'assistant',
    text: '',
    time: formatDealDeskClockTime(),
    status: 'generating',
  };
}

export function buildResolvedAssistantTurn(
  pendingTurn: DealDeskTurn,
  replyTurn: DealDeskAssistantReplyPayload
): DealDeskTurn {
  const resolvedProcess = resolveFinalProcess(replyTurn.process);
  return {
    ...pendingTurn,
    ...replyTurn,
    id: pendingTurn.id,
    role: 'assistant',
    process: resolvedProcess,
    status: replyTurn.status ?? 'default',
  };
}
