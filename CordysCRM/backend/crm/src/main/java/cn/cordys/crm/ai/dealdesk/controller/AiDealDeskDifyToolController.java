package cn.cordys.crm.ai.dealdesk.controller;

import cn.cordys.common.response.handler.NoResultHolder;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskToolRequest;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskToolResponse;
import cn.cordys.crm.ai.dealdesk.service.AiDealDeskToolExecutionContext;
import cn.cordys.crm.ai.dealdesk.service.AiDealDeskToolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "多Agent智能助手 Dify Tools")
@RestController
@RequestMapping("/anonymous/ai/deal-desk/dify-tools")
public class AiDealDeskDifyToolController {
    static final String TOOL_TOKEN_HEADER = "X-DIFY-TOOL-TOKEN";

    private final AiDealDeskToolService aiDealDeskToolService;
    private final String toolToken;
    private final String toolUserId;
    private final String organizationId;

    public AiDealDeskDifyToolController(
            AiDealDeskToolService aiDealDeskToolService,
            @Value("${ai.deal-desk.dify.tool-token:}") String toolToken,
            @Value("${ai.deal-desk.dify.user-id:admin}") String toolUserId,
            @Value("${ai.deal-desk.dify.organization-id:100001}") String organizationId) {
        this.aiDealDeskToolService = aiDealDeskToolService;
        this.toolToken = toolToken;
        this.toolUserId = toolUserId;
        this.organizationId = organizationId;
    }

    @NoResultHolder
    @PostMapping(value = "/{toolName}", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @Operation(summary = "多Agent智能助手 Dify 工具网关")
    public DealDeskToolResponse invokeTool(
            @PathVariable String toolName,
            @RequestHeader(value = TOOL_TOKEN_HEADER, required = false) String requestToken,
            @RequestBody(required = false) DealDeskToolRequest request) {
        if (!isAuthorized(requestToken)) {
            return DealDeskToolResponse.fail("UNAUTHORIZED", "Dify 工具调用未授权。");
        }
        DealDeskToolRequest safeRequest = request == null ? new DealDeskToolRequest() : request;
        return AiDealDeskToolExecutionContext.runAs(toolUserId, organizationId, () -> dispatchTool(toolName, safeRequest));
    }

    private DealDeskToolResponse dispatchTool(String toolName, DealDeskToolRequest safeRequest) {
        return switch (StringUtils.trimToEmpty(toolName)) {
            case "search-customers" -> aiDealDeskToolService.searchCustomers(safeRequest);
            case "search-opportunities" -> aiDealDeskToolService.searchOpportunities(safeRequest);
            case "resolve-crm-object" -> aiDealDeskToolService.resolveCrmObject(safeRequest);
            case "get-customer-context" -> aiDealDeskToolService.getCustomerContext(safeRequest);
            case "get-opportunity-context" -> aiDealDeskToolService.getOpportunityContext(safeRequest);
            case "create-follow-record" -> aiDealDeskToolService.createFollowRecord(safeRequest);
            case "create-follow-plan" -> aiDealDeskToolService.createFollowPlan(safeRequest);
            case "get-funnel-snapshot" -> aiDealDeskToolService.getFunnelSnapshot(safeRequest);
            case "get-customer-l2c-health" -> aiDealDeskToolService.getCustomerL2cHealth(safeRequest);
            case "get-contract-revenue-snapshot" -> aiDealDeskToolService.getContractRevenueSnapshot(safeRequest);
            default -> DealDeskToolResponse.fail("TOOL_NOT_FOUND", "不支持的 Dify 工具：" + toolName);
        };
    }

    private boolean isAuthorized(String requestToken) {
        return StringUtils.isNotBlank(toolToken) && StringUtils.equals(toolToken, requestToken);
    }
}
