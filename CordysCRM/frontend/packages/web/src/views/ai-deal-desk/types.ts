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
  | 'task_identified'
  | 'object_required'
  | 'object_selected'
  | 'context_loaded'
  | 'memory_used'
  | 'rule_checked'
  | 'risk_found'
  | 'suggestion_generated'
  | 'confirmation_required'
  | 'writeback_completed'
  | 'failed';

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
  title: string;
  updatedAt: string;
  group: 'today' | 'yesterday' | 'earlier';
  turns: DealDeskTurn[];
  boundObject?: DealDeskReference | null;
  activeWriteback?: DealDeskWritebackPayload | null;
  conversationId?: string | null;
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
