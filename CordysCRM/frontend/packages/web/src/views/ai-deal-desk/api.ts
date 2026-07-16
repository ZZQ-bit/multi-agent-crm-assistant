import type { CommonList } from '@lib/shared/models/common';
import type { CustomerListItem } from '@lib/shared/models/customer';
import type { OpportunityItem } from '@lib/shared/models/opportunity';

import { getToken } from '@lib/shared/method/auth';
import CDR from '@/api/http';
import { getGlobalCustomerList, globalSearchOptPage } from '@/api/modules';

import { createAnswerDeltaScheduler } from './answerDeltaScheduler';

import type {
  DealDeskChatflowResponse,
  DealDeskConversationSavePayload,
  DealDeskChatRequestPayload,
  DealDeskChatStreamFrame,
  DealDeskChatStreamHandlers,
  DealDeskChatStreamOptions,
  DealDeskMessageSavePayload,
  DealDeskStopChatResponse,
  DealDeskStoredConversationPayload,
  DealDeskStoredMessagePayload,
  DealDeskUploadedFilePayload,
} from './types';

const chatUrl = '/ai/deal-desk/chat';
const chatStreamUrl = '/ai/deal-desk/chat/stream';
const chatStopUrl = '/ai/deal-desk/chat/stop';
const uploadUrl = '/ai/deal-desk/file/upload';
const conversationsUrl = '/ai/deal-desk/conversations';

function buildDealDeskApiUrl(path: string) {
  const apiBase = String(import.meta.env.VITE_API_BASE_URL || '').replace(/^\/+|\/+$/g, '');
  const normalizedPath = path.startsWith('/') ? path : `/${path}`;
  return apiBase ? `/${apiBase}${normalizedPath}` : normalizedPath;
}

function unwrapDealDeskResponse<T>(response: T | { data?: T }) {
  if (response && typeof response === 'object' && 'data' in response) {
    return response.data as T;
  }
  return response as T;
}

export async function sendDealDeskChat(data: DealDeskChatRequestPayload) {
  const response = await CDR.post<DealDeskChatflowResponse | { data?: DealDeskChatflowResponse }>(
    { url: chatUrl, data },
    {
      isTransformResponse: false,
    }
  );
  return unwrapDealDeskResponse(response);
}

function buildStreamHeaders() {
  const app = JSON.parse(localStorage.getItem('app') || '{}') as { orgId?: string };
  const { sessionId, csrfToken } = getToken();
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    'Accept-Language': localStorage.getItem('CRM-locale') || 'zh-CN',
    'Organization-Id': app.orgId || '',
  };
  if (sessionId && csrfToken) {
    headers['X-AUTH-TOKEN'] = sessionId;
    headers['CSRF-TOKEN'] = csrfToken;
  }
  return headers;
}

function parseSseFrames(buffer: string) {
  const chunks = buffer.split(/\n\n/);
  const rest = chunks.pop() || '';
  const frames = chunks
    .map((chunk) =>
      chunk
        .split(/\n/)
        .filter((line) => line.startsWith('data:'))
        .map((line) => line.replace(/^data:\s?/, ''))
        .join('\n')
    )
    .filter(Boolean);
  return { frames, rest };
}

export async function streamDealDeskChat(
  data: DealDeskChatRequestPayload,
  handlers: DealDeskChatStreamHandlers = {},
  options: DealDeskChatStreamOptions & { signal?: AbortSignal } = {}
): Promise<DealDeskChatflowResponse> {
  const response = await fetch(buildDealDeskApiUrl(chatStreamUrl), {
    method: 'POST',
    headers: buildStreamHeaders(),
    body: JSON.stringify(data),
    signal: options.signal,
    credentials: 'same-origin',
  });

  if (!response.ok || !response.body) {
    throw new Error(`多Agent智能助手 stream request failed: ${response.status}`);
  }

  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  const answerDeltaScheduler = createAnswerDeltaScheduler(handlers.onAnswerDelta, options.signal);
  let buffer = '';
  let finalPayload: DealDeskChatflowResponse | null = null;

  async function readNextChunk(): Promise<void> {
    const { done, value } = await reader.read();
    buffer += decoder.decode(value || new Uint8Array(), { stream: !done });
    const parsed = parseSseFrames(buffer);
    buffer = parsed.rest;

    for (let index = 0; index < parsed.frames.length; index += 1) {
      const frameText = parsed.frames[index];
      const frame = JSON.parse(frameText) as DealDeskChatStreamFrame;
      if (frame.taskId) {
        handlers.onTaskId?.(frame.taskId);
      }
      if (frame.type === 'process_event') {
        handlers.onProcessEvent?.(frame.event);
      } else if (frame.type === 'answer_delta') {
        answerDeltaScheduler.enqueue(frame.text, {
          conversationId: frame.conversationId,
          messageId: frame.messageId,
          taskId: frame.taskId,
        });
      } else if (frame.type === 'final') {
        finalPayload = frame.payload;
      }
    }

    if (!done) {
      await readNextChunk();
    }
  }

  try {
    await readNextChunk();

    if (!finalPayload) {
      throw new Error('多Agent智能助手 stream ended without final payload');
    }
    await answerDeltaScheduler.flush();
    return finalPayload;
  } finally {
    answerDeltaScheduler.dispose();
  }
}

export async function stopDealDeskChat(taskId: string) {
  const response = await CDR.post<DealDeskStopChatResponse | { data?: DealDeskStopChatResponse }>(
    { url: chatStopUrl, data: { taskId } },
    {
      isTransformResponse: false,
    }
  );
  return unwrapDealDeskResponse(response);
}

export async function uploadDealDeskFile(file: File) {
  const response = await CDR.uploadFile<DealDeskUploadedFilePayload | { data?: DealDeskUploadedFilePayload }>(
    { url: uploadUrl },
    { fileList: [file] }
  );
  return unwrapDealDeskResponse(response);
}

export async function listDealDeskConversations(limit = 50) {
  const response = await CDR.get<DealDeskStoredConversationPayload[] | { data?: DealDeskStoredConversationPayload[] }>(
    { url: conversationsUrl, params: { limit } },
    {
      isTransformResponse: false,
    }
  );
  return unwrapDealDeskResponse(response) || [];
}

export async function getDealDeskConversation(id: string) {
  const response = await CDR.get<DealDeskStoredConversationPayload | { data?: DealDeskStoredConversationPayload }>(
    { url: `${conversationsUrl}/${id}` },
    {
      isTransformResponse: false,
    }
  );
  return unwrapDealDeskResponse(response);
}

export async function createDealDeskConversation(data: DealDeskConversationSavePayload) {
  const response = await CDR.post<DealDeskStoredConversationPayload | { data?: DealDeskStoredConversationPayload }>(
    { url: conversationsUrl, data },
    {
      isTransformResponse: false,
    }
  );
  return unwrapDealDeskResponse(response);
}

export async function updateDealDeskConversation(id: string, data: DealDeskConversationSavePayload) {
  const response = await CDR.put<DealDeskStoredConversationPayload | { data?: DealDeskStoredConversationPayload }>(
    { url: `${conversationsUrl}/${id}`, data },
    {
      isTransformResponse: false,
    }
  );
  return unwrapDealDeskResponse(response);
}

export async function saveDealDeskMessage(id: string, data: DealDeskMessageSavePayload) {
  const response = await CDR.post<DealDeskStoredMessagePayload | { data?: DealDeskStoredMessagePayload }>(
    { url: `${conversationsUrl}/${id}/messages`, data },
    {
      isTransformResponse: false,
    }
  );
  return unwrapDealDeskResponse(response);
}

export async function deleteDealDeskConversation(id: string) {
  const response = await CDR.delete<{ success?: boolean } | { data?: { success?: boolean } }>(
    { url: `${conversationsUrl}/${id}` },
    {
      isTransformResponse: false,
    }
  );
  return unwrapDealDeskResponse(response);
}

export function searchDealDeskCustomers(keyword: string, limit: number) {
  return getGlobalCustomerList({
    current: 1,
    pageSize: limit,
    keyword,
  }) as Promise<CommonList<CustomerListItem>>;
}

export function searchDealDeskOpportunities(keyword: string, limit: number) {
  return globalSearchOptPage({
    current: 1,
    pageSize: limit,
    keyword,
  }) as Promise<CommonList<OpportunityItem>>;
}
