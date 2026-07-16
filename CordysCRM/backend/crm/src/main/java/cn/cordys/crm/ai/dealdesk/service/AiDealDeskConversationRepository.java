package cn.cordys.crm.ai.dealdesk.service;

import cn.cordys.crm.ai.dealdesk.domain.AiDealDeskConversation;
import cn.cordys.crm.ai.dealdesk.domain.AiDealDeskMessage;

import java.util.List;

public interface AiDealDeskConversationRepository {
    void insertConversation(AiDealDeskConversation conversation);

    void updateConversation(AiDealDeskConversation conversation);

    AiDealDeskConversation findConversation(String id, String userId, String organizationId);

    List<AiDealDeskConversation> listConversations(String userId, String organizationId, int limit);

    void insertMessage(AiDealDeskMessage message);

    List<AiDealDeskMessage> listMessages(String conversationId, String userId, String organizationId);

    void softDeleteConversation(String id, String userId, String organizationId, long updateTime);

    void softDeleteMessages(String conversationId, String userId, String organizationId, long updateTime);

    int countMessages(String conversationId, String userId, String organizationId);
}
