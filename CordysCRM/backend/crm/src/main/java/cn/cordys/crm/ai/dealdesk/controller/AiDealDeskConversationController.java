package cn.cordys.crm.ai.dealdesk.controller;

import cn.cordys.context.OrganizationContext;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskConversationRequest;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskConversationResponse;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskMessageRequest;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskMessageResponse;
import cn.cordys.crm.ai.dealdesk.service.AiDealDeskConversationService;
import cn.cordys.security.SessionUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "多Agent智能助手历史会话")
@RestController
@RequestMapping({"/ai/deal-desk", "/front/ai/deal-desk"})
public class AiDealDeskConversationController {

    @Resource
    private AiDealDeskConversationService conversationService;

    @GetMapping(value = "/conversations", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @Operation(summary = "多Agent智能助手 历史会话列表")
    public java.util.List<DealDeskConversationResponse> list(@RequestParam(defaultValue = "50") int limit) {
        return conversationService.list(currentUserId(), currentOrganizationId(), limit);
    }

    @GetMapping(value = "/conversations/{id}", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @Operation(summary = "多Agent智能助手 历史会话详情")
    public DealDeskConversationResponse detail(@PathVariable String id) {
        return conversationService.detail(id, currentUserId(), currentOrganizationId());
    }

    @PostMapping(value = "/conversations", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @Operation(summary = "多Agent智能助手 创建历史会话")
    public DealDeskConversationResponse create(@RequestBody(required = false) DealDeskConversationRequest request) {
        return conversationService.create(request, currentUserId(), currentOrganizationId());
    }

    @PutMapping(value = "/conversations/{id}", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @Operation(summary = "多Agent智能助手 更新历史会话")
    public DealDeskConversationResponse update(
            @PathVariable String id,
            @RequestBody(required = false) DealDeskConversationRequest request
    ) {
        return conversationService.update(id, request, currentUserId(), currentOrganizationId());
    }

    @PostMapping(value = "/conversations/{id}/messages", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @Operation(summary = "多Agent智能助手 保存历史消息")
    public DealDeskMessageResponse saveMessage(@PathVariable String id, @RequestBody DealDeskMessageRequest request) {
        return conversationService.saveMessage(id, request, currentUserId(), currentOrganizationId());
    }

    @DeleteMapping(value = "/conversations/{id}", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @Operation(summary = "多Agent智能助手 删除历史会话")
    public Map<String, Boolean> delete(@PathVariable String id) {
        return Map.of("success", conversationService.delete(id, currentUserId(), currentOrganizationId()));
    }

    private String currentUserId() {
        String userId = SessionUtils.getUserId();
        if (StringUtils.isBlank(userId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AI Deal Desk history requires login");
        }
        return userId;
    }

    private String currentOrganizationId() {
        String organizationId = OrganizationContext.getOrganizationId();
        if (StringUtils.isBlank(organizationId)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "AI Deal Desk history requires organization");
        }
        return organizationId;
    }
}
