package cn.cordys.crm.ai.dealdesk.service;

import cn.cordys.common.constants.InternalUserView;
import cn.cordys.common.constants.PermissionConstants;
import cn.cordys.common.domain.BaseModuleFieldValue;
import cn.cordys.common.dto.DeptDataPermissionDTO;
import cn.cordys.common.dto.OptionDTO;
import cn.cordys.common.pager.PagerWithOption;
import cn.cordys.common.service.DataScopeService;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskToolRequest;
import cn.cordys.crm.customer.dto.response.CustomerContactListAllResponse;
import cn.cordys.crm.customer.dto.response.CustomerContactListResponse;
import cn.cordys.crm.customer.dto.response.CustomerGetResponse;
import cn.cordys.crm.customer.service.CustomerService;
import cn.cordys.crm.follow.domain.FollowUpPlan;
import cn.cordys.crm.follow.domain.FollowUpRecord;
import cn.cordys.crm.follow.dto.request.FollowUpPlanAddRequest;
import cn.cordys.crm.follow.dto.request.FollowUpRecordAddRequest;
import cn.cordys.crm.follow.dto.request.PlanHomePageRequest;
import cn.cordys.crm.follow.dto.request.RecordHomePageRequest;
import cn.cordys.crm.follow.dto.response.FollowUpPlanListResponse;
import cn.cordys.crm.follow.dto.response.FollowUpRecordListResponse;
import cn.cordys.crm.follow.service.FollowUpPlanService;
import cn.cordys.crm.follow.service.FollowUpRecordService;
import cn.cordys.crm.opportunity.dto.request.OpportunityPageRequest;
import cn.cordys.crm.opportunity.dto.response.OpportunityDetailResponse;
import cn.cordys.crm.opportunity.dto.response.OpportunityListResponse;
import cn.cordys.crm.opportunity.service.OpportunityService;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Component
public class CordysCrmDealDeskToolGateway implements AiDealDeskToolGateway {

    @Resource
    private CustomerService customerService;
    @Resource
    private OpportunityService opportunityService;
    @Resource
    private FollowUpRecordService followUpRecordService;
    @Resource
    private FollowUpPlanService followUpPlanService;
    @Resource
    private DataScopeService dataScopeService;
    @Resource
    private JdbcTemplate jdbcTemplate;

    @Override
    public List<Map<String, Object>> searchCustomers(String keyword, int limit) {
        return safeList(customerService.getCustomerOptions(keyword, currentOrganizationId())).stream()
                .limit(limit)
                .map(this::mapCustomerOption)
                .toList();
    }

    @Override
    public List<Map<String, Object>> searchOpportunities(String keyword, int limit) {
        OpportunityPageRequest request = new OpportunityPageRequest();
        request.setCurrent(1);
        request.setPageSize(limit);
        request.setViewId(InternalUserView.ALL.name());
        request.initKeyword(keyword);
        DeptDataPermissionDTO permission = dataScope(PermissionConstants.OPPORTUNITY_MANAGEMENT_READ, request.getViewId());
        PagerWithOption<List<OpportunityListResponse>> page = opportunityService.list(
                request,
                currentUserId(),
                currentOrganizationId(),
                permission,
                false
        );
        return safeList(page == null ? null : page.getList()).stream().map(this::mapOpportunityCandidate).toList();
    }

    @Override
    public Map<String, Object> getCustomer(String customerId) {
        if (StringUtils.isBlank(customerId)) {
            return Collections.emptyMap();
        }
        CustomerGetResponse customer = customerService.getWithDataPermissionCheck(
                customerId,
                currentUserId(),
                currentOrganizationId()
        );
        if (customer == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", customer.getId());
        result.put("name", customer.getName());
        result.put("ownerId", customer.getOwner());
        result.put("ownerName", customer.getOwnerName());
        result.put("departmentId", customer.getDepartmentId());
        result.put("departmentName", customer.getDepartmentName());
        result.put("createTime", customer.getCreateTime());
        result.put("updateTime", customer.getUpdateTime());
        result.put("followerId", customer.getFollower());
        result.put("followerName", customer.getFollowerName());
        result.put("followTime", customer.getFollowTime());
        result.put("recyclePoolName", customer.getRecyclePoolName());
        result.put("reasonName", customer.getReasonName());
        result.put("customFields", mapCustomFields(customer.getModuleFields()));
        result.put("attachmentFieldCount", attachmentFieldCount(customer.getAttachmentMap()));
        return result;
    }

    @Override
    public Map<String, Object> getOpportunity(String opportunityId) {
        if (StringUtils.isBlank(opportunityId)) {
            return Collections.emptyMap();
        }
        OpportunityDetailResponse opportunity = opportunityService.getWithDataPermissionCheck(
                opportunityId,
                currentUserId(),
                currentOrganizationId()
        );
        if (opportunity == null) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", opportunity.getId());
        result.put("name", opportunity.getName());
        result.put("customerId", opportunity.getCustomerId());
        result.put("customerName", opportunity.getCustomerName());
        result.put("amount", formatAmount(opportunity.getAmount()));
        result.put("amountText", formatAmount(opportunity.getAmount()));
        result.put("possible", formatAmount(opportunity.getPossible()));
        result.put("products", opportunity.getProducts());
        result.put("lastStage", opportunity.getLastStage());
        result.put("stage", opportunity.getStage());
        result.put("stageName", opportunity.getStageName());
        result.put("ownerId", opportunity.getOwner());
        result.put("ownerName", opportunity.getOwnerName());
        result.put("contactId", opportunity.getContactId());
        result.put("contactName", opportunity.getContactName());
        result.put("expectedEndTime", opportunity.getExpectedEndTime());
        result.put("actualEndTime", opportunity.getActualEndTime());
        result.put("failureReason", opportunity.getFailureReason());
        result.put("createTime", opportunity.getCreateTime());
        result.put("updateTime", opportunity.getUpdateTime());
        result.put("followerId", opportunity.getFollower());
        result.put("followerName", opportunity.getFollowerName());
        result.put("followTime", opportunity.getFollowTime());
        result.put("departmentId", opportunity.getDepartmentId());
        result.put("departmentName", opportunity.getDepartmentName());
        result.put("customFields", mapCustomFields(opportunity.getModuleFields()));
        result.put("attachmentFieldCount", attachmentFieldCount(opportunity.getAttachmentMap()));
        return result;
    }

    @Override
    public List<Map<String, Object>> listOpportunityContacts(String opportunityId) {
        CustomerContactListAllResponse response = opportunityService.getContactList(opportunityId, currentOrganizationId());
        return safeList(response == null ? null : response.getList()).stream().map(this::mapContact).toList();
    }

    @Override
    public List<Map<String, Object>> listRecentFollowRecords(String customerId, String opportunityId, int limit) {
        RecordHomePageRequest request = new RecordHomePageRequest();
        request.setCurrent(1);
        request.setPageSize(limit);
        request.setViewId(InternalUserView.ALL.name());
        request.setCustomerId(customerId);
        request.setOpportunityId(opportunityId);
        PagerWithOption<List<FollowUpRecordListResponse>> page = followUpRecordService.totalList(
                request,
                currentUserId(),
                currentOrganizationId(),
                dataScope(PermissionConstants.CLUE_MANAGEMENT_READ, request.getViewId()),
                dataScope(PermissionConstants.CUSTOMER_MANAGEMENT_READ, request.getViewId())
        );
        return safeList(page == null ? null : page.getList()).stream()
                .limit(limit)
                .map(this::mapFollowRecord)
                .toList();
    }

    @Override
    public List<Map<String, Object>> listOpenFollowPlans(String customerId, String opportunityId, int limit) {
        PlanHomePageRequest request = new PlanHomePageRequest();
        request.setCurrent(1);
        request.setPageSize(limit);
        request.setViewId(InternalUserView.ALL.name());
        request.setCustomerId(customerId);
        request.setOpportunityId(opportunityId);
        request.setStatus("PENDING");
        PagerWithOption<List<FollowUpPlanListResponse>> page = followUpPlanService.totalList(
                request,
                currentUserId(),
                currentOrganizationId(),
                dataScope(PermissionConstants.CLUE_MANAGEMENT_READ, request.getViewId()),
                dataScope(PermissionConstants.CUSTOMER_MANAGEMENT_READ, request.getViewId())
        );
        return safeList(page == null ? null : page.getList()).stream()
                .limit(limit)
                .map(this::mapFollowPlan)
                .toList();
    }

    @Override
    public Map<String, Object> createFollowRecord(DealDeskToolRequest request) {
        FollowUpRecordAddRequest addRequest = new FollowUpRecordAddRequest();
        addRequest.setCustomerId(resolveCustomerId(request));
        addRequest.setOpportunityId(request.getOpportunityId());
        addRequest.setContactId(request.getContactId());
        addRequest.setContent(request.getContent());
        addRequest.setFollowMethod(firstNonBlank(request.getFollowMethod(), "2"));
        addRequest.setFollowTime(request.getFollowTime() == null ? System.currentTimeMillis() : request.getFollowTime());
        addRequest.setOwner(firstNonBlank(request.getOwnerId(), currentUserId()));
        addRequest.setType(StringUtils.isNotBlank(request.getOpportunityId()) ? "BUSINESS" : "CUSTOMER");
        FollowUpRecord record = followUpRecordService.add(addRequest, currentUserId(), currentOrganizationId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("recordId", record == null ? "" : record.getId());
        return data;
    }

    @Override
    public Map<String, Object> createFollowPlan(DealDeskToolRequest request) {
        FollowUpPlanAddRequest addRequest = new FollowUpPlanAddRequest();
        addRequest.setCustomerId(resolveCustomerId(request));
        addRequest.setOpportunityId(request.getOpportunityId());
        addRequest.setContactId(request.getContactId());
        addRequest.setContent(request.getContent());
        addRequest.setMethod(firstNonBlank(request.getPlanMethod(), request.getFollowMethod(), "2"));
        addRequest.setEstimatedTime(request.getPlanTime() == null ? System.currentTimeMillis() : request.getPlanTime());
        addRequest.setOwner(firstNonBlank(request.getOwnerId(), currentUserId()));
        addRequest.setType(StringUtils.isNotBlank(request.getOpportunityId()) ? "BUSINESS" : "CUSTOMER");
        FollowUpPlan plan = followUpPlanService.add(addRequest, currentUserId(), currentOrganizationId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("planId", plan == null ? "" : plan.getId());
        return data;
    }

    @Override
    public List<Map<String, Object>> getFunnelSnapshot(String organizationId) {
        // Fetch all opportunities visible to the current user
        OpportunityPageRequest request = new OpportunityPageRequest();
        request.setCurrent(1);
        request.setPageSize(500);
        request.setViewId(InternalUserView.ALL.name());
        DeptDataPermissionDTO permission = dataScope(PermissionConstants.OPPORTUNITY_MANAGEMENT_READ, request.getViewId());
        PagerWithOption<List<OpportunityListResponse>> page = opportunityService.list(
                request,
                currentUserId(),
                organizationId,
                permission,
                false
        );
        List<OpportunityListResponse> opportunities = safeList(page == null ? null : page.getList());

        // Group by stageName and count
        Map<String, Long> stageCounts = new LinkedHashMap<>();
        for (OpportunityListResponse opp : opportunities) {
            String stageName = StringUtils.isNotBlank(opp.getStageName()) ? opp.getStageName() : "未分类";
            stageCounts.merge(stageName, 1L, Long::sum);
        }

        return stageCounts.entrySet().stream()
                .map(entry -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", entry.getKey());
                    item.put("value", entry.getValue());
                    return item;
                })
                .toList();
    }

    @Override
    public Map<String, Object> getCustomerL2cHealth(String organizationId) {
        // Fetch all opportunities to derive customer-level L2C metrics
        OpportunityPageRequest request = new OpportunityPageRequest();
        request.setCurrent(1);
        request.setPageSize(500);
        request.setViewId(InternalUserView.ALL.name());
        DeptDataPermissionDTO permission = dataScope(PermissionConstants.OPPORTUNITY_MANAGEMENT_READ, request.getViewId());
        PagerWithOption<List<OpportunityListResponse>> page = opportunityService.list(
                request,
                currentUserId(),
                organizationId,
                permission,
                false
        );
        List<OpportunityListResponse> opportunities = safeList(page == null ? null : page.getList());

        // Distinct customers that have at least one opportunity
        long customersWithOpportunity = opportunities.stream()
                .map(OpportunityListResponse::getCustomerId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .count();

        // Customers that have at least one "won" opportunity (stage typically contains "赢单" or "成交")
        long customersWon = opportunities.stream()
                .filter(opp -> isWonStage(opp.getStageName()))
                .map(OpportunityListResponse::getCustomerId)
                .filter(StringUtils::isNotBlank)
                .distinct()
                .count();

        long totalOpportunities = opportunities.size();
        long wonOpportunities = opportunities.stream()
                .filter(opp -> isWonStage(opp.getStageName()))
                .count();

        // Build health metrics
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("customersWithOpportunity", customersWithOpportunity);
        result.put("customersWon", customersWon);
        result.put("totalOpportunities", totalOpportunities);
        result.put("wonOpportunities", wonOpportunities);
        result.put("opportunityWinRate", totalOpportunities > 0
                ? Math.round(wonOpportunities * 100.0 / totalOpportunities) : 0);
        result.put("customerConversionRate", customersWithOpportunity > 0
                ? Math.round(customersWon * 100.0 / customersWithOpportunity) : 0);

        // Breakdown for chart rendering
        List<Map<String, Object>> metrics = List.of(
                metricEntry("有商机客户数", customersWithOpportunity),
                metricEntry("赢单客户数", customersWon),
                metricEntry("商机赢单率(%)", totalOpportunities > 0
                        ? Math.round(wonOpportunities * 100.0 / totalOpportunities) : 0),
                metricEntry("客户转化率(%)", customersWithOpportunity > 0
                        ? Math.round(customersWon * 100.0 / customersWithOpportunity) : 0)
        );
        result.put("metrics", metrics);
        return result;
    }

    @Override
    public Map<String, Object> getContractRevenueSnapshot(String organizationId) {
        String sql = """
                select
                  coalesce((select sum(c.amount) from contract c where c.organization_id = ?), 0) as contract_amount,
                  coalesce((select sum(r.record_amount) from contract_payment_record r where r.organization_id = ?), 0) as paid_amount,
                  coalesce((select sum(i.amount) from contract_invoice i where i.organization_id = ?), 0) as invoice_amount,
                  coalesce((select sum(p.plan_amount) from contract_payment_plan p where p.organization_id = ? and p.plan_status <> 'COMPLETED'), 0) as pending_amount,
                  coalesce((
                    select sum(p.plan_amount)
                    from contract_payment_plan p
                    where p.organization_id = ?
                      and p.plan_status <> 'COMPLETED'
                      and p.plan_end_time is not null
                      and p.plan_end_time < ?
                  ), 0) as overdue_pending_amount
                """;
        long now = System.currentTimeMillis();
        return jdbcTemplate.queryForObject(
                sql,
                (rs, rowNum) -> {
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("contractAmount", rs.getBigDecimal("contract_amount"));
                    result.put("paidAmount", rs.getBigDecimal("paid_amount"));
                    result.put("invoiceAmount", rs.getBigDecimal("invoice_amount"));
                    result.put("pendingAmount", rs.getBigDecimal("pending_amount"));
                    result.put("overduePendingAmount", rs.getBigDecimal("overdue_pending_amount"));
                    return result;
                },
                organizationId,
                organizationId,
                organizationId,
                organizationId,
                organizationId,
                now
        );
    }

    private boolean isWonStage(String stageName) {
        if (StringUtils.isBlank(stageName)) return false;
        return stageName.contains("赢单") || stageName.contains("成交") || stageName.contains("签约");
    }

    private Map<String, Object> metricEntry(String name, long value) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", name);
        entry.put("value", value);
        return entry;
    }

    private String resolveCustomerId(DealDeskToolRequest request) {
        if (StringUtils.isNotBlank(request.getCustomerId())) {
            return request.getCustomerId();
        }
        return String.valueOf(getOpportunity(request.getOpportunityId()).getOrDefault("customerId", ""));
    }

    private DeptDataPermissionDTO dataScope(String permission, String viewId) {
        return dataScopeService.getDeptDataPermission(currentUserId(), currentOrganizationId(), viewId, permission);
    }

    private String currentUserId() {
        return AiDealDeskToolExecutionContext.getUserId();
    }

    private String currentOrganizationId() {
        return AiDealDeskToolExecutionContext.getOrganizationId();
    }

    private Map<String, Object> mapCustomerOption(OptionDTO option) {
        Map<String, Object> customer = getCustomer(option.getId());
        if (!customer.isEmpty()) {
            return customer;
        }
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", option.getId());
        map.put("name", option.getName());
        return map;
    }

    private Map<String, Object> mapOpportunityCandidate(OpportunityListResponse opportunity) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", opportunity.getId());
        map.put("name", opportunity.getName());
        map.put("customerId", opportunity.getCustomerId());
        map.put("customerName", opportunity.getCustomerName());
        map.put("amount", formatAmount(opportunity.getAmount()));
        map.put("amountText", formatAmount(opportunity.getAmount()));
        map.put("possible", formatAmount(opportunity.getPossible()));
        map.put("products", opportunity.getProducts());
        map.put("lastStage", opportunity.getLastStage());
        map.put("stage", opportunity.getStage());
        map.put("stageName", opportunity.getStageName());
        map.put("ownerId", opportunity.getOwner());
        map.put("ownerName", opportunity.getOwnerName());
        map.put("contactId", opportunity.getContactId());
        map.put("contactName", opportunity.getContactName());
        map.put("expectedEndTime", opportunity.getExpectedEndTime());
        map.put("actualEndTime", opportunity.getActualEndTime());
        map.put("failureReason", opportunity.getFailureReason());
        map.put("followTime", opportunity.getFollowTime());
        map.put("followerId", opportunity.getFollower());
        map.put("followerName", opportunity.getFollowerName());
        map.put("departmentId", opportunity.getDepartmentId());
        map.put("departmentName", opportunity.getDepartmentName());
        map.put("customFields", mapCustomFields(opportunity.getModuleFields()));
        return map;
    }

    private Map<String, Object> mapContact(CustomerContactListResponse contact) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", contact.getId());
        map.put("name", contact.getName());
        map.put("customerId", contact.getCustomerId());
        map.put("customerName", contact.getCustomerName());
        map.put("ownerId", contact.getOwner());
        map.put("ownerName", contact.getOwnerName());
        map.put("phone", contact.getPhone());
        return map;
    }

    private Map<String, Object> mapFollowRecord(FollowUpRecordListResponse record) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", record.getId());
        map.put("customerId", record.getCustomerId());
        map.put("customerName", record.getCustomerName());
        map.put("opportunityId", record.getOpportunityId());
        map.put("opportunityName", record.getOpportunityName());
        map.put("content", record.getContent());
        map.put("followTime", record.getFollowTime());
        map.put("followMethod", record.getFollowMethod());
        map.put("ownerId", record.getOwner());
        map.put("ownerName", record.getOwnerName());
        map.put("contactId", record.getContactId());
        map.put("contactName", record.getContactName());
        return map;
    }

    private Map<String, Object> mapFollowPlan(FollowUpPlanListResponse plan) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", plan.getId());
        map.put("customerId", plan.getCustomerId());
        map.put("customerName", plan.getCustomerName());
        map.put("opportunityId", plan.getOpportunityId());
        map.put("opportunityName", plan.getOpportunityName());
        map.put("content", plan.getContent());
        map.put("planTime", plan.getEstimatedTime());
        map.put("planMethod", plan.getMethod());
        map.put("status", plan.getStatus());
        map.put("ownerId", plan.getOwner());
        map.put("ownerName", plan.getOwnerName());
        map.put("contactId", plan.getContactId());
        map.put("contactName", plan.getContactName());
        return map;
    }

    private boolean matchesObject(String recordCustomerId, String recordOpportunityId, String customerId, String opportunityId) {
        if (StringUtils.isNotBlank(opportunityId)) {
            return Objects.equals(recordOpportunityId, opportunityId);
        }
        if (StringUtils.isNotBlank(customerId)) {
            return Objects.equals(recordCustomerId, customerId);
        }
        return true;
    }

    private String formatAmount(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private List<Map<String, Object>> mapCustomFields(List<BaseModuleFieldValue> fields) {
        return safeList(fields).stream()
                .filter(BaseModuleFieldValue::valid)
                .map(field -> {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("label", "自定义字段");
                    map.put("value", field.getFieldValue());
                    return map;
                })
                .toList();
    }

    private int attachmentFieldCount(Map<?, ?> attachmentMap) {
        return attachmentMap == null ? 0 : attachmentMap.size();
    }

    private <T> List<T> safeList(List<T> list) {
        return list == null ? Collections.emptyList() : list;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }
}
