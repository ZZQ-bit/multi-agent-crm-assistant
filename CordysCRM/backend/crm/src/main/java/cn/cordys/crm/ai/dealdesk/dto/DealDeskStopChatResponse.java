package cn.cordys.crm.ai.dealdesk.dto;

import lombok.Data;

@Data
public class DealDeskStopChatResponse {

    private boolean success;

    private String message;

    public static DealDeskStopChatResponse ok() {
        DealDeskStopChatResponse response = new DealDeskStopChatResponse();
        response.setSuccess(true);
        response.setMessage("stopped");
        return response;
    }

    public static DealDeskStopChatResponse fail(String message) {
        DealDeskStopChatResponse response = new DealDeskStopChatResponse();
        response.setSuccess(false);
        response.setMessage(message);
        return response;
    }
}
