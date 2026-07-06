package cn.cordys.crm.ai.dealdesk.dto;

import lombok.Data;

@Data
public class DealDeskFileUploadResponse {

    private String id;

    private String name;

    private Integer size;

    private String extension;

    private String mimeType;

    private String previewUrl;
}
