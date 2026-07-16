package cn.cordys.crm.ai.dealdesk.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class DealDeskConversationResponse {
    private String id;
    private String title;
    private String difyConversationId;
    private String boundObjectJson;
    private String lastMessageText;
    private Integer messageCount;
    private Long createTime;
    private Long updateTime;
    private List<DealDeskMessageResponse> messages = new ArrayList<>();
}
