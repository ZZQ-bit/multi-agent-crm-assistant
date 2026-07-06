package cn.cordys.crm.ai.dealdesk.controller;

import cn.cordys.common.response.handler.NoResultHolder;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskChatRequest;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskChatResponse;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskFileUploadResponse;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskStopChatRequest;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskStopChatResponse;
import cn.cordys.crm.ai.dealdesk.service.AiDealDeskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

@Tag(name = "多Agent智能助手")
@Slf4j
@RestController
@RequestMapping({"/ai/deal-desk", "/front/ai/deal-desk"})
public class AiDealDeskController {

    @Resource
    private AiDealDeskService aiDealDeskService;

    @PostMapping(value = "/chat", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @Operation(summary = "多Agent智能助手 对话")
    public DealDeskChatResponse chat(@RequestBody DealDeskChatRequest request) {
        log.info("多Agent智能助手 chat request received, query={}, conversationId={}",
                request == null ? null : request.getQuery(),
                request == null ? null : request.getConversationId());
        DealDeskChatResponse response = aiDealDeskService.chat(request);
        log.info("多Agent智能助手 chat response ready, turnType={}, conversationId={}, messageId={}",
                response == null ? null : response.getTurnType(),
                response == null ? null : response.getConversationId(),
                response == null ? null : response.getMessageId());
        return response;
    }

    @NoResultHolder
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "多Agent智能助手 streaming chat")
    public StreamingResponseBody streamChat(@RequestBody DealDeskChatRequest request, HttpServletResponse response) {
        String difyUser = aiDealDeskService.buildCurrentDifyUser();
        log.info("多Agent智能助手 stream request received, query={}, conversationId={}, files={}",
                request == null ? null : request.getQuery(),
                request == null ? null : request.getConversationId(),
                request == null || request.getFiles() == null ? 0 : request.getFiles().size());
        response.setHeader("X-Accel-Buffering", "no");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        response.setCharacterEncoding("UTF-8");
        return outputStream -> aiDealDeskService.streamChat(request, outputStream, difyUser);
    }

    @PostMapping(value = "/chat/stop", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @Operation(summary = "多Agent智能助手 stop streaming chat")
    public DealDeskStopChatResponse stopChat(@RequestBody DealDeskStopChatRequest request) {
        return aiDealDeskService.stopChat(request);
    }

    @PostMapping(value = "/file/upload", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @Operation(summary = "多Agent智能助手 上传文件")
    public DealDeskFileUploadResponse uploadFile(@RequestPart("file") MultipartFile file) {
        log.info("多Agent智能助手 file upload received, name={}, contentType={}, size={}",
                file == null ? null : file.getOriginalFilename(),
                file == null ? null : file.getContentType(),
                file == null ? null : file.getSize());
        return aiDealDeskService.uploadFile(file);
    }
}
