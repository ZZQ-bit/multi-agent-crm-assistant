import type {
  DealDeskAssistantReply,
  DealDeskAssistantReplyKind,
  DealDeskBoundObjectPayload,
  DealDeskChatflowResponse,
  DealDeskTurnType,
  DealDeskWritebackPayload,
} from '../types';
import { formatDealDeskClockTime } from '../dealDeskTime';
import { buildProcessSummaryText, normalizeProcessEvents } from './dealDeskProcessFallback';

function mapTurnTypeToReplyKind(turnType: DealDeskTurnType, status?: string): DealDeskAssistantReplyKind {
  if (turnType === 'writeback_confirm') {
    return 'writeback-confirm';
  }

  if (turnType === 'writeback_result') {
    return status === 'cancelled' ? 'writeback-cancel' : 'writeback-success';
  }

  if (turnType === 'text_analysis') {
    return 'analysis';
  }

  return 'quick-answer';
}

function normalizeWriteback(writeback?: DealDeskWritebackPayload) {
  return writeback?.id && writeback.status ? writeback : undefined;
}

function normalizeBoundObject(boundObject?: DealDeskBoundObjectPayload) {
  return boundObject?.objectType && boundObject.objectId ? boundObject : undefined;
}

function shouldExposeProcessEvents(turnType: DealDeskTurnType) {
  return turnType === 'text_analysis' || turnType === 'writeback_confirm' || turnType === 'writeback_result';
}

function adaptChatflowPayloadToAssistantReply(payload: DealDeskChatflowResponse): DealDeskAssistantReply {
  const processEvents = shouldExposeProcessEvents(payload.turnType)
    ? normalizeProcessEvents(
        payload.processEvents?.map((event) => ({
          id: event.id,
          type: event.type,
          text: event.text,
          status: event.status,
          evidenceRefs: event.evidenceRefs,
        })),
        payload.turnType
      )
    : [];
  const writeback = normalizeWriteback(payload.writeback);
  const boundObject = normalizeBoundObject(payload.boundObject);
  const replyKind = mapTurnTypeToReplyKind(payload.turnType, writeback?.status);
  const process =
    processEvents.length > 0
      ? {
          summary: buildProcessSummaryText(processEvents, payload.turnType),
          expanded: false,
          events: processEvents,
        }
      : undefined;

  return {
    kind: replyKind,
    writeback,
    boundObject,
    conversationId: payload.conversationId,
    messageId: payload.messageId,
    suggestedSessionTitle: payload.suggestedSessionTitle,
    turn: {
      time: formatDealDeskClockTime(),
      text: payload.answerText,
      process,
      status: payload.turnType === 'failed' ? 'failed' : undefined,
    },
  };
}

export default adaptChatflowPayloadToAssistantReply;
