package cn.cordys.crm.ai.dealdesk.dto;

import lombok.Data;

@Data
public class DealDeskMessageResponse {
    private String id;
    private String role;
    private String content;
    private String referencesJson;
    private String processEventsJson;
    private String writebackJson;
    private String boundObjectJson;
    private String difyMessageId;
    private String status;
    private Long createTime;
}
