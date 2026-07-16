package cn.cordys.crm.ai.dealdesk.service;

import cn.cordys.common.uid.IDGenerator;
import cn.cordys.crm.ai.dealdesk.domain.AiDealDeskConversation;
import cn.cordys.crm.ai.dealdesk.domain.AiDealDeskMessage;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskConversationRequest;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskConversationResponse;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskMessageRequest;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskMessageResponse;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class AiDealDeskConversationService {

    private static final int DEFAULT_LIMIT = 50;
    private static final int MAX_LIMIT = 100;
    private static final int TITLE_LIMIT = 18;

    private final AiDealDeskConversationRepository repository;

    public AiDealDeskConversationService(AiDealDeskConversationRepository repository) {
        this.repository = repository;
    }

    public DealDeskConversationResponse create(DealDeskConversationRequest request, String userId, String organizationId) {
        long now = System.currentTimeMillis();
        DealDeskConversationRequest safeRequest = request == null ? new DealDeskConversationRequest() : request;
        AiDealDeskConversation conversation = new AiDealDeskConversation();
        conversation.setId(nextId());
        conversation.setOrganizationId(organizationId);
        conversation.setUserId(userId);
        conversation.setTitle(defaultTitle(safeRequest.getTitle()));
        conversation.setDifyConversationId(blankToNull(safeRequest.getDifyConversationId()));
        conversation.setBoundObjectJson(blankToNull(safeRequest.getBoundObjectJson()));
        conversation.setLastMessageText(blankToNull(safeRequest.getLastMessageText()));
        conversation.setMessageCount(0);
        conversation.setCreateTime(now);
        conversation.setUpdateTime(now);
        conversation.setCreateUser(userId);
        conversation.setUpdateUser(userId);
        conversation.setDeleted(false);
        repository.insertConversation(conversation);
        return toConversationResponse(conversation, List.of());
    }

    public List<DealDeskConversationResponse> list(String userId, String organizationId, int limit) {
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return repository.listConversations(userId, organizationId, safeLimit).stream()
                .map(conversation -> toConversationResponse(conversation, List.of()))
                .toList();
    }

    public DealDeskConversationResponse detail(String id, String userId, String organizationId) {
        AiDealDeskConversation conversation = repository.findConversation(id, userId, organizationId);
        if (conversation == null) {
            return null;
        }
        List<DealDeskMessageResponse> messages = repository.listMessages(id, userId, organizationId).stream()
                .map(this::toMessageResponse)
                .toList();
        return toConversationResponse(conversation, messages);
    }

    public DealDeskConversationResponse update(
            String id,
            DealDeskConversationRequest request,
            String userId,
            String organizationId
    ) {
        AiDealDeskConversation existing = repository.findConversation(id, userId, organizationId);
        if (existing == null) {
            return null;
        }
        DealDeskConversationRequest safeRequest = request == null ? new DealDeskConversationRequest() : request;
        AiDealDeskConversation update = new AiDealDeskConversation();
        update.setId(id);
        if (safeRequest.getTitle() != null) {
            update.setTitle(defaultTitle(safeRequest.getTitle()));
        }
        if (safeRequest.getDifyConversationId() != null) {
            update.setDifyConversationId(blankToNull(safeRequest.getDifyConversationId()));
        }
        if (safeRequest.getBoundObjectJson() != null) {
            update.setBoundObjectJson(blankToNull(safeRequest.getBoundObjectJson()));
        }
        if (safeRequest.getLastMessageText() != null) {
            update.setLastMessageText(blankToNull(safeRequest.getLastMessageText()));
        }
        update.setUpdateTime(System.currentTimeMillis());
        update.setUpdateUser(userId);
        repository.updateConversation(update);
        return detail(id, userId, organizationId);
    }

    public DealDeskMessageResponse saveMessage(
            String conversationId,
            DealDeskMessageRequest request,
            String userId,
            String organizationId
    ) {
        AiDealDeskConversation conversation = repository.findConversation(conversationId, userId, organizationId);
        if (conversation == null) {
            return null;
        }
        long now = System.currentTimeMillis();
        DealDeskMessageRequest safeRequest = request == null ? new DealDeskMessageRequest() : request;
        AiDealDeskMessage message = new AiDealDeskMessage();
        message.setId(nextId());
        message.setConversationId(conversationId);
        message.setOrganizationId(organizationId);
        message.setUserId(userId);
        message.setRole(normalizeRole(safeRequest.getRole()));
        message.setContent(StringUtils.defaultString(safeRequest.getContent()));
        message.setReferencesJson(blankToNull(safeRequest.getReferencesJson()));
        message.setProcessEventsJson(blankToNull(safeRequest.getProcessEventsJson()));
        message.setWritebackJson(blankToNull(safeRequest.getWritebackJson()));
        message.setBoundObjectJson(blankToNull(safeRequest.getBoundObjectJson()));
        message.setDifyMessageId(blankToNull(safeRequest.getDifyMessageId()));
        message.setStatus(defaultStatus(safeRequest.getStatus()));
        message.setCreateTime(now);
        message.setUpdateTime(now);
        message.setCreateUser(userId);
        message.setUpdateUser(userId);
        message.setDeleted(false);
        repository.insertMessage(message);

        AiDealDeskConversation update = new AiDealDeskConversation();
        update.setId(conversationId);
        update.setMessageCount(repository.countMessages(conversationId, userId, organizationId));
        update.setLastMessageText(message.getContent());
        update.setUpdateTime(now);
        update.setUpdateUser(userId);
        repository.updateConversation(update);
        return toMessageResponse(message);
    }

    public boolean delete(String id, String userId, String organizationId) {
        AiDealDeskConversation conversation = repository.findConversation(id, userId, organizationId);
        if (conversation == null) {
            return false;
        }
        long now = System.currentTimeMillis();
        repository.softDeleteMessages(id, userId, organizationId, now);
        repository.softDeleteConversation(id, userId, organizationId, now);
        return true;
    }

    private DealDeskConversationResponse toConversationResponse(
            AiDealDeskConversation conversation,
            List<DealDeskMessageResponse> messages
    ) {
        DealDeskConversationResponse response = new DealDeskConversationResponse();
        response.setId(conversation.getId());
        response.setTitle(conversation.getTitle());
        response.setDifyConversationId(conversation.getDifyConversationId());
        response.setBoundObjectJson(conversation.getBoundObjectJson());
        response.setLastMessageText(conversation.getLastMessageText());
        response.setMessageCount(conversation.getMessageCount());
        response.setCreateTime(conversation.getCreateTime());
        response.setUpdateTime(conversation.getUpdateTime());
        response.setMessages(messages);
        return response;
    }

    private DealDeskMessageResponse toMessageResponse(AiDealDeskMessage message) {
        DealDeskMessageResponse response = new DealDeskMessageResponse();
        response.setId(message.getId());
        response.setRole(message.getRole());
        response.setContent(message.getContent());
        response.setReferencesJson(message.getReferencesJson());
        response.setProcessEventsJson(message.getProcessEventsJson());
        response.setWritebackJson(message.getWritebackJson());
        response.setBoundObjectJson(message.getBoundObjectJson());
        response.setDifyMessageId(message.getDifyMessageId());
        response.setStatus(message.getStatus());
        response.setCreateTime(message.getCreateTime());
        return response;
    }

    private String defaultTitle(String title) {
        String normalized = StringUtils.defaultIfBlank(title, "新会话").trim();
        return normalized.length() > TITLE_LIMIT ? normalized.substring(0, TITLE_LIMIT) : normalized;
    }

    private String normalizeRole(String role) {
        return "assistant".equals(role) ? "assistant" : "user";
    }

    private String defaultStatus(String status) {
        return StringUtils.defaultIfBlank(status, "default").trim();
    }

    private String blankToNull(String value) {
        return StringUtils.isBlank(value) ? null : value;
    }

    private String nextId() {
        try {
            return IDGenerator.nextStr();
        } catch (NullPointerException ignored) {
            return UUID.randomUUID().toString().replace("-", "");
        }
    }
}
