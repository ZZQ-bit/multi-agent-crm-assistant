package cn.cordys.crm.agent;

import cn.cordys.common.response.handler.NoResultHolder;
import cn.cordys.crm.ai.dealdesk.controller.AiDealDeskToolController;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskToolRequest;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskToolResponse;
import cn.cordys.crm.ai.dealdesk.service.AiDealDeskToolGateway;
import cn.cordys.crm.ai.dealdesk.service.AiDealDeskToolService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiDealDeskToolServiceTest {

    @Test
    void shouldReturnToolEnvelopeWithoutGlobalResultHolderWrapping() throws NoSuchMethodException {
        for (String methodName : List.of(
                "searchCustomers",
                "searchOpportunities",
                "getCustomerContext",
                "getOpportunityContext",
                "createFollowRecord",
                "createFollowPlan")) {
            Method method = AiDealDeskToolController.class.getMethod(methodName, DealDeskToolRequest.class);
            assertTrue(method.isAnnotationPresent(NoResultHolder.class), methodName + " must keep the tool envelope as the HTTP response body");
        }
    }

    @Test
    void shouldSearchCustomersWithUnifiedEnvelope() {
        FakeGateway gateway = new FakeGateway();
        gateway.customers.add(mapOf("id", "customer-1", "name", "华东智造集团", "ownerName", "周雨晴"));
        AiDealDeskToolService service = new AiDealDeskToolService(gateway);

        DealDeskToolResponse response = service.searchCustomers(request("华东"));

        assertTrue(response.isSuccess());
        assertEquals("OK", response.getCode());
        assertEquals(1, ((List<?>) response.getData().get("candidates")).size());
    }

    @Test
    void shouldReturnCandidateListForMultipleCustomers() {
        FakeGateway gateway = new FakeGateway();
        gateway.customers.add(mapOf("id", "customer-1", "name", "华东智造集团"));
        gateway.customers.add(mapOf("id", "customer-2", "name", "华东智造集团华南分部"));
        AiDealDeskToolService service = new AiDealDeskToolService(gateway);

        DealDeskToolResponse response = service.searchCustomers(request("华东智造"));

        assertTrue(response.isSuccess());
        assertEquals("OK", response.getCode());
        assertEquals("candidate_list", response.getData().get("resultType"));
        assertEquals(2, response.getData().get("count"));
        assertEquals(2, ((List<?>) response.getData().get("candidates")).size());
    }

    @Test
    void shouldReturnCandidateListForMultipleOpportunities() {
        FakeGateway gateway = new FakeGateway();
        gateway.opportunities.add(mapOf("id", "opportunity-1", "name", "智造一期"));
        gateway.opportunities.add(mapOf("id", "opportunity-2", "name", "智造二期"));
        AiDealDeskToolService service = new AiDealDeskToolService(gateway);

        DealDeskToolResponse response = service.searchOpportunities(request("智造"));

        assertTrue(response.isSuccess());
        assertEquals("OK", response.getCode());
        assertEquals("candidate_list", response.getData().get("resultType"));
        assertEquals(2, response.getData().get("count"));
        assertEquals(2, ((List<?>) response.getData().get("candidates")).size());
    }

    @Test
    void shouldRetryOpportunitySearchWithCleanedBusinessQuestionKeyword() {
        FakeGateway gateway = new FakeGateway();
        gateway.opportunities.add(mapOf("id", "opportunity-1", "name", "华东智造集团AI客服升级项目"));
        gateway.filterOpportunitySearch = true;
        AiDealDeskToolService service = new AiDealDeskToolService(gateway);

        DealDeskToolResponse response = service.searchOpportunities(request("华东智造集团AI客服升级项目这个商机有什么风险"));

        assertTrue(response.isSuccess());
        assertEquals("OK", response.getCode());
        assertEquals(2, gateway.opportunitySearchKeywords.size());
        assertEquals("华东智造集团AI客服升级项目", gateway.opportunitySearchKeywords.get(1));
        assertEquals(1, ((List<?>) response.getData().get("candidates")).size());
    }

    @Test
    void shouldRetryOpportunitySearchWithPunctuatedCustomerAndDealKeyword() {
        FakeGateway gateway = new FakeGateway();
        gateway.opportunities.add(mapOf("id", "opportunity-1", "name", "华东智造集团AI客服升级项目"));
        gateway.filterOpportunitySearch = true;
        AiDealDeskToolService service = new AiDealDeskToolService(gateway);

        DealDeskToolResponse response = service.searchOpportunities(request("华东智造集团、AI客服升级项目"));

        assertTrue(response.isSuccess());
        assertEquals("OK", response.getCode());
        assertTrue(gateway.opportunitySearchKeywords.contains("华东智造集团AI客服升级项目"));
        assertEquals(1, ((List<?>) response.getData().get("candidates")).size());
    }

    @Test
    void shouldRetryOpportunitySearchWithNaturalReviewRequestKeyword() {
        FakeGateway gateway = new FakeGateway();
        gateway.opportunities.add(mapOf("id", "opportunity-1", "name", "华东智造集团AI客服升级项目"));
        gateway.filterOpportunitySearch = true;
        AiDealDeskToolService service = new AiDealDeskToolService(gateway);

        DealDeskToolResponse response = service.searchOpportunities(request("帮我评审华东智造集团AI客服升级项目，判断是否适合推进，并给出下一步建议。"));

        assertTrue(response.isSuccess());
        assertEquals("OK", response.getCode());
        assertTrue(gateway.opportunitySearchKeywords.contains("华东智造集团AI客服升级项目"));
        assertEquals(1, ((List<?>) response.getData().get("candidates")).size());
    }

    @Test
    void shouldReturnOpportunityContextWhenUniqueSearchRequestsContext() {
        FakeGateway gateway = new FakeGateway();
        gateway.opportunities.add(mapOf(
                "id", "opportunity-ctx",
                "name", "华东智造集团AI客服升级项目",
                "customerId", "customer-ctx",
                "customerName", "华东智造集团"
        ));
        gateway.filterOpportunitySearch = true;
        gateway.opportunity = mapOf(
                "id", "opportunity-ctx",
                "name", "华东智造集团AI客服升级项目",
                "customerId", "customer-ctx",
                "customerName", "华东智造集团",
                "amount", "880000"
        );
        gateway.customer = mapOf("id", "customer-ctx", "name", "华东智造集团");
        gateway.records.add(mapOf("id", "record-ctx", "content", "客户关注付款节点"));
        gateway.plans.add(mapOf("id", "plan-ctx", "content", "确认折扣审批和交付拆分"));
        AiDealDeskToolService service = new AiDealDeskToolService(gateway);
        DealDeskToolRequest request = request("华东智造集团AI客服升级项目");
        request.setIncludeContextOnUnique(true);

        DealDeskToolResponse response = service.searchOpportunities(request);

        assertTrue(response.isSuccess());
        assertNotNull(response.getData().get("opportunity"));
        assertNotNull(response.getData().get("businessFacts"));
        assertEquals("客户关注付款节点", ((Map<?, ?>) response.getData().get("businessFacts")).get("latestFollowRecord"));
        assertFalse(response.getData().containsKey("candidates"));
    }

    @Test
    void shouldReturnObjectNotFoundForEmptySearch() {
        AiDealDeskToolService service = new AiDealDeskToolService(new FakeGateway());

        DealDeskToolResponse response = service.searchCustomers(request("不存在"));

        assertFalse(response.isSuccess());
        assertEquals("OBJECT_NOT_FOUND", response.getCode());
    }

    @Test
    void shouldBuildOpportunityContextPackage() {
        FakeGateway gateway = new FakeGateway();
        gateway.opportunity = mapOf(
                "id", "opportunity-1",
                "name", "华东智造集团 AI 客服升级项目",
                "customerId", "customer-1",
                "customerName", "华东智造集团",
                "amount", new BigDecimal("880000")
        );
        gateway.customer = mapOf("id", "customer-1", "name", "华东智造集团");
        gateway.contacts.add(mapOf("id", "contact-1", "name", "张伟"));
        gateway.records.add(mapOf("id", "record-1", "content", "客户关注付款节点"));
        gateway.plans.add(mapOf("id", "plan-1", "content", "确认首付款比例", "status", "PENDING"));
        AiDealDeskToolService service = new AiDealDeskToolService(gateway);

        DealDeskToolRequest request = new DealDeskToolRequest();
        request.setOpportunityId("opportunity-1");
        DealDeskToolResponse response = service.getOpportunityContext(request);

        assertTrue(response.isSuccess());
        assertNotNull(response.getData().get("opportunity"));
        assertNotNull(response.getData().get("customer"));
        assertEquals(1, ((List<?>) response.getData().get("contacts")).size());
        assertEquals(1, ((List<?>) response.getData().get("recentFollowRecords")).size());
        assertEquals(1, ((List<?>) response.getData().get("openFollowPlans")).size());
        assertNotNull(response.getData().get("riskSignals"));
        assertNotNull(response.getData().get("missingFields"));
        assertNotNull(response.getData().get("sourceRefs"));
    }

    @Test
    void shouldExtractDealDeskContextFactsAndTerms() {
        FakeGateway gateway = new FakeGateway();
        gateway.opportunity = mapOf(
                "id", "opportunity-ctx",
                "name", "多Agent智能助手 项目",
                "customerId", "customer-ctx",
                "customerName", "华东智造集团",
                "amount", "880000",
                "possible", "45",
                "stageName", "方案评审",
                "products", List.of("AI 客服"),
                "contactName", "张伟",
                "expectedEndTime", 1782748800000L,
                "customFields", List.of(mapOf("label", "自定义字段", "value", "验收标准需要在上线前确认，合同包含违约赔付条款"))
        );
        gateway.customer = mapOf("id", "customer-ctx", "name", "华东智造集团", "ownerName", "周雨晴");
        gateway.contacts.add(mapOf("id", "contact-ctx", "name", "张伟"));
        gateway.records.add(mapOf("id", "record-ctx", "content", "客户要求付款账期延长，并希望争取折扣"));
        gateway.plans.add(mapOf("id", "plan-ctx", "content", "确认交付范围和下一里程碑", "status", "PENDING"));
        AiDealDeskToolService service = new AiDealDeskToolService(gateway);

        DealDeskToolRequest request = new DealDeskToolRequest();
        request.setOpportunityId("opportunity-ctx");
        DealDeskToolResponse response = service.getOpportunityContext(request);

        assertTrue(response.isSuccess());
        Map<?, ?> businessFacts = (Map<?, ?>) response.getData().get("businessFacts");
        assertEquals("方案评审", businessFacts.get("stageName"));
        assertEquals("客户要求付款账期延长，并希望争取折扣", businessFacts.get("latestFollowRecord"));
        Map<?, ?> dealTerms = (Map<?, ?>) response.getData().get("dealTerms");
        assertEquals("客户要求付款账期延长，并希望争取折扣", dealTerms.get("paymentTerms"));
        assertEquals("客户要求付款账期延长，并希望争取折扣", dealTerms.get("discountRequest"));
        assertEquals("确认交付范围和下一里程碑", dealTerms.get("deliveryDeadline"));
        assertEquals("验收标准需要在上线前确认，合同包含违约赔付条款", dealTerms.get("acceptanceCriteria"));
        assertEquals("验收标准需要在上线前确认，合同包含违约赔付条款", dealTerms.get("contractTerms"));
        assertTrue(((List<?>) response.getData().get("riskSignals")).stream().anyMatch(signal -> String.valueOf(signal).contains("赢率")));
        assertTrue(((List<?>) response.getData().get("riskSignals")).stream().anyMatch(signal -> String.valueOf(signal).contains("折扣")));
        assertTrue(((List<?>) response.getData().get("missingFields")).contains("decisionMakers"));
    }

    @Test
    void shouldValidateWritebackContent() {
        AiDealDeskToolService service = new AiDealDeskToolService(new FakeGateway());
        DealDeskToolRequest request = new DealDeskToolRequest();
        request.setOpportunityId("opportunity-1");
        request.setIdempotencyKey("writeback-1");

        DealDeskToolResponse response = service.createFollowRecord(request);

        assertFalse(response.isSuccess());
        assertEquals("WRITEBACK_VALIDATION_FAILED", response.getCode());
    }

    @Test
    void shouldCreateFollowRecordAndDeduplicateByIdempotencyKey() {
        FakeGateway gateway = new FakeGateway();
        gateway.opportunity = mapOf("id", "opportunity-1", "customerId", "customer-1", "ownerId", "user-1");
        AiDealDeskToolService service = new AiDealDeskToolService(gateway);
        DealDeskToolRequest request = writebackRequest();

        DealDeskToolResponse first = service.createFollowRecord(request);
        DealDeskToolResponse second = service.createFollowRecord(request);

        assertTrue(first.isSuccess());
        assertTrue(second.isSuccess());
        assertEquals(first.getData().get("recordId"), second.getData().get("recordId"));
        assertEquals(1, gateway.createdRecords);
    }

    @Test
    void shouldNormalizeFollowRecordMethodAndTimeToNativeFormValuesBeforeWriteback() {
        FakeGateway gateway = new FakeGateway();
        gateway.opportunity = mapOf("id", "opportunity-1", "customerId", "customer-1", "ownerId", "user-1");
        AiDealDeskToolService service = new AiDealDeskToolService(gateway);
        DealDeskToolRequest request = writebackRequest();
        request.setFollowMethod("OTHER");
        request.setFollowTime(null);

        DealDeskToolResponse response = service.createFollowRecord(request);

        assertTrue(response.isSuccess());
        assertEquals("2", gateway.lastRecordRequest.getFollowMethod());
        assertTrue(gateway.lastRecordRequest.getFollowTime() > 0);
    }

    @Test
    void shouldEnrichFollowRecordWritebackFromOpportunityWhenFieldsAreMissing() {
        FakeGateway gateway = new FakeGateway();
        gateway.opportunity = mapOf(
                "id", "opportunity-1",
                "customerId", "customer-1",
                "ownerId", "owner-1",
                "contactId", "contact-1"
        );
        AiDealDeskToolService service = new AiDealDeskToolService(gateway);
        DealDeskToolRequest request = writebackRequest();
        request.setCustomerId(null);
        request.setOwnerId(null);
        request.setContactId(null);

        DealDeskToolResponse response = service.createFollowRecord(request);

        assertTrue(response.isSuccess());
        assertEquals("customer-1", gateway.lastRecordRequest.getCustomerId());
        assertEquals("owner-1", gateway.lastRecordRequest.getOwnerId());
        assertEquals("contact-1", gateway.lastRecordRequest.getContactId());
    }

    @Test
    void shouldCreateFollowPlanAndDeduplicateByIdempotencyKey() {
        FakeGateway gateway = new FakeGateway();
        gateway.opportunity = mapOf("id", "opportunity-1", "customerId", "customer-1", "ownerId", "user-1");
        AiDealDeskToolService service = new AiDealDeskToolService(gateway);
        DealDeskToolRequest request = writebackRequest();

        DealDeskToolResponse first = service.createFollowPlan(request);
        DealDeskToolResponse second = service.createFollowPlan(request);

        assertTrue(first.isSuccess());
        assertTrue(second.isSuccess());
        assertEquals(first.getData().get("planId"), second.getData().get("planId"));
        assertEquals(1, gateway.createdPlans);
    }

    @Test
    void shouldNormalizeFollowPlanMethodAndTimeToNativeFormValuesBeforeWriteback() {
        FakeGateway gateway = new FakeGateway();
        gateway.opportunity = mapOf("id", "opportunity-1", "customerId", "customer-1", "ownerId", "user-1");
        AiDealDeskToolService service = new AiDealDeskToolService(gateway);
        DealDeskToolRequest request = writebackRequest();
        request.setPlanMethod("PHONE");
        request.setPlanTime(null);

        DealDeskToolResponse response = service.createFollowPlan(request);

        assertTrue(response.isSuccess());
        assertEquals("2", gateway.lastPlanRequest.getPlanMethod());
        assertTrue(gateway.lastPlanRequest.getPlanTime() > 0);
    }

    @Test
    void shouldEnrichFollowPlanWritebackFromOpportunityWhenFieldsAreMissing() {
        FakeGateway gateway = new FakeGateway();
        gateway.opportunity = mapOf(
                "id", "opportunity-1",
                "customerId", "customer-1",
                "ownerId", "owner-1",
                "contactId", "contact-1"
        );
        AiDealDeskToolService service = new AiDealDeskToolService(gateway);
        DealDeskToolRequest request = writebackRequest();
        request.setCustomerId(null);
        request.setOwnerId(null);
        request.setContactId(null);

        DealDeskToolResponse response = service.createFollowPlan(request);

        assertTrue(response.isSuccess());
        assertEquals("customer-1", gateway.lastPlanRequest.getCustomerId());
        assertEquals("owner-1", gateway.lastPlanRequest.getOwnerId());
        assertEquals("contact-1", gateway.lastPlanRequest.getContactId());
    }

    @Test
    void shouldSortFunnelSnapshotIntoStableDealDeskStageOrder() {
        FakeGateway gateway = new FakeGateway();
        gateway.funnelSnapshot.add(mapOf("name", "\u5546\u52a1\u91c7\u8d2d", "value", 19L));
        gateway.funnelSnapshot.add(mapOf("name", "\u65b0\u5efa", "value", 13L));
        gateway.funnelSnapshot.add(mapOf("name", "\u7b7e\u7ea6\u6210\u529f", "value", 6L));
        gateway.funnelSnapshot.add(mapOf("name", "\u7acb\u9879\u6c47\u62a5", "value", 14L));
        gateway.funnelSnapshot.add(mapOf("name", "\u65b9\u6848\u9a8c\u8bc1", "value", 9L));
        gateway.funnelSnapshot.add(mapOf("name", "\u5931\u8d25", "value", 1L));
        gateway.funnelSnapshot.add(mapOf("name", "\u9700\u6c42\u660e\u786e", "value", 8L));
        AiDealDeskToolService service = new AiDealDeskToolService(gateway);

        DealDeskToolResponse response = service.getFunnelSnapshot(new DealDeskToolRequest());

        assertTrue(response.isSuccess());
        List<?> stages = (List<?>) response.getData().get("stages");
        assertEquals(
                List.of(
                        "\u65b0\u5efa",
                        "\u9700\u6c42\u660e\u786e",
                        "\u65b9\u6848\u9a8c\u8bc1",
                        "\u7acb\u9879\u6c47\u62a5",
                        "\u5546\u52a1\u91c7\u8d2d",
                        "\u7b7e\u7ea6\u6210\u529f",
                        "\u5931\u8d25"
                ),
                stages.stream().map(item -> String.valueOf(((Map<?, ?>) item).get("name"))).toList()
        );
        Map<?, ?> chart = (Map<?, ?>) response.getData().get("chart");
        assertEquals(stages, chart.get("data"));
    }

    @Test
    void shouldExposeOnlyRateMetricsOnL2cChartXAxis() {
        FakeGateway gateway = new FakeGateway();
        gateway.customerL2cHealth = mapOf(
                "customerConversionRate", 20L,
                "opportunityWinRate", 9L,
                "metrics", List.of(
                        mapOf("name", "有商机客户数", "value", 30L),
                        mapOf("name", "赢单客户数", "value", 6L),
                        mapOf("name", "商机赢单率(%)", "value", 9L),
                        mapOf("name", "客户转化率(%)", "value", 20L)
                )
        );
        AiDealDeskToolService service = new AiDealDeskToolService(gateway);

        DealDeskToolResponse response = service.getCustomerL2cHealth(new DealDeskToolRequest());

        assertTrue(response.isSuccess());
        Map<?, ?> chart = (Map<?, ?>) response.getData().get("chart");
        assertEquals(
                List.of("商机赢单率(%)", "客户转化率(%)"),
                chart.get("xData")
        );
        assertEquals(
                List.of(9L, 20L),
                ((List<?>) chart.get("data")).stream()
                        .map(item -> ((Map<?, ?>) item).get("value"))
                        .toList()
        );
    }

    private static DealDeskToolRequest request(String keyword) {
        DealDeskToolRequest request = new DealDeskToolRequest();
        request.setKeyword(keyword);
        return request;
    }

    private static DealDeskToolRequest writebackRequest() {
        DealDeskToolRequest request = new DealDeskToolRequest();
        request.setOpportunityId("opportunity-1");
        request.setContent("确认首付款比例和验收范围");
        request.setFollowMethod("PHONE");
        request.setIdempotencyKey("writeback-1");
        return request;
    }

    private static Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(String.valueOf(values[i]), values[i + 1]);
        }
        return map;
    }

    private static class FakeGateway implements AiDealDeskToolGateway {
        private final List<Map<String, Object>> customers = new ArrayList<>();
        private final List<Map<String, Object>> opportunities = new ArrayList<>();
        private final List<Map<String, Object>> contacts = new ArrayList<>();
        private final List<Map<String, Object>> records = new ArrayList<>();
        private final List<Map<String, Object>> plans = new ArrayList<>();
        private final List<Map<String, Object>> funnelSnapshot = new ArrayList<>();
        private Map<String, Object> customerL2cHealth = new HashMap<>();
        private Map<String, Object> contractRevenueSnapshot = new HashMap<>();
        private final List<String> opportunitySearchKeywords = new ArrayList<>();
        private Map<String, Object> customer = mapOf("id", "customer-1");
        private Map<String, Object> opportunity = new HashMap<>();
        private boolean filterOpportunitySearch;
        private int createdRecords;
        private int createdPlans;
        private DealDeskToolRequest lastRecordRequest;
        private DealDeskToolRequest lastPlanRequest;

        @Override
        public List<Map<String, Object>> searchCustomers(String keyword, int limit) {
            return customers;
        }

        @Override
        public List<Map<String, Object>> searchOpportunities(String keyword, int limit) {
            opportunitySearchKeywords.add(keyword);
            if (filterOpportunitySearch) {
                return opportunities.stream()
                        .filter(opportunity -> String.valueOf(opportunity.get("name")).contains(keyword))
                        .toList();
            }
            return opportunities;
        }

        @Override
        public Map<String, Object> getCustomer(String customerId) {
            return customer;
        }

        @Override
        public Map<String, Object> getOpportunity(String opportunityId) {
            return opportunity;
        }

        @Override
        public List<Map<String, Object>> listOpportunityContacts(String opportunityId) {
            return contacts;
        }

        @Override
        public List<Map<String, Object>> listRecentFollowRecords(String customerId, String opportunityId, int limit) {
            return records;
        }

        @Override
        public List<Map<String, Object>> listOpenFollowPlans(String customerId, String opportunityId, int limit) {
            return plans;
        }

        @Override
        public Map<String, Object> createFollowRecord(DealDeskToolRequest request) {
            createdRecords += 1;
            lastRecordRequest = request;
            return mapOf("recordId", "record-" + createdRecords);
        }

        @Override
        public Map<String, Object> createFollowPlan(DealDeskToolRequest request) {
            createdPlans += 1;
            lastPlanRequest = request;
            return mapOf("planId", "plan-" + createdPlans);
        }

        @Override
        public List<Map<String, Object>> getFunnelSnapshot(String organizationId) {
            return funnelSnapshot;
        }

        @Override
        public Map<String, Object> getCustomerL2cHealth(String organizationId) {
            return customerL2cHealth;
        }

        @Override
        public Map<String, Object> getContractRevenueSnapshot(String organizationId) {
            return contractRevenueSnapshot;
        }
    }
}
