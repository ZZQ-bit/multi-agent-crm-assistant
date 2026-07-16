package cn.cordys.crm.ai.dealdesk.domain;

import cn.cordys.common.domain.BaseModel;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "ai_deal_desk_conversation")
public class AiDealDeskConversation extends BaseModel {
    private String organizationId;
    private String userId;
    private String title;
    private String difyConversationId;
    private String boundObjectJson;
    private String lastMessageText;
    private Integer messageCount;
    private Boolean deleted;
}
