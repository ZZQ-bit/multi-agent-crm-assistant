package cn.cordys.crm.ai.dealdesk.dto;

import lombok.Data;

import java.util.List;

@Data
public class DealDeskChatRequest {

    private String query;

    private String conversationId;

    private BoundObject boundObject;

    private ActiveWriteback activeWriteback;

    private List<ChatFile> files;

    @Data
    public static class BoundObject {
        private String objectType;
        private String objectId;
        private String objectName;
        private String customerId;
        private String customerName;
        private String source;
    }

    @Data
    public static class ActiveWriteback {
        private String id;
        private String type;
        private String status;
        private Target target;
        private Draft recordDraft;
        private Draft planDraft;
        private String resultMessage;
    }

    @Data
    public static class Target {
        private String customerId;
        private String customerName;
        private String opportunityId;
        private String opportunityName;
        private String ownerId;
        private String ownerName;
        private String contactId;
        private String contactName;
    }

    @Data
    public static class Draft {
        private String followMethod;
        private String followTimeText;
        private String planMethod;
        private String planTimeText;
        private String content;
    }

    @Data
    public static class ChatFile {
        private String id;
        private String name;
        private String type;
        private String uploadFileId;
        private String mimeType;
    }
}
