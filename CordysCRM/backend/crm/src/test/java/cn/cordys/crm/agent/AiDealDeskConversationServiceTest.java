package cn.cordys.crm.agent;

import cn.cordys.crm.ai.dealdesk.domain.AiDealDeskConversation;
import cn.cordys.crm.ai.dealdesk.domain.AiDealDeskMessage;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskConversationRequest;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskConversationResponse;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskMessageRequest;
import cn.cordys.crm.ai.dealdesk.service.AiDealDeskConversationRepository;
import cn.cordys.crm.ai.dealdesk.service.AiDealDeskConversationService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiDealDeskConversationServiceTest {

    @Test
    void shouldCreateConversationForCurrentUserAndOrganization() {
        InMemoryRepository repository = new InMemoryRepository();
        AiDealDeskConversationService service = new AiDealDeskConversationService(repository);

        DealDeskConversationResponse response = service.create(new DealDeskConversationRequest(), "user-a", "org-a");

        assertNotNull(response.getId());
        assertEquals("新会话", response.getTitle());
        assertEquals("user-a", repository.conversations.get(0).getUserId());
        assertEquals("org-a", repository.conversations.get(0).getOrganizationId());
        assertFalse(repository.conversations.get(0).getDeleted());
    }

    @Test
    void shouldListOnlyCurrentUserConversationsOrderedByUpdateTime() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.conversations.add(conversation("c-old", "org-a", "user-a", "旧会话", 1000L, false));
        repository.conversations.add(conversation("c-other-user", "org-a", "user-b", "其他用户", 3000L, false));
        repository.conversations.add(conversation("c-other-org", "org-b", "user-a", "其他组织", 4000L, false));
        repository.conversations.add(conversation("c-deleted", "org-a", "user-a", "已删除", 5000L, true));
        repository.conversations.add(conversation("c-new", "org-a", "user-a", "新会话", 6000L, false));
        AiDealDeskConversationService service = new AiDealDeskConversationService(repository);

        List<DealDeskConversationResponse> responses = service.list("user-a", "org-a", 50);

        assertEquals(List.of("c-new", "c-old"), responses.stream().map(DealDeskConversationResponse::getId).toList());
    }

    @Test
    void shouldSaveMessagesAndReturnDetailInCreateTimeOrder() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.conversations.add(conversation("c-1", "org-a", "user-a", "测试会话", 1000L, false));
        AiDealDeskConversationService service = new AiDealDeskConversationService(repository);

        DealDeskMessageRequest userMessage = new DealDeskMessageRequest();
        userMessage.setRole("user");
        userMessage.setContent("看一下华东智造集团有哪些商机");
        userMessage.setReferencesJson("[{\"type\":\"customer\"}]");
        service.saveMessage("c-1", userMessage, "user-a", "org-a");

        DealDeskMessageRequest assistantMessage = new DealDeskMessageRequest();
        assistantMessage.setRole("assistant");
        assistantMessage.setContent("查到 2 条商机");
        assistantMessage.setProcessEventsJson("[{\"text\":\"已读取 CRM\"}]");
        assistantMessage.setBoundObjectJson("{\"objectType\":\"customer\"}");
        assistantMessage.setDifyMessageId("dify-message-1");
        service.saveMessage("c-1", assistantMessage, "user-a", "org-a");

        DealDeskConversationResponse detail = service.detail("c-1", "user-a", "org-a");

        assertEquals(2, detail.getMessages().size());
        assertEquals("user", detail.getMessages().get(0).getRole());
        assertEquals("assistant", detail.getMessages().get(1).getRole());
        assertEquals("[{\"text\":\"已读取 CRM\"}]", detail.getMessages().get(1).getProcessEventsJson());
        assertEquals("dify-message-1", detail.getMessages().get(1).getDifyMessageId());
        assertEquals(2, repository.conversations.get(0).getMessageCount());
        assertEquals("查到 2 条商机", repository.conversations.get(0).getLastMessageText());
    }

    @Test
    void shouldSoftDeleteConversationAndMessagesWithoutAffectingOtherUsers() {
        InMemoryRepository repository = new InMemoryRepository();
        repository.conversations.add(conversation("c-1", "org-a", "user-a", "测试会话", 1000L, false));
        repository.conversations.add(conversation("c-2", "org-a", "user-b", "其他用户", 1000L, false));
        repository.messages.add(message("m-1", "c-1", "org-a", "user-a", false, 1000L));
        repository.messages.add(message("m-2", "c-2", "org-a", "user-b", false, 1000L));
        AiDealDeskConversationService service = new AiDealDeskConversationService(repository);

        assertTrue(service.delete("c-1", "user-a", "org-a"));
        assertFalse(service.delete("c-2", "user-a", "org-a"));

        assertTrue(repository.conversations.get(0).getDeleted());
        assertFalse(repository.conversations.get(1).getDeleted());
        assertTrue(repository.messages.get(0).getDeleted());
        assertFalse(repository.messages.get(1).getDeleted());
        assertEquals(0, service.list("user-a", "org-a", 50).size());
    }

    private static AiDealDeskConversation conversation(
            String id,
            String organizationId,
            String userId,
            String title,
            long updateTime,
            boolean deleted
    ) {
        AiDealDeskConversation conversation = new AiDealDeskConversation();
        conversation.setId(id);
        conversation.setOrganizationId(organizationId);
        conversation.setUserId(userId);
        conversation.setTitle(title);
        conversation.setCreateTime(updateTime);
        conversation.setUpdateTime(updateTime);
        conversation.setMessageCount(0);
        conversation.setDeleted(deleted);
        return conversation;
    }

    private static AiDealDeskMessage message(
            String id,
            String conversationId,
            String organizationId,
            String userId,
            boolean deleted,
            long createTime
    ) {
        AiDealDeskMessage message = new AiDealDeskMessage();
        message.setId(id);
        message.setConversationId(conversationId);
        message.setOrganizationId(organizationId);
        message.setUserId(userId);
        message.setRole("user");
        message.setContent("hello");
        message.setStatus("default");
        message.setCreateTime(createTime);
        message.setDeleted(deleted);
        return message;
    }

    private static class InMemoryRepository implements AiDealDeskConversationRepository {
        private final List<AiDealDeskConversation> conversations = new ArrayList<>();
        private final List<AiDealDeskMessage> messages = new ArrayList<>();

        @Override
        public void insertConversation(AiDealDeskConversation conversation) {
            conversations.add(conversation);
        }

        @Override
        public void updateConversation(AiDealDeskConversation conversation) {
            AiDealDeskConversation existing = findConversationById(conversation.getId());
            if (existing == null) {
                return;
            }
            if (conversation.getTitle() != null) existing.setTitle(conversation.getTitle());
            if (conversation.getDifyConversationId() != null) existing.setDifyConversationId(conversation.getDifyConversationId());
            if (conversation.getBoundObjectJson() != null) existing.setBoundObjectJson(conversation.getBoundObjectJson());
            if (conversation.getLastMessageText() != null) existing.setLastMessageText(conversation.getLastMessageText());
            if (conversation.getMessageCount() != null) existing.setMessageCount(conversation.getMessageCount());
            if (conversation.getUpdateTime() != null) existing.setUpdateTime(conversation.getUpdateTime());
            if (conversation.getDeleted() != null) existing.setDeleted(conversation.getDeleted());
        }

        @Override
        public AiDealDeskConversation findConversation(String id, String userId, String organizationId) {
            return conversations.stream()
                    .filter(item -> Objects.equals(item.getId(), id))
                    .filter(item -> Objects.equals(item.getUserId(), userId))
                    .filter(item -> Objects.equals(item.getOrganizationId(), organizationId))
                    .filter(item -> !Boolean.TRUE.equals(item.getDeleted()))
                    .findFirst()
                    .orElse(null);
        }

        @Override
        public List<AiDealDeskConversation> listConversations(String userId, String organizationId, int limit) {
            return conversations.stream()
                    .filter(item -> Objects.equals(item.getUserId(), userId))
                    .filter(item -> Objects.equals(item.getOrganizationId(), organizationId))
                    .filter(item -> !Boolean.TRUE.equals(item.getDeleted()))
                    .sorted(Comparator.comparing(AiDealDeskConversation::getUpdateTime, Comparator.nullsLast(Long::compareTo)).reversed())
                    .limit(limit)
                    .collect(Collectors.toList());
        }

        @Override
        public void insertMessage(AiDealDeskMessage message) {
            messages.add(message);
        }

        @Override
        public List<AiDealDeskMessage> listMessages(String conversationId, String userId, String organizationId) {
            return messages.stream()
                    .filter(item -> Objects.equals(item.getConversationId(), conversationId))
                    .filter(item -> Objects.equals(item.getUserId(), userId))
                    .filter(item -> Objects.equals(item.getOrganizationId(), organizationId))
                    .filter(item -> !Boolean.TRUE.equals(item.getDeleted()))
                    .sorted(Comparator.comparing(AiDealDeskMessage::getCreateTime, Comparator.nullsLast(Long::compareTo)))
                    .collect(Collectors.toList());
        }

        @Override
        public void softDeleteConversation(String id, String userId, String organizationId, long updateTime) {
            AiDealDeskConversation existing = findConversationById(id);
            if (existing != null
                    && Objects.equals(existing.getUserId(), userId)
                    && Objects.equals(existing.getOrganizationId(), organizationId)
                    && !Boolean.TRUE.equals(existing.getDeleted())) {
                existing.setDeleted(true);
                existing.setUpdateTime(updateTime);
            }
        }

        @Override
        public void softDeleteMessages(String conversationId, String userId, String organizationId, long updateTime) {
            messages.stream()
                    .filter(item -> Objects.equals(item.getConversationId(), conversationId))
                    .filter(item -> Objects.equals(item.getUserId(), userId))
                    .filter(item -> Objects.equals(item.getOrganizationId(), organizationId))
                    .forEach(item -> item.setDeleted(true));
        }

        @Override
        public int countMessages(String conversationId, String userId, String organizationId) {
            return listMessages(conversationId, userId, organizationId).size();
        }

        private AiDealDeskConversation findConversationById(String id) {
            return conversations.stream().filter(item -> Objects.equals(item.getId(), id)).findFirst().orElse(null);
        }
    }
}
