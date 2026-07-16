package cn.cordys.crm.ai.dealdesk.dto;

import lombok.Data;

@Data
public class DealDeskConversationRequest {
    private String title;
    private String difyConversationId;
    private String boundObjectJson;
    private String lastMessageText;
}
