import { RouteLocationNormalizedLoaded } from 'vue-router';
import { useMessage } from 'naive-ui';

import { useI18n } from '@lib/shared/hooks/useI18n';

import { buildProcessSummaryText, normalizeProcessEvents } from '../adapters/dealDeskProcessFallback';
import {
  createDealDeskConversation,
  deleteDealDeskConversation,
  getDealDeskConversation,
  listDealDeskConversations,
  saveDealDeskMessage,
  searchDealDeskCustomers,
  searchDealDeskOpportunities,
  stopDealDeskChat,
  updateDealDeskConversation,
} from '../api';
import { formatDealDeskClockTime, formatDealDeskSessionTime } from '../dealDeskTime';
import createDealDeskDifyProvider from '../providers/dealDeskDifyProvider';
import type {
  DealDeskAssistantReply,
  DealDeskBoundObjectPayload,
  DealDeskConversationSavePayload,
  DealDeskMentionOption,
  DealDeskMessageSavePayload,
  DealDeskProcessEvent,
  DealDeskProcessEventPayload,
  DealDeskReference,
  DealDeskSession,
  DealDeskStoredConversationPayload,
  DealDeskStoredMessagePayload,
  DealDeskTurn,
  DealDeskWritebackPayload,
} from '../types';
import {
  buildCustomerMentionOptionsFromList,
  buildOpportunityMentionOptionsFromList,
  getRouteReference,
  resolveBoundObject,
} from './useAiDealDeskChat.helpers';
import { buildResolvedAssistantTurn, createPendingAssistantTurn } from './useAiDealDeskPendingTurn';

const MAX_MENTION_OPTIONS = 5;
const MENTION_SEARCH_PAGE_SIZE = 30;
const MAX_IMAGE_ATTACHMENTS = 5;
const SYNTHETIC_PROCESS_EVENT_IDS = new Set(['pending-status', 'live-thinking']);
const STOPPED_GENERATION_TEXT = '已停止生成';

export default function useAiDealDeskChat(route: RouteLocationNormalizedLoaded) {
  const Message = useMessage();
  const { t } = useI18n();
  const sessions = ref<DealDeskSession[]>([]);
  const historyCollapsed = ref(false);
  const mentionOpen = ref(false);
  const draftMessage = ref('');
  const composerReferences = ref<DealDeskReference[]>([]);
  const activeSessionId = ref('');
  const activeGenerations = ref<
    Array<{
      sessionId: string;
      pendingTurnId: string;
      taskId: string | null;
      abortController: AbortController;
      stopping: boolean;
    }>
  >([]);
  const starters = [
    '看一下华东智造集团有哪些商机',
    '总结华东智造集团AI客服升级项目的推进情况',
    '分析华东智造集团AI客服升级项目的付款风险',
    '评审华东智造集团AI客服升级项目是否值得继续推进',
    '查询京东集团近期在AI客服方面的公开进展',
  ];
  const provider = createDealDeskDifyProvider();
  let isBootstrapping = false;
  let sequence = 0;
  let mentionSearchSequence = 0;
  let loadSequence = 0;

  const activeMentionQuery = computed(() => draftMessage.value.match(/@([^\s@]*)$/)?.[1] ?? '');
  const mentionOptions = ref<DealDeskMentionOption[]>([]);
  const activeSession = computed(() => sessions.value.find((item) => item.id === activeSessionId.value));
  const activeSessionGeneration = computed(() =>
    activeGenerations.value.find((generation) => generation.sessionId === activeSessionId.value)
  );
  const isResponding = computed(() => Boolean(activeSessionGeneration.value));
  const isStopping = computed(() => Boolean(activeSessionGeneration.value?.stopping));

  function nextId(prefix: string) {
    sequence += 1;
    return `${prefix}-${Date.now()}-${sequence}`;
  }

  function createUserTurn(text: string, references: DealDeskReference[]): DealDeskTurn {
    return {
      id: nextId('user'),
      role: 'user',
      text,
      time: formatDealDeskClockTime(),
      references: references.length ? references : undefined,
    };
  }

  function createAssistantTurn(partial: Omit<DealDeskTurn, 'id' | 'role'>): DealDeskTurn {
    return {
      id: nextId('assistant'),
      role: 'assistant',
      status: 'default',
      ...partial,
    };
  }

  function createEmptySession(title = '新会话'): DealDeskSession {
    return {
      id: nextId('session'),
      title,
      updatedAt: formatDealDeskSessionTime(),
      group: 'today',
      turns: [],
      boundObject: null,
      conversationId: null,
    };
  }

  function getSessionGroup(timestamp?: number): DealDeskSession['group'] {
    if (!timestamp) return 'today';
    const target = new Date(timestamp);
    const today = new Date();
    const todayStart = new Date(today.getFullYear(), today.getMonth(), today.getDate()).getTime();
    const targetStart = new Date(target.getFullYear(), target.getMonth(), target.getDate()).getTime();
    if (targetStart === todayStart) return 'today';
    if (targetStart === todayStart - 24 * 60 * 60 * 1000) return 'yesterday';
    return 'earlier';
  }

  function safeParseJson<T>(value?: string | null, fallback?: T): T | undefined {
    if (!value) return fallback;
    try {
      return JSON.parse(value) as T;
    } catch {
      return fallback;
    }
  }

  function safeStringify(value: unknown) {
    if (value === undefined || value === null) return null;
    return JSON.stringify(value);
  }

  function serializeReferences(references?: DealDeskReference[]) {
    if (!references?.length) return null;
    return JSON.stringify(
      references.map((reference) => ({
        id: reference.id,
        label: reference.label,
        type: reference.type,
        url: reference.rawFile ? undefined : reference.url,
        source: reference.source,
        mimeType: reference.mimeType,
        uploadFileId: reference.uploadFileId,
      }))
    );
  }

  function mapBoundReferenceToPayload(reference?: DealDeskReference | null): DealDeskBoundObjectPayload | null {
    if (!reference || (reference.type !== 'customer' && reference.type !== 'opportunity')) {
      return null;
    }
    return {
      objectType: reference.type,
      objectId: reference.id,
      objectName: reference.label.replace(/^@/, ''),
      source: reference.source || 'mention',
    };
  }

  function mapBoundPayloadToReference(payload?: DealDeskBoundObjectPayload | null): DealDeskReference | null {
    if (!payload) return null;
    return {
      id: payload.objectId,
      label: `@${payload.objectName}`,
      type: payload.objectType,
      source: payload.source,
    };
  }

  function conversationSavePayload(session: DealDeskSession, lastMessageText?: string): DealDeskConversationSavePayload {
    return {
      title: session.title,
      difyConversationId: session.conversationId || null,
      boundObjectJson: safeStringify(mapBoundReferenceToPayload(session.boundObject)),
      lastMessageText: lastMessageText ?? session.turns.at(-1)?.text ?? null,
    };
  }

  function messageSavePayload(
    turn: DealDeskTurn,
    options: {
      writeback?: DealDeskWritebackPayload;
      boundObject?: DealDeskReference | null;
      difyMessageId?: string;
    } = {}
  ): DealDeskMessageSavePayload {
    return {
      role: turn.role,
      content: turn.text || '',
      referencesJson: serializeReferences(turn.references),
      processEventsJson: safeStringify(turn.process?.events),
      writebackJson: safeStringify(options.writeback),
      boundObjectJson: safeStringify(mapBoundReferenceToPayload(options.boundObject)),
      difyMessageId: options.difyMessageId || null,
      status: turn.status || 'default',
    };
  }

  function sessionFromStored(conversation: DealDeskStoredConversationPayload, includeMessages = false): DealDeskSession {
    const boundObject = mapBoundPayloadToReference(
      safeParseJson<DealDeskBoundObjectPayload>(conversation.boundObjectJson || '')
    );
    return {
      id: conversation.id,
      remoteId: conversation.id,
      title: conversation.title || '新会话',
      updatedAt: conversation.updateTime ? formatDealDeskSessionTime(new Date(conversation.updateTime)) : formatDealDeskSessionTime(),
      group: getSessionGroup(conversation.updateTime),
      turns: includeMessages ? (conversation.messages || []).map(turnFromStoredMessage) : [],
      boundObject,
      conversationId: conversation.difyConversationId || null,
    };
  }

  function turnFromStoredMessage(message: DealDeskStoredMessagePayload): DealDeskTurn {
    const role = message.role === 'assistant' ? 'assistant' : 'user';
    const events = safeParseJson<DealDeskProcessEvent[]>(message.processEventsJson || '', []);
    const references = safeParseJson<DealDeskReference[]>(message.referencesJson || '', []);
    return {
      id: message.id,
      role,
      text: message.content || '',
      time: message.createTime ? formatDealDeskClockTime(new Date(message.createTime)) : formatDealDeskClockTime(),
      references: references?.length ? references : undefined,
      process:
        role === 'assistant' && events?.length
          ? {
              summary: buildProcessSummaryText(events),
              expanded: false,
              events,
            }
          : undefined,
      status: message.status || 'default',
    };
  }

  async function ensurePersistedSession(session: DealDeskSession) {
    if (session.remoteId) return session.remoteId;
    const persisted = await createDealDeskConversation(conversationSavePayload(session));
    session.remoteId = persisted.id;
    session.id = persisted.id;
    activeSessionId.value = persisted.id;
    return persisted.id;
  }

  async function persistMessage(session: DealDeskSession, turn: DealDeskTurn, options?: Parameters<typeof messageSavePayload>[1]) {
    try {
      const conversationId = await ensurePersistedSession(session);
      await saveDealDeskMessage(conversationId, messageSavePayload(turn, options));
    } catch {
      Message.warning('历史会话保存失败，本轮对话仍会继续显示。');
    }
  }

  async function persistConversation(session: DealDeskSession, lastMessageText?: string) {
    if (!session.remoteId) return;
    try {
      await updateDealDeskConversation(session.remoteId, conversationSavePayload(session, lastMessageText));
    } catch {
      Message.warning('历史会话信息更新失败。');
    }
  }

  function resetFromRoute() {
    isBootstrapping = true;
    const emptySession = createEmptySession();
    const routeReference = getRouteReference(route.query as Record<string, unknown>);
    sessions.value = [emptySession];
    activeSessionId.value = emptySession.id;
    draftMessage.value = '';
    composerReferences.value = routeReference ? [routeReference] : [];
    mentionOpen.value = false;
    mentionOptions.value = [];
    isBootstrapping = false;
  }

  async function loadStoredSessionDetail(sessionId: string) {
    const stored = await getDealDeskConversation(sessionId);
    if (!stored?.id) return;
    const detail = sessionFromStored(stored, true);
    const index = sessions.value.findIndex((item) => item.id === sessionId || item.remoteId === sessionId);
    if (index >= 0) {
      sessions.value.splice(index, 1, detail);
    } else {
      sessions.value.unshift(detail);
    }
    activeSessionId.value = detail.id;
  }

  async function loadStoredConversations() {
    const currentLoad = ++loadSequence;
    isBootstrapping = true;
    try {
      const routeReference = getRouteReference(route.query as Record<string, unknown>);
      const conversations = await listDealDeskConversations(50);
      if (currentLoad !== loadSequence) return;

      if (!conversations.length) {
        resetFromRoute();
        composerReferences.value = routeReference ? [routeReference] : [];
        return;
      }

      sessions.value = conversations.map((conversation) => sessionFromStored(conversation));
      activeSessionId.value = sessions.value[0]?.id || '';
      draftMessage.value = '';
      composerReferences.value = routeReference ? [routeReference] : [];
      mentionOpen.value = false;
      mentionOptions.value = [];

      if (activeSessionId.value) {
        await loadStoredSessionDetail(activeSessionId.value);
      }
    } catch {
      resetFromRoute();
      Message.warning('历史会话加载失败，已切换到临时会话。');
    } finally {
      isBootstrapping = false;
    }
  }

  function updateSession(mutator: (session: DealDeskSession) => void) {
    const session = activeSession.value;
    if (!session) return;
    mutator(session);
    session.updatedAt = formatDealDeskSessionTime();
  }

  function updateSessionById(sessionId: string, mutator: (session: DealDeskSession) => void) {
    const session = sessions.value.find((item) => item.id === sessionId);
    if (!session) return;
    mutator(session);
    session.updatedAt = formatDealDeskSessionTime();
  }

  function applyAssistantReplyState(session: DealDeskSession, reply: DealDeskAssistantReply) {
    if (reply.conversationId) {
      session.conversationId = reply.conversationId;
    }

    if (reply.boundObject) {
      session.boundObject = {
        id: reply.boundObject.objectId,
        label: `@${reply.boundObject.objectName}`,
        type: reply.boundObject.objectType,
        source: reply.boundObject.source,
      };
    }

    if (reply.suggestedSessionTitle && (session.title === '新会话' || !session.title.trim())) {
      session.title = reply.suggestedSessionTitle;
    }

    if (!reply.writeback) {
      return;
    }

    if (reply.writeback.status === 'awaiting_confirm' || reply.writeback.status === 'failed') {
      session.activeWriteback = reply.writeback;
      return;
    }

    session.activeWriteback = null;
  }

  function createNewSession() {
    const session = createEmptySession();
    sessions.value = [session, ...sessions.value.filter((item) => item.turns.length > 0 || item.remoteId)];
    activeSessionId.value = session.id;
    draftMessage.value = '';
    composerReferences.value = [];
    mentionOptions.value = [];
    mentionOpen.value = false;
  }

  async function selectSession(sessionId: string) {
    if (sessionId === activeSessionId.value) return;
    const target = sessions.value.find((item) => item.id === sessionId);
    if (!target) return;
    activeSessionId.value = sessionId;
    if (target.remoteId && target.turns.length === 0) {
      try {
        await loadStoredSessionDetail(target.remoteId);
      } catch {
        Message.warning('历史会话详情加载失败。');
      }
    }
  }

  async function deleteSession(sessionId: string) {
    const target = sessions.value.find((item) => item.id === sessionId);
    if (!target) return;
    if (!window.confirm('确定删除该历史会话吗？')) return;

    const generation = activeGenerations.value.find((item) => item.sessionId === sessionId);
    if (generation) {
      generation.abortController.abort();
      activeGenerations.value = activeGenerations.value.filter((item) => item.sessionId !== sessionId);
    }

    try {
      if (target.remoteId) {
        await deleteDealDeskConversation(target.remoteId);
      }
      const wasActive = activeSessionId.value === sessionId;
      sessions.value = sessions.value.filter((item) => item.id !== sessionId);
      if (wasActive) {
        const nextSession = sessions.value[0] || createEmptySession();
        if (!sessions.value.length) {
          sessions.value = [nextSession];
        }
        activeSessionId.value = nextSession.id;
        if (nextSession.remoteId && nextSession.turns.length === 0) {
          await loadStoredSessionDetail(nextSession.remoteId);
        }
      }
    } catch {
      Message.error('删除历史会话失败，请稍后重试。');
    }
  }

  function toggleProcess(turnId: string) {
    updateSession((session) => {
      const turn = session.turns.find((item) => item.id === turnId);
      if (turn?.process) {
        turn.process.expanded = !turn.process.expanded;
      }
    });
  }

  function removeReference(referenceId: string) {
    composerReferences.value = composerReferences.value.filter((item) => item.id !== referenceId);
  }

  function isImageFile(file: File) {
    if (file.type?.startsWith('image/')) {
      return true;
    }
    return /\.(jpe?g|png|gif|webp|svg)$/i.test(file.name);
  }

  function addFiles(files: File[]) {
    const existingImageCount = composerReferences.value.filter((item) => item.type === 'file').length;
    const availableSlots = Math.max(MAX_IMAGE_ATTACHMENTS - existingImageCount, 0);
    const imageFiles = files.filter(isImageFile);

    if (imageFiles.length !== files.length) {
      Message.warning(t('aiDealDesk.onlyImageUpload'));
    }
    if (availableSlots <= 0) {
      Message.warning(t('aiDealDesk.imageUploadLimit', { limit: MAX_IMAGE_ATTACHMENTS }));
      return;
    }

    const acceptedFiles = imageFiles.slice(0, availableSlots);
    if (imageFiles.length > acceptedFiles.length) {
      Message.warning(t('aiDealDesk.imageUploadLimit', { limit: MAX_IMAGE_ATTACHMENTS }));
    }
    if (!acceptedFiles.length) {
      return;
    }

    const fileReferences = acceptedFiles.map((file) => ({
      id: nextId('file'),
      label: `@${file.name}`,
      type: 'file' as const,
      url: URL.createObjectURL(file),
      rawFile: file,
      mimeType: file.type,
    }));
    composerReferences.value = [...composerReferences.value, ...fileReferences];
  }

  function selectMention(reference: DealDeskReference) {
    mentionOpen.value = false;
    draftMessage.value = draftMessage.value.replace(/@([^\s@]*)$/, '').trimStart();

    if (reference.type === 'file') {
      composerReferences.value = [...composerReferences.value, reference];
      return;
    }

    const fileReferences = composerReferences.value.filter((item) => item.type === 'file');
    composerReferences.value = [...fileReferences, reference];
  }

  function openMentionPicker() {
    mentionOpen.value = activeMentionQuery.value.length > 0 && mentionOptions.value.length > 0;
  }

  async function loadMentionOptions(keyword: string) {
    const normalizedKeyword = keyword.trim();
    if (!normalizedKeyword) {
      mentionOptions.value = [];
      mentionOpen.value = false;
      return;
    }

    const currentSearch = ++mentionSearchSequence;

    try {
      const [customerResult, opportunityResult] = await Promise.all([
        searchDealDeskCustomers(normalizedKeyword, MENTION_SEARCH_PAGE_SIZE),
        searchDealDeskOpportunities(normalizedKeyword, MENTION_SEARCH_PAGE_SIZE),
      ]);

      if (currentSearch !== mentionSearchSequence) {
        return;
      }

      const remoteOptions = [
        ...buildCustomerMentionOptionsFromList(customerResult.list || [], normalizedKeyword),
        ...buildOpportunityMentionOptionsFromList(opportunityResult.list || [], normalizedKeyword),
      ];
      mentionOptions.value = remoteOptions
        .filter(
          (option, index, list) =>
            list.findIndex((item) => item.id === option.id && item.type === option.type) === index
        )
        .slice(0, MAX_MENTION_OPTIONS);
    } catch {
      if (currentSearch !== mentionSearchSequence) {
        return;
      }
      mentionOptions.value = [];
    }

    mentionOpen.value = mentionOptions.value.length > 0;
  }

  function buildStreamingProcessEvents(events: DealDeskProcessEvent[]) {
    return normalizeProcessEvents(events.filter((event) => !SYNTHETIC_PROCESS_EVENT_IDS.has(event.id)));
  }

  function buildStreamingProcessSummary(events: DealDeskProcessEvent[]) {
    const completedEvents = events.filter((event) => event.status !== 'running');
    if (!completedEvents.length) {
      return events.find((event) => event.status === 'running')?.text || '正在识别本轮任务';
    }
    return buildProcessSummaryText(completedEvents);
  }

  function mergePendingProcessEvent(turn: DealDeskTurn, incoming: DealDeskProcessEventPayload) {
    const existingEvents = turn.process?.events || [];
    const event = {
      id: incoming.id,
      type: incoming.type,
      status: incoming.status,
      text: incoming.text,
      evidenceRefs: incoming.evidenceRefs,
    };
    const nextEvents = [...existingEvents];
    const eventIndex = nextEvents.findIndex((item) => item.id === event.id);
    if (eventIndex >= 0) {
      nextEvents.splice(eventIndex, 1, event);
    } else {
      nextEvents.push(event);
    }

    const normalizedEvents = buildStreamingProcessEvents(nextEvents);
    turn.process = {
      summary: buildStreamingProcessSummary(normalizedEvents),
      expanded: true,
      events: normalizedEvents,
    };
  }

  function appendPendingAnswerDelta(turn: DealDeskTurn, text: string) {
    const nextText = `${turn.text || ''}${text}`;
    const trimmed = nextText.trimStart();
    if (trimmed.startsWith('{') || trimmed.startsWith('```json')) {
      return;
    }
    turn.text = nextText;
  }

  function buildFailedAssistantReply(message: string): DealDeskAssistantReply {
    return {
      kind: 'quick-answer',
      turn: {
        time: formatDealDeskClockTime(),
        text: message,
        status: 'failed',
        process: {
          summary: '本轮请求失败',
          expanded: false,
          events: [
            {
              id: nextId('failed'),
              type: 'failed',
              status: 'failed',
              text: message,
            },
          ],
        },
      },
    };
  }

  function isAbortError(error: unknown) {
    return error instanceof DOMException && error.name === 'AbortError';
  }

  function isRetryableFailedTurn(turn?: DealDeskTurn | null) {
    if (!turn || turn.role !== 'assistant') return false;
    if (turn.status === 'failed') return true;
    const text = turn.text || '';
    return text.includes('未返回有效最终回复') || text.includes('请求失败') || text.includes('request failed');
  }

  function buildStoppedAssistantReply(existingText: string): DealDeskAssistantReply {
    const text = existingText.trim()
      ? `${existingText.trimEnd()}\n\n${STOPPED_GENERATION_TEXT}`
      : STOPPED_GENERATION_TEXT;
    return {
      kind: 'quick-answer',
      turn: {
        time: formatDealDeskClockTime(),
        text,
      },
    };
  }

  async function requestAssistantReply(input: {
    text: string;
    boundObject: DealDeskReference | null;
    references: DealDeskReference[];
    session: DealDeskSession;
    signal?: AbortSignal;
    onTaskId?: (taskId: string) => void;
    onProcessEvent?: (event: DealDeskProcessEventPayload) => void;
    onAnswerDelta?: (text: string, meta?: { conversationId?: string; messageId?: string; taskId?: string }) => void;
  }) {
    if (provider.streamAssistantReply) {
      return provider.streamAssistantReply(
        input,
        {
          onTaskId: input.onTaskId,
          onProcessEvent: input.onProcessEvent,
          onAnswerDelta: input.onAnswerDelta,
        },
        {
          signal: input.signal,
        }
      );
    }
    return provider.getAssistantReply(input);
  }

  async function runAssistantGeneration(input: {
    text: string;
    boundObject: DealDeskReference | null;
    references: DealDeskReference[];
    session: DealDeskSession;
    sessionId: string;
    pendingAssistantTurn: DealDeskTurn;
  }) {
    const { text, boundObject, references, session, sessionId, pendingAssistantTurn } = input;
    const abortController = new AbortController();
    const generation = {
      sessionId,
      pendingTurnId: pendingAssistantTurn.id,
      taskId: null as string | null,
      abortController,
      stopping: false,
    };
    activeGenerations.value = [...activeGenerations.value, generation];

    let reply: DealDeskAssistantReply;
    try {
      reply = await requestAssistantReply({
        text,
        boundObject,
        references,
        session,
        signal: abortController.signal,
        onTaskId(taskId) {
          generation.taskId = taskId;
        },
        onProcessEvent(event) {
          updateSessionById(sessionId, (currentSession) => {
            const pendingTurn = currentSession.turns.find((turn) => turn.id === pendingAssistantTurn.id);
            if (pendingTurn) {
              mergePendingProcessEvent(pendingTurn, event);
            }
          });
        },
        onAnswerDelta(delta, meta) {
          updateSessionById(sessionId, (currentSession) => {
            if (meta?.conversationId) {
              currentSession.conversationId = meta.conversationId;
            }
            if (meta?.taskId) {
              generation.taskId = meta.taskId;
            }
            const pendingTurn = currentSession.turns.find((turn) => turn.id === pendingAssistantTurn.id);
            if (pendingTurn) {
              appendPendingAnswerDelta(pendingTurn, delta);
            }
          });
        },
      });
    } catch (error) {
      if (generation.stopping || isAbortError(error)) {
        const pendingSession = sessions.value.find((item) => item.id === sessionId);
        const pendingText = pendingSession?.turns.find((turn) => turn.id === pendingAssistantTurn.id)?.text || '';
        reply = buildStoppedAssistantReply(pendingText);
      } else {
        const message = error instanceof Error ? error.message : '多Agent智能助手 请求失败，请稍后重试。';
        reply = buildFailedAssistantReply(message);
      }
    }

    let finalAssistantTurnId = '';
    updateSessionById(sessionId, (currentSession) => {
      applyAssistantReplyState(currentSession, reply);
      const pendingTurnIndex = currentSession.turns.findIndex((turn) => turn.id === pendingAssistantTurn.id);
      if (pendingTurnIndex >= 0) {
        const currentPendingTurn = currentSession.turns[pendingTurnIndex];
        const assistantTurn = buildResolvedAssistantTurn(currentPendingTurn, reply.turn);
        currentSession.turns.splice(pendingTurnIndex, 1, assistantTurn);
        finalAssistantTurnId = assistantTurn.id;
        return;
      }
      const assistantTurn = createAssistantTurn(reply.turn);
      currentSession.turns.push(assistantTurn);
      finalAssistantTurnId = assistantTurn.id;
    });

    const finalSession = sessions.value.find((item) => item.id === sessionId);
    const finalAssistantTurn = finalSession?.turns.find((turn) => turn.id === finalAssistantTurnId);
    if (finalSession && finalAssistantTurn) {
      await persistMessage(finalSession, finalAssistantTurn, {
        writeback: reply.writeback,
        boundObject: finalSession.boundObject,
        difyMessageId: reply.messageId,
      });
      await persistConversation(finalSession, finalAssistantTurn.text);
    }

    activeGenerations.value = activeGenerations.value.filter((item) => item.pendingTurnId !== pendingAssistantTurn.id);
  }

  async function sendMessage() {
    if (!activeSession.value) return;
    if (activeSessionGeneration.value) return;

    const normalizedText = draftMessage.value.trim();
    const references = [...composerReferences.value];
    if (!normalizedText && references.length === 0) {
      return;
    }

    const text = normalizedText || '继续分析';
    const userTurn = createUserTurn(text, references);
    const boundObject = resolveBoundObject(references) ?? activeSession.value.boundObject ?? null;
    const pendingAssistantTurn = createPendingAssistantTurn(nextId('assistant'), boundObject);

    updateSession((session) => {
      session.turns.push(userTurn);
      session.turns.push(pendingAssistantTurn);
      if (boundObject) {
        session.boundObject = boundObject;
      }
      if (session.title === '新会话' || !session.title.trim()) {
        session.title = text.slice(0, 18);
      }
    });

    draftMessage.value = '';
    composerReferences.value = [];
    mentionOpen.value = false;

    const session = activeSession.value;
    if (!session) return;
    await persistMessage(session, userTurn, {
      boundObject,
    });
    const sessionId = session.id;

    await runAssistantGeneration({
      text,
      boundObject,
      references,
      session,
      sessionId,
      pendingAssistantTurn,
    });
  }

  async function retryTurn(turnId: string) {
    if (!activeSession.value || activeSessionGeneration.value) return;

    const session = activeSession.value;
    const failedTurnIndex = session.turns.findIndex((turn) => turn.id === turnId);
    const failedTurn = failedTurnIndex >= 0 ? session.turns[failedTurnIndex] : null;
    const userTurn = failedTurnIndex > 0 ? session.turns[failedTurnIndex - 1] : null;
    if (!isRetryableFailedTurn(failedTurn)) return;
    if (!userTurn || userTurn.role !== 'user') return;

    const references = [...(userTurn.references || [])];
    const text = userTurn.text?.trim() || '继续分析';
    const boundObject = resolveBoundObject(references) ?? session.boundObject ?? null;
    const pendingAssistantTurn = createPendingAssistantTurn(nextId('assistant'), boundObject);
    const sessionId = session.id;

    updateSessionById(sessionId, (currentSession) => {
      const index = currentSession.turns.findIndex((turn) => turn.id === turnId);
      if (index >= 0) {
        currentSession.turns.splice(index, 1, pendingAssistantTurn);
      }
      if (boundObject) {
        currentSession.boundObject = boundObject;
      }
    });

    const currentSession = sessions.value.find((item) => item.id === sessionId);
    if (!currentSession) return;

    await runAssistantGeneration({
      text,
      boundObject,
      references,
      session: currentSession,
      sessionId,
      pendingAssistantTurn,
    });
  }

  function stopCurrentGeneration() {
    const generation = activeSessionGeneration.value;
    if (!generation || generation.stopping) {
      return;
    }

    generation.stopping = true;
    if (generation.taskId) {
      stopDealDeskChat(generation.taskId).catch(() => {
        // Local abort still gives the user immediate control if the remote stop call fails.
      });
    }
    generation.abortController.abort();
  }

  function sendStarter(text: string) {
    draftMessage.value = text;
    sendMessage().catch(() => {
      // sendMessage already renders a failed assistant turn.
    });
  }

  watch(
    () => route.fullPath,
    () => {
      loadStoredConversations().catch(() => {
        resetFromRoute();
      });
    },
    { immediate: true }
  );

  watch(activeSessionId, () => {
    if (isBootstrapping) {
      return;
    }
    mentionOpen.value = false;
    draftMessage.value = '';
    composerReferences.value = [];
  });

  watch(
    activeMentionQuery,
    (query) => {
      loadMentionOptions(query).catch(() => {
        mentionOptions.value = [];
        mentionOpen.value = false;
      });
    },
    { immediate: true }
  );

  return {
    sessions,
    historyCollapsed,
    mentionOpen,
    draftMessage,
    composerReferences,
    activeSessionId,
    activeSession,
    isResponding,
    isStopping,
    starters,
    mentionOptions,
    selectSession,
    deleteSession,
    createNewSession,
    toggleProcess,
    removeReference,
    addFiles,
    selectMention,
    openMentionPicker,
    sendStarter,
    sendMessage,
    retryTurn,
    stopCurrentGeneration,
  };
}
