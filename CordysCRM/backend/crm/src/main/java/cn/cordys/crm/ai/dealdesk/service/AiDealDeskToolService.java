package cn.cordys.crm.ai.dealdesk.service;

import cn.cordys.crm.ai.dealdesk.dto.DealDeskToolRequest;
import cn.cordys.crm.ai.dealdesk.dto.DealDeskToolResponse;
import jakarta.annotation.Resource;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class AiDealDeskToolService {
    private static final int DEFAULT_SEARCH_LIMIT = 10;
    private static final int DEFAULT_CONTEXT_LIMIT = 5;
    private static final String FOLLOW_METHOD_VISIT = "1";
    private static final String FOLLOW_METHOD_PHONE = "2";
    private static final List<String> OPPORTUNITY_QUERY_SUFFIXES = List.of(
            "这个商机有什么风险",
            "这个项目有什么风险",
            "该商机有什么风险",
            "该项目有什么风险",
            "这个商机付款风险高吗",
            "这个项目付款风险高吗",
            "有什么风险",
            "有哪些风险",
            "风险高吗",
            "付款风险高吗",
            "帮我评审一下",
            "评审一下",
            "做一下评审",
            "分析一下",
            "商机",
            "项目"
    );

    private final Map<String, DealDeskToolResponse> idempotentWritebacks = new ConcurrentHashMap<>();

    @Resource
    private AiDealDeskToolGateway gateway;

    public AiDealDeskToolService() {
    }

    public AiDealDeskToolService(AiDealDeskToolGateway gateway) {
        this.gateway = gateway;
    }

    public DealDeskToolResponse searchCustomers(DealDeskToolRequest request) {
        try {
            String keyword = normalizeKeyword(request);
            if (StringUtils.isBlank(keyword)) {
                return DealDeskToolResponse.fail("INVALID_ARGUMENT", "请输入客户关键词。");
            }
            List<Map<String, Object>> candidates = gateway.searchCustomers(keyword, resolveLimit(request));
            DealDeskToolResponse response = buildCandidateResponse(candidates, "未找到匹配客户。", "找到多个匹配客户，请先选择。");
            if (shouldIncludeContextOnUnique(request, response)) {
                DealDeskToolRequest contextRequest = new DealDeskToolRequest();
                contextRequest.setCustomerId(asText(firstCandidate(response).get("id")));
                contextRequest.setLimit(request.getLimit());
                return getCustomerContext(contextRequest);
            }
            return response;
        } catch (RuntimeException e) {
            return failureFromException(e, "CRM_READ_FAILED", "客户查询失败。");
        }
    }

    public DealDeskToolResponse searchOpportunities(DealDeskToolRequest request) {
        try {
            String keyword = normalizeKeyword(request);
            if (StringUtils.isBlank(keyword)) {
                return DealDeskToolResponse.fail("INVALID_ARGUMENT", "请输入商机关键词。");
            }
            List<Map<String, Object>> candidates = searchOpportunitiesWithFallback(keyword, resolveLimit(request));
            DealDeskToolResponse response = buildCandidateResponse(candidates, "未找到匹配商机。", "找到多个匹配商机，请先选择。");
            if (shouldIncludeContextOnUnique(request, response)) {
                Map<String, Object> candidate = firstCandidate(response);
                DealDeskToolRequest contextRequest = new DealDeskToolRequest();
                contextRequest.setOpportunityId(asText(candidate.get("id")));
                contextRequest.setCustomerId(asText(candidate.get("customerId")));
                contextRequest.setLimit(request.getLimit());
                return getOpportunityContext(contextRequest);
            }
            return response;
        } catch (RuntimeException e) {
            return failureFromException(e, "CRM_READ_FAILED", "商机查询失败。");
        }
    }

    private List<Map<String, Object>> searchOpportunitiesWithFallback(String keyword, int limit) {
        List<Map<String, Object>> candidates = gateway.searchOpportunities(keyword, limit);
        if (!candidates.isEmpty()) {
            return candidates;
        }
        for (String fallbackKeyword : opportunitySearchFallbackKeywords(keyword)) {
            candidates = gateway.searchOpportunities(fallbackKeyword, limit);
            if (!candidates.isEmpty()) {
                return candidates;
            }
        }
        return candidates;
    }

    private List<String> opportunitySearchFallbackKeywords(String keyword) {
        String normalized = normalizeOpportunitySearchText(keyword);
        if (StringUtils.isBlank(normalized)) {
            return Collections.emptyList();
        }
        Set<String> keywords = new LinkedHashSet<>();
        keywords.add(normalized);

        for (String suffix : OPPORTUNITY_QUERY_SUFFIXES) {
            if (normalized.endsWith(suffix) && normalized.length() > suffix.length()) {
                String candidate = normalized.substring(0, normalized.length() - suffix.length());
                if (StringUtils.isNotBlank(candidate)) {
                    keywords.add(candidate);
                }
            }
        }

        String withoutQuestionTail = trimOpportunityQuestionTail(normalized);
        if (StringUtils.isNotBlank(withoutQuestionTail)) {
            keywords.add(withoutQuestionTail);
            keywords.add(stripOpportunityCommandPrefix(withoutQuestionTail));
        }
        keywords.add(stripOpportunityCommandPrefix(normalized));

        return keywords.stream()
                .filter(value -> value.length() >= 2)
                .filter(value -> !StringUtils.equals(value, keyword))
                .toList();
    }

    private String normalizeOpportunitySearchText(String keyword) {
        return StringUtils.defaultString(keyword)
                .replaceAll("[\\s\\p{Z}\\u3000,，、;；:：\"“”'‘’《》<>（）()\\[\\]【】]+", "");
    }

    private String trimOpportunityQuestionTail(String keyword) {
        String value = StringUtils.defaultString(keyword);
        for (String marker : List.of("判断是否", "是否适合", "并给出", "给出下一步", "下一步建议", "有什么风险", "有哪些风险", "风险", "评审", "分析", "付款", "合同", "交付", "销售", "财务")) {
            int index = value.indexOf(marker);
            if (index > 0) {
                return value.substring(0, index);
            }
        }
        return value;
    }

    private String stripOpportunityCommandPrefix(String keyword) {
        return StringUtils.defaultString(keyword)
                .replaceFirst("^(帮我|请帮我|麻烦|请)?(评审|评估|分析|看一下|看看|判断|复核|review|assess|evaluate)", "");
    }

    public DealDeskToolResponse getCustomerContext(DealDeskToolRequest request) {
        try {
            if (request == null || StringUtils.isBlank(request.getCustomerId())) {
                return DealDeskToolResponse.fail("INVALID_ARGUMENT", "缺少客户 ID。");
            }
            Map<String, Object> customer = gateway.getCustomer(request.getCustomerId());
            if (customer == null || customer.isEmpty()) {
                return DealDeskToolResponse.fail("OBJECT_NOT_FOUND", "未找到指定客户。");
            }
            List<Map<String, Object>> recentFollowRecords = gateway.listRecentFollowRecords(request.getCustomerId(), null, DEFAULT_CONTEXT_LIMIT);
            List<Map<String, Object>> openFollowPlans = gateway.listOpenFollowPlans(request.getCustomerId(), null, DEFAULT_CONTEXT_LIMIT);
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("customer", customer);
            data.put("recentFollowRecords", recentFollowRecords);
            data.put("openFollowPlans", openFollowPlans);
            data.put("businessFacts", buildCustomerBusinessFacts(customer, recentFollowRecords, openFollowPlans));
            data.put("riskSignals", buildCustomerRiskSignals(customer, recentFollowRecords, openFollowPlans));
            data.put("missingFields", buildCustomerMissingFields(customer));
            data.put("sourceRefs", List.of("customer:" + request.getCustomerId()));
            return DealDeskToolResponse.ok(data);
        } catch (RuntimeException e) {
            return failureFromException(e, "CRM_READ_FAILED", "客户上下文读取失败。");
        }
    }

    public DealDeskToolResponse getOpportunityContext(DealDeskToolRequest request) {
        try {
            if (request == null || StringUtils.isBlank(request.getOpportunityId())) {
                return DealDeskToolResponse.fail("INVALID_ARGUMENT", "缺少商机 ID。");
            }
            Map<String, Object> opportunity = gateway.getOpportunity(request.getOpportunityId());
            if (opportunity == null || opportunity.isEmpty()) {
                return DealDeskToolResponse.fail("OBJECT_NOT_FOUND", "未找到指定商机。");
            }

            String customerId = firstNonBlank(asText(opportunity.get("customerId")), request.getCustomerId());
            Map<String, Object> customer = StringUtils.isBlank(customerId) ? Collections.emptyMap() : gateway.getCustomer(customerId);
            List<Map<String, Object>> contacts = gateway.listOpportunityContacts(request.getOpportunityId());
            List<Map<String, Object>> recentFollowRecords = gateway.listRecentFollowRecords(customerId, request.getOpportunityId(), DEFAULT_CONTEXT_LIMIT);
            List<Map<String, Object>> openFollowPlans = gateway.listOpenFollowPlans(customerId, request.getOpportunityId(), DEFAULT_CONTEXT_LIMIT);
            Map<String, Object> dealTerms = buildDealTerms(opportunity, customer, recentFollowRecords, openFollowPlans);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("opportunity", opportunity);
            data.put("customer", customer);
            data.put("contacts", contacts);
            data.put("recentFollowRecords", recentFollowRecords);
            data.put("openFollowPlans", openFollowPlans);
            data.put("businessFacts", buildOpportunityBusinessFacts(opportunity, customer, contacts, recentFollowRecords, openFollowPlans));
            data.put("dealTerms", dealTerms);
            data.put("riskSignals", buildOpportunityRiskSignals(opportunity, recentFollowRecords, openFollowPlans, dealTerms));
            data.put("missingFields", buildOpportunityMissingFields(opportunity, dealTerms));
            data.put("sourceRefs", buildSourceRefs(customerId, request.getOpportunityId()));
            return DealDeskToolResponse.ok(data);
        } catch (RuntimeException e) {
            return failureFromException(e, "CRM_READ_FAILED", "商机上下文读取失败。");
        }
    }

    public DealDeskToolResponse createFollowRecord(DealDeskToolRequest request) {
        try {
            enrichWritebackFromOpportunity(request);
            DealDeskToolResponse validation = validateWriteback(request);
            if (!validation.isSuccess()) {
                return validation;
            }
            normalizeFollowRecordWriteback(request);
            String key = idempotencyKey("follow_record", request);
            DealDeskToolResponse cached = idempotentWritebacks.get(key);
            if (cached != null) {
                return cached;
            }
            DealDeskToolResponse response = DealDeskToolResponse.ok(gateway.createFollowRecord(request));
            idempotentWritebacks.put(key, response);
            return response;
        } catch (RuntimeException e) {
            return failureFromException(e, "WRITEBACK_FAILED", "跟进记录写入失败。");
        }
    }

    public DealDeskToolResponse createFollowPlan(DealDeskToolRequest request) {
        try {
            enrichWritebackFromOpportunity(request);
            DealDeskToolResponse validation = validateWriteback(request);
            if (!validation.isSuccess()) {
                return validation;
            }
            normalizeFollowPlanWriteback(request);
            String key = idempotencyKey("follow_plan", request);
            DealDeskToolResponse cached = idempotentWritebacks.get(key);
            if (cached != null) {
                return cached;
            }
            DealDeskToolResponse response = DealDeskToolResponse.ok(gateway.createFollowPlan(request));
            idempotentWritebacks.put(key, response);
            return response;
        } catch (RuntimeException e) {
            return failureFromException(e, "WRITEBACK_FAILED", "跟进计划写入失败。");
        }
    }

    private DealDeskToolResponse buildCandidateResponse(List<Map<String, Object>> candidates, String emptyMessage, String ambiguousMessage) {
        List<Map<String, Object>> safeCandidates = candidates == null ? Collections.emptyList() : candidates;
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resultType", "candidate_list");
        data.put("count", safeCandidates.size());
        data.put("candidates", safeCandidates);
        if (safeCandidates.isEmpty()) {
            return DealDeskToolResponse.fail("OBJECT_NOT_FOUND", emptyMessage, data);
        }
        return DealDeskToolResponse.ok(data);
    }

    private boolean shouldIncludeContextOnUnique(DealDeskToolRequest request, DealDeskToolResponse response) {
        return request != null
                && Boolean.TRUE.equals(request.getIncludeContextOnUnique())
                && response != null
                && response.isSuccess()
                && firstCandidate(response).containsKey("id");
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> firstCandidate(DealDeskToolResponse response) {
        if (response == null || response.getData() == null) {
            return Collections.emptyMap();
        }
        Object candidates = response.getData().get("candidates");
        if (!(candidates instanceof List<?> list) || list.size() != 1 || !(list.get(0) instanceof Map<?, ?> map)) {
            return Collections.emptyMap();
        }
        return (Map<String, Object>) map;
    }

    private DealDeskToolResponse validateWriteback(DealDeskToolRequest request) {
        if (request == null) {
            return DealDeskToolResponse.fail("WRITEBACK_VALIDATION_FAILED", "缺少写回内容。");
        }
        if (StringUtils.isBlank(request.getOpportunityId()) && StringUtils.isBlank(request.getCustomerId())) {
            return DealDeskToolResponse.fail("WRITEBACK_VALIDATION_FAILED", "写回前必须确认唯一客户或商机。");
        }
        if (StringUtils.isBlank(request.getContent())) {
            return DealDeskToolResponse.fail("WRITEBACK_VALIDATION_FAILED", "写回内容不能为空。");
        }
        if (StringUtils.isBlank(request.getIdempotencyKey())) {
            return DealDeskToolResponse.fail("WRITEBACK_VALIDATION_FAILED", "缺少写回幂等键。");
        }
        if (StringUtils.isNotBlank(request.getOpportunityId())) {
            Map<String, Object> opportunity = gateway.getOpportunity(request.getOpportunityId());
            if (opportunity == null || opportunity.isEmpty()) {
                return DealDeskToolResponse.fail("OBJECT_NOT_FOUND", "未找到待写入商机。");
            }
        }
        if (StringUtils.isNotBlank(request.getCustomerId())) {
            Map<String, Object> customer = gateway.getCustomer(request.getCustomerId());
            if (customer == null || customer.isEmpty()) {
                return DealDeskToolResponse.fail("OBJECT_NOT_FOUND", "未找到待写入客户。");
            }
        }
        return DealDeskToolResponse.ok(Collections.emptyMap());
    }

    private void normalizeFollowRecordWriteback(DealDeskToolRequest request) {
        request.setFollowMethod(normalizeFollowMethod(request.getFollowMethod()));
        if (request.getFollowTime() == null) {
            request.setFollowTime(System.currentTimeMillis());
        }
    }

    private void normalizeFollowPlanWriteback(DealDeskToolRequest request) {
        request.setPlanMethod(normalizeFollowMethod(firstNonBlank(request.getPlanMethod(), request.getFollowMethod())));
        if (request.getPlanTime() == null) {
            request.setPlanTime(System.currentTimeMillis());
        }
    }

    private String normalizeFollowMethod(String method) {
        String value = StringUtils.trimToEmpty(method);
        if (FOLLOW_METHOD_VISIT.equals(value)
                || StringUtils.equalsIgnoreCase(value, "VISIT")
                || value.contains("到访")) {
            return FOLLOW_METHOD_VISIT;
        }
        return FOLLOW_METHOD_PHONE;
    }

    private void enrichWritebackFromOpportunity(DealDeskToolRequest request) {
        if (request == null || StringUtils.isBlank(request.getOpportunityId())) {
            return;
        }
        Map<String, Object> opportunity = gateway.getOpportunity(request.getOpportunityId());
        if (opportunity == null || opportunity.isEmpty()) {
            return;
        }
        if (StringUtils.isBlank(request.getCustomerId())) {
            request.setCustomerId(asText(opportunity.get("customerId")));
        }
        if (StringUtils.isBlank(request.getOwnerId())) {
            request.setOwnerId(firstNonBlank(asText(opportunity.get("ownerId")), asText(opportunity.get("owner"))));
        }
        if (StringUtils.isBlank(request.getContactId())) {
            request.setContactId(asText(opportunity.get("contactId")));
        }
    }

    private List<String> buildRiskSignals(Map<String, Object> opportunity) {
        List<String> signals = new ArrayList<>();
        String stageName = firstNonBlank(asText(opportunity.get("stageName")), asText(opportunity.get("stage")));
        if (StringUtils.isNotBlank(stageName)) {
            signals.add("当前商机阶段：" + stageName);
        }
        String amount = asText(opportunity.get("amount"));
        if (StringUtils.isNotBlank(amount)) {
            signals.add("当前商机金额：" + amount);
        }
        return signals;
    }

    private List<String> buildMissingFields(Map<String, Object> opportunity) {
        List<String> missing = new ArrayList<>();
        if (StringUtils.isBlank(asText(opportunity.get("customerId")))) {
            missing.add("customerId");
        }
        if (StringUtils.isBlank(asText(opportunity.get("ownerId"))) && StringUtils.isBlank(asText(opportunity.get("owner")))) {
            missing.add("ownerId");
        }
        return missing;
    }

    private Map<String, Object> buildCustomerBusinessFacts(
            Map<String, Object> customer,
            List<Map<String, Object>> recentFollowRecords,
            List<Map<String, Object>> openFollowPlans
    ) {
        Map<String, Object> facts = new LinkedHashMap<>();
        putIfPresent(facts, "customerName", customer.get("name"));
        putIfPresent(facts, "ownerName", customer.get("ownerName"));
        putIfPresent(facts, "departmentName", customer.get("departmentName"));
        putIfPresent(facts, "followerName", customer.get("followerName"));
        putIfPresent(facts, "lastFollowTime", customer.get("followTime"));
        putIfPresent(facts, "latestFollowRecord", firstContent(recentFollowRecords));
        putIfPresent(facts, "nextOpenPlan", firstContent(openFollowPlans));
        facts.put("openPlanCount", safeSize(openFollowPlans));
        putIfPresent(facts, "customFieldFacts", customFieldValues(customer));
        return facts;
    }

    private Map<String, Object> buildOpportunityBusinessFacts(
            Map<String, Object> opportunity,
            Map<String, Object> customer,
            List<Map<String, Object>> contacts,
            List<Map<String, Object>> recentFollowRecords,
            List<Map<String, Object>> openFollowPlans
    ) {
        Map<String, Object> facts = new LinkedHashMap<>();
        putIfPresent(facts, "opportunityName", opportunity.get("name"));
        putIfPresent(facts, "customerName", firstNonBlank(asText(opportunity.get("customerName")), asText(customer.get("name"))));
        putIfPresent(facts, "stageName", firstNonBlank(asText(opportunity.get("stageName")), asText(opportunity.get("stage"))));
        putIfPresent(facts, "amount", firstNonBlank(asText(opportunity.get("amount")), asText(opportunity.get("amountText"))));
        putIfPresent(facts, "winRate", opportunity.get("possible"));
        putIfPresent(facts, "products", opportunity.get("products"));
        putIfPresent(facts, "ownerName", opportunity.get("ownerName"));
        putIfPresent(facts, "primaryContact", firstNonBlank(asText(opportunity.get("contactName")), firstName(contacts)));
        putIfPresent(facts, "expectedEndTime", opportunity.get("expectedEndTime"));
        putIfPresent(facts, "lastFollowTime", opportunity.get("followTime"));
        putIfPresent(facts, "latestFollowRecord", firstContent(recentFollowRecords));
        putIfPresent(facts, "nextOpenPlan", firstContent(openFollowPlans));
        facts.put("contactCount", safeSize(contacts));
        facts.put("openPlanCount", safeSize(openFollowPlans));
        putIfPresent(facts, "opportunityCustomFieldFacts", customFieldValues(opportunity));
        putIfPresent(facts, "customerCustomFieldFacts", customFieldValues(customer));
        return facts;
    }

    private Map<String, Object> buildDealTerms(
            Map<String, Object> opportunity,
            Map<String, Object> customer,
            List<Map<String, Object>> recentFollowRecords,
            List<Map<String, Object>> openFollowPlans
    ) {
        List<String> opportunityTexts = new ArrayList<>();
        List<String> customerTexts = new ArrayList<>();
        List<String> recordTexts = new ArrayList<>();
        List<String> planTexts = new ArrayList<>();
        collectContextTexts(opportunityTexts, opportunity);
        collectContextTexts(customerTexts, customer);
        safeList(recentFollowRecords).forEach(record -> collectContextTexts(recordTexts, record));
        safeList(openFollowPlans).forEach(plan -> collectContextTexts(planTexts, plan));

        List<String> texts = new ArrayList<>();
        texts.addAll(opportunityTexts);
        texts.addAll(customerTexts);
        texts.addAll(recordTexts);
        texts.addAll(planTexts);

        Map<String, Object> terms = new LinkedHashMap<>();
        putIfPresent(terms, "paymentTerms", firstMatchingText(texts, "付款", "回款", "账期", "首付", "尾款"));
        putIfPresent(terms, "discountRequest", firstMatchingText(texts, "折扣", "报价", "价格", "优惠"));
        putIfPresent(terms, "deliveryDeadline", firstNonBlank(
                firstMatchingText(planTexts, "交付", "上线", "实施", "定制", "范围"),
                firstMatchingText(texts, "交付", "上线", "实施", "定制", "范围")
        ));
        putIfPresent(terms, "acceptanceCriteria", firstMatchingText(texts, "验收", "验收标准"));
        putIfPresent(terms, "contractTerms", firstMatchingText(texts, "合同", "条款", "赔付", "违约", "法务"));
        putIfPresent(terms, "decisionMakers", firstMatchingText(texts, "决策", "审批", "采购", "负责人"));
        putIfPresent(terms, "competitors", firstMatchingText(texts, "竞品", "竞争", "比价"));
        putIfPresent(terms, "budgetStatus", firstMatchingText(texts, "预算"));
        putIfPresent(terms, "nextMilestone", firstContent(openFollowPlans));
        return terms;
    }

    private List<String> buildCustomerRiskSignals(
            Map<String, Object> customer,
            List<Map<String, Object>> recentFollowRecords,
            List<Map<String, Object>> openFollowPlans
    ) {
        List<String> signals = new ArrayList<>();
        if (StringUtils.isBlank(asText(customer.get("ownerId"))) && StringUtils.isBlank(asText(customer.get("ownerName")))) {
            signals.add("客户负责人缺失，后续跟进责任不清。");
        }
        if (StringUtils.isBlank(asText(customer.get("followTime"))) && safeSize(recentFollowRecords) == 0) {
            signals.add("缺少最近跟进记录，客户活跃度需要重新确认。");
        }
        if (safeSize(openFollowPlans) > 0) {
            signals.add("存在未完成跟进计划：" + safeSize(openFollowPlans) + " 项。");
        }
        String recyclePoolName = asText(customer.get("recyclePoolName"));
        if (StringUtils.isNotBlank(recyclePoolName)) {
            signals.add("客户可能涉及公海或回收池规则：" + recyclePoolName);
        }
        return signals;
    }

    private List<String> buildCustomerMissingFields(Map<String, Object> customer) {
        List<String> missing = new ArrayList<>();
        if (StringUtils.isBlank(asText(customer.get("ownerId"))) && StringUtils.isBlank(asText(customer.get("ownerName")))) {
            missing.add("owner");
        }
        if (StringUtils.isBlank(asText(customer.get("followTime")))) {
            missing.add("lastFollowTime");
        }
        return missing;
    }

    private List<String> buildOpportunityRiskSignals(
            Map<String, Object> opportunity,
            List<Map<String, Object>> recentFollowRecords,
            List<Map<String, Object>> openFollowPlans,
            Map<String, Object> dealTerms
    ) {
        List<String> signals = new ArrayList<>();
        String stageName = firstNonBlank(asText(opportunity.get("stageName")), asText(opportunity.get("stage")));
        if (StringUtils.isNotBlank(stageName)) {
            signals.add("当前商机阶段：" + stageName);
        }
        String amount = firstNonBlank(asText(opportunity.get("amount")), asText(opportunity.get("amountText")));
        if (StringUtils.isNotBlank(amount)) {
            signals.add("当前商机金额：" + amount);
        }
        BigDecimal possible = asDecimal(opportunity.get("possible"));
        if (possible != null && possible.compareTo(new BigDecimal("50")) < 0) {
            signals.add("赢率低于 50%，需要补充关键推进证据。");
        }
        if (StringUtils.isNotBlank(asText(dealTerms.get("discountRequest")))) {
            signals.add("存在折扣或价格诉求，需要销售与财务共同评估毛利影响。");
        }
        if (StringUtils.isNotBlank(asText(dealTerms.get("paymentTerms")))) {
            signals.add("付款条件已被提及，需要确认账期、首付款和回款节点。");
        }
        if (StringUtils.isNotBlank(asText(dealTerms.get("deliveryDeadline")))) {
            signals.add("交付期限或实施范围已被提及，需要交付侧确认资源与边界。");
        }
        if (StringUtils.isNotBlank(asText(dealTerms.get("acceptanceCriteria")))) {
            signals.add("验收口径已被提及，需要明确可验收标准和责任边界。");
        }
        if (StringUtils.isNotBlank(asText(dealTerms.get("contractTerms")))) {
            signals.add("合同或法务条款已被提及，需要关注违约、赔付和特殊条款。");
        }
        if (StringUtils.isBlank(asText(opportunity.get("contactId"))) && StringUtils.isBlank(asText(opportunity.get("contactName")))) {
            signals.add("商机缺少主联系人，决策链和推进人需要补齐。");
        }
        if (StringUtils.isBlank(asText(opportunity.get("followTime"))) && safeSize(recentFollowRecords) == 0) {
            signals.add("缺少最近跟进记录，推进节奏和客户真实意向需要确认。");
        }
        if (safeSize(openFollowPlans) > 0) {
            signals.add("存在未完成跟进计划：" + safeSize(openFollowPlans) + " 项。");
        }
        return signals;
    }

    private List<String> buildOpportunityMissingFields(Map<String, Object> opportunity, Map<String, Object> dealTerms) {
        List<String> missing = new ArrayList<>();
        if (StringUtils.isBlank(asText(opportunity.get("customerId")))) {
            missing.add("customerId");
        }
        if (StringUtils.isBlank(asText(opportunity.get("ownerId"))) && StringUtils.isBlank(asText(opportunity.get("owner")))) {
            missing.add("ownerId");
        }
        if (StringUtils.isBlank(asText(opportunity.get("contactId"))) && StringUtils.isBlank(asText(opportunity.get("contactName")))) {
            missing.add("primaryContact");
        }
        if (StringUtils.isBlank(asText(opportunity.get("expectedEndTime")))) {
            missing.add("expectedEndTime");
        }
        if (StringUtils.isBlank(asText(dealTerms.get("paymentTerms")))) {
            missing.add("paymentTerms");
        }
        if (StringUtils.isBlank(asText(dealTerms.get("deliveryDeadline")))) {
            missing.add("deliveryDeadline");
        }
        if (StringUtils.isBlank(asText(dealTerms.get("acceptanceCriteria")))) {
            missing.add("acceptanceCriteria");
        }
        if (StringUtils.isBlank(asText(dealTerms.get("decisionMakers")))) {
            missing.add("decisionMakers");
        }
        return missing;
    }

    private List<String> buildSourceRefs(String customerId, String opportunityId) {
        List<String> refs = new ArrayList<>();
        if (StringUtils.isNotBlank(customerId)) {
            refs.add("customer:" + customerId);
        }
        refs.add("opportunity:" + opportunityId);
        return refs;
    }

    private String normalizeKeyword(DealDeskToolRequest request) {
        return request == null ? "" : StringUtils.trimToEmpty(request.getKeyword());
    }

    private int resolveLimit(DealDeskToolRequest request) {
        if (request == null || request.getLimit() == null || request.getLimit() <= 0) {
            return DEFAULT_SEARCH_LIMIT;
        }
        return Math.min(request.getLimit(), 20);
    }

    private String idempotencyKey(String type, DealDeskToolRequest request) {
        return type + ":" + request.getIdempotencyKey();
    }

    private DealDeskToolResponse failureFromException(RuntimeException e, String fallbackCode, String fallbackMessage) {
        String name = e.getClass().getName().toLowerCase();
        if (name.contains("authorization") || name.contains("permission")) {
            return DealDeskToolResponse.fail("PERMISSION_DENIED", "当前用户无权执行该 CRM 工具。");
        }
        return DealDeskToolResponse.fail(fallbackCode, StringUtils.defaultIfBlank(e.getMessage(), fallbackMessage));
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private List<Map<String, Object>> safeList(List<Map<String, Object>> list) {
        return list == null ? Collections.emptyList() : list;
    }

    private int safeSize(List<Map<String, Object>> list) {
        return list == null ? 0 : list.size();
    }

    private void putIfPresent(Map<String, Object> target, String key, Object value) {
        if (value instanceof List<?> list && list.isEmpty()) {
            return;
        }
        if (StringUtils.isNotBlank(asText(value))) {
            target.put(key, value);
        }
    }

    private String firstContent(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : safeList(rows)) {
            String content = firstNonBlank(asText(row.get("content")), asText(row.get("name")));
            if (StringUtils.isNotBlank(content)) {
                return content;
            }
        }
        return "";
    }

    private String firstName(List<Map<String, Object>> rows) {
        for (Map<String, Object> row : safeList(rows)) {
            String name = asText(row.get("name"));
            if (StringUtils.isNotBlank(name)) {
                return name;
            }
        }
        return "";
    }

    private List<String> customFieldValues(Map<String, Object> object) {
        List<String> values = new ArrayList<>();
        Object fields = object == null ? null : object.get("customFields");
        if (fields instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    String value = asText(map.get("value"));
                    if (StringUtils.isNotBlank(value)) {
                        values.add(value);
                    }
                }
            }
        }
        return values;
    }

    private void collectContextTexts(List<String> texts, Map<String, Object> object) {
        if (object == null || object.isEmpty()) {
            return;
        }
        for (String key : List.of("name", "customerName", "stageName", "failureReason", "content")) {
            String value = asText(object.get(key));
            if (StringUtils.isNotBlank(value)) {
                texts.add(value);
            }
        }
        texts.addAll(customFieldValues(object));
    }

    private String firstMatchingText(List<String> texts, String... keywords) {
        for (String text : texts) {
            for (String keyword : keywords) {
                if (StringUtils.contains(text, keyword)) {
                    return text;
                }
            }
        }
        return "";
    }

    private BigDecimal asDecimal(Object value) {
        String text = asText(value);
        if (StringUtils.isBlank(text)) {
            return null;
        }
        try {
            return new BigDecimal(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (StringUtils.isNotBlank(value)) {
                return value;
            }
        }
        return "";
    }

    // ==================== Stats Endpoints ====================

    /**
     * 获取销售漏斗快照 — 按商机阶段聚合数量，返回 funnel chart 数据。
     */
    public DealDeskToolResponse getFunnelSnapshot(DealDeskToolRequest request) {
        try {
            String organizationId = AiDealDeskToolExecutionContext.getOrganizationId();
            List<Map<String, Object>> stages = sortFunnelStages(gateway.getFunnelSnapshot(organizationId));
            if (stages == null || stages.isEmpty()) {
                return DealDeskToolResponse.fail("NO_DATA", "当前没有商机数据，无法生成销售漏斗。");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("chartType", "funnel");
            data.put("title", "销售漏斗");
            data.put("unit", "条");
            data.put("stages", stages);
            data.put("chart", buildChart("funnel", "销售漏斗", "条", stages, null));
            return DealDeskToolResponse.ok(data);
        } catch (RuntimeException e) {
            return failureFromException(e, "STATS_FAILED", "销售漏斗统计失败。");
        }
    }

    /**
     * 获取客户 L2C 健康度 — 线索→商机→赢单转化链路的断裂信号。
     */
    public DealDeskToolResponse getCustomerL2cHealth(DealDeskToolRequest request) {
        try {
            String organizationId = AiDealDeskToolExecutionContext.getOrganizationId();
            Map<String, Object> health = gateway.getCustomerL2cHealth(organizationId);
            if (health == null || health.isEmpty()) {
                return DealDeskToolResponse.fail("NO_DATA", "当前没有足够数据计算 L2C 健康度。");
            }
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("chartType", "bar");
            data.put("title", "客户 L2C 转化健康度");
            data.put("unit", "%");
            data.putAll(health);
            List<Map<String, Object>> rateMetrics = List.of(
                    metricEntry("商机赢单率(%)", safeLong(health.get("opportunityWinRate"))),
                    metricEntry("客户转化率(%)", safeLong(health.get("customerConversionRate")))
            );
            data.put("chart", buildChart("bar", "客户 L2C 转化健康度", "%", rateMetrics, extractNames(rateMetrics)));
            return DealDeskToolResponse.ok(data);
        } catch (RuntimeException e) {
            return failureFromException(e, "STATS_FAILED", "L2C 健康度统计失败。");
        }
    }

    /**
     * 获取合同回款与开票概览。
     */
    public DealDeskToolResponse getContractRevenueSnapshot(DealDeskToolRequest request) {
        try {
            String organizationId = AiDealDeskToolExecutionContext.getOrganizationId();
            Map<String, Object> snapshot = gateway.getContractRevenueSnapshot(organizationId);
            if (snapshot == null || snapshot.isEmpty()) {
                return DealDeskToolResponse.fail("NO_DATA", "当前没有足够数据计算合同收入概览。");
            }
            List<Map<String, Object>> metrics = List.of(
                    metricEntry("合同总额", safeLong(snapshot.get("contractAmount"))),
                    metricEntry("已回款金额", safeLong(snapshot.get("paidAmount"))),
                    metricEntry("已开票金额", safeLong(snapshot.get("invoiceAmount"))),
                    metricEntry("待回款金额", safeLong(snapshot.get("pendingAmount"))),
                    metricEntry("逾期未回款金额", safeLong(snapshot.get("overduePendingAmount")))
            );
            Map<String, Object> data = new LinkedHashMap<>();
            data.put("chartType", "bar");
            data.put("title", "合同回款与开票概览");
            data.put("unit", "元");
            data.putAll(snapshot);
            data.put("metrics", metrics);
            data.put("chart", buildChart("bar", "合同回款与开票概览", "元", metrics, extractNames(metrics)));
            return DealDeskToolResponse.ok(data);
        } catch (RuntimeException e) {
            return failureFromException(e, "STATS_FAILED", "合同收入统计失败。");
        }
    }

    private List<Map<String, Object>> sortFunnelStages(List<Map<String, Object>> stages) {
        if (stages == null || stages.isEmpty()) {
            return Collections.emptyList();
        }
        return stages.stream()
                .sorted(Comparator
                        .comparingInt((Map<String, Object> item) -> funnelStageOrder(asText(item.get("name"))))
                        .thenComparing(item -> asText(item.get("name"))))
                .toList();
    }

    private int funnelStageOrder(String stageName) {
        return switch (stageName) {
            case "\u65b0\u5efa" -> 0;
            case "\u9700\u6c42\u660e\u786e" -> 1;
            case "\u65b9\u6848\u9a8c\u8bc1" -> 2;
            case "\u7acb\u9879\u6c47\u62a5" -> 3;
            case "\u5546\u52a1\u91c7\u8d2d" -> 4;
            case "\u7b7e\u7ea6\u6210\u529f" -> 5;
            case "\u5931\u8d25" -> 6;
            case "\u672a\u5206\u7c7b" -> 7;
            default -> 99;
        };
    }

    private Map<String, Object> buildChart(
            String type,
            String title,
            String unit,
            List<Map<String, Object>> data,
            List<String> xData
    ) {
        Map<String, Object> chart = new LinkedHashMap<>();
        chart.put("type", type);
        chart.put("title", title);
        chart.put("unit", unit);
        chart.put("data", data == null ? Collections.emptyList() : data);
        if (xData != null && !xData.isEmpty()) {
            chart.put("xData", xData);
        }
        return chart;
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> castChartData(Object value) {
        if (value instanceof List<?> list) {
            return (List<Map<String, Object>>) list;
        }
        return Collections.emptyList();
    }

    private List<String> extractNames(List<Map<String, Object>> metrics) {
        List<String> names = new ArrayList<>();
        for (Map<String, Object> metric : metrics) {
            String name = asText(metric.get("name"));
            if (StringUtils.isNotBlank(name)) {
                names.add(name);
            }
        }
        return names;
    }

    private long safeLong(Object value) {
        BigDecimal decimal = asDecimal(value);
        return decimal == null ? 0L : decimal.longValue();
    }

    private Map<String, Object> metricEntry(String name, long value) {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", name);
        entry.put("value", value);
        return entry;
    }
}
