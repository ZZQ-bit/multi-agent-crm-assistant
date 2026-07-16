package cn.cordys.crm.ai.dealdesk.controller;

import cn.cordys.common.response.handler.NoResultHolder;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskToolRequest;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskToolResponse;
import cn.cordys.crm.ai.dealdesk.service.AiDealDeskToolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "多Agent智能助手 Tools")
@RestController
@RequestMapping({"/ai/deal-desk/tools", "/front/ai/deal-desk/tools"})
public class AiDealDeskToolController {

    @Resource
    private AiDealDeskToolService aiDealDeskToolService;

    @NoResultHolder
    @PostMapping(value = "/search-customers", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @Operation(summary = "多Agent智能助手 查询客户工具")
    public DealDeskToolResponse searchCustomers(@RequestBody DealDeskToolRequest request) {
        return aiDealDeskToolService.searchCustomers(request);
    }

    @NoResultHolder
    @PostMapping(value = "/search-opportunities", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @Operation(summary = "多Agent智能助手 查询商机工具")
    public DealDeskToolResponse searchOpportunities(@RequestBody DealDeskToolRequest request) {
        return aiDealDeskToolService.searchOpportunities(request);
    }

    @NoResultHolder
    @PostMapping(value = "/resolve-crm-object", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @Operation(summary = "多Agent智能助手 解析 CRM 客户或商机对象")
    public DealDeskToolResponse resolveCrmObject(@RequestBody DealDeskToolRequest request) {
        return aiDealDeskToolService.resolveCrmObject(request);
    }

    @NoResultHolder
    @PostMapping(value = "/get-customer-context", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @Operation(summary = "多Agent智能助手 客户上下文工具")
    public DealDeskToolResponse getCustomerContext(@RequestBody DealDeskToolRequest request) {
        return aiDealDeskToolService.getCustomerContext(request);
    }

    @NoResultHolder
    @PostMapping(value = "/get-opportunity-context", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @Operation(summary = "多Agent智能助手 商机上下文工具")
    public DealDeskToolResponse getOpportunityContext(@RequestBody DealDeskToolRequest request) {
        return aiDealDeskToolService.getOpportunityContext(request);
    }

    @NoResultHolder
    @PostMapping(value = "/create-follow-record", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @Operation(summary = "多Agent智能助手 创建跟进记录工具")
    public DealDeskToolResponse createFollowRecord(@RequestBody DealDeskToolRequest request) {
        return aiDealDeskToolService.createFollowRecord(request);
    }

    @NoResultHolder
    @PostMapping(value = "/create-follow-plan", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @Operation(summary = "多Agent智能助手 创建跟进计划工具")
    public DealDeskToolResponse createFollowPlan(@RequestBody DealDeskToolRequest request) {
        return aiDealDeskToolService.createFollowPlan(request);
    }

    @NoResultHolder
    @PostMapping(value = "/get-funnel-snapshot", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @Operation(summary = "多Agent智能助手 销售漏斗统计工具")
    public DealDeskToolResponse getFunnelSnapshot(@RequestBody DealDeskToolRequest request) {
        return aiDealDeskToolService.getFunnelSnapshot(request);
    }

    @NoResultHolder
    @PostMapping(value = "/get-customer-l2c-health", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @Operation(summary = "多Agent智能助手 客户 L2C 健康度统计工具")
    public DealDeskToolResponse getCustomerL2cHealth(@RequestBody DealDeskToolRequest request) {
        return aiDealDeskToolService.getCustomerL2cHealth(request);
    }

    @NoResultHolder
    @PostMapping(value = "/get-contract-revenue-snapshot", produces = MediaType.APPLICATION_JSON_VALUE + ";charset=UTF-8")
    @Operation(summary = "多Agent智能助手 合同回款与开票统计工具")
    public DealDeskToolResponse getContractRevenueSnapshot(@RequestBody DealDeskToolRequest request) {
        return aiDealDeskToolService.getContractRevenueSnapshot(request);
    }
}
