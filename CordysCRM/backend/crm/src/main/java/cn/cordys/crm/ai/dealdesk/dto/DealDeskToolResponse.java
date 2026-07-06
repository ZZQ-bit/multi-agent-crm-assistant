package cn.cordys.crm.ai.dealdesk.dto;

import lombok.Data;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Data
public class DealDeskToolResponse {
    private boolean success;
    private String code;
    private String message;
    private Map<String, Object> data = new LinkedHashMap<>();
    private List<String> warnings = new ArrayList<>();

    public static DealDeskToolResponse ok(Map<String, Object> data) {
        DealDeskToolResponse response = new DealDeskToolResponse();
        response.setSuccess(true);
        response.setCode("OK");
        response.setMessage("");
        response.setData(data == null ? new LinkedHashMap<>() : data);
        return response;
    }

    public static DealDeskToolResponse fail(String code, String message) {
        return fail(code, message, null);
    }

    public static DealDeskToolResponse fail(String code, String message, Map<String, Object> data) {
        DealDeskToolResponse response = new DealDeskToolResponse();
        response.setSuccess(false);
        response.setCode(code);
        response.setMessage(message);
        response.setData(data == null ? new LinkedHashMap<>() : data);
        return response;
    }
}
