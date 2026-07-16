package cn.cordys.crm.ai.dealdesk.dto;

import lombok.Data;

@Data
public class DealDeskToolRequest {
    private String keyword;
    private String objectReference;
    private String customerId;
    private String opportunityId;
    private String contactId;
    private String ownerId;
    private String content;
    private String followMethod;
    private Long followTime;
    private String planMethod;
    private Long planTime;
    private String idempotencyKey;
    private Integer limit;
    private Boolean includeContextOnUnique;
}
