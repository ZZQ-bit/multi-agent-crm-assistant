package cn.cordys.crm.ai.dealdesk.dto;

import lombok.Data;

@Data
public class DealDeskMessageRequest {
    private String role;
    private String content;
    private String referencesJson;
    private String processEventsJson;
    private String writebackJson;
    private String boundObjectJson;
    private String difyMessageId;
    private String status;
}
