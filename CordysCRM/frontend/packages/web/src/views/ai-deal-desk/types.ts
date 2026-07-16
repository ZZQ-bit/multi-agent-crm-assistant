export type DealDeskReferenceType = 'customer' | 'opportunity' | 'file';

export interface DealDeskReference {
  id: string;
  label: string;
  type: DealDeskReferenceType;
  url?: string;
  source?: 'mention' | 'selection' | 'auto_detected' | 'route_query';
  rawFile?: File;
  mimeType?: string;
  uploadFileId?: string;
}

export interface DealDeskMentionOption extends DealDeskReference {
  subtitle?: string;
}

export type DealDeskProcessEventType =
  | 'task_understanding'
  | 'attachment_analysis'
  | 'crm_retrieval'
  | 'knowledge_retrieval'
  | 'external_research'
  | 'sales_analysis'
  | 'finance_analysis'
  | 'delivery_analysis'
  | 'legal_analysis'
  | 'analytics_analysis'
  | 'answer_generation'
  | 'object_required'
  | 'confirmation_required'
  | 'writeback_completed'
  | 'failed'
  // 兼容已保存的旧会话，V3 不再生成以下事件类型。
  | 'task_identified'
  | 'object_selected'
  | 'context_loaded'
  | 'memory_used'
  | 'rule_checked'
  | 'risk_found'
  | 'suggestion_generated';

export interface DealDeskProcessEvent {
  id: string;
  type?: DealDeskProcessEventType;
  text: string;
  status: 'completed' | 'running' | 'warning' | 'failed';
  evidenceRefs?: string[];
}

export interface DealDeskProcessBlock {
  summary: string;
  expanded: boolean;
  events: DealDeskProcessEvent[];
}

export interface DealDeskTurn {
  id: string;
  role: 'user' | 'assistant';
  text: string;
  time: string;
  references?: DealDeskReference[];
  process?: DealDeskProcessBlock;
  status?: 'default' | 'generating' | 'failed';
}

export type DealDeskAssistantReplyKind =
  | 'quick-answer'
  | 'analysis'
  | 'writeback-confirm'
  | 'writeback-success'
  | 'writeback-cancel';

export type DealDeskAssistantReplyPayload = Omit<DealDeskTurn, 'id' | 'role'>;

export interface DealDeskAssistantReply {
  kind: DealDeskAssistantReplyKind;
  turn: DealDeskAssistantReplyPayload;
  writeback?: DealDeskWritebackPayload;
  boundObject?: DealDeskBoundObjectPayload;
  conversationId?: string;
  messageId?: string;
  suggestedSessionTitle?: string;
}

export interface DealDeskSession {
  id: string;
  remoteId?: string;
  title: string;
  updatedAt: string;
  group: 'today' | 'yesterday' | 'earlier';
  turns: DealDeskTurn[];
  boundObject?: DealDeskReference | null;
  activeWriteback?: DealDeskWritebackPayload | null;
  conversationId?: string | null;
}

export interface DealDeskStoredMessagePayload {
  id: string;
  role: 'user' | 'assistant';
  content?: string;
  referencesJson?: string;
  processEventsJson?: string;
  writebackJson?: string;
  boundObjectJson?: string;
  difyMessageId?: string;
  status?: 'default' | 'generating' | 'failed';
  createTime?: number;
}

export interface DealDeskStoredConversationPayload {
  id: string;
  title: string;
  difyConversationId?: string;
  boundObjectJson?: string;
  lastMessageText?: string;
  messageCount?: number;
  createTime?: number;
  updateTime?: number;
  messages?: DealDeskStoredMessagePayload[];
}

export interface DealDeskConversationSavePayload {
  title?: string;
  difyConversationId?: string | null;
  boundObjectJson?: string | null;
  lastMessageText?: string | null;
}

export interface DealDeskMessageSavePayload {
  role: 'user' | 'assistant';
  content: string;
  referencesJson?: string | null;
  processEventsJson?: string | null;
  writebackJson?: string | null;
  boundObjectJson?: string | null;
  difyMessageId?: string | null;
  status?: 'default' | 'generating' | 'failed';
}

export type DealDeskTurnType =
  | 'quick_answer'
  | 'object_select'
  | 'text_analysis'
  | 'writeback_confirm'
  | 'writeback_result'
  | 'fallback'
  | 'failed';

export interface DealDeskBoundObjectPayload {
  objectType: 'customer' | 'opportunity';
  objectId: string;
  objectName: string;
  customerId?: string;
  customerName?: string;
  source: 'mention' | 'selection' | 'auto_detected' | 'route_query';
}

export interface DealDeskProcessEventPayload {
  id: string;
  type: DealDeskProcessEventType;
  status: 'running' | 'completed' | 'warning' | 'failed';
  text: string;
  evidenceRefs?: string[];
}

export type DealDeskWritebackType = 'follow_record' | 'follow_plan' | 'follow_record_and_plan';

export interface DealDeskWritebackPayload {
  id: string;
  type: DealDeskWritebackType;
  status: 'awaiting_confirm' | 'confirmed' | 'cancelled' | 'failed';
  target: {
    customerId?: string;
    customerName: string;
    opportunityId?: string;
    opportunityName?: string;
    ownerId?: string;
    ownerName?: string;
    contactId?: string;
    contactName?: string;
  };
  recordDraft?: {
    followMethod?: string;
    followTimeText?: string;
    content: string;
  };
  planDraft?: {
    planMethod?: string;
    planTimeText?: string;
    content: string;
  };
  resultMessage?: string;
}

export interface DealDeskChatflowPayload {
  protocolVersion: '1.0';
  turnType: DealDeskTurnType;
  answerText: string;
  processEvents?: DealDeskProcessEventPayload[];
  writeback?: DealDeskWritebackPayload;
  boundObject?: DealDeskBoundObjectPayload;
  suggestedSessionTitle?: string;
  warnings?: string[];
}

export interface DealDeskChatflowResponse extends DealDeskChatflowPayload {
  conversationId?: string;
  messageId?: string;
}

export type DealDeskChatStreamFrame =
  | {
      type: 'process_event';
      event: DealDeskProcessEventPayload;
      taskId?: string;
    }
  | {
      type: 'answer_delta';
      text: string;
      conversationId?: string;
      messageId?: string;
      taskId?: string;
    }
  | {
      type: 'final';
      payload: DealDeskChatflowResponse;
      taskId?: string;
    };

export interface DealDeskChatStreamHandlers {
  onTaskId?: (taskId: string) => void;
  onProcessEvent?: (event: DealDeskProcessEventPayload) => void;
  onAnswerDelta?: (text: string, meta?: { conversationId?: string; messageId?: string; taskId?: string }) => void;
}

export interface DealDeskChatStreamOptions {
  signal?: AbortSignal;
}

export interface DealDeskChatRequestPayload {
  query: string;
  conversationId?: string;
  boundObject?: DealDeskBoundObjectPayload | null;
  activeWriteback?: DealDeskWritebackPayload | null;
  files?: Array<{
    id: string;
    name: string;
    type: string;
    uploadFileId: string;
    mimeType?: string;
  }>;
}

export interface DealDeskUploadedFilePayload {
  id: string;
  name: string;
  size?: number;
  extension?: string;
  mimeType?: string;
  previewUrl?: string;
}

export interface DealDeskStopChatResponse {
  success: boolean;
  message?: string;
}
