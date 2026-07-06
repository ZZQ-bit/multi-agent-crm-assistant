package cn.cordys.crm.ai.dealdesk.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class DealDeskChatResponse {

    private String protocolVersion = "1.0";

    private String turnType;

    private String answerText;

    private List<Map<String, Object>> processEvents;

    private Map<String, Object> writeback;

    private Map<String, Object> boundObject;

    private String suggestedSessionTitle;

    private List<String> warnings;

    private String conversationId;

    private String messageId;
}
