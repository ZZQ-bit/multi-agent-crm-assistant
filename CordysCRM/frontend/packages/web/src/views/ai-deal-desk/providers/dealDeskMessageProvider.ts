import type {
  DealDeskAssistantReply,
  DealDeskChatStreamHandlers,
  DealDeskChatStreamOptions,
  DealDeskReference,
  DealDeskSession,
} from '../types';

export interface DealDeskReplyInput {
  text: string;
  boundObject: DealDeskReference | null;
  references: DealDeskReference[];
  session: DealDeskSession;
}

export interface DealDeskMessageProvider {
  getAssistantReply(input: DealDeskReplyInput): Promise<DealDeskAssistantReply>;
  streamAssistantReply?(
    input: DealDeskReplyInput,
    handlers: DealDeskChatStreamHandlers,
    options?: DealDeskChatStreamOptions
  ): Promise<DealDeskAssistantReply>;
}
