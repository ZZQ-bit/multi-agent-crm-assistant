package cn.cordys.crm.agent;

import cn.cordys.crm.ai.dealdesk.dto.DealDeskChatRequest;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskStopChatRequest;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskStopChatResponse;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskToolRequest;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskToolResponse;
import cn.cordys.crm.ai.dealdesk.service.AiDealDeskService;
import cn.cordys.crm.ai.dealdesk.service.AiDealDeskToolExecutionContext;
import cn.cordys.crm.ai.dealdesk.service.AiDealDeskToolService;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiDealDeskStreamingEventParsingTest {

    @Test
    void shouldNotKeepLocalObjectSelectFallbackMethodsInRuntimeService() {
        boolean hasLocalObjectSelectFallback = Arrays.stream(AiDealDeskService.class.getDeclaredMethods())
                .map(Method::getName)
                .anyMatch(name -> name.contains("LocalObjectSelect")
                        || name.contains("LocalOpportunityCandidates")
                        || name.contains("FallbackToObjectSelect"));

        assertFalse(hasLocalObjectSelectFallback);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldBuildRealtimeFramesFromStreamingPayload() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Method method = AiDealDeskService.class.getDeclaredMethod("adaptStreamingFrame", String.class);
        method.setAccessible(true);

        List<Map<String, Object>> frames = new ArrayList<>();
        String[] payloads = {
                "{\"event\":\"node_started\",\"node\":{\"id\":\"sales_agent\",\"title\":\"可自由修改的节点标题\",\"data\":{\"type\":\"llm\"}}}",
                "{\"event\":\"node_finished\",\"node\":{\"id\":\"simple_answer\",\"title\":\"可自由修改的回答标题\",\"data\":{\"type\":\"answer\"}},\"data\":{\"status\":\"succeeded\"}}",
                "{\"event\":\"message\",\"answer\":\"first chunk\",\"conversation_id\":\"conv-1\",\"message_id\":\"msg-1\",\"task_id\":\"task-1\"}"
        };

        for (String payload : payloads) {
            Object frame = method.invoke(service, payload);
            if (frame != null) {
                frames.add((Map<String, Object>) frame);
            }
        }

        assertEquals("process_event", frames.get(0).get("type"));
        Map<String, Object> startedEvent = (Map<String, Object>) frames.get(0).get("event");
        assertEquals("sales_agent", startedEvent.get("id"));
        assertEquals("sales_analysis", startedEvent.get("type"));
        assertEquals("running", startedEvent.get("status"));

        assertEquals("process_event", frames.get(1).get("type"));
        Map<String, Object> finishedEvent = (Map<String, Object>) frames.get(1).get("event");
        assertEquals("simple_answer", finishedEvent.get("id"));
        assertEquals("answer_generation", finishedEvent.get("type"));
        assertEquals("completed", finishedEvent.get("status"));

        assertEquals("answer_delta", frames.get(2).get("type"));
        assertEquals("first chunk", frames.get(2).get("text"));
        assertEquals("conv-1", frames.get(2).get("conversationId"));
        assertEquals("msg-1", frames.get(2).get("messageId"));
        assertEquals("task-1", frames.get(2).get("taskId"));
    }

    @Test
    void shouldStopDifyChatGenerationWithTaskIdAndUser() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Field difyApiKeyField = AiDealDeskService.class.getDeclaredField("difyApiKey");
        difyApiKeyField.setAccessible(true);
        difyApiKeyField.set(service, "test-key");

        AtomicReference<String> receivedPath = new AtomicReference<>();
        AtomicReference<String> receivedBody = new AtomicReference<>();
        AtomicReference<String> receivedAuthorization = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat-messages/task-1/stop", exchange -> {
            receivedPath.set(exchange.getRequestURI().getPath());
            receivedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            receivedBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"result\":\"success\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            Field difyBaseUrlField = AiDealDeskService.class.getDeclaredField("difyBaseUrl");
            difyBaseUrlField.setAccessible(true);
            difyBaseUrlField.set(service, "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");

            DealDeskStopChatRequest request = new DealDeskStopChatRequest();
            request.setTaskId("task-1");
            DealDeskStopChatResponse response = service.stopChat(request);

            assertTrue(response.isSuccess());
            assertEquals("/v1/chat-messages/task-1/stop", receivedPath.get());
            assertEquals("Bearer test-key", receivedAuthorization.get());
            assertTrue(receivedBody.get().contains("\"user\""));
        } finally {
            server.stop(0);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldReturnFailedStreamFrameWhenDifyReturnsServerError() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Field difyApiKeyField = AiDealDeskService.class.getDeclaredField("difyApiKey");
        difyApiKeyField.setAccessible(true);
        difyApiKeyField.set(service, "test-key");

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat-messages", exchange -> {
            byte[] response = "{\"message\":\"workflow node failed\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(500, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            Field difyBaseUrlField = AiDealDeskService.class.getDeclaredField("difyBaseUrl");
            difyBaseUrlField.setAccessible(true);
            difyBaseUrlField.set(service, "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");

            DealDeskChatRequest request = new DealDeskChatRequest();
            request.setQuery("review deal");
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            service.streamChat(request, outputStream);

            String output = outputStream.toString(StandardCharsets.UTF_8);
            assertTrue(output.contains("\"type\":\"final\""));
            assertTrue(output.contains("\"turnType\":\"failed\""));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldExposeWorkflowFailureReasonWhenDifyReturnsEmptyOutputs() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Field difyApiKeyField = AiDealDeskService.class.getDeclaredField("difyApiKey");
        difyApiKeyField.setAccessible(true);
        difyApiKeyField.set(service, "test-key");

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat-messages", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream;charset=UTF-8");
            String payload = """
                    data: {"event":"workflow_started","workflow_run_id":"wf-1"}

                    data: {"event":"workflow_finished","data":{"status":"failed","outputs":{},"error":"[models] Error: API request failed with status code 403: {\\\"code\\\":30001,\\\"message\\\":\\\"Sorry, your account balance is insufficient\\\",\\\"data\\\":null}"}}

                    """;
            byte[] response = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            Field difyBaseUrlField = AiDealDeskService.class.getDeclaredField("difyBaseUrl");
            difyBaseUrlField.setAccessible(true);
            difyBaseUrlField.set(service, "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");

            DealDeskChatRequest request = new DealDeskChatRequest();
            request.setQuery("hello");
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            service.streamChat(request, outputStream);

            String output = outputStream.toString(StandardCharsets.UTF_8);
            assertTrue(output.contains("\"turnType\":\"failed\""));
            assertTrue(output.contains("Sorry, your account balance is insufficient"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldStreamVisibleAnswerDeltasWhenWorkflowOnlyReturnsProtocolJson() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Field difyApiKeyField = AiDealDeskService.class.getDeclaredField("difyApiKey");
        difyApiKeyField.setAccessible(true);
        difyApiKeyField.set(service, "test-key");

        String answerText = "结论：当前卡点是决策人缺失。";
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat-messages", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream;charset=UTF-8");
            String payload = """
                    data: {"event":"workflow_started","workflow_run_id":"wf-1"}

                    data: {"event":"workflow_finished","data":{"outputs":{"protocolVersion":"1.0","turnType":"text_analysis","answerText":"结论：当前卡点是决策人缺失。"}}}

                    """;
            byte[] response = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            Field difyBaseUrlField = AiDealDeskService.class.getDeclaredField("difyBaseUrl");
            difyBaseUrlField.setAccessible(true);
            difyBaseUrlField.set(service, "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");

            DealDeskChatRequest request = new DealDeskChatRequest();
            request.setQuery("分析当前商机卡点");
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            service.streamChat(request, outputStream);

            String output = outputStream.toString(StandardCharsets.UTF_8);
            int deltaIndex = output.indexOf("\"type\":\"answer_delta\"");
            int finalIndex = output.indexOf("\"type\":\"final\"");

            assertTrue(deltaIndex >= 0);
            assertTrue(finalIndex > deltaIndex);
            assertTrue(output.contains("\"text\":\"" + answerText + "\""));
            assertTrue(output.contains("\"answerText\":\"" + answerText + "\""));
            assertFalse(output.contains("\"text\":\"{"));
            assertFalse(output.contains("\"text\":\"```json"));
            assertFalse(output.contains("\"text\":\"protocolVersion"));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldKeepRealtimeMarkdownAndRecoverProtocolFromWorkflowNodeOutputs() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Field difyApiKeyField = AiDealDeskService.class.getDeclaredField("difyApiKey");
        difyApiKeyField.setAccessible(true);
        difyApiKeyField.set(service, "test-key");

        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/v1/chat-messages", exchange -> {
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream;charset=UTF-8");
            String payload = """
                    data: {"event":"workflow_started","workflow_run_id":"wf-1"}

                    data: {"event":"node_finished","data":{"node_id":"task_plan_normalize","status":"succeeded","outputs":{"task_type":"finance_check","resolution_status":"resolved"}}}

                    data: {"event":"node_finished","data":{"node_id":"evidence_ledger","status":"succeeded","outputs":{"target_object_json":"{\\\"objectType\\\":\\\"opportunity\\\",\\\"objectId\\\":\\\"opp-1\\\",\\\"objectName\\\":\\\"华东智造集团AI客服升级项目\\\"}"}}}

                    data: {"event":"message","answer":"第一段 Markdown。","conversation_id":"conv-1","message_id":"msg-1","task_id":"task-1"}

                    data: {"event":"message","answer":"第二段 Markdown。","conversation_id":"conv-1","message_id":"msg-1","task_id":"task-1"}

                    data: {"event":"workflow_finished","data":{"status":"succeeded","outputs":{"answer":"第一段 Markdown。第二段 Markdown。"}}}

                    """;
            byte[] response = payload.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        try {
            Field difyBaseUrlField = AiDealDeskService.class.getDeclaredField("difyBaseUrl");
            difyBaseUrlField.setAccessible(true);
            difyBaseUrlField.set(service, "http://127.0.0.1:" + server.getAddress().getPort() + "/v1");

            DealDeskChatRequest request = new DealDeskChatRequest();
            request.setQuery("分析这个商机的付款风险");
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            service.streamChat(request, outputStream);

            String output = outputStream.toString(StandardCharsets.UTF_8);
            int firstDeltaIndex = output.indexOf("\"text\":\"第一段 Markdown。\"");
            int secondDeltaIndex = output.indexOf("\"text\":\"第二段 Markdown。\"");
            int finalIndex = output.indexOf("\"type\":\"final\"");

            assertTrue(firstDeltaIndex >= 0);
            assertTrue(secondDeltaIndex > firstDeltaIndex);
            assertTrue(finalIndex > secondDeltaIndex);
            assertTrue(output.contains("\"answerText\":\"第一段 Markdown。第二段 Markdown。\""));
            assertTrue(output.contains("\"turnType\":\"text_analysis\""));
            assertTrue(output.contains("\"objectType\":\"opportunity\""));
            assertTrue(output.contains("\"objectId\":\"opp-1\""));
            assertTrue(output.contains("\"objectName\":\"华东智造集团AI客服升级项目\""));
        } finally {
            server.stop(0);
        }
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldExtractNodeEventsFromStreamingPayload() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Method method = AiDealDeskService.class.getDeclaredMethod("adaptStreamingResponse", String.class);
        method.setAccessible(true);

        String sseBody = String.join("\n",
                "data: {\"event\":\"workflow_started\",\"workflow_run_id\":\"wf-1\"}",
                "data: {\"event\":\"node_started\",\"data\":{\"node_id\":\"task_planner\",\"title\":\"任务规划\",\"node_type\":\"agent\"}}",
                "data: {\"event\":\"node_finished\",\"data\":{\"node_id\":\"task_planner\",\"title\":\"任务规划\",\"status\":\"succeeded\"}}",
                "data: {\"event\":\"node_started\",\"node\":{\"id\":\"crm_read_request\",\"title\":\"CRM 数据获取\"}}",
                "data: {\"event\":\"node_finished\",\"node\":{\"id\":\"crm_read_request\",\"title\":\"CRM 数据获取\"},\"data\":{\"status\":\"succeeded\"}}",
                "data: {\"event\":\"node_started\",\"node\":{\"id\":\"deal_rules_knowledge\",\"title\":\"知识检索\"}}",
                "data: {\"event\":\"node_finished\",\"node\":{\"id\":\"deal_rules_knowledge\",\"title\":\"知识检索\"},\"data\":{\"status\":\"succeeded\"}}",
                "data: {\"event\":\"node_started\",\"node\":{\"id\":\"sales_agent\",\"title\":\"销售 Agent\"}}",
                "data: {\"event\":\"node_finished\",\"node\":{\"id\":\"sales_agent\",\"title\":\"销售 Agent\"},\"data\":{\"status\":\"succeeded\"}}",
                "data: {\"event\":\"node_started\",\"node\":{\"id\":\"business_answer_agent\",\"title\":\"综合回答\"}}",
                "data: {\"event\":\"node_finished\",\"node\":{\"id\":\"business_answer_agent\",\"title\":\"综合回答\"},\"data\":{\"status\":\"succeeded\"}}",
                "data: {\"event\":\"message\",\"answer\":\"最终回复\",\"conversation_id\":\"conv-1\",\"message_id\":\"msg-1\"}",
                "data: {\"event\":\"workflow_finished\",\"data\":{\"outputs\":{\"answer\":\"最终回复\"}}}"
        );

        String adapted = (String) method.invoke(service, sseBody);
        Method parseMapMethod = AiDealDeskService.class.getDeclaredMethod("parseMap", Object.class);
        parseMapMethod.setAccessible(true);
        Map<String, Object> payload = (Map<String, Object>) parseMapMethod.invoke(service, adapted);
        List<Map<String, Object>> processEvents = (List<Map<String, Object>>) payload.get("process_events");

        assertEquals("最终回复", payload.get("answer"));
        assertEquals("conv-1", payload.get("conversation_id"));
        assertEquals("msg-1", payload.get("message_id"));
        assertFalse(processEvents.isEmpty());
        assertEquals("task_understanding", processEvents.get(0).get("type"));
        assertTrue(processEvents.stream().anyMatch(event ->
                "crm_retrieval".equals(event.get("type")) && "completed".equals(event.get("status"))));
        assertTrue(processEvents.stream().anyMatch(event ->
                "knowledge_retrieval".equals(event.get("type")) && "completed".equals(event.get("status"))));
        assertTrue(processEvents.stream().anyMatch(event ->
                "sales_analysis".equals(event.get("type")) && "completed".equals(event.get("status"))));
        assertTrue(processEvents.stream().anyMatch(event ->
                "answer_generation".equals(event.get("type")) && "completed".equals(event.get("status"))));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldMapCurrentV3NodeIdsWithoutDependingOnTitles() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Method method = AiDealDeskService.class.getDeclaredMethod("adaptStreamingFrame", String.class);
        method.setAccessible(true);

        Map<String, String> visibleNodes = Map.of(
                "task_planner", "task_understanding",
                "crm_read_request", "crm_retrieval",
                "deal_rules_knowledge", "knowledge_retrieval",
                "external_intel_agent", "external_research"
        );

        for (Map.Entry<String, String> visibleNode : visibleNodes.entrySet()) {
            Map<String, Object> frame = (Map<String, Object>) method.invoke(
                    service,
                    "{\"event\":\"node_started\",\"node\":{\"id\":\"" + visibleNode.getKey()
                            + "\",\"title\":\"任意中文标题\",\"data\":{\"type\":\"code\"}}}"
            );

            assertEquals("process_event", frame.get("type"));
            assertEquals(visibleNode.getValue(), ((Map<String, Object>) frame.get("event")).get("type"));
            assertEquals("running", ((Map<String, Object>) frame.get("event")).get("status"));
        }

        Map<String, Object> completedFrame = (Map<String, Object>) method.invoke(
                service,
                "{\"event\":\"node_finished\",\"data\":{\"node_id\":\"crm_read_request\",\"title\":\"已改名的节点\",\"status\":\"succeeded\"}}"
        );
        Map<String, Object> skippedFrame = (Map<String, Object>) method.invoke(
                service,
                "{\"event\":\"node_started\",\"node\":{\"id\":\"node-skip\",\"title\":\"CRM Tool API 跳过响应\",\"data\":{\"type\":\"code\"}}}"
        );

        assertEquals("process_event", completedFrame.get("type"));
        assertEquals("crm_retrieval", ((Map<String, Object>) completedFrame.get("event")).get("type"));
        assertEquals("completed", ((Map<String, Object>) completedFrame.get("event")).get("status"));
        assertEquals(null, skippedFrame);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldIgnoreUntrackedWorkflowNodesWithoutLeakingTechnicalText() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Method method = AiDealDeskService.class.getDeclaredMethod("adaptStreamingFrame", String.class);
        method.setAccessible(true);

        Map<String, Object> frame = (Map<String, Object>) method.invoke(
                service,
                "{\"event\":\"node_started\",\"node\":{\"id\":\"node-debug\",\"title\":\"内部调试 tool_call success\",\"data\":{\"type\":\"code\"}}}"
        );

        assertEquals(null, frame);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldPreferTopLevelProcessEventsWhenAdaptingChatResponse() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Method parseMapMethod = AiDealDeskService.class.getDeclaredMethod("parseMap", Object.class);
        parseMapMethod.setAccessible(true);
        Method adaptChatResponseMethod = AiDealDeskService.class.getDeclaredMethod(
                "adaptChatResponse",
                Map.class,
                DealDeskChatRequest.class
        );
        adaptChatResponseMethod.setAccessible(true);

        Map<String, Object> rawResponse = (Map<String, Object>) parseMapMethod.invoke(
                service,
                """
                {
                  "answer": "测试回复",
                  "process_events": [
                    {
                      "id": "wf-1",
                      "type": "task_identified",
                      "status": "running",
                      "text": "工作流已启动"
                    },
                    {
                      "id": "node-1",
                      "type": "suggestion_generated",
                      "status": "completed",
                      "text": "已完成回答"
                    }
                  ]
                }
                """
        );

        DealDeskChatRequest request = new DealDeskChatRequest();
        request.setQuery("分析付款风险");

        Object response = adaptChatResponseMethod.invoke(service, rawResponse, request);
        Field processEventsField = response.getClass().getDeclaredField("processEvents");
        processEventsField.setAccessible(true);
        List<Map<String, Object>> processEvents = (List<Map<String, Object>>) processEventsField.get(response);

        assertEquals(2, processEvents.size());
        assertEquals("正在识别本轮任务", processEvents.get(0).get("text"));
        assertEquals("已生成结论和下一步建议", processEvents.get(1).get("text"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldKeepBusinessAnalysisTurnTypeForUnboundRiskAnalysis() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Method parseMapMethod = AiDealDeskService.class.getDeclaredMethod("parseMap", Object.class);
        parseMapMethod.setAccessible(true);
        Method adaptChatResponseMethod = AiDealDeskService.class.getDeclaredMethod(
                "adaptChatResponse",
                Map.class,
                DealDeskChatRequest.class
        );
        adaptChatResponseMethod.setAccessible(true);

        Map<String, Object> rawResponse = (Map<String, Object>) parseMapMethod.invoke(
                service,
                """
                {
                  "answer": "#财务风险判断##1.结论暂不建议",
                  "process_events": [
                    {
                      "id": "node-task",
                      "type": "task_identified",
                      "status": "completed",
                      "text": "已识别本轮任务"
                    },
                    {
                      "id": "node-context",
                      "type": "context_loaded",
                      "status": "completed",
                      "text": "已读取相关业务资料"
                    },
                    {
                      "id": "node-rule",
                      "type": "rule_checked",
                      "status": "completed",
                      "text": "已核对关键业务条件"
                    },
                    {
                      "id": "node-suggestion",
                      "type": "suggestion_generated",
                      "status": "completed",
                      "text": "已生成结论和下一步建议"
                    }
                  ]
                }
                """
        );
        DealDeskChatRequest request = new DealDeskChatRequest();
        request.setQuery("分析付款方案风险");

        Object response = adaptChatResponseMethod.invoke(service, rawResponse, request);
        Field turnTypeField = response.getClass().getDeclaredField("turnType");
        turnTypeField.setAccessible(true);
        Field processEventsField = response.getClass().getDeclaredField("processEvents");
        processEventsField.setAccessible(true);
        List<Map<String, Object>> processEvents = (List<Map<String, Object>>) processEventsField.get(response);

        assertEquals("text_analysis", turnTypeField.get(response));
        assertEquals(4, processEvents.size());
        assertEquals("rule_checked", processEvents.get(2).get("type"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldNotTreatEmptyProcessEventsFieldAsBusinessAnalysis() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Method parseMapMethod = AiDealDeskService.class.getDeclaredMethod("parseMap", Object.class);
        parseMapMethod.setAccessible(true);
        Method adaptChatResponseMethod = AiDealDeskService.class.getDeclaredMethod(
                "adaptChatResponse",
                Map.class,
                DealDeskChatRequest.class
        );
        adaptChatResponseMethod.setAccessible(true);

        Map<String, Object> rawResponse = (Map<String, Object>) parseMapMethod.invoke(
                service,
                """
                {
                  "answer": "多Agent智能助手 是销售协同评审机制。",
                  "metadata": {
                    "workflow_outputs": {
                      "protocolVersion": "1.0",
                      "answerText": "多Agent智能助手 是销售协同评审机制。",
                      "processEvents": []
                    }
                  }
                }
                """
        );
        DealDeskChatRequest request = new DealDeskChatRequest();
        request.setQuery("多Agent智能助手 是什么");

        Object response = adaptChatResponseMethod.invoke(service, rawResponse, request);
        Field turnTypeField = response.getClass().getDeclaredField("turnType");
        turnTypeField.setAccessible(true);
        Field processEventsField = response.getClass().getDeclaredField("processEvents");
        processEventsField.setAccessible(true);
        List<Map<String, Object>> processEvents = (List<Map<String, Object>>) processEventsField.get(response);

        assertEquals("quick_answer", turnTypeField.get(response));
        assertTrue(processEvents == null || processEvents.isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldSuppressWorkflowProcessEventsForQuickAnswers() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Method parseMapMethod = AiDealDeskService.class.getDeclaredMethod("parseMap", Object.class);
        parseMapMethod.setAccessible(true);
        Method adaptChatResponseMethod = AiDealDeskService.class.getDeclaredMethod(
                "adaptChatResponse",
                Map.class,
                DealDeskChatRequest.class
        );
        adaptChatResponseMethod.setAccessible(true);

        Map<String, Object> rawResponse = (Map<String, Object>) parseMapMethod.invoke(
                service,
                """
                {
                  "answer": "hello",
                  "process_events": [
                    {
                      "id": "node-task",
                      "type": "task_identified",
                      "status": "completed",
                      "text": "workflow route"
                    },
                    {
                      "id": "node-context",
                      "type": "context_loaded",
                      "status": "running",
                      "text": "tool planning"
                    },
                    {
                      "id": "node-suggestion",
                      "type": "suggestion_generated",
                      "status": "completed",
                      "text": "light answer"
                    }
                  ]
                }
                """
        );
        DealDeskChatRequest request = new DealDeskChatRequest();
        request.setQuery("hello");

        Object response = adaptChatResponseMethod.invoke(service, rawResponse, request);
        Field turnTypeField = response.getClass().getDeclaredField("turnType");
        turnTypeField.setAccessible(true);
        Field processEventsField = response.getClass().getDeclaredField("processEvents");
        processEventsField.setAccessible(true);
        List<Map<String, Object>> processEvents = (List<Map<String, Object>>) processEventsField.get(response);

        assertEquals("quick_answer", turnTypeField.get(response));
        assertTrue(processEvents == null || processEvents.isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldNotGenerateFallbackProcessEventsWhenWorkflowReturnsNone() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Method parseMapMethod = AiDealDeskService.class.getDeclaredMethod("parseMap", Object.class);
        parseMapMethod.setAccessible(true);
        Method adaptChatResponseMethod = AiDealDeskService.class.getDeclaredMethod(
                "adaptChatResponse",
                Map.class,
                DealDeskChatRequest.class
        );
        adaptChatResponseMethod.setAccessible(true);

        Map<String, Object> rawResponse = (Map<String, Object>) parseMapMethod.invoke(
                service,
                """
                {
                  "answer": "普通说明文本"
                }
                """
        );
        DealDeskChatRequest request = new DealDeskChatRequest();
        request.setQuery("普通说明");

        Object response = adaptChatResponseMethod.invoke(service, rawResponse, request);
        Field processEventsField = response.getClass().getDeclaredField("processEvents");
        processEventsField.setAccessible(true);
        List<Map<String, Object>> processEvents = (List<Map<String, Object>>) processEventsField.get(response);

        assertTrue(processEvents == null || processEvents.isEmpty());
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldReturnFailedWhenDifyReturnsNoValidAnswer() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Method parseMapMethod = AiDealDeskService.class.getDeclaredMethod("parseMap", Object.class);
        parseMapMethod.setAccessible(true);
        Method adaptChatResponseMethod = AiDealDeskService.class.getDeclaredMethod(
                "adaptChatResponse",
                Map.class,
                DealDeskChatRequest.class
        );
        adaptChatResponseMethod.setAccessible(true);

        Map<String, Object> rawResponse = (Map<String, Object>) parseMapMethod.invoke(
                service,
                """
                {
                  "answer": "",
                  "process_events": [
                    {
                      "id": "node-context",
                      "type": "context_loaded",
                      "status": "running"
                    }
                  ]
                }
                """
        );

        Object response = adaptChatResponseMethod.invoke(service, rawResponse, new DealDeskChatRequest());
        Field turnTypeField = response.getClass().getDeclaredField("turnType");
        turnTypeField.setAccessible(true);
        Field processEventsField = response.getClass().getDeclaredField("processEvents");
        processEventsField.setAccessible(true);
        List<Map<String, Object>> processEvents = (List<Map<String, Object>>) processEventsField.get(response);

        assertEquals("failed", turnTypeField.get(response));
        assertTrue(processEvents.stream().anyMatch(event ->
                "failed".equals(event.get("type")) && "failed".equals(event.get("status"))));
    }

    @Test
    void shouldExtractAnswerFromProtocolJsonAnswerPayload() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Method cleanAnswerTextMethod = AiDealDeskService.class.getDeclaredMethod("cleanAnswerText", String.class);
        cleanAnswerTextMethod.setAccessible(true);

        String answerText = (String) cleanAnswerTextMethod.invoke(
                service,
                """
                {
                  "answer": "写入结果：本轮写入暂未成功，请检查 CRM 工具返回。",
                  "turnType": "writeback_result"
                }
                """
        );

        assertEquals("写入结果：本轮写入暂未成功，请检查 CRM 工具返回。", answerText);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldNotReportWritebackSuccessWithoutRealCrmResultId() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Method parseMapMethod = AiDealDeskService.class.getDeclaredMethod("parseMap", Object.class);
        parseMapMethod.setAccessible(true);
        Method adaptChatResponseMethod = AiDealDeskService.class.getDeclaredMethod(
                "adaptChatResponse",
                Map.class,
                DealDeskChatRequest.class
        );
        adaptChatResponseMethod.setAccessible(true);

        Map<String, Object> rawResponse = (Map<String, Object>) parseMapMethod.invoke(
                service,
                """
                {
                  "answer": "已完成写入。",
                  "metadata": {
                    "workflow_outputs": {
                      "protocolVersion": "1.0",
                      "turnType": "writeback_result",
                      "answerText": "已完成写入。",
                      "writeback": {
                        "id": "pending-writeback-001",
                        "type": "follow_record",
                        "status": "confirmed",
                        "resultMessage": "已完成写入，相关跟进内容已写回 CRM。"
                      }
                    }
                  }
                }
                """
        );

        Object response = adaptChatResponseMethod.invoke(service, rawResponse, new DealDeskChatRequest());
        Field writebackField = response.getClass().getDeclaredField("writeback");
        writebackField.setAccessible(true);
        Map<String, Object> writeback = (Map<String, Object>) writebackField.get(response);
        Field answerTextField = response.getClass().getDeclaredField("answerText");
        answerTextField.setAccessible(true);

        assertEquals("failed", writeback.get("status"));
        assertEquals("CRM 写回未返回真实记录 ID，本轮不能标记为写入成功。", writeback.get("resultMessage"));
        assertEquals("CRM 写回未返回真实记录 ID，本轮不能标记为写入成功。", answerTextField.get(response));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldPromoteNestedCrmRecordIdWhenDifyWrapsToolResultInsideWriteback() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Method parseMapMethod = AiDealDeskService.class.getDeclaredMethod("parseMap", Object.class);
        parseMapMethod.setAccessible(true);
        Method adaptChatResponseMethod = AiDealDeskService.class.getDeclaredMethod(
                "adaptChatResponse",
                Map.class,
                DealDeskChatRequest.class
        );
        adaptChatResponseMethod.setAccessible(true);

        Map<String, Object> rawResponse = (Map<String, Object>) parseMapMethod.invoke(
                service,
                """
                {
                  "answer": "writeback completed",
                  "metadata": {
                    "workflow_outputs": {
                      "protocolVersion": "1.0",
                      "turnType": "writeback_result",
                      "answerText": "writeback completed",
                      "writeback": {
                        "id": "pending-writeback-001",
                        "type": "follow_record",
                        "status": "confirmed",
                        "data": {
                          "recordId": "record-001"
                        },
                        "resultMessage": "writeback completed"
                      }
                    }
                  }
                }
                """
        );

        Object response = adaptChatResponseMethod.invoke(service, rawResponse, new DealDeskChatRequest());
        Field writebackField = response.getClass().getDeclaredField("writeback");
        writebackField.setAccessible(true);
        Map<String, Object> writeback = (Map<String, Object>) writebackField.get(response);
        Field answerTextField = response.getClass().getDeclaredField("answerText");
        answerTextField.setAccessible(true);

        assertEquals("confirmed", writeback.get("status"));
        assertEquals("record-001", writeback.get("recordId"));
        assertEquals("writeback completed", answerTextField.get(response));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldDirectlyWriteBackPendingDraftWhenDifyConfirmsWithoutRecordId() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        CapturingToolService toolService = new CapturingToolService("record-direct-001");
        Field toolServiceField = AiDealDeskService.class.getDeclaredField("aiDealDeskToolService");
        toolServiceField.setAccessible(true);
        toolServiceField.set(service, toolService);

        DealDeskChatRequest request = new DealDeskChatRequest();
        DealDeskChatRequest.ActiveWriteback activeWriteback = new DealDeskChatRequest.ActiveWriteback();
        activeWriteback.setId("writeback-direct-001");
        activeWriteback.setType("follow_record");
        activeWriteback.setStatus("awaiting_confirm");
        DealDeskChatRequest.Target target = new DealDeskChatRequest.Target();
        target.setCustomerId("customer-001");
        target.setOpportunityId("opportunity-001");
        target.setContactId("contact-001");
        target.setOwnerId("owner-001");
        activeWriteback.setTarget(target);
        DealDeskChatRequest.Draft draft = new DealDeskChatRequest.Draft();
        draft.setContent("Customer asked to go live within two weeks.");
        draft.setFollowMethod("2");
        activeWriteback.setRecordDraft(draft);
        request.setActiveWriteback(activeWriteback);

        Method parseMapMethod = AiDealDeskService.class.getDeclaredMethod("parseMap", Object.class);
        parseMapMethod.setAccessible(true);
        Method adaptChatResponseMethod = AiDealDeskService.class.getDeclaredMethod(
                "adaptChatResponse",
                Map.class,
                DealDeskChatRequest.class
        );
        adaptChatResponseMethod.setAccessible(true);

        Map<String, Object> rawResponse = (Map<String, Object>) parseMapMethod.invoke(
                service,
                """
                {
                  "answer": "writeback completed",
                  "metadata": {
                    "workflow_outputs": {
                      "protocolVersion": "1.0",
                      "turnType": "writeback_result",
                      "answerText": "writeback completed",
                      "writeback": {
                        "id": "writeback-direct-001",
                        "type": "follow_record",
                        "status": "confirmed",
                        "resultMessage": "writeback completed"
                      }
                    }
                  }
                }
                """
        );

        Object response = adaptChatResponseMethod.invoke(service, rawResponse, request);
        Field writebackField = response.getClass().getDeclaredField("writeback");
        writebackField.setAccessible(true);
        Map<String, Object> writeback = (Map<String, Object>) writebackField.get(response);

        assertEquals("confirmed", writeback.get("status"));
        assertEquals("record-direct-001", writeback.get("recordId"));
        assertEquals("opportunity-001", toolService.lastRequest.getOpportunityId());
        assertEquals("customer-001", toolService.lastRequest.getCustomerId());
        assertEquals("contact-001", toolService.lastRequest.getContactId());
        assertEquals("owner-001", toolService.lastRequest.getOwnerId());
        assertEquals("Customer asked to go live within two weeks.", toolService.lastRequest.getContent());
        assertEquals("2", toolService.lastRequest.getFollowMethod());
        assertEquals("writeback-direct-001", toolService.lastRequest.getIdempotencyKey());
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldResolveNamedPendingDraftBeforeDirectWritebackWhenTargetIdsAreMissing() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        CapturingToolService toolService = new CapturingToolService("record-direct-002");
        toolService.searchOpportunity = Map.of(
                "id", "opportunity-002",
                "customerId", "customer-002",
                "contactId", "contact-002",
                "ownerId", "owner-002"
        );
        Field toolServiceField = AiDealDeskService.class.getDeclaredField("aiDealDeskToolService");
        toolServiceField.setAccessible(true);
        toolServiceField.set(service, toolService);

        DealDeskChatRequest request = new DealDeskChatRequest();
        DealDeskChatRequest.ActiveWriteback activeWriteback = new DealDeskChatRequest.ActiveWriteback();
        activeWriteback.setId("writeback-direct-002");
        activeWriteback.setType("follow_record");
        activeWriteback.setStatus("awaiting_confirm");
        DealDeskChatRequest.Target target = new DealDeskChatRequest.Target();
        target.setCustomerName("华东智造集团");
        target.setOpportunityName("AI客服升级项目");
        activeWriteback.setTarget(target);
        DealDeskChatRequest.Draft draft = new DealDeskChatRequest.Draft();
        draft.setContent("近期调研该商机，客户反馈要求两周内上线。");
        draft.setFollowMethod("2");
        activeWriteback.setRecordDraft(draft);
        request.setActiveWriteback(activeWriteback);

        Method parseMapMethod = AiDealDeskService.class.getDeclaredMethod("parseMap", Object.class);
        parseMapMethod.setAccessible(true);
        Method adaptChatResponseMethod = AiDealDeskService.class.getDeclaredMethod(
                "adaptChatResponse",
                Map.class,
                DealDeskChatRequest.class
        );
        adaptChatResponseMethod.setAccessible(true);

        Map<String, Object> rawResponse = (Map<String, Object>) parseMapMethod.invoke(
                service,
                """
                {
                  "answer": "writeback completed",
                  "metadata": {
                    "workflow_outputs": {
                      "protocolVersion": "1.0",
                      "turnType": "writeback_result",
                      "answerText": "writeback completed",
                      "writeback": {
                        "id": "writeback-direct-002",
                        "type": "follow_record",
                        "status": "confirmed",
                        "resultMessage": "writeback completed"
                      }
                    }
                  }
                }
                """
        );

        Object response = adaptChatResponseMethod.invoke(service, rawResponse, request);
        Field writebackField = response.getClass().getDeclaredField("writeback");
        writebackField.setAccessible(true);
        Map<String, Object> writeback = (Map<String, Object>) writebackField.get(response);

        assertEquals("confirmed", writeback.get("status"));
        assertEquals("record-direct-002", writeback.get("recordId"));
        assertEquals("华东智造集团AI客服升级项目", toolService.lastSearchKeyword);
        assertEquals("admin", toolService.lastSearchUserId);
        assertEquals("100001", toolService.lastSearchOrganizationId);
        assertEquals("opportunity-002", toolService.lastRequest.getOpportunityId());
        assertEquals("customer-002", toolService.lastRequest.getCustomerId());
        assertEquals("contact-002", toolService.lastRequest.getContactId());
        assertEquals("owner-002", toolService.lastRequest.getOwnerId());
        assertEquals("admin", toolService.lastWriteUserId);
        assertEquals("100001", toolService.lastWriteOrganizationId);
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldRenderWritebackConfirmationFromStructuredFields() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Method parseMapMethod = AiDealDeskService.class.getDeclaredMethod("parseMap", Object.class);
        parseMapMethod.setAccessible(true);
        Method adaptChatResponseMethod = AiDealDeskService.class.getDeclaredMethod(
                "adaptChatResponse",
                Map.class,
                DealDeskChatRequest.class
        );
        adaptChatResponseMethod.setAccessible(true);

        Map<String, Object> rawResponse = (Map<String, Object>) parseMapMethod.invoke(
                service,
                """
                {
                  "metadata": {
                    "workflow_outputs": {
                      "protocolVersion": "1.0",
                      "turnType": "writeback_confirm",
                      "answerText": "模型自由输出的总结",
                      "writeback": {
                        "id": "writeback-confirm-001",
                        "type": "follow_record",
                        "status": "awaiting_confirm",
                        "target": {
                          "customerName": "华东智造集团",
                          "opportunityName": "华东智造集团AI客服升级项目",
                          "ownerName": "周雨晴",
                          "contactName": "张伟"
                        },
                        "recordDraft": {
                          "followMethod": "2",
                          "content": "客户反馈要求项目在两周内完成上线。"
                        }
                      }
                    }
                  }
                }
                """
        );

        Object response = adaptChatResponseMethod.invoke(service, rawResponse, new DealDeskChatRequest());
        Field answerTextField = response.getClass().getDeclaredField("answerText");
        answerTextField.setAccessible(true);
        String answerText = (String) answerTextField.get(response);

        assertTrue(answerText.contains("写入类型：跟进记录"));
        assertTrue(answerText.contains("关联商机：华东智造集团AI客服升级项目"));
        assertTrue(answerText.contains("关联客户：华东智造集团"));
        assertTrue(answerText.contains("跟进方式：电话"));
        assertTrue(answerText.contains("负责人：周雨晴"));
        assertTrue(answerText.contains("联系人：张伟"));
        assertTrue(answerText.contains("客户反馈要求项目在两周内完成上线。"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldTrackDomainAnalysisStartAndFinishWithTheSameEventId() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Method method = AiDealDeskService.class.getDeclaredMethod("adaptStreamingFrame", String.class);
        method.setAccessible(true);

        Map<String, Object> runningFrame = (Map<String, Object>) method.invoke(
                service,
                "{\"event\":\"node_started\",\"node\":{\"id\":\"sales_agent\",\"title\":\"销售分析\",\"data\":{\"type\":\"llm\"}}}"
        );
        Map<String, Object> finishedAgentFrame = (Map<String, Object>) method.invoke(
                service,
                "{\"event\":\"node_finished\",\"node\":{\"id\":\"sales_agent\",\"title\":\"销售分析\",\"data\":{\"type\":\"llm\"}},\"data\":{\"status\":\"succeeded\"}}"
        );
        Map<String, Object> finalOutputFrame = (Map<String, Object>) method.invoke(
                service,
                "{\"event\":\"node_finished\",\"node\":{\"id\":\"simple_answer\",\"title\":\"回答\",\"data\":{\"type\":\"answer\"}},\"data\":{\"status\":\"succeeded\"}}"
        );

        assertEquals("process_event", runningFrame.get("type"));
        assertEquals("sales_analysis", ((Map<String, Object>) runningFrame.get("event")).get("type"));
        assertEquals("running", ((Map<String, Object>) runningFrame.get("event")).get("status"));
        assertEquals("正在进行销售分析", ((Map<String, Object>) runningFrame.get("event")).get("text"));

        assertEquals("process_event", finishedAgentFrame.get("type"));
        assertEquals("sales_agent", ((Map<String, Object>) finishedAgentFrame.get("event")).get("id"));
        assertEquals("sales_analysis", ((Map<String, Object>) finishedAgentFrame.get("event")).get("type"));
        assertEquals("completed", ((Map<String, Object>) finishedAgentFrame.get("event")).get("status"));

        assertEquals("process_event", finalOutputFrame.get("type"));
        assertEquals("answer_generation", ((Map<String, Object>) finalOutputFrame.get("event")).get("type"));
        assertEquals("completed", ((Map<String, Object>) finalOutputFrame.get("event")).get("status"));
        assertEquals("已生成回答", ((Map<String, Object>) finalOutputFrame.get("event")).get("text"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldCompleteSuggestionForDealDeskBusinessJudgmentFinalNode() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Method method = AiDealDeskService.class.getDeclaredMethod("adaptStreamingFrame", String.class);
        method.setAccessible(true);

        Map<String, Object> finalOutputFrame = (Map<String, Object>) method.invoke(
                service,
                "{\"event\":\"node_finished\",\"node\":{\"id\":\"business_answer_agent\",\"title\":\"任意标题\",\"data\":{\"type\":\"answer\"}},\"data\":{\"status\":\"succeeded\"}}"
        );

        assertEquals("process_event", finalOutputFrame.get("type"));
        assertEquals("answer_generation", ((Map<String, Object>) finalOutputFrame.get("event")).get("type"));
        assertEquals("completed", ((Map<String, Object>) finalOutputFrame.get("event")).get("status"));
        assertEquals("已生成结论", ((Map<String, Object>) finalOutputFrame.get("event")).get("text"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldExposeFailedEventForTrackedWorkflowNode() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Method method = AiDealDeskService.class.getDeclaredMethod("adaptStreamingFrame", String.class);
        method.setAccessible(true);

        Map<String, Object> failedFrame = (Map<String, Object>) method.invoke(
                service,
                "{\"event\":\"node_finished\",\"node\":{\"id\":\"deal_rules_knowledge\",\"title\":\"知识检索\",\"data\":{\"type\":\"knowledge-retrieval\"}},\"data\":{\"status\":\"failed\"}}"
        );

        assertEquals("process_event", failedFrame.get("type"));
        assertEquals("knowledge_retrieval", ((Map<String, Object>) failedFrame.get("event")).get("type"));
        assertEquals("failed", ((Map<String, Object>) failedFrame.get("event")).get("status"));
        assertEquals("业务规则检索失败", ((Map<String, Object>) failedFrame.get("event")).get("text"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldInjectBoundCustomerNameIntoPronounQuery() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Method buildChatRequestBodyMethod = AiDealDeskService.class.getDeclaredMethod("buildChatRequestBody", DealDeskChatRequest.class);
        buildChatRequestBodyMethod.setAccessible(true);

        Field difyApiKeyField = AiDealDeskService.class.getDeclaredField("difyApiKey");
        difyApiKeyField.setAccessible(true);
        difyApiKeyField.set(service, "test-key");

        DealDeskChatRequest request = new DealDeskChatRequest();
        request.setQuery("看一下这个客户有哪些商机");

        DealDeskChatRequest.BoundObject boundObject = new DealDeskChatRequest.BoundObject();
        boundObject.setObjectType("customer");
        boundObject.setObjectId("customer-1");
        boundObject.setObjectName("华东智造集团");
        boundObject.setSource("mention");
        request.setBoundObject(boundObject);

        Map<String, Object> body = (Map<String, Object>) buildChatRequestBodyMethod.invoke(service, request);

        assertEquals("看一下华东智造集团有哪些商机", body.get("query"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldInjectBoundCustomerNameIntoBareOpportunityListQuery() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Method buildChatRequestBodyMethod = AiDealDeskService.class.getDeclaredMethod("buildChatRequestBody", DealDeskChatRequest.class);
        buildChatRequestBodyMethod.setAccessible(true);

        Field difyApiKeyField = AiDealDeskService.class.getDeclaredField("difyApiKey");
        difyApiKeyField.setAccessible(true);
        difyApiKeyField.set(service, "test-key");

        DealDeskChatRequest request = new DealDeskChatRequest();
        request.setQuery("有哪些商机");

        DealDeskChatRequest.BoundObject boundObject = new DealDeskChatRequest.BoundObject();
        boundObject.setObjectType("customer");
        boundObject.setObjectId("customer-1");
        boundObject.setObjectName("华东智造集团");
        boundObject.setSource("mention");
        request.setBoundObject(boundObject);

        Map<String, Object> body = (Map<String, Object>) buildChatRequestBodyMethod.invoke(service, request);
        Map<String, Object> inputs = (Map<String, Object>) body.get("inputs");

        assertEquals("华东智造集团有哪些商机", body.get("query"));
        assertEquals("", inputs.get("bound_object_type"));
        assertEquals("", inputs.get("bound_object_id"));
        assertEquals("", inputs.get("bound_object_name"));
        assertEquals("", inputs.get("route_customer_id"));
    }

    @SuppressWarnings("unchecked")
    @Test
    void shouldPassUploadedFilesToTopLevelAndChatflowFileInput() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Method buildChatRequestBodyMethod = AiDealDeskService.class.getDeclaredMethod("buildChatRequestBody", DealDeskChatRequest.class);
        buildChatRequestBodyMethod.setAccessible(true);

        Field difyApiKeyField = AiDealDeskService.class.getDeclaredField("difyApiKey");
        difyApiKeyField.setAccessible(true);
        difyApiKeyField.set(service, "test-key");

        DealDeskChatRequest.ChatFile file = new DealDeskChatRequest.ChatFile();
        file.setName("quote-screenshot.png");
        file.setType("image");
        file.setUploadFileId("upload-file-1");
        file.setMimeType("image/png");

        DealDeskChatRequest request = new DealDeskChatRequest();
        request.setQuery("please read this screenshot");
        request.setFiles(List.of(file));

        Map<String, Object> body = (Map<String, Object>) buildChatRequestBodyMethod.invoke(service, request);
        Map<String, Object> inputs = (Map<String, Object>) body.get("inputs");
        List<Map<String, Object>> chatFiles = (List<Map<String, Object>>) body.get("files");

        List<Map<String, Object>> inputFiles = (List<Map<String, Object>>) inputs.get("uploaded_files");

        assertEquals("upload-file-1", inputFiles.get(0).get("upload_file_id"));
        assertEquals("image", inputFiles.get(0).get("type"));
        assertEquals("local_file", inputFiles.get(0).get("transfer_method"));
        assertEquals("upload-file-1", chatFiles.get(0).get("upload_file_id"));
        assertEquals("image", chatFiles.get(0).get("type"));
        assertEquals("local_file", chatFiles.get(0).get("transfer_method"));
        assertFalse(chatFiles.get(0).containsKey("name"));
        assertFalse(chatFiles.get(0).containsKey("mime_type"));
    }

    @Test
    void shouldUseCapturedDifyUserWhenBuildingStreamingChatBody() throws Exception {
        AiDealDeskService service = new AiDealDeskService();
        Method buildChatRequestBodyMethod = AiDealDeskService.class.getDeclaredMethod(
                "buildChatRequestBody",
                DealDeskChatRequest.class,
                String.class
        );
        buildChatRequestBodyMethod.setAccessible(true);

        Field difyApiKeyField = AiDealDeskService.class.getDeclaredField("difyApiKey");
        difyApiKeyField.setAccessible(true);
        difyApiKeyField.set(service, "test-key");

        DealDeskChatRequest request = new DealDeskChatRequest();
        request.setQuery("please read this screenshot");

        Map<String, Object> body = (Map<String, Object>) buildChatRequestBodyMethod.invoke(
                service,
                request,
                "100001:admin"
        );

        assertEquals("100001:admin", body.get("user"));
    }

    private static class CapturingToolService extends AiDealDeskToolService {
        private final String recordId;
        private DealDeskToolRequest lastRequest;
        private Map<String, Object> searchOpportunity;
        private String lastSearchKeyword;
        private String lastSearchUserId;
        private String lastSearchOrganizationId;
        private String lastWriteUserId;
        private String lastWriteOrganizationId;

        private CapturingToolService(String recordId) {
            this.recordId = recordId;
        }

        @Override
        public DealDeskToolResponse searchOpportunities(DealDeskToolRequest request) {
            this.lastSearchKeyword = request.getKeyword();
            this.lastSearchUserId = AiDealDeskToolExecutionContext.getUserId();
            this.lastSearchOrganizationId = AiDealDeskToolExecutionContext.getOrganizationId();
            if (searchOpportunity == null || searchOpportunity.isEmpty()) {
                return DealDeskToolResponse.fail("OBJECT_NOT_FOUND", "not found");
            }
            return DealDeskToolResponse.ok(Map.of("opportunity", searchOpportunity));
        }

        @Override
        public DealDeskToolResponse createFollowRecord(DealDeskToolRequest request) {
            this.lastRequest = request;
            this.lastWriteUserId = AiDealDeskToolExecutionContext.getUserId();
            this.lastWriteOrganizationId = AiDealDeskToolExecutionContext.getOrganizationId();
            return DealDeskToolResponse.ok(Map.of("recordId", recordId));
        }
    }
}
