package cn.cordys.crm.ai.dealdesk.service;

import cn.cordys.crm.ai.dealdesk.dto.DealDeskToolRequest;

import java.util.List;
import java.util.Map;

public interface AiDealDeskToolGateway {
    List<Map<String, Object>> searchCustomers(String keyword, int limit);

    List<Map<String, Object>> searchOpportunities(String keyword, int limit);

    Map<String, Object> getCustomer(String customerId);

    Map<String, Object> getOpportunity(String opportunityId);

    List<Map<String, Object>> listOpportunityContacts(String opportunityId);

    List<Map<String, Object>> listRecentFollowRecords(String customerId, String opportunityId, int limit);

    List<Map<String, Object>> listOpenFollowPlans(String customerId, String opportunityId, int limit);

    Map<String, Object> createFollowRecord(DealDeskToolRequest request);

    Map<String, Object> createFollowPlan(DealDeskToolRequest request);

    /**
     * 获取销售漏斗快照：按商机阶段聚合当前所有商机数量。
     * 返回 List&lt;{name: stageName, value: count}&gt;，按阶段序号排序。
     */
    List<Map<String, Object>> getFunnelSnapshot(String organizationId);

    /**
     * 获取客户 L2C 健康度：按客户维度计算线索→商机→赢单的转化链路断裂信号。
     * 返回 Map 包含 totalCustomers, customersWithOpportunity, customersWon, conversionRates, breakSignals 等。
     */
    Map<String, Object> getCustomerL2cHealth(String organizationId);

    /**
     * 获取合同收入概览：合同金额、回款、开票、待回款和逾期未回款汇总。
     */
    Map<String, Object> getContractRevenueSnapshot(String organizationId);
}
