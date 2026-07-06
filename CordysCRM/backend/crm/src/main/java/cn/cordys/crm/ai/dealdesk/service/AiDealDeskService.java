package cn.cordys.crm.ai.dealdesk.service;

import cn.cordys.common.util.JSON;
import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskChatRequest;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskChatResponse;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskFileUploadResponse;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskStopChatRequest;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskStopChatResponse;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskToolRequest;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskToolResponse;
import cn.cordys.crm.integration.common.utils.HttpClientUtils;
import cn.cordys.security.SessionUtils;
import com.fasterxml.jackson.core.type.TypeReference;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.StringHttpMessageConverter;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static java.util.Map.entry;

@Slf4j
@Service
public class AiDealDeskService {

    private static final String CHAT_MESSAGES_PATH = "/chat-messages";
    private static final String FILE_UPLOAD_PATH = "/files/upload";
    private static final int DIFY_CHAT_MAX_ATTEMPTS = 3;
    private static final int DIFY_FILE_UPLOAD_MAX_ATTEMPTS = 3;
    private static final int STREAMING_ANSWER_DELTA_CHARS = 48;
    private record WorkflowProcessNode(String eventType, boolean completesOnFinish) {
    }

    private static final Map<String, WorkflowProcessNode> WORKFLOW_PROCESS_NODES = Map.ofEntries(
            entry("主 Agent - 参数抽取", new WorkflowProcessNode("task_identified", false)),
            entry("主 Agent - 路由判断", new WorkflowProcessNode("task_identified", true)),
            entry("主 Agent - 上下文标准化", new WorkflowProcessNode("context_loaded", false)),
            entry("主 Agent - CRM 工具调用规划", new WorkflowProcessNode("context_loaded", false)),
            entry("主 Agent - CRM 工具调用判断", new WorkflowProcessNode("context_loaded", false)),
            entry("CRM Tool API 请求", new WorkflowProcessNode("context_loaded", false)),
            entry("主 Agent - CRM 上下文标准化", new WorkflowProcessNode("context_loaded", true)),
            entry("规则知识库检索", new WorkflowProcessNode("context_loaded", true)),
            entry("规则召回结果标准化", new WorkflowProcessNode("rule_checked", true)),
            entry("轻量问答 Agent", new WorkflowProcessNode("suggestion_generated", false)),
            entry("销售运营 Agent", new WorkflowProcessNode("suggestion_generated", false)),
            entry("财务专项 Agent", new WorkflowProcessNode("suggestion_generated", false)),
            entry("交付专项 Agent", new WorkflowProcessNode("suggestion_generated", false)),
            entry("合同专项 Agent", new WorkflowProcessNode("suggestion_generated", false)),
            entry("销售评估 Agent", new WorkflowProcessNode("suggestion_generated", false)),
            entry("财务风控 Agent", new WorkflowProcessNode("suggestion_generated", false)),
            entry("交付可行性 Agent", new WorkflowProcessNode("suggestion_generated", false)),
            entry("合同风险 Agent", new WorkflowProcessNode("suggestion_generated", false)),
            entry("协调总结 Agent", new WorkflowProcessNode("suggestion_generated", false)),
            entry("最终输出 - 轻量回答", new WorkflowProcessNode("suggestion_generated", true)),
            entry("最终输出 - 财务专项", new WorkflowProcessNode("suggestion_generated", true)),
            entry("最终输出 - 交付专项", new WorkflowProcessNode("suggestion_generated", true)),
            entry("最终输出 - 合同专项", new WorkflowProcessNode("suggestion_generated", true)),
            entry("最终输出 - 销售运营任务", new WorkflowProcessNode("suggestion_generated", true)),
            entry("最终输出 - 多Agent智能助手 业务判断", new WorkflowProcessNode("suggestion_generated", true))
    );
    private static final Map<String, String> CONVERSATION_VARIABLE_IDS = Map.of(
            "active_writeback_id", "0fcbcff0-3c62-4f0f-a2cb-7d4f88b54811",
            "active_writeback_status", "488a2f8d-e971-4db0-a3bd-0450e10c2ae2",
            "active_writeback_type", "f6d7d452-19c5-403b-9f47-3088d5c8db0a",
            "active_writeback_payload_json", "54f17a72-c725-4aeb-a63e-c5cc8d529436"
    );

    private final RestTemplate restTemplate;

    @Value("${ai.deal-desk.dify.base-url:https://api.dify.ai/v1}")
    private String difyBaseUrl;

    @Value("${ai.deal-desk.dify.api-key:}")
    private String difyApiKey;

    @Value("${ai.deal-desk.dify.user-id:admin}")
    private String difyToolUserId;

    @Value("${ai.deal-desk.dify.organization-id:100001}")
    private String difyToolOrganizationId;

    @Autowired(required = false)
    private AiDealDeskToolService aiDealDeskToolService;

    public AiDealDeskService() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(10000);
        factory.setReadTimeout(600000);
        this.restTemplate = new RestTemplate(factory);
        this.restTemplate.getMessageConverters().removeIf(StringHttpMessageConverter.class::isInstance);
        this.restTemplate.getMessageConverters().add(0, new StringHttpMessageConverter(StandardCharsets.UTF_8));
    }

    public DealDeskChatResponse chat(DealDeskChatRequest request) {
        Map<String, Object> body = buildChatRequestBody(request, buildCurrentDifyUser());

        String responseBody;
        try {
            responseBody = postStreamingChat(buildUrl(CHAT_MESSAGES_PATH), body);
        } catch (HttpServerErrorException.GatewayTimeout e) {
            log.warn("Dify timed out: {}", e.getMessage());
            throw new IllegalStateException("多Agent智能助手 request timed out. Please retry.", e);
        }

        if (!looksLikeJson(responseBody)) {
            throw new IllegalStateException("Dify chat response is not valid JSON");
        }

        Map<String, Object> rawResponse = JSON.parseToMap(responseBody);
        return adaptChatResponse(rawResponse, request);
    }

    public DealDeskStopChatResponse stopChat(DealDeskStopChatRequest request) {
        validateConfig();
        String taskId = request == null ? "" : StringUtils.trimToEmpty(request.getTaskId());
        if (StringUtils.isBlank(taskId)) {
            return DealDeskStopChatResponse.fail("taskId can not be blank");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("user", buildCurrentDifyUser());
        try {
            postJson(buildUrl(CHAT_MESSAGES_PATH + "/" + taskId + "/stop"), body);
            return DealDeskStopChatResponse.ok();
        } catch (RestClientException e) {
            log.warn("Failed to stop Dify chat generation, taskId={}", taskId, e);
            return DealDeskStopChatResponse.fail("Failed to stop Dify chat generation: " + e.getMessage());
        }
    }

    public void streamChat(DealDeskChatRequest request, OutputStream outputStream) {
        streamChat(request, outputStream, buildCurrentDifyUser());
    }

    public void streamChat(DealDeskChatRequest request, OutputStream outputStream, String difyUser) {
        Map<String, Object> body = buildChatRequestBody(request, difyUser);
        StringBuilder rawSseBody = new StringBuilder();
        AtomicBoolean visibleAnswerDeltaSent = new AtomicBoolean(false);

        try {
            postStreamingChat(buildUrl(CHAT_MESSAGES_PATH), body, payload -> {
                rawSseBody.append("data: ").append(payload).append('\n');
                Map<String, Object> frame = adaptStreamingFrame(payload);
                if (frame == null || frame.isEmpty()) {
                    return;
                }
                if ("final".equals(frame.get("type"))) {
                    return;
                }
                if ("answer_delta".equals(frame.get("type"))) {
                    String text = asText(frame.get("text"));
                    if (shouldSuppressStreamingAnswerDelta(text)) {
                        return;
                    }
                    visibleAnswerDeltaSent.set(true);
                }
                writeSseFrame(outputStream, frame);
            });
            String adapted = adaptStreamingResponse(rawSseBody.toString());
            if (looksLikeJson(adapted)) {
                DealDeskChatResponse finalResponse = adaptChatResponse(JSON.parseToMap(adapted), request);
                if (!visibleAnswerDeltaSent.get()) {
                    writeVisibleAnswerDeltas(outputStream, finalResponse);
                }
                writeSseFrame(outputStream, buildFinalFrame(finalResponse));
            }
        } catch (HttpServerErrorException.GatewayTimeout e) {
            log.warn("Dify timed out in streaming chat: {}", e.getMessage());
            writeSseFrame(outputStream, buildFinalFrame(buildFailedChatResponse("多Agent智能助手 request timed out. Please retry.")));
        } catch (RestClientException e) {
            log.warn("Dify failed in streaming chat: {}", e.getMessage(), e);
            writeSseFrame(outputStream, buildFinalFrame(buildFailedChatResponse("多Agent智能助手 request failed. Please retry.")));
        }
    }

    public DealDeskFileUploadResponse uploadFile(MultipartFile file) {
        validateConfig();
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("file can not be empty");
        }

        String user = buildCurrentDifyUser();
        LinkedMultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new NamedByteArrayResource(readBytes(file), file.getOriginalFilename()));
        form.add("user", user);

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(difyApiKey);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        ResponseEntity<String> response = uploadDifyFileWithRetry(form, headers);

        Map<String, Object> body = JSON.parseToMap(StringUtils.defaultString(response.getBody(), "{}"));
        DealDeskFileUploadResponse uploadResponse = new DealDeskFileUploadResponse();
        uploadResponse.setId(asText(body.get("id")));
        uploadResponse.setName(asText(body.get("name")));
        uploadResponse.setSize(asInteger(body.get("size")));
        uploadResponse.setExtension(asText(body.get("extension")));
        uploadResponse.setMimeType(asText(body.get("mime_type")));
        uploadResponse.setPreviewUrl(asText(body.get("preview_url")));
        log.info("Dify file upload succeeded, id={}, name={}, mimeType={}, size={}",
                uploadResponse.getId(),
                uploadResponse.getName(),
                uploadResponse.getMimeType(),
                uploadResponse.getSize());
        return uploadResponse;
    }

    private ResponseEntity<String> uploadDifyFileWithRetry(LinkedMultiValueMap<String, Object> form, HttpHeaders headers) {
        ResourceAccessException lastException = null;
        for (int attempt = 1; attempt <= DIFY_FILE_UPLOAD_MAX_ATTEMPTS; attempt++) {
            try {
                return restTemplate.exchange(
                        buildUrl(FILE_UPLOAD_PATH),
                        HttpMethod.POST,
                        new HttpEntity<>(form, headers),
                        String.class
                );
            } catch (ResourceAccessException e) {
                lastException = e;
                if (attempt >= DIFY_FILE_UPLOAD_MAX_ATTEMPTS) {
                    throw e;
                }
                log.warn("Dify file upload connection failed, retrying attempt {}/{}: {}",
                        attempt + 1, DIFY_FILE_UPLOAD_MAX_ATTEMPTS, e.getMessage());
                sleepBeforeRetry(attempt);
            }
        }
        throw lastException;
    }

    private void syncConversationVariables(String conversationId, String user, DealDeskChatRequest.ActiveWriteback activeWriteback) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("active_writeback_id", activeWriteback == null ? "" : StringUtils.defaultString(activeWriteback.getId()));
        values.put("active_writeback_status", activeWriteback == null ? "" : StringUtils.defaultString(activeWriteback.getStatus()));
        values.put("active_writeback_type", activeWriteback == null ? "" : StringUtils.defaultString(activeWriteback.getType()));
        values.put("active_writeback_payload_json", activeWriteback == null ? "" : JSON.toJSONString(activeWriteback));

        for (Map.Entry<String, String> entry : CONVERSATION_VARIABLE_IDS.entrySet()) {
            Map<String, Object> requestBody = new LinkedHashMap<>();
            requestBody.put("value", values.get(entry.getKey()));
            requestBody.put("user", user);
            String path = "/conversations/" + conversationId + "/variables/" + entry.getValue();
            HttpClientUtils.sendRequest(HttpMethod.PUT, buildUrl(path), JSON.toJSONString(requestBody), buildJsonHeaders());
        }
    }

    private Map<String, Object> buildInputs(DealDeskChatRequest request) {
        Map<String, Object> inputs = new LinkedHashMap<>();
        DealDeskChatRequest.BoundObject boundObject = request.getBoundObject();
        DealDeskChatRequest.ActiveWriteback activeWriteback = request.getActiveWriteback();
        if (isCustomer(boundObject) && isCustomerOpportunityListQuery(normalizeQueryWithBoundObject(request))) {
            boundObject = null;
        }

        inputs.put("bound_object_type", boundObject == null ? "" : StringUtils.defaultString(boundObject.getObjectType()));
        inputs.put("bound_object_id", boundObject == null ? "" : StringUtils.defaultString(boundObject.getObjectId()));
        inputs.put("bound_object_name", boundObject == null ? "" : StringUtils.defaultString(boundObject.getObjectName()));
        inputs.put("bound_object_source", boundObject == null ? "" : StringUtils.defaultString(boundObject.getSource()));
        inputs.put("route_customer_id", isCustomer(boundObject) ? StringUtils.defaultString(boundObject.getObjectId()) : "");
        inputs.put("route_opportunity_id", isOpportunity(boundObject) ? StringUtils.defaultString(boundObject.getObjectId()) : "");
        inputs.put("attachments_summary", buildAttachmentSummary(request.getFiles()));
        inputs.put("attachment_names", buildAttachmentNames(request.getFiles()));
        inputs.put("uploaded_files", buildDifyFileReferences(request.getFiles()));
        inputs.put("active_writeback_id", activeWriteback == null ? "" : StringUtils.defaultString(activeWriteback.getId()));
        inputs.put("active_writeback_status", activeWriteback == null ? "" : StringUtils.defaultString(activeWriteback.getStatus()));
        inputs.put("active_writeback_type", activeWriteback == null ? "" : StringUtils.defaultString(activeWriteback.getType()));
        inputs.put("active_writeback_payload_json", activeWriteback == null ? "" : JSON.toJSONString(activeWriteback));
        inputs.put("resume_query", isSelection(boundObject) ? StringUtils.defaultString(request.getQuery()) : "");
        inputs.put("selected_object_type", isSelection(boundObject) ? StringUtils.defaultString(boundObject.getObjectType()) : "");
        inputs.put("selected_object_id", isSelection(boundObject) ? StringUtils.defaultString(boundObject.getObjectId()) : "");
        inputs.put("selected_object_name", isSelection(boundObject) ? StringUtils.defaultString(boundObject.getObjectName()) : "");
        return inputs;
    }

    private List<Map<String, Object>> buildChatFiles(List<DealDeskChatRequest.ChatFile> files) {
        return buildDifyFileReferences(files);
    }

    private List<Map<String, Object>> buildDifyFileReferences(List<DealDeskChatRequest.ChatFile> files) {
        if (files == null || files.isEmpty()) {
            return Collections.emptyList();
        }

        return files.stream()
                .filter(item -> StringUtils.isNotBlank(item.getUploadFileId()))
                .map(item -> {
                    Map<String, Object> file = new LinkedHashMap<>();
                    file.put("type", StringUtils.defaultIfBlank(item.getType(), mapFileType(item.getMimeType())));
                    file.put("transfer_method", "local_file");
                    file.put("upload_file_id", item.getUploadFileId());
                    return file;
                })
                .collect(Collectors.toList());
    }

    private Map<String, Object> buildChatRequestBody(DealDeskChatRequest request) {
        return buildChatRequestBody(request, buildCurrentDifyUser());
    }

    private Map<String, Object> buildChatRequestBody(DealDeskChatRequest request, String difyUser) {
        validateConfig();

        String query = normalizeQueryWithBoundObject(request);
        String conversationId = StringUtils.trimToEmpty(request.getConversationId());
        String user = StringUtils.isNotBlank(difyUser) ? difyUser : buildCurrentDifyUser();

        if (StringUtils.isBlank(query)) {
            throw new IllegalArgumentException("query can not be blank");
        }

        if (StringUtils.isNotBlank(conversationId)) {
            syncConversationVariables(conversationId, user, request.getActiveWriteback());
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("inputs", buildInputs(request));
        body.put("query", query);
        body.put("response_mode", "streaming");
        body.put("conversation_id", conversationId);
        body.put("user", user);
        body.put("auto_generate_name", false);
        body.put("files", buildChatFiles(request.getFiles()));
        return body;
    }

    private String normalizeQueryWithBoundObject(DealDeskChatRequest request) {
        if (request == null) {
            return "";
        }

        String query = StringUtils.trimToEmpty(request.getQuery());
        DealDeskChatRequest.BoundObject boundObject = request.getBoundObject();
        if (StringUtils.isBlank(query) || boundObject == null || StringUtils.isBlank(boundObject.getObjectName())) {
            return query;
        }

        String objectName = StringUtils.trimToEmpty(boundObject.getObjectName());
        if (StringUtils.isBlank(objectName) || query.contains(objectName)) {
            return query;
        }

        if (isCustomer(boundObject)) {
            String normalizedCustomerQuery = query
                    .replace("这个客户", objectName)
                    .replace("该客户", objectName)
                    .replace("当前客户", objectName);
            if (isCustomerOpportunityListQuery(normalizedCustomerQuery) && !normalizedCustomerQuery.contains(objectName)) {
                return objectName + normalizedCustomerQuery;
            }
            return normalizedCustomerQuery;
        }

        if (isOpportunity(boundObject)) {
            return query
                    .replace("这个商机", objectName)
                    .replace("该商机", objectName)
                    .replace("当前商机", objectName)
                    .replace("这个项目", objectName)
                    .replace("该项目", objectName)
                    .replace("当前项目", objectName);
        }

        return query;
    }

    private boolean isCustomerOpportunityListQuery(String query) {
        String normalized = StringUtils.trimToEmpty(query);
        return StringUtils.containsAny(normalized, "哪些商机", "商机列表", "相关商机", "所有商机");
    }

    private String postJson(String url, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        buildJsonHeaders().forEach(headers::set);
        ResponseEntity<String> response = restTemplate.exchange(
                url,
                HttpMethod.POST,
                new HttpEntity<>(JSON.toJSONString(body), headers),
                String.class
        );
        return StringUtils.defaultString(response.getBody());
    }

    private String postStreamingChat(String url, Map<String, Object> body) {
        String sseBody = postDifyChatWithRetry(url, body);
        return adaptStreamingResponse(sseBody);
    }

    private void postStreamingChat(String url, Map<String, Object> body, java.util.function.Consumer<String> onPayload) {
        ResourceAccessException lastException = null;
        for (int attempt = 1; attempt <= DIFY_CHAT_MAX_ATTEMPTS; attempt++) {
            boolean[] payloadSeen = {false};
            try {
                postStreamingChatOnce(url, body, payload -> {
                    payloadSeen[0] = true;
                    onPayload.accept(payload);
                });
                return;
            } catch (ResourceAccessException e) {
                lastException = e;
                if (payloadSeen[0] || attempt >= DIFY_CHAT_MAX_ATTEMPTS) {
                    throw e;
                }
                log.warn("Dify streaming chat connection failed, retrying attempt {}/{}: {}",
                        attempt + 1, DIFY_CHAT_MAX_ATTEMPTS, e.getMessage());
                sleepBeforeRetry(attempt);
            }
        }
        throw lastException;
    }

    private String postDifyChatWithRetry(String url, Map<String, Object> body) {
        ResourceAccessException lastException = null;
        for (int attempt = 1; attempt <= DIFY_CHAT_MAX_ATTEMPTS; attempt++) {
            try {
                return postJson(url, body);
            } catch (ResourceAccessException e) {
                lastException = e;
                if (attempt >= DIFY_CHAT_MAX_ATTEMPTS) {
                    throw e;
                }
                log.warn("Dify blocking chat connection failed, retrying attempt {}/{}: {}",
                        attempt + 1, DIFY_CHAT_MAX_ATTEMPTS, e.getMessage());
                sleepBeforeRetry(attempt);
            }
        }
        throw lastException;
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(Math.min(1000L * attempt, 3000L));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while retrying Dify chat request", e);
        }
    }

    private void postStreamingChatOnce(String url, Map<String, Object> body, java.util.function.Consumer<String> onPayload) {
        HttpHeaders headers = new HttpHeaders();
        buildJsonHeaders().forEach(headers::set);
        restTemplate.execute(
                url,
                HttpMethod.POST,
                request -> {
                    headers.forEach((key, values) -> values.forEach(value -> request.getHeaders().add(key, value)));
                    request.getBody().write(JSON.toJSONString(body).getBytes(StandardCharsets.UTF_8));
                },
                response -> {
                    try (BufferedReader reader = new BufferedReader(new InputStreamReader(response.getBody(), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = reader.readLine()) != null) {
                            String payload = extractSsePayload(line);
                            if (StringUtils.isBlank(payload)) {
                                continue;
                            }
                            onPayload.accept(payload);
                        }
                    }
                    return null;
                }
        );
    }

    private String adaptStreamingResponse(String sseBody) {
        Map<String, Object> response = new LinkedHashMap<>();
        StringBuilder answer = new StringBuilder();
        List<Map<String, Object>> processEvents = new ArrayList<>();
        List<String> eventTypes = new ArrayList<>();
        List<String> workflowOutputKeys = new ArrayList<>();
        String workflowError = "";

        try (BufferedReader reader = new BufferedReader(new StringReader(StringUtils.defaultString(sseBody)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String payload = extractSsePayload(line);
                if (StringUtils.isBlank(payload)) {
                    continue;
                }

                Map<String, Object> event = JSON.parseToMap(payload);
                String eventType = asText(event.get("event"));
                eventTypes.add(eventType);
                appendWorkflowProcessEvent(processEvents, eventType, event);
                if ("message".equals(eventType)) {
                    appendIfPresent(answer, asText(event.get("answer")));
                    putIfPresent(response, "conversation_id", event.get("conversation_id"));
                    putIfPresent(response, "message_id", event.get("message_id"));
                    continue;
                }

                if ("workflow_finished".equals(eventType)) {
                    Map<String, Object> data = parseMap(event.get("data"));
                    Map<String, Object> outputs = parseMap(data.get("outputs"));
                    if ("failed".equalsIgnoreCase(asText(data.get("status"))) && StringUtils.isNotBlank(asText(data.get("error")))) {
                        workflowError = asText(data.get("error"));
                    }
                    workflowOutputKeys.clear();
                    workflowOutputKeys.addAll(outputs.keySet());
                    if (StringUtils.isBlank(answer) && outputs.containsKey("answer")) {
                        appendIfPresent(answer, asText(outputs.get("answer")));
                    }
                    response.put("metadata", Map.of("workflow_outputs", outputs));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read Dify streaming response", e);
        }

        if (StringUtils.isBlank(answer) && StringUtils.isNotBlank(workflowError)) {
            String failureMessage = buildDifyWorkflowFailureMessage(workflowError);
            appendIfPresent(answer, failureMessage);
            workflowOutputKeys.clear();
            workflowOutputKeys.add("protocolVersion");
            workflowOutputKeys.add("turnType");
            workflowOutputKeys.add("answerText");
            processEvents.add(buildEvent("event-failed", "failed", "failed", failureMessage));
            Map<String, Object> outputs = new LinkedHashMap<>();
            outputs.put("protocolVersion", "1.0");
            outputs.put("turnType", "failed");
            outputs.put("answerText", failureMessage);
            response.put("metadata", Map.of("workflow_outputs", outputs));
        }

        response.put("answer", answer.toString());
        if (!processEvents.isEmpty()) {
            response.put("process_events", processEvents);
        }
        boolean hasStructuredAnswer = workflowOutputKeys.stream().anyMatch(key ->
                "answerText".equals(key) || "answer_text".equals(key));
        if (StringUtils.isBlank(answer) && !hasStructuredAnswer) {
            log.warn("Dify streaming response had no final answer, events={}, workflowOutputKeys={}, processEvents={}",
                    eventTypes,
                    workflowOutputKeys,
                    processEvents.size());
        } else {
            log.info("Dify streaming response parsed, answerChars={}, structuredAnswer={}, events={}, workflowOutputKeys={}",
                    answer.length(),
                    hasStructuredAnswer,
                    eventTypes,
                    workflowOutputKeys);
        }
        return JSON.toJSONString(response);
    }

    private Map<String, Object> adaptStreamingFrame(String payload) {
        if (StringUtils.isBlank(payload) || "[DONE]".equals(StringUtils.trim(payload))) {
            return null;
        }

        Map<String, Object> event = JSON.parseToMap(payload);
        String eventType = asText(event.get("event"));
        if ("message".equals(eventType)) {
            String text = asText(event.get("answer"));
            if (StringUtils.isBlank(text)) {
                return null;
            }
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("type", "answer_delta");
            frame.put("text", text);
            putIfPresent(frame, "conversationId", event.get("conversation_id"));
            putIfPresent(frame, "messageId", event.get("message_id"));
            putIfPresent(frame, "taskId", event.get("task_id"));
            return frame;
        }

        if ("workflow_finished".equals(eventType)) {
            Map<String, Object> rawResponse = adaptWorkflowFinishedFrame(event);
            if (rawResponse.isEmpty()) {
                return null;
            }
            return buildRawFinalFrame(rawResponse, null);
        }

        Map<String, Object> processEvent = buildWorkflowProcessEvent(eventType, event);
        if (processEvent.isEmpty()) {
            return null;
        }

        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "process_event");
        frame.put("event", processEvent);
        putIfPresent(frame, "taskId", event.get("task_id"));
        return frame;
    }

    private String extractSsePayload(String line) {
        if (!StringUtils.startsWith(line, "data:")) {
            return "";
        }

        String payload = StringUtils.trim(line.substring("data:".length()));
        if (StringUtils.isBlank(payload) || "[DONE]".equals(payload)) {
            return "";
        }
        return payload;
    }

    private boolean shouldSuppressStreamingAnswerDelta(String text) {
        String trimmed = StringUtils.trimToEmpty(text);
        return StringUtils.startsWith(trimmed, "{") || StringUtils.startsWith(trimmed, "```json");
    }

    private void writeVisibleAnswerDeltas(OutputStream outputStream, DealDeskChatResponse response) {
        String answerText = response == null ? "" : StringUtils.defaultString(response.getAnswerText());
        if (StringUtils.isBlank(answerText) || shouldSuppressStreamingAnswerDelta(answerText)) {
            return;
        }

        int offset = 0;
        while (offset < answerText.length()) {
            int nextOffset = Math.min(offset + STREAMING_ANSWER_DELTA_CHARS, answerText.length());
            Map<String, Object> frame = new LinkedHashMap<>();
            frame.put("type", "answer_delta");
            frame.put("text", answerText.substring(offset, nextOffset));
            putIfPresent(frame, "conversationId", response.getConversationId());
            putIfPresent(frame, "messageId", response.getMessageId());
            writeSseFrame(outputStream, frame);
            offset = nextOffset;
        }
    }

    private Map<String, Object> adaptWorkflowFinishedFrame(Map<String, Object> event) {
        Map<String, Object> data = parseMap(event.get("data"));
        Map<String, Object> outputs = parseMap(data.get("outputs"));
        if (outputs.isEmpty()) {
            return Collections.emptyMap();
        }

        Map<String, Object> rawResponse = new LinkedHashMap<>();
        putIfPresent(rawResponse, "conversation_id", event.get("conversation_id"));
        putIfPresent(rawResponse, "message_id", event.get("message_id"));
        if (outputs.containsKey("answer")) {
            rawResponse.put("answer", asText(outputs.get("answer")));
        }
        rawResponse.put("metadata", Map.of("workflow_outputs", outputs));
        return rawResponse;
    }

    private void appendWorkflowProcessEvent(List<Map<String, Object>> processEvents, String eventType, Map<String, Object> event) {
        if (processEvents == null || StringUtils.isBlank(eventType) || event == null) {
            return;
        }

        Map<String, Object> normalizedEvent = buildWorkflowProcessEvent(eventType, event);
        if (!normalizedEvent.isEmpty()) {
            processEvents.add(normalizedEvent);
        }
    }

    private Map<String, Object> buildWorkflowProcessEvent(String eventType, Map<String, Object> event) {
        if (StringUtils.isBlank(eventType) || event == null) {
            return Collections.emptyMap();
        }

        if ("workflow_started".equals(eventType)) {
            return buildEvent(
                    firstNonBlank(asText(event.get("workflow_run_id")), "workflow-started"),
                    "task_identified",
                    "running",
                    buildProcessEventText("task_identified", "running")
            );
        }

        if (!"node_started".equals(eventType) && !"node_finished".equals(eventType)) {
            return Collections.emptyMap();
        }

        Map<String, Object> node = extractNode(event);
        Map<String, Object> data = parseMap(event.get("data"));
        String status = "node_finished".equals(eventType)
                ? mapWorkflowNodeStatus(firstNonBlank(asText(data.get("status")), asText(event.get("status"))))
                : "running";
        String nodeId = firstNonBlank(
                asText(node.get("id")),
                asText(data.get("node_id")),
                asText(event.get("task_id")),
                "workflow-node"
        );
        String nodeTitle = extractWorkflowNodeTitle(node);
        return mapWorkflowProcessEventByNode(nodeId, nodeTitle, eventType, status);
    }

    private String mapWorkflowNodeStatus(String status) {
        String normalized = StringUtils.lowerCase(status);
        if (StringUtils.containsAny(normalized, "fail", "error", "exception")) {
            return "failed";
        }
        if (StringUtils.containsAny(normalized, "warn")) {
            return "warning";
        }
        if (StringUtils.containsAny(normalized, "success", "succeeded", "completed", "finish")) {
            return "completed";
        }
        return "running";
    }

    private String buildProcessEventText(String type, String status) {
        if ("failed".equals(status) || "failed".equals(type)) {
            return "暂时无法完成本轮处理";
        }

        boolean running = "running".equals(status);
        if ("task_identified".equals(type)) {
            return running ? "正在识别本轮任务" : "已识别本轮任务";
        }
        if ("object_required".equals(type)) {
            return "需要先确认本轮分析对象";
        }
        if ("object_selected".equals(type)) {
            return running ? "正在确认本轮分析对象" : "已确定本轮分析对象";
        }
        if ("context_loaded".equals(type)) {
            return running ? "正在读取相关业务资料" : "已读取相关业务资料";
        }
        if ("memory_used".equals(type)) {
            return "已参考当前会话信息";
        }
        if ("rule_checked".equals(type)) {
            return running ? "正在核对关键业务条件" : "已核对关键业务条件";
        }
        if ("risk_found".equals(type)) {
            return running ? "正在识别风险与信息缺口" : "已识别风险与信息缺口";
        }
        if ("suggestion_generated".equals(type)) {
            return running ? "正在生成结论和下一步建议" : "已生成结论和下一步建议";
        }
        if ("confirmation_required".equals(type)) {
            return "等待你确认下一步操作";
        }
        if ("writeback_completed".equals(type)) {
            return running ? "正在写入 CRM" : "已完成 CRM 写入";
        }
        return running ? "正在识别本轮任务" : "已识别本轮任务";
    }

    private Map<String, Object> mapWorkflowProcessEventByNode(String nodeId, String nodeTitle, String eventType, String status) {
        if (StringUtils.isBlank(nodeTitle)) {
            return Collections.emptyMap();
        }

        WorkflowProcessNode processNode = WORKFLOW_PROCESS_NODES.get(nodeTitle);
        if (processNode == null) {
            return Collections.emptyMap();
        }

        if ("failed".equals(status)) {
            return buildEvent(nodeId, "failed", "failed", buildProcessEventText("failed", "failed"));
        }

        if ("node_started".equals(eventType)) {
            return buildEvent(
                    nodeId,
                    processNode.eventType(),
                    "running",
                    buildProcessEventText(processNode.eventType(), "running")
            );
        }

        if (!"node_finished".equals(eventType)) {
            return Collections.emptyMap();
        }

        if (processNode.completesOnFinish()) {
            return buildEvent(
                    nodeId,
                    processNode.eventType(),
                    status,
                    buildProcessEventText(processNode.eventType(), status)
            );
        }

        return Collections.emptyMap();
    }

    private String extractWorkflowNodeTitle(Map<String, Object> node) {
        return StringUtils.trimToEmpty(firstNonBlank(
                asText(node.get("title")),
                asText(node.get("name")),
                asText(parseMap(node.get("data")).get("title"))
        ));
    }

    private Map<String, Object> extractNode(Map<String, Object> event) {
        Map<String, Object> node = parseMap(event.get("node"));
        Map<String, Object> data = parseMap(event.get("data"));
        if (!node.isEmpty()) {
            if (!node.containsKey("id")) {
                putIfPresent(node, "id", data.get("node_id"));
            }
            if (!node.containsKey("title")) {
                putIfPresent(node, "title", data.get("title"));
            }
            if (!node.containsKey("type")) {
                putIfPresent(node, "type", data.get("node_type"));
            }
            return node;
        }
        Map<String, Object> normalized = new LinkedHashMap<>();
        putIfPresent(normalized, "id", data.get("node_id"));
        putIfPresent(normalized, "title", data.get("title"));
        putIfPresent(normalized, "type", data.get("node_type"));
        return normalized;
    }

    private DealDeskChatResponse adaptChatResponse(Map<String, Object> rawResponse, DealDeskChatRequest request) {
        Map<String, Object> outputMap = extractOutputMap(rawResponse);
        String answer = asText(rawResponse.get("answer"));

        DealDeskChatResponse response = new DealDeskChatResponse();
        response.setConversationId(asText(rawResponse.get("conversation_id")));
        response.setMessageId(asText(rawResponse.get("message_id")));
        response.setAnswerText(cleanAnswerText(firstNonBlank(
                asText(outputMap.get("answerText")),
                asText(outputMap.get("answer_text")),
                answer
        )));
        response.setTurnType(resolveTurnType(outputMap, request, response.getAnswerText()));
        response.setSuggestedSessionTitle(firstNonBlank(
                asText(outputMap.get("suggestedSessionTitle")),
                asText(outputMap.get("suggested_session_title"))
        ));
        response.setWarnings(parseStringList(outputMap.get("warnings")));
        response.setProcessEvents(resolveProcessEvents(rawResponse, outputMap, request, response.getTurnType()));
        Map<String, Object> writeback = normalizeWritebackResult(resolveWriteback(outputMap, request), request);
        setIfNotEmpty(response::setWriteback, writeback);
        if ("awaiting_confirm".equals(asText(writeback.get("status")))) {
            response.setAnswerText(buildWritebackConfirmationText(writeback));
        }
        if ("failed".equals(asText(writeback.get("status"))) && StringUtils.isNotBlank(asText(writeback.get("resultMessage")))) {
            response.setAnswerText(asText(writeback.get("resultMessage")));
        }
        setIfNotEmpty(response::setBoundObject, resolveBoundObject(outputMap, request));

        if (StringUtils.isBlank(response.getAnswerText())) {
            String message = "多Agent智能助手 未返回有效最终回复。请检查 Dify 工作流节点是否失败、CRM_TOOL_BASE_URL 是否为当前可访问 tunnel，以及 DIFY_TOOL_TOKEN 是否一致。";
            DealDeskChatResponse failed = buildFailedChatResponse(message);
            List<Map<String, Object>> events = new ArrayList<>(response.getProcessEvents() == null ? Collections.emptyList() : response.getProcessEvents());
            events.add(buildEvent("event-failed", "failed", "failed", message));
            failed.setProcessEvents(events);
            return failed;
        }

        return response;
    }

    private DealDeskChatResponse buildFailedChatResponse(String message) {
        DealDeskChatResponse response = new DealDeskChatResponse();
        response.setTurnType("failed");
        response.setAnswerText(message);
        response.setProcessEvents(List.of(
                buildEvent("event-failed", "failed", "failed", message)
        ));
        return response;
    }

    private String buildDifyWorkflowFailureMessage(String workflowError) {
        String normalizedError = StringUtils.abbreviate(StringUtils.normalizeSpace(workflowError), 1200);
        if (StringUtils.containsIgnoreCase(normalizedError, "account balance is insufficient")) {
            return "Dify 模型调用失败：模型供应商账号余额不足。原始错误：" + normalizedError;
        }
        return "Dify 工作流执行失败：" + normalizedError;
    }

    private Map<String, Object> extractOutputMap(Map<String, Object> rawResponse) {
        List<Object> candidates = new ArrayList<>();
        candidates.add(rawResponse.get("outputs"));
        candidates.add(rawResponse.get("data"));

        Object metadata = rawResponse.get("metadata");
        if (metadata instanceof Map<?, ?> metadataMap) {
            candidates.add(metadataMap.get("outputs"));
            candidates.add(metadataMap.get("workflow_outputs"));
            candidates.add(metadataMap.get("data"));
        }

        for (Object candidate : candidates) {
            Map<String, Object> normalized = normalizeOutputCandidate(candidate);
            if (!normalized.isEmpty()) {
                return normalized;
            }
        }

        String answer = asText(rawResponse.get("answer"));
        if (looksLikeJson(answer)) {
            try {
                Map<String, Object> parsed = JSON.parseToMap(answer);
                Map<String, Object> normalized = normalizeOutputCandidate(parsed);
                if (!normalized.isEmpty()) {
                    return normalized;
                }
            } catch (Exception e) {
                log.debug("answer is not protocol json", e);
            }
        }

        return Collections.emptyMap();
    }

    private Map<String, Object> normalizeOutputCandidate(Object candidate) {
        if (!(candidate instanceof Map<?, ?> map)) {
            return Collections.emptyMap();
        }

        Map<String, Object> normalized = new LinkedHashMap<>();
        map.forEach((key, value) -> {
            if (key instanceof String keyText) {
                normalized.put(keyText, value);
            }
        });

        if (normalized.containsKey("protocolVersion")
                || normalized.containsKey("protocol_version")
                || normalized.containsKey("turnType")
                || normalized.containsKey("turn_type")
                || normalized.containsKey("processEvents")
                || normalized.containsKey("process_events_json")
                || normalized.containsKey("writeback")
                || normalized.containsKey("boundObject")) {
            return normalized;
        }

        if (normalized.size() == 1 && normalized.values().iterator().next() instanceof Map<?, ?> nested) {
            return normalizeOutputCandidate(nested);
        }

        return Collections.emptyMap();
    }

    private String resolveTurnType(Map<String, Object> outputMap, DealDeskChatRequest request, String answerText) {
        String turnType = firstNonBlank(asText(outputMap.get("turnType")), asText(outputMap.get("turn_type")));
        if (StringUtils.isNotBlank(turnType)) {
            if ("object_select".equals(turnType)) {
                return "text_analysis";
            }
            return turnType;
        }

        Map<String, Object> writeback = resolveWriteback(outputMap, null);
        if (!writeback.isEmpty()) {
            return "awaiting_confirm".equals(asText(writeback.get("status"))) ? "writeback_confirm" : "writeback_result";
        }

        if (shouldTreatAsBusinessAnalysis(request, answerText)) {
            return "text_analysis";
        }

        return "quick_answer";
    }

    private List<Map<String, Object>> resolveProcessEvents(Map<String, Object> rawResponse, Map<String, Object> outputMap, DealDeskChatRequest request, String turnType) {
        if (!shouldExposeProcessEvents(turnType)) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> processEvents = parseMapList(rawResponse.get("process_events"));
        if (!processEvents.isEmpty()) {
            return normalizeProcessEvents(processEvents);
        }

        processEvents = parseMapList(rawResponse.get("processEvents"));
        if (!processEvents.isEmpty()) {
            return normalizeProcessEvents(processEvents);
        }

        processEvents = parseMapList(outputMap.get("processEvents"));
        if (!processEvents.isEmpty()) {
            return normalizeProcessEvents(processEvents);
        }

        processEvents = parseMapList(outputMap.get("process_events"));
        if (!processEvents.isEmpty()) {
            return normalizeProcessEvents(processEvents);
        }

        processEvents = parseMapList(outputMap.get("process_events_json"));
        if (!processEvents.isEmpty()) {
            return normalizeProcessEvents(processEvents);
        }

        return Collections.emptyList();
    }

    private boolean shouldExposeProcessEvents(String turnType) {
        return "text_analysis".equals(turnType)
                || "writeback_confirm".equals(turnType)
                || "writeback_result".equals(turnType)
                || "failed".equals(turnType);
    }

    private List<Map<String, Object>> normalizeProcessEvents(List<Map<String, Object>> events) {
        if (events == null || events.isEmpty()) {
            return Collections.emptyList();
        }

        List<Map<String, Object>> normalized = new ArrayList<>();
        for (Map<String, Object> event : events) {
            String type = asText(event.get("type"));
            if (StringUtils.isBlank(type) || "memory_used".equals(type)) {
                continue;
            }

            String status = firstNonBlank(asText(event.get("status")), "completed");
            Map<String, Object> normalizedEvent = new LinkedHashMap<>(event);
            normalizedEvent.put("id", firstNonBlank(asText(event.get("id")), "event-" + type));
            normalizedEvent.put("type", type);
            normalizedEvent.put("status", status);
            normalizedEvent.put("text", buildProcessEventText(type, status));
            normalized.add(normalizedEvent);
        }
        return normalized;
    }

    private Map<String, Object> resolveWriteback(Map<String, Object> outputMap, DealDeskChatRequest request) {
        Map<String, Object> direct = parseMap(outputMap.get("writeback"));
        if (!direct.isEmpty()) {
            return direct;
        }

        Map<String, Object> parsed = parseMap(outputMap.get("writeback_payload_json"));
        if (!parsed.isEmpty()) {
            return parsed;
        }

        if (request == null || request.getActiveWriteback() == null) {
            return Collections.emptyMap();
        }

        DealDeskChatRequest.ActiveWriteback activeWriteback = request.getActiveWriteback();
        Map<String, Object> writeback = new LinkedHashMap<>();
        writeback.put("id", activeWriteback.getId());
        writeback.put("type", activeWriteback.getType());
        writeback.put("status", activeWriteback.getStatus());
        if (activeWriteback.getTarget() != null) {
            writeback.put("target", JSON.parseToMap(JSON.toJSONString(activeWriteback.getTarget())));
        }
        if (activeWriteback.getRecordDraft() != null) {
            writeback.put("recordDraft", JSON.parseToMap(JSON.toJSONString(activeWriteback.getRecordDraft())));
        }
        if (activeWriteback.getPlanDraft() != null) {
            writeback.put("planDraft", JSON.parseToMap(JSON.toJSONString(activeWriteback.getPlanDraft())));
        }
        if (StringUtils.isNotBlank(activeWriteback.getResultMessage())) {
            writeback.put("resultMessage", activeWriteback.getResultMessage());
        }
        return writeback;
    }

    private Map<String, Object> normalizeWritebackResult(Map<String, Object> writeback, DealDeskChatRequest request) {
        if (writeback == null || writeback.isEmpty()) {
            return Collections.emptyMap();
        }
        String status = asText(writeback.get("status"));
        if (!"confirmed".equals(status)) {
            return writeback;
        }
        Map<String, Object> normalized = new LinkedHashMap<>(writeback);
        promoteNestedWritebackResultId(normalized, "data");
        promoteNestedWritebackResultId(normalized, "result");
        promoteNestedWritebackResultId(normalized, "toolResult");
        promoteNestedWritebackResultId(normalized, "crmToolResult");
        recoverWritebackResultIdFromActiveDraft(normalized, request);
        if (hasWritebackResultId(normalized)) {
            return normalized;
        }
        normalized.put("status", "failed");
        normalized.put("resultMessage", "CRM 写回未返回真实记录 ID，本轮不能标记为写入成功。");
        return normalized;
    }

    private String buildWritebackConfirmationText(Map<String, Object> writeback) {
        Map<String, Object> target = parseMap(writeback.get("target"));
        Map<String, Object> recordDraft = parseMap(writeback.get("recordDraft"));
        Map<String, Object> planDraft = parseMap(writeback.get("planDraft"));
        boolean isPlan = !planDraft.isEmpty() && recordDraft.isEmpty();
        Map<String, Object> draft = isPlan ? planDraft : recordDraft;

        List<String> lines = new ArrayList<>();
        lines.add("请确认以下内容是否按 CRM 字段写入：");
        lines.add("");
        lines.add("写入类型：" + (isPlan ? "跟进计划" : "跟进记录"));
        appendConfirmationLine(lines, "关联商机", target.get("opportunityName"));
        appendConfirmationLine(lines, "关联客户", target.get("customerName"));
        appendConfirmationLine(lines, "联系人", target.get("contactName"));
        appendConfirmationLine(lines, "负责人", target.get("ownerName"));
        lines.add((isPlan ? "跟进方式：" : "跟进方式：") + followMethodLabel(asText(draft.get(isPlan ? "planMethod" : "followMethod"))));
        appendConfirmationLine(lines, isPlan ? "预计开始时间" : "跟进时间", firstNonBlank(
                asText(draft.get(isPlan ? "planTimeText" : "followTimeText")),
                "确认写入时使用当前时间"
        ));
        lines.add("");
        lines.add((isPlan ? "预计沟通内容：" : "跟进内容："));
        lines.add(firstNonBlank(asText(draft.get("content")), "待确认"));
        lines.add("");
        lines.add("确认无误后回复“确认”执行写入；如需调整，请直接说明要修改的字段。");
        return String.join("\n", lines);
    }

    private void appendConfirmationLine(List<String> lines, String label, Object value) {
        String text = asText(value);
        if (StringUtils.isNotBlank(text)) {
            lines.add(label + "：" + text);
        }
    }

    private String followMethodLabel(String method) {
        if ("1".equals(method) || StringUtils.equalsIgnoreCase(method, "VISIT") || StringUtils.contains(method, "到访")) {
            return "到访";
        }
        return "电话";
    }

    private void recoverWritebackResultIdFromActiveDraft(Map<String, Object> writeback, DealDeskChatRequest request) {
        if (hasWritebackResultId(writeback)
                || aiDealDeskToolService == null
                || request == null
                || request.getActiveWriteback() == null) {
            return;
        }
        DealDeskChatRequest.ActiveWriteback activeWriteback = request.getActiveWriteback();
        if (!"awaiting_confirm".equals(asText(activeWriteback.getStatus()))
                || !"follow_record".equals(asText(activeWriteback.getType()))
                || activeWriteback.getRecordDraft() == null) {
            return;
        }
        DealDeskToolRequest toolRequest = runWithDifyToolContext(() -> {
            DealDeskToolRequest toolRequestCandidate = buildFollowRecordToolRequest(activeWriteback);
            enrichFollowRecordToolRequestFromTargetNames(toolRequestCandidate, activeWriteback.getTarget());
            return toolRequestCandidate;
        });
        if (toolRequest == null
                || StringUtils.isBlank(toolRequest.getContent())
                || StringUtils.isBlank(toolRequest.getIdempotencyKey())
                || (StringUtils.isBlank(toolRequest.getCustomerId()) && StringUtils.isBlank(toolRequest.getOpportunityId()))) {
            return;
        }
        DealDeskToolResponse toolResponse = runWithDifyToolContext(() -> aiDealDeskToolService.createFollowRecord(toolRequest));
        if (toolResponse == null || !toolResponse.isSuccess()) {
            return;
        }
        Map<String, Object> data = toolResponse.getData();
        if (data == null || data.isEmpty()) {
            return;
        }
        copyWritebackResultIdIfPresent(writeback, data, "recordId");
        copyWritebackResultIdIfPresent(writeback, data, "crmRecordId");
        if (hasWritebackResultId(writeback)) {
            writeback.put("status", "confirmed");
            writeback.put("resultMessage", firstNonBlank(asText(writeback.get("resultMessage")), "CRM writeback completed."));
        }
    }

    private DealDeskToolRequest buildFollowRecordToolRequest(DealDeskChatRequest.ActiveWriteback activeWriteback) {
        DealDeskChatRequest.Target target = activeWriteback.getTarget();
        DealDeskChatRequest.Draft draft = activeWriteback.getRecordDraft();
        DealDeskToolRequest toolRequest = new DealDeskToolRequest();
        toolRequest.setIdempotencyKey(activeWriteback.getId());
        toolRequest.setContent(draft.getContent());
        toolRequest.setFollowMethod(draft.getFollowMethod());
        if (target != null) {
            toolRequest.setCustomerId(target.getCustomerId());
            toolRequest.setOpportunityId(target.getOpportunityId());
            toolRequest.setContactId(target.getContactId());
            toolRequest.setOwnerId(target.getOwnerId());
        }
        return toolRequest;
    }

    @SuppressWarnings("unchecked")
    private void enrichFollowRecordToolRequestFromTargetNames(DealDeskToolRequest toolRequest, DealDeskChatRequest.Target target) {
        if (toolRequest == null
                || target == null
                || StringUtils.isNotBlank(toolRequest.getOpportunityId())
                || aiDealDeskToolService == null) {
            return;
        }

        for (String keyword : buildWritebackTargetSearchKeywords(target)) {
            DealDeskToolRequest searchRequest = new DealDeskToolRequest();
            searchRequest.setKeyword(keyword);
            searchRequest.setLimit(5);
            searchRequest.setIncludeContextOnUnique(true);
            DealDeskToolResponse response = aiDealDeskToolService.searchOpportunities(searchRequest);
            if (response == null || !response.isSuccess()) {
                continue;
            }
            Map<String, Object> data = response.getData();
            Map<String, Object> opportunity = parseMap(data == null ? null : data.get("opportunity"));
            if (opportunity.isEmpty()) {
                Object candidates = data == null ? null : data.get("candidates");
                if (candidates instanceof List<?> list && list.size() == 1 && list.get(0) instanceof Map<?, ?> map) {
                    opportunity = new LinkedHashMap<>((Map<String, Object>) map);
                }
            }
            if (opportunity.isEmpty()) {
                continue;
            }
            toolRequest.setOpportunityId(firstNonBlank(toolRequest.getOpportunityId(), asText(opportunity.get("id"))));
            toolRequest.setCustomerId(firstNonBlank(toolRequest.getCustomerId(), asText(opportunity.get("customerId"))));
            toolRequest.setContactId(firstNonBlank(toolRequest.getContactId(), asText(opportunity.get("contactId"))));
            toolRequest.setOwnerId(firstNonBlank(toolRequest.getOwnerId(), asText(opportunity.get("ownerId"))));
            return;
        }
    }

    private <T> T runWithDifyToolContext(Supplier<T> supplier) {
        return AiDealDeskToolExecutionContext.runAs(
                firstNonBlank(difyToolUserId, "admin"),
                firstNonBlank(difyToolOrganizationId, OrganizationContext.getOrganizationId(), "100001"),
                supplier
        );
    }

    private List<String> buildWritebackTargetSearchKeywords(DealDeskChatRequest.Target target) {
        List<String> keywords = new ArrayList<>();
        String customerName = asText(target.getCustomerName());
        String opportunityName = asText(target.getOpportunityName());
        if (StringUtils.isNoneBlank(customerName, opportunityName)) {
            keywords.add(customerName + opportunityName);
            keywords.add(customerName + " " + opportunityName);
        }
        if (StringUtils.isNotBlank(opportunityName)) {
            keywords.add(opportunityName);
        }
        if (StringUtils.isNotBlank(customerName)) {
            keywords.add(customerName);
        }
        return keywords.stream()
                .map(StringUtils::trimToEmpty)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .toList();
    }

    private void promoteNestedWritebackResultId(Map<String, Object> writeback, String key) {
        Map<String, Object> nested = parseMap(writeback.get(key));
        if (nested.isEmpty()) {
            return;
        }
        copyWritebackResultIdIfPresent(writeback, nested, "recordId");
        copyWritebackResultIdIfPresent(writeback, nested, "planId");
        copyWritebackResultIdIfPresent(writeback, nested, "crmRecordId");
        copyWritebackResultIdIfPresent(writeback, nested, "crmPlanId");
        Map<String, Object> nestedData = parseMap(nested.get("data"));
        if (!nestedData.isEmpty()) {
            copyWritebackResultIdIfPresent(writeback, nestedData, "recordId");
            copyWritebackResultIdIfPresent(writeback, nestedData, "planId");
            copyWritebackResultIdIfPresent(writeback, nestedData, "crmRecordId");
            copyWritebackResultIdIfPresent(writeback, nestedData, "crmPlanId");
        }
    }

    private void copyWritebackResultIdIfPresent(Map<String, Object> target, Map<String, Object> source, String key) {
        if (StringUtils.isBlank(asText(target.get(key))) && StringUtils.isNotBlank(asText(source.get(key)))) {
            target.put(key, source.get(key));
        }
    }

    private boolean hasWritebackResultId(Map<String, Object> writeback) {
        return StringUtils.isNotBlank(asText(writeback.get("recordId")))
                || StringUtils.isNotBlank(asText(writeback.get("planId")))
                || StringUtils.isNotBlank(asText(writeback.get("crmRecordId")))
                || StringUtils.isNotBlank(asText(writeback.get("crmPlanId")));
    }

    private Map<String, Object> resolveBoundObject(Map<String, Object> outputMap, DealDeskChatRequest request) {
        Map<String, Object> direct = parseMap(outputMap.get("boundObject"));
        if (!direct.isEmpty()) {
            return direct;
        }

        Map<String, Object> parsed = parseMap(outputMap.get("bound_object_json"));
        if (!parsed.isEmpty()) {
            return parsed;
        }

        if (request == null || request.getBoundObject() == null) {
            return Collections.emptyMap();
        }

        return JSON.parseToMap(JSON.toJSONString(request.getBoundObject()));
    }

    private Map<String, Object> buildEvent(String id, String type, String status, String text) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("id", id);
        event.put("type", type);
        event.put("status", status);
        event.put("text", text);
        return event;
    }

    private Map<String, Object> buildFinalFrame(DealDeskChatResponse response) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "final");
        frame.put("payload", response);
        return frame;
    }

    private Map<String, Object> buildRawFinalFrame(Map<String, Object> rawResponse, DealDeskChatRequest request) {
        Map<String, Object> frame = new LinkedHashMap<>();
        frame.put("type", "final");
        frame.put("payload", adaptChatResponse(rawResponse, request));
        return frame;
    }

    private void writeSseFrame(OutputStream outputStream, Map<String, Object> frame) {
        try {
            outputStream.write(("data: " + JSON.toJSONString(frame) + "\n\n").getBytes(StandardCharsets.UTF_8));
            outputStream.flush();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to write 多Agent智能助手 stream frame", e);
        }
    }

    private void setIfNotEmpty(java.util.function.Consumer<Map<String, Object>> setter, Map<String, Object> value) {
        if (value != null && !value.isEmpty()) {
            setter.accept(value);
        }
    }

    private String buildAttachmentSummary(List<DealDeskChatRequest.ChatFile> files) {
        if (files == null || files.isEmpty()) {
            return "";
        }
        return files.stream()
                .map(item -> StringUtils.defaultIfBlank(item.getName(), item.getId()))
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining("; "));
    }

    private String buildAttachmentNames(List<DealDeskChatRequest.ChatFile> files) {
        if (files == null || files.isEmpty()) {
            return "";
        }
        return files.stream()
                .map(DealDeskChatRequest.ChatFile::getName)
                .filter(StringUtils::isNotBlank)
                .collect(Collectors.joining(", "));
    }

    private boolean isCustomer(DealDeskChatRequest.BoundObject boundObject) {
        return boundObject != null && "customer".equals(boundObject.getObjectType());
    }

    private boolean isOpportunity(DealDeskChatRequest.BoundObject boundObject) {
        return boundObject != null && "opportunity".equals(boundObject.getObjectType());
    }

    private boolean isSelection(DealDeskChatRequest.BoundObject boundObject) {
        return boundObject != null && "selection".equals(boundObject.getSource());
    }

    private String mapFileType(String mimeType) {
        if (StringUtils.startsWithIgnoreCase(mimeType, "image/")) {
            return "image";
        }
        if (StringUtils.startsWithIgnoreCase(mimeType, "audio/")) {
            return "audio";
        }
        if (StringUtils.startsWithIgnoreCase(mimeType, "video/")) {
            return "video";
        }
        return "document";
    }

    private Map<String, String> buildJsonHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HttpHeaders.AUTHORIZATION, "Bearer " + difyApiKey);
        headers.put(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8");
        headers.put(HttpHeaders.ACCEPT_CHARSET, StandardCharsets.UTF_8.name());
        return headers;
    }

    private String buildUrl(String path) {
        return StringUtils.removeEnd(difyBaseUrl, "/") + path;
    }

    public String buildCurrentDifyUser() {
        return OrganizationContext.getOrganizationId() + ":" + StringUtils.defaultString(SessionUtils.getUserId(), "anonymous");
    }

    private void validateConfig() {
        if (StringUtils.isBlank(difyApiKey)) {
            throw new IllegalStateException("Dify API key is not configured");
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read upload file", e);
        }
    }

    private boolean looksLikeJson(String value) {
        String text = StringUtils.trimToEmpty(value);
        return text.startsWith("{") && text.endsWith("}");
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }

    private String cleanAnswerText(String value) {
        String text = StringUtils.defaultString(value);
        text = text.replaceAll("(?is)<think>.*?</think>", "");
        text = StringUtils.trim(text);
        if (looksLikeJson(text)) {
            try {
                Map<String, Object> parsed = JSON.parseToMap(text);
                String nestedAnswerText = asText(parsed.get("answerText"));
                if (StringUtils.isNotBlank(nestedAnswerText)) {
                    return cleanAnswerText(nestedAnswerText);
                }
                String nestedAnswer = asText(parsed.get("answer"));
                if (StringUtils.isNotBlank(nestedAnswer)) {
                    return cleanAnswerText(nestedAnswer);
                }
            } catch (Exception e) {
                log.debug("answerText is not nested protocol json", e);
            }
        }
        return text;
    }

    private boolean shouldTreatAsBusinessAnalysis(DealDeskChatRequest request, String answerText) {
        String query = request == null ? "" : StringUtils.defaultString(request.getQuery());
        String text = (query + "\n" + StringUtils.defaultString(answerText)).toLowerCase();
        return containsAny(text,
                "财务", "付款", "回款", "折扣", "账期", "首付", "尾款",
                "交付", "上线", "实施", "定制",
                "合同", "条款", "验收", "赔付",
                "风险");
    }

    private boolean containsAny(String text, String... keywords) {
        String value = StringUtils.defaultString(text).toLowerCase();
        for (String keyword : keywords) {
            if (StringUtils.isNotBlank(keyword) && value.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    private void appendIfPresent(StringBuilder builder, String value) {
        if (StringUtils.isNotBlank(value)) {
            builder.append(value);
        }
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value != null && StringUtils.isNotBlank(String.valueOf(value))) {
            target.put(key, value);
        }
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Integer asInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return null;
        }
        try {
            return Integer.parseInt(String.valueOf(value));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Map<String, Object> parseMap(Object raw) {
        if (raw instanceof Map<?, ?> rawMap) {
            return castMap(rawMap);
        }
        if (raw instanceof String rawText && looksLikeJson(rawText)) {
            return JSON.parseToMap(rawText);
        }
        return Collections.emptyMap();
    }

    private List<Map<String, Object>> parseMapList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(item -> castMap((Map<?, ?>) item))
                    .collect(Collectors.toList());
        }

        if (raw instanceof String rawText && StringUtils.isNotBlank(rawText) && rawText.trim().startsWith("[")) {
            try {
                List<Map<String, Object>> result = JSON.parseArray(rawText, new TypeReference<Map<String, Object>>() {
                });
                return result == null ? Collections.emptyList() : result;
            } catch (Exception e) {
                log.warn("Failed to parse process/object list: {}", e.getMessage());
            }
        }

        return Collections.emptyList();
    }

    private List<String> parseStringList(Object raw) {
        if (raw instanceof List<?> list) {
            return list.stream().map(String::valueOf).collect(Collectors.toList());
        }

        if (raw instanceof String rawText && StringUtils.isNotBlank(rawText) && rawText.trim().startsWith("[")) {
            try {
                return JSON.parseArray(rawText, String.class);
            } catch (Exception e) {
                log.warn("Failed to parse warnings: {}", e.getMessage());
            }
        }

        return Collections.emptyList();
    }

    private Map<String, Object> castMap(Map<?, ?> source) {
        Map<String, Object> result = new LinkedHashMap<>();
        source.forEach((key, value) -> result.put(String.valueOf(key), value));
        return result;
    }

    private static class NamedByteArrayResource extends ByteArrayResource {

        private final String fileName;

        private NamedByteArrayResource(byte[] byteArray, String fileName) {
            super(byteArray);
            this.fileName = fileName;
        }

        @Override
        public String getFilename() {
            return fileName;
        }
    }
}
