import adaptChatflowPayloadToAssistantReply from '../adapters/dealDeskChatflowAdapter';
import { sendDealDeskChat, streamDealDeskChat, uploadDealDeskFile } from '../api';
import type {
  DealDeskAssistantReply,
  DealDeskBoundObjectPayload,
  DealDeskChatRequestPayload,
  DealDeskChatStreamHandlers,
  DealDeskReference,
  DealDeskWritebackPayload,
} from '../types';
import type { DealDeskMessageProvider } from './dealDeskMessageProvider';

function mapReferenceToBoundObject(reference: DealDeskReference | null | undefined): DealDeskBoundObjectPayload | null {
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

function isImageReference(reference: DealDeskReference) {
  const mimeType = reference.mimeType || reference.rawFile?.type || '';
  if (mimeType.startsWith('image/')) {
    return true;
  }
  return /\.(jpe?g|png|gif|webp|svg)$/i.test(reference.label.replace(/^@/, ''));
}

async function buildRequestPayload(input: {
  text: string;
  boundObject: DealDeskReference | null;
  references: DealDeskReference[];
  session: {
    conversationId?: string | null;
    activeWriteback?: DealDeskWritebackPayload | null;
  };
}): Promise<DealDeskChatRequestPayload> {
  const uploadTargets = input.references.filter(
    (item) => item.type === 'file' && item.rawFile && isImageReference(item)
  );
  const uploadedFiles = await Promise.all(
    uploadTargets.map(async (reference) => {
      const uploaded = await uploadDealDeskFile(reference.rawFile as File);
      const mimeType = uploaded.mimeType || reference.mimeType;
      return {
        id: reference.id,
        name: uploaded.name || reference.label.replace(/^@/, ''),
        type: 'image',
        uploadFileId: uploaded.id,
        mimeType,
      };
    })
  );

  return {
    query: input.text,
    conversationId: input.session.conversationId || undefined,
    boundObject: mapReferenceToBoundObject(input.boundObject),
    activeWriteback: input.session.activeWriteback || undefined,
    files: uploadedFiles,
  };
}

export default function createDealDeskDifyProvider(): DealDeskMessageProvider {
  return {
    async getAssistantReply(input) {
      try {
        const requestPayload = await buildRequestPayload(input);
        const payload = await sendDealDeskChat(requestPayload);
        const reply = adaptChatflowPayloadToAssistantReply(payload);

        return {
          ...reply,
          conversationId: payload.conversationId,
          messageId: payload.messageId,
        } satisfies DealDeskAssistantReply;
      } catch (error) {
        throw new Error(error instanceof Error ? error.message : '多Agent智能助手 服务请求失败');
      }
    },
    async streamAssistantReply(input, handlers: DealDeskChatStreamHandlers, options) {
      try {
        const requestPayload = await buildRequestPayload(input);
        const payload = await streamDealDeskChat(requestPayload, handlers, options);
        const reply = adaptChatflowPayloadToAssistantReply(payload);

        return {
          ...reply,
          conversationId: payload.conversationId,
          messageId: payload.messageId,
        } satisfies DealDeskAssistantReply;
      } catch (error) {
        throw new Error(error instanceof Error ? error.message : '多Agent智能助手 服务请求失败');
      }
    },
  };
}
