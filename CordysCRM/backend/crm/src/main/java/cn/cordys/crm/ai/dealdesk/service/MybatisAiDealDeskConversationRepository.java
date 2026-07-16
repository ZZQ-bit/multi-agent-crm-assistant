package cn.cordys.crm.ai.dealdesk.service;

import cn.cordys.crm.ai.dealdesk.domain.AiDealDeskConversation;
import cn.cordys.crm.ai.dealdesk.domain.AiDealDeskMessage;
import cn.cordys.mybatis.BaseMapper;
import cn.cordys.mybatis.lambda.LambdaQueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MybatisAiDealDeskConversationRepository implements AiDealDeskConversationRepository {

    @Resource
    private BaseMapper<AiDealDeskConversation> conversationMapper;

    @Resource
    private BaseMapper<AiDealDeskMessage> messageMapper;

    @Override
    public void insertConversation(AiDealDeskConversation conversation) {
        conversationMapper.insert(conversation);
    }

    @Override
    public void updateConversation(AiDealDeskConversation conversation) {
        conversationMapper.update(conversation);
    }

    @Override
    public AiDealDeskConversation findConversation(String id, String userId, String organizationId) {
        LambdaQueryWrapper<AiDealDeskConversation> wrapper = scopedConversationWrapper(userId, organizationId)
                .eq(AiDealDeskConversation::getId, id);
        List<AiDealDeskConversation> rows = conversationMapper.selectListByLambda(wrapper);
        return rows.isEmpty() ? null : rows.get(0);
    }

    @Override
    public List<AiDealDeskConversation> listConversations(String userId, String organizationId, int limit) {
        LambdaQueryWrapper<AiDealDeskConversation> wrapper = scopedConversationWrapper(userId, organizationId)
                .orderByDesc(AiDealDeskConversation::getUpdateTime);
        return conversationMapper.selectListByLambda(wrapper).stream()
                .limit(limit)
                .toList();
    }

    @Override
    public void insertMessage(AiDealDeskMessage message) {
        messageMapper.insert(message);
    }

    @Override
    public List<AiDealDeskMessage> listMessages(String conversationId, String userId, String organizationId) {
        LambdaQueryWrapper<AiDealDeskMessage> wrapper = scopedMessageWrapper(userId, organizationId)
                .eq(AiDealDeskMessage::getConversationId, conversationId);
        wrapper.orderByAsc(AiDealDeskMessage::getCreateTime);
        return messageMapper.selectListByLambda(wrapper);
    }

    @Override
    public void softDeleteConversation(String id, String userId, String organizationId, long updateTime) {
        AiDealDeskConversation conversation = findConversation(id, userId, organizationId);
        if (conversation == null) {
            return;
        }
        AiDealDeskConversation update = new AiDealDeskConversation();
        update.setId(conversation.getId());
        update.setDeleted(true);
        update.setUpdateTime(updateTime);
        update.setUpdateUser(userId);
        conversationMapper.update(update);
    }

    @Override
    public void softDeleteMessages(String conversationId, String userId, String organizationId, long updateTime) {
        for (AiDealDeskMessage message : listMessages(conversationId, userId, organizationId)) {
            AiDealDeskMessage update = new AiDealDeskMessage();
            update.setId(message.getId());
            update.setDeleted(true);
            update.setUpdateTime(updateTime);
            update.setUpdateUser(userId);
            messageMapper.update(update);
        }
    }

    @Override
    public int countMessages(String conversationId, String userId, String organizationId) {
        return listMessages(conversationId, userId, organizationId).size();
    }

    private LambdaQueryWrapper<AiDealDeskConversation> scopedConversationWrapper(String userId, String organizationId) {
        return new LambdaQueryWrapper<AiDealDeskConversation>()
                .eq(AiDealDeskConversation::getUserId, userId)
                .eq(AiDealDeskConversation::getOrganizationId, organizationId)
                .eq(AiDealDeskConversation::getDeleted, false);
    }

    private LambdaQueryWrapper<AiDealDeskMessage> scopedMessageWrapper(String userId, String organizationId) {
        return new LambdaQueryWrapper<AiDealDeskMessage>()
                .eq(AiDealDeskMessage::getUserId, userId)
                .eq(AiDealDeskMessage::getOrganizationId, organizationId)
                .eq(AiDealDeskMessage::getDeleted, false);
    }
}
