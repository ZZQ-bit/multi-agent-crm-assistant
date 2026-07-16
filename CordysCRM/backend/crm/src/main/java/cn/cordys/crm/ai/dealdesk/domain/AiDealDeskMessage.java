package cn.cordys.crm.ai.dealdesk.domain;

import cn.cordys.common.domain.BaseModel;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.EqualsAndHashCode;

@Data
@EqualsAndHashCode(callSuper = true)
@Table(name = "ai_deal_desk_message")
public class AiDealDeskMessage extends BaseModel {
    private String conversationId;
    private String organizationId;
    private String userId;
    private String role;
    private String content;
    private String referencesJson;
    private String processEventsJson;
    private String writebackJson;
    private String boundObjectJson;
    private String difyMessageId;
    private String status;
    private Boolean deleted;
}
