from pathlib import Path

import inspect
import json
import yaml


ROOT = Path(__file__).resolve().parents[2]
CHATFLOW_PATH = ROOT / "chatflows" / "ai-deal-desk-v3.example.yml"
OPENAPI_PATH = ROOT / "docs" / "reference" / "dify" / "AI Deal Desk CRM Resolver.openapi.yml"
KNOWLEDGE_PATH = (
    ROOT
    / "knowledge-base"
    / "ai-deal-desk"
    / "AI Deal Desk 规则知识库 - Dify 单页版.md"
)


def node_by_id(nodes: list[dict], node_id: str) -> dict:
    return next(node for node in nodes if node.get("id") == node_id)


def code_main(nodes: list[dict], node_id: str):
    namespace: dict = {}
    exec(node_by_id(nodes, node_id)["data"]["code"], namespace)
    return namespace["main"]


def main() -> None:
    chatflow = yaml.safe_load(CHATFLOW_PATH.read_text(encoding="utf-8"))
    nodes = chatflow["workflow"]["graph"]["nodes"]
    edges = chatflow["workflow"]["graph"]["edges"]
    node_ids = [node["id"] for node in nodes]
    assert len(node_ids) == len(set(node_ids))
    assert all(edge["source"] in node_ids and edge["target"] in node_ids for edge in edges)

    adjacency = {node_id: [] for node_id in node_ids}
    indegree = {node_id: 0 for node_id in node_ids}
    for edge in edges:
        adjacency[edge["source"]].append(edge["target"])
        indegree[edge["target"]] += 1
    queue = [node_id for node_id, degree in indegree.items() if degree == 0]
    visited = 0
    while queue:
        current = queue.pop()
        visited += 1
        for target in adjacency[current]:
            indegree[target] -= 1
            if indegree[target] == 0:
                queue.append(target)
    assert visited == len(node_ids), "chatflow graph must remain acyclic"

    code_nodes = [node for node in nodes if node.get("data", {}).get("type") == "code"]
    for node in code_nodes:
        namespace: dict = {}
        compiled = compile(node["data"]["code"], f"<{node['id']}>", "exec")
        exec(compiled, namespace)
        bound_variables = {
            variable["variable"] for variable in node["data"].get("variables", [])
        }
        main_parameters = set(inspect.signature(namespace["main"]).parameters)
        assert bound_variables == main_parameters, (
            node["id"],
            bound_variables - main_parameters,
            main_parameters - bound_variables,
        )

    planner = node_by_id(nodes, "task_planner")
    planner_tools = planner["data"]["agent_parameters"]["tools"]["value"]
    tool_names = [tool["tool_name"] for tool in planner_tools]
    assert tool_names == ["resolve_crm_object"], tool_names
    planner_query = planner["data"]["agent_parameters"]["query"]["value"]
    assert planner_query == "{{#conversation_context.context_text#}}"
    planner_instruction = planner["data"]["agent_parameters"]["instruction"]["value"]
    assert "direct_answer" in planner_instruction
    assert "调用 resolve_crm_object 重试一次" not in planner_instruction
    assert planner["data"]["agent_parameters"]["maximum_iterations"]["value"] == 2
    planner_memory = planner["data"]["memory"]
    assert planner_memory["window"] == {"enabled": True, "size": 5}

    external_agent = node_by_id(nodes, "external_intel_agent")
    external_instruction = external_agent["data"]["agent_parameters"]["instruction"]["value"]
    assert "相关度分数只表示文本相关，不代表来源权威" in external_instruction
    assert "合格来源不足时允许少于要求数量" in external_instruction
    assert "不能单独支撑“行业主流”“行业共识”" in external_instruction
    assert "发布日期不明时不得称为“近期”" in external_instruction
    assert "最近一年”只包含 2025-07-15 及之后发布的资料" in external_instruction
    assert "每次最多 10 条候选" in external_instruction
    assert "设置 include_domains 后，query 中不得再次写 site:" in external_instruction
    assert "填写 start_date 与 end_date" in external_instruction
    assert "所有引用 URL 必须逐字来自本轮 Tavily Search 或 Tavily Extract" in external_instruction
    assert external_agent["data"]["agent_parameters"]["maximum_iterations"]["value"] == 5
    external_query = external_agent["data"]["agent_parameters"]["query"]["value"]
    assert "先检索足够的高质量候选资料" in external_query
    external_tools = external_agent["data"]["agent_parameters"]["tools"]["value"]
    tavily_search = next(tool for tool in external_tools if tool["tool_name"] == "tavily_search")
    assert tavily_search["settings"]["max_results"]["value"]["value"] == 10

    business_answer = node_by_id(nodes, "business_answer_agent")
    business_system_prompt = next(
        item["text"]
        for item in business_answer["data"]["prompt_template"]
        if item["id"] == "business-answer-system"
    )
    assert "不得升级成确定事实" in business_system_prompt
    assert "不能单独改写成行业趋势或行业共识" in business_system_prompt
    assert "合格来源不足时返回实际数量" in business_system_prompt
    assert "发布日期不明的来源不得描述为近期" in business_system_prompt
    assert "是否使用加粗以及加粗哪些内容，由你根据当前回答的语义层级与可读性自主决定" in business_system_prompt
    assert "不要求必须出现加粗，也不规定固定的加粗位置或类型" in business_system_prompt
    assert "开头 1-2 句核心判断必须使用一组完整的 **加粗**" not in business_system_prompt
    assert "直接附可读的来源名称链接" in business_system_prompt
    assert "[1]、[2] 等数字角标" in business_system_prompt
    assert "只有用户明确要求“列出来源”或“给出参考链接”时" in business_system_prompt
    assert "URL 必须逐字保留，不得改写、拼接或凭记忆补全" in business_system_prompt
    assert "纯 CRM、附件或知识库回答不要生成来源链接或来源章节" in business_system_prompt

    knowledge_decider = node_by_id(nodes, "knowledge_query_decider")
    knowledge_system_prompt = next(
        item["text"]
        for item in knowledge_decider["data"]["prompt_template"]
        if item["id"] == "knowledge-query-decision-system"
    )
    assert "关键业务事实 + 待查规则主题" in knowledge_system_prompt
    assert "客户名、商机名" in knowledge_system_prompt
    assert "普通销售阶段" in knowledge_system_prompt
    assert "验收样本、试运行周期、退款条件、延期赔付和赔付上限" in knowledge_system_prompt
    assert "客户备选诉求、若坚持、可能" in knowledge_system_prompt

    normalizer = node_by_id(nodes, "task_plan_normalize")
    assert {
        "object_reference",
        "resolution_status",
        "direct_answer",
        "knowledge_policy",
        "knowledge_goal",
    } <= set(
        normalizer["data"]["outputs"]
    )

    def has_edge(source: str, handle: str, target: str) -> bool:
        return any(
            edge.get("source") == source
            and edge.get("sourceHandle") == handle
            and edge.get("target") == target
            for edge in edges
        )

    assert has_edge("crm_evidence", "source", "knowledge_context_prepare")
    assert has_edge("attachment_evidence", "source", "knowledge_context_prepare")
    assert has_edge("knowledge_context_prepare", "source", "knowledge_policy_gate")
    assert has_edge("knowledge_policy_gate", "need_knowledge", "knowledge_query_decider")
    assert has_edge("knowledge_query_decider", "source", "query_rewrite")
    assert has_edge("query_rewrite", "source", "knowledge_gate")
    assert has_edge("knowledge_gate", "need_knowledge", "deal_rules_knowledge")

    general_edge = next(
        edge
        for edge in edges
        if edge.get("source") == "task_type_gate"
        and edge.get("sourceHandle") == "general_chat"
    )
    assert general_edge["target"] == "protocol_adapter"
    assert general_edge["data"]["targetType"] == "code"

    for source, stream_target in {
        "simple_answer": "simple_answer_stream",
        "image_answer": "image_answer_stream",
        "business_answer_agent": "business_answer_stream",
    }.items():
        stream_edge = next(edge for edge in edges if edge.get("source") == source)
        assert stream_edge["target"] == stream_target
        assert stream_edge["data"]["targetType"] == "answer"
        stream_node = node_by_id(nodes, stream_target)
        assert stream_node["data"]["type"] == "answer"
        assert stream_node["data"]["answer"] == f"{{{{#{source}.text#}}}}"
        assert not any(
            edge.get("source") == source and edge.get("target") == "protocol_adapter"
            for edge in edges
        )

    protocol_adapter_node = node_by_id(nodes, "protocol_adapter")
    protocol_variables = {
        variable["variable"] for variable in protocol_adapter_node["data"]["variables"]
    }
    assert {"task_type", "resolution_status", "target_object_json"} <= protocol_variables

    evidence_router = node_by_id(nodes, "evidence_router")
    router_variables = {variable["variable"] for variable in evidence_router["data"]["variables"]}
    assert {"object_reference", "resolution_status"} <= router_variables

    normalize = code_main(nodes, "task_plan_normalize")
    general_plan = normalize(
        planner_text=json.dumps(
            {
                "task_type": "general_chat",
                "resolution_status": "not_required",
                "target_object": {},
                "required_sources": {
                    "crm": False,
                    "knowledge": False,
                    "external": False,
                    "attachment": False,
                },
                "required_agents": {
                    "sales": False,
                    "finance": False,
                    "delivery": False,
                    "legal": False,
                    "analytics": False,
                },
                "answer_mode": "general",
                "direct_answer": "我是嵌在 CRM 工作台里的 AI Deal Desk 助手。",
            },
            ensure_ascii=False,
        ),
        has_images="false",
        bound_object_json="{}",
    )
    assert general_plan["route_group"] == "general_chat"
    assert general_plan["direct_answer"] == "我是嵌在 CRM 工作台里的 AI Deal Desk 助手。"
    assert general_plan["need_crm"] == "false"
    assert general_plan["agent_route"] == "none"

    ambiguous_plan = normalize(
        planner_text=json.dumps(
            {
                "task_type": "full_review",
                "object_reference": "华东智造",
                "resolution_status": "ambiguous",
                "target_object": {},
                "required_sources": {
                    "crm": True,
                    "knowledge": True,
                    "external": True,
                },
                "required_agents": {
                    "sales": True,
                    "finance": True,
                    "delivery": True,
                    "legal": True,
                },
            },
            ensure_ascii=False,
        ),
        has_images="false",
        bound_object_json=json.dumps(
            {
                "objectType": "opportunity",
                "objectId": "old-opportunity",
                "objectName": "旧商机",
            },
            ensure_ascii=False,
        ),
    )
    assert ambiguous_plan["resolution_status"] == "ambiguous"
    assert json.loads(ambiguous_plan["target_object_json"]) == {}
    assert ambiguous_plan["agent_route"] == "crm_light_answer"
    assert ambiguous_plan["need_crm"] == "true"
    assert ambiguous_plan["need_knowledge"] == "false"
    assert ambiguous_plan["need_external"] == "false"

    bound_plan = normalize(
        planner_text=json.dumps(
            {
                "task_type": "sales_assist",
                "object_reference": "",
                "resolution_status": "bound",
                "target_object": {},
                "required_sources": {"crm": True},
                "required_agents": {"sales": True},
            },
            ensure_ascii=False,
        ),
        has_images="false",
        bound_object_json=json.dumps(
            {
                "objectType": "opportunity",
                "objectId": "bound-opportunity",
                "objectName": "当前商机",
            },
            ensure_ascii=False,
        ),
    )
    assert json.loads(bound_plan["target_object_json"])["objectId"] == "bound-opportunity"
    assert bound_plan["agent_route"] == "sales"
    assert bound_plan["direct_answer"] == ""

    finance_plan = normalize(
        planner_text=json.dumps(
            {
                "task_type": "finance_check",
                "resolution_status": "bound",
                "target_object": {},
                "required_sources": {
                    "crm": True,
                    "knowledge": False,
                    "external": False,
                    "attachment": False,
                },
                "required_agents": {
                    "sales": False,
                    "finance": True,
                    "delivery": False,
                    "legal": False,
                    "analytics": False,
                },
            },
            ensure_ascii=False,
        ),
        has_images="false",
        bound_object_json=json.dumps(
            {
                "objectType": "opportunity",
                "objectId": "bound-opportunity",
                "objectName": "当前商机",
            },
            ensure_ascii=False,
        ),
    )
    assert finance_plan["agent_route"] == "finance"
    assert finance_plan["need_crm"] == "true"
    assert finance_plan["need_knowledge"] == "false"
    assert finance_plan["need_external"] == "false"

    external_without_evidence = normalize(
        planner_text=json.dumps(
            {
                "task_type": "external_research",
                "resolution_status": "not_required",
                "target_object": {},
                "required_sources": {
                    "crm": False,
                    "knowledge": False,
                    "external": False,
                    "attachment": False,
                },
                "required_agents": {
                    "sales": False,
                    "finance": False,
                    "delivery": False,
                    "legal": False,
                    "analytics": False,
                },
            },
            ensure_ascii=False,
        ),
        has_images="false",
        bound_object_json="{}",
    )
    assert external_without_evidence["need_external"] == "false"
    assert external_without_evidence["need_crm"] == "false"
    assert external_without_evidence["agent_route"] == "none"

    full_review_fallback = normalize(
        planner_text=json.dumps(
            {
                "task_type": "full_review",
                "resolution_status": "bound",
                "target_object": {},
                "required_sources": {
                    "crm": True,
                    "knowledge": False,
                    "external": False,
                    "attachment": False,
                },
                "required_agents": {
                    "sales": False,
                    "finance": False,
                    "delivery": False,
                    "legal": False,
                    "analytics": False,
                },
            },
            ensure_ascii=False,
        ),
        has_images="false",
        bound_object_json=json.dumps(
            {
                "objectType": "opportunity",
                "objectId": "bound-opportunity",
                "objectName": "当前商机",
            },
            ensure_ascii=False,
        ),
    )
    assert full_review_fallback["agent_route"] == "full_review"
    assert full_review_fallback["need_crm"] == "true"
    assert full_review_fallback["need_knowledge"] == "false"
    assert full_review_fallback["need_external"] == "false"

    conditional_plan = normalize(
        planner_text=json.dumps(
            {
                "task_type": "finance_check",
                "resolution_status": "bound",
                "target_object": {},
                "required_sources": {
                    "crm": True,
                    "knowledge": True,
                    "external": False,
                    "attachment": False,
                },
                "knowledge_policy": "conditional",
                "knowledge_goal": "判断付款、折扣和账期是否触及内部财务规则",
                "required_agents": {"finance": True},
                "answer_goal": "评估当前付款方案",
            },
            ensure_ascii=False,
        ),
        has_images="false",
        bound_object_json=json.dumps(
            {
                "objectType": "opportunity",
                "objectId": "bound-opportunity",
                "objectName": "当前商机",
            },
            ensure_ascii=False,
        ),
    )
    assert conditional_plan["knowledge_policy"] == "conditional"
    assert conditional_plan["knowledge_goal"] == "判断付款、折扣和账期是否触及内部财务规则"
    assert conditional_plan["need_knowledge"] == "true"

    object_query_forces_no_knowledge = normalize(
        planner_text=json.dumps(
            {
                "task_type": "object_query",
                "resolution_status": "bound",
                "target_object": {},
                "required_sources": {"crm": True, "knowledge": True},
                "knowledge_policy": "required",
                "knowledge_goal": "不应执行",
                "required_agents": {},
                "answer_mode": "detail_lookup",
            },
            ensure_ascii=False,
        ),
        has_images="false",
        bound_object_json=json.dumps(
            {
                "objectType": "opportunity",
                "objectId": "bound-opportunity",
                "objectName": "当前商机",
            },
            ensure_ascii=False,
        ),
    )
    assert object_query_forces_no_knowledge["knowledge_policy"] == "none"
    assert object_query_forces_no_knowledge["need_knowledge"] == "false"

    query_validator_node = node_by_id(nodes, "query_rewrite")
    assert "TASK_TERMS" not in query_validator_node["data"]["code"]
    validate_knowledge_query = code_main(nodes, "query_rewrite")
    required_query = validate_knowledge_query(
        decision_text=json.dumps(
            {
                "need_knowledge": False,
                "knowledge_query": "88万元商机 75折 30%首付 验收后90天 内部财务审批与风险规则",
                "focus_domains": ["finance", "legal"],
                "facts_used": ["金额88万元", "折扣75折", "首付30%", "尾款账期90天"],
                "reason": "需要内部规则作为评审依据",
            },
            ensure_ascii=False,
        ),
        knowledge_policy="required",
        original_query="评审当前商机",
        knowledge_goal="确认财务审批和合同责任边界",
        answer_goal="形成商机评审",
    )
    assert required_query["need_knowledge"] == "true"
    assert len(required_query["text"]) <= 260
    assert json.loads(required_query["focus_domains_json"]) == ["finance", "legal"]

    conditional_skip = validate_knowledge_query(
        decision_text=json.dumps(
            {
                "need_knowledge": False,
                "knowledge_query": "不应保留",
                "reason": "现有证据已足够回答事实问题",
            },
            ensure_ascii=False,
        ),
        knowledge_policy="conditional",
        original_query="总结当前进展",
        knowledge_goal="",
        answer_goal="总结 CRM 事实",
    )
    assert conditional_skip["need_knowledge"] == "false"
    assert conditional_skip["text"] == ""

    conditional_fallback = validate_knowledge_query(
        decision_text="not-json",
        knowledge_policy="conditional",
        original_query="这个方案是否符合内部要求",
        knowledge_goal="确认付款与折扣规则",
        answer_goal="给出财务判断",
    )
    assert conditional_fallback["need_knowledge"] == "true"
    assert "确认付款与折扣规则" in conditional_fallback["text"]
    assert "降级查询" in conditional_fallback["reason"]

    conversation_context = code_main(nodes, "conversation_context")
    context_result = conversation_context(
        original_query="介绍一下你自己",
        bound_object_json="{}",
        page_context_text="当前未绑定对象\n附件摘要：无",
        attachments_summary="无",
    )
    assert context_result["context_text"].count("介绍一下你自己") == 1
    assert context_result["context_text"].count("附件摘要：无") == 1

    route_evidence = code_main(nodes, "evidence_router")
    ambiguous_route = route_evidence(
        task_type="object_query",
        original_query="帮我看看华东智造",
        target_object_json="{}",
        object_reference="华东智造",
        resolution_status="ambiguous",
        need_crm="true",
    )
    assert ambiguous_route["crm_action"] == "resolve_crm_object"
    assert ambiguous_route["crm_tool_url_path"] == "/resolve-crm-object"
    assert json.loads(ambiguous_route["crm_request_body"])["objectReference"] == "华东智造"

    resolved_route = route_evidence(
        task_type="full_review",
        original_query="评审这个商机",
        target_object_json=json.dumps(
            {
                "objectType": "opportunity",
                "objectId": "opportunity-1",
                "objectName": "华东智造集团AI客服升级项目",
            },
            ensure_ascii=False,
        ),
        object_reference="华东智造集团AI客服升级项目",
        resolution_status="resolved",
        need_crm="true",
    )
    assert resolved_route["crm_action"] == "get_opportunity_context"
    assert json.loads(resolved_route["crm_request_body"])["opportunityId"] == "opportunity-1"

    light_answer = code_main(nodes, "crm_light_answer")
    candidate_answer = light_answer(
        crm_evidence_json=json.dumps(
            {
                "action": "resolve_crm_object",
                "count": 2,
                "candidates": [
                    {
                        "objectType": "customer",
                        "objectId": "customer-1",
                        "objectName": "华东智造集团",
                        "ownerName": "周雨晴",
                    },
                    {
                        "objectType": "opportunity",
                        "objectId": "opportunity-1",
                        "objectName": "华东智造集团AI客服升级项目",
                        "customerName": "华东智造集团",
                        "stageName": "商务采购",
                    },
                ],
            },
            ensure_ascii=False,
        )
    )["answer_text"]
    assert "输入完整客户名或商机名" in candidate_answer
    assert "华东智造集团AI客服升级项目" in candidate_answer

    crm_evidence = code_main(nodes, "crm_evidence")
    large_context = {
        "opportunity": {
            "id": "opportunity-1",
            "name": "华东智造集团AI客服升级项目",
            "customerName": "华东智造集团",
            "stageName": "商务采购",
            "amount": "880000",
            "possible": "68",
            "ownerName": "周雨晴",
        },
        "customer": {
            "id": "customer-1",
            "name": "华东智造集团",
            "ownerName": "周雨晴",
        },
        "businessFacts": {
            "latestFollowRecord": "客户希望尽快完成商务定稿。",
            "nextOpenPlan": "确认付款方案与验收口径。",
        },
        "recentFollowRecords": [
            {"id": f"record-{index}", "content": "跟进内容" * 100}
            for index in range(30)
        ],
    }
    evidence_result = crm_evidence(
        response_body=json.dumps(
            {"success": True, "code": "OK", "message": "", "data": large_context},
            ensure_ascii=False,
        ),
        crm_action="get_opportunity_context",
    )
    evidence_payload = json.loads(evidence_result["evidence_json"])
    structured_fact = json.loads(evidence_payload["facts"][0])
    assert structured_fact["opportunity"]["id"] == "opportunity-1"
    assert len(structured_fact["recentFollowRecords"]) == 10

    resolved_evidence_result = crm_evidence(
        response_body=json.dumps(
            {
                "success": True,
                "code": "OK",
                "message": "",
                "data": {
                    "resultType": "resolved_object",
                    "matchType": "exact",
                    "resolvedObject": {
                        "objectType": "opportunity",
                        "objectId": "opportunity-1",
                        "objectName": "华东智造集团AI客服升级项目",
                        "customerId": "customer-1",
                        "customerName": "华东智造集团",
                        "source": "crm_resolver",
                    },
                },
            },
            ensure_ascii=False,
        ),
        crm_action="resolve_crm_object",
    )
    resolved_evidence_payload = json.loads(resolved_evidence_result["evidence_json"])
    assert resolved_evidence_payload["result_type"] == "resolved_object"
    assert resolved_evidence_payload["objects"] == [
        {
            "objectType": "opportunity",
            "objectId": "opportunity-1",
            "objectName": "华东智造集团AI客服升级项目",
            "customerId": "customer-1",
            "customerName": "华东智造集团",
            "source": "crm_resolver",
        }
    ]

    evidence_ledger = code_main(nodes, "evidence_ledger")
    resolved_ledger_result = evidence_ledger(
        crm_evidence_json=resolved_evidence_result["evidence_json"],
        knowledge_evidence_json="{}",
        external_evidence_json="{}",
        attachment_evidence_json="{}",
        target_object_json="{}",
        answer_goal="联网搜索辅助验证当前商机",
        success_criteria_json="[]",
    )
    resolved_target = json.loads(resolved_ledger_result["target_object_json"])
    assert resolved_target["objectId"] == "opportunity-1"
    assert resolved_target["objectName"] == "华东智造集团AI客服升级项目"

    knowledge_evidence = code_main(nodes, "knowledge_evidence")
    knowledge_payload = json.loads(
        knowledge_evidence(
            knowledge_result=[
                {
                    "title": "规则 FIN-005：折扣换条件",
                    "content": "折扣低于80折且未换取付款、周期或范围条件时，需要升级处理。",
                    "score": 0.98,
                    "metadata": {"document_name": "AI Deal Desk 规则知识库"},
                },
                {
                    "title": "规则 FIN-005：折扣换条件",
                    "content": "折扣低于80折且未换取付款、周期或范围条件时，需要升级处理。",
                    "score": 0.97,
                    "metadata": {"document_name": "AI Deal Desk 规则知识库"},
                },
                {
                    "title": "规则 FIN-008：首付款不足",
                    "content": "首付款比例低于50%时，需要关注现金流和交付投入风险。",
                    "score": 0.95,
                    "metadata": {"document_name": "AI Deal Desk 规则知识库"},
                },
            ],
            decision_skipped="",
            policy_skipped="",
            query_text="88万元 75折 30%首付 内部财务风险规则",
        )["evidence_json"]
    )
    assert knowledge_payload["retrieval_status"] == "completed"
    assert len(knowledge_payload["rules"]) == 2
    assert knowledge_payload["rules"][0]["relevance"] == 0.98
    assert knowledge_payload["rules"][0]["source"] == "AI Deal Desk 规则知识库"

    skipped_knowledge_payload = json.loads(
        knowledge_evidence(
            knowledge_result=None,
            decision_skipped="",
            policy_skipped="planner_policy_none",
            query_text="",
        )["evidence_json"]
    )
    assert skipped_knowledge_payload["retrieval_status"] == "not_required"
    assert skipped_knowledge_payload["skip_reason"] == "planner_policy_none"
    assert skipped_knowledge_payload["gaps"] == []

    detail_answer = light_answer(
        crm_evidence_json=evidence_result["evidence_json"]
    )["answer_text"]
    assert "这是当前 CRM 里查到的商机摘要" in detail_answer
    assert "华东智造集团AI客服升级项目" in detail_answer
    assert "880,000" in detail_answer
    assert "确认付款方案与验收口径" in detail_answer

    protocol_adapter = code_main(nodes, "protocol_adapter")
    general_protocol = json.loads(
        protocol_adapter(
            direct_answer="我是嵌在 CRM 工作台里的 AI Deal Desk 助手。",
            task_type="general_chat",
            target_object_json="{}",
        )["protocol_answer"]
    )
    assert general_protocol["turnType"] == "quick_answer"
    assert general_protocol["answerText"] == "我是嵌在 CRM 工作台里的 AI Deal Desk 助手。"

    resolved_protocol = json.loads(
        protocol_adapter(
            crm_light_answer="这是当前 CRM 里查到的商机摘要。",
            task_type="object_query",
            resolution_status="resolved",
            target_object_json=json.dumps(
                {
                    "object_type": "opportunity",
                    "object_id": "opportunity-1",
                    "object_name": "华东智造集团AI客服升级项目",
                },
                ensure_ascii=False,
            ),
        )["protocol_answer"]
    )
    assert resolved_protocol["turnType"] == "quick_answer"
    assert resolved_protocol["boundObject"] == {
        "objectType": "opportunity",
        "objectId": "opportunity-1",
        "objectName": "华东智造集团AI客服升级项目",
    }
    assert "object_type" not in resolved_protocol["boundObject"]
    ambiguous_protocol = json.loads(
        protocol_adapter(
            crm_light_answer="找到多个候选对象，请选择。",
            task_type="object_query",
            resolution_status="ambiguous",
            target_object_json=json.dumps(
                {
                    "objectType": "customer",
                    "objectId": "candidate-1",
                    "objectName": "第一个候选不应成为绑定对象",
                },
                ensure_ascii=False,
            ),
        )["protocol_answer"]
    )
    assert ambiguous_protocol["turnType"] == "object_select"
    assert ambiguous_protocol["boundObject"] == {}

    for node_id in [
        "sales_agent",
        "finance_agent",
        "delivery_agent",
        "legal_agent",
    ]:
        domain_node = node_by_id(nodes, node_id)
        assert domain_node["data"]["model"]["completion_params"]["max_tokens"] == 600
        domain_prompt = domain_node["data"]["prompt_template"][0]["text"]
        assert "unique_findings 最多 2 条" in domain_prompt
        assert "actions 最多 2 条" in domain_prompt
        assert "missing_or_confirmation 最多 2 条" in domain_prompt
        assert "不要 Markdown、代码块或额外解释" in domain_prompt
        assert "不使用分数、关键词命中或代码评分" in domain_prompt
        assert "不复述客户、商机、金额、阶段、负责人、联系人等公共概览" in domain_prompt
        assert "风险等级只是局部证据信号" in domain_prompt
        assert "只能作为参考基线" in domain_prompt
        assert "不得称为公司正式政策、标准底线或已批准条件" in domain_prompt

    assert (
        node_by_id(nodes, "analytics_agent")["data"]["model"][
            "completion_params"
        ]["max_tokens"]
        == 800
    )

    business_answer_prompt = node_by_id(nodes, "business_answer_agent")["data"][
        "prompt_template"
    ][0]["text"]
    assert "领域 Agent 输出只是素材，不是展示结构" in business_answer_prompt
    assert "这就是全文唯一的总体、统一或最终结论" in business_answer_prompt
    assert "即使用户明确要求“统一推进结论”" in business_answer_prompt
    assert "最重要的 3-5 个业务问题" in business_answer_prompt
    assert "默认保留最重要的 3 个问题" in business_answer_prompt
    assert "紧凑矩阵" in business_answer_prompt
    assert "不使用分数、关键词命中或代码评分" in business_answer_prompt
    assert "同一问题的风险和优先级标签必须前后一致" in business_answer_prompt
    assert "全文只能有一组 3-5 个可执行“下一步”" in business_answer_prompt
    assert "矩阵只保留“视角 / 独有判断 / 必要证据”三列" in business_answer_prompt
    assert "不得再生成“统一推进结论”“统一交易结构”" in business_answer_prompt
    assert "关键议题或矩阵结束后必须直接进入“下一步”" in business_answer_prompt
    assert "不得称为公司正式政策、标准底线或已批准条件" in business_answer_prompt
    assert "AI 自行组合的新比例只能称为备选谈判草案" in business_answer_prompt
    assert "若证据没有说明金额是折前还是折后" in business_answer_prompt
    assert business_answer_prompt.count(
        "AI 输出是内部评审建议，折扣、付款、交付承诺和合同条款需相应负责人确认"
    ) == 1

    finance_prompt = node_by_id(nodes, "finance_agent")["data"]["prompt_template"][0][
        "text"
    ]
    assert "备选谈判草案，待财务复核" in finance_prompt
    assert "不得称为可接受底线、正式推荐方案或已获认可条件" in finance_prompt
    assert "未说明当前金额是折前还是折后" in finance_prompt

    assert (
        node_by_id(nodes, "analytics_agent")["data"]["memory"]["window"]["enabled"]
        is False
    )
    assert (
        node_by_id(nodes, "business_answer_agent")["data"]["memory"]["window"]["enabled"]
        is False
    )

    knowledge_text = KNOWLEDGE_PATH.read_text(encoding="utf-8")
    assert "Qwen/Qwen3-Reranker-4B" in knowledge_text
    assert "Top K 设置为 8" in knowledge_text
    assert "暂时关闭 Score 阈值" in knowledge_text
    assert "规则治理说明" in knowledge_text
    assert "未标注来源、版本、适用范围和责任人的数值规则" in knowledge_text
    assert "主题驱动的商机评审结论" in knowledge_text
    assert "默认保留最重要的 3 个业务问题" in knowledge_text
    assert "统一响应结构：" not in knowledge_text
    assert "Agent 运行状态展示规范：" not in knowledge_text
    assert "通用卡片结构：" not in knowledge_text
    assert "操作按钮 action" not in knowledge_text
    assert "模板 OUT-014" not in knowledge_text

    openapi = yaml.safe_load(OPENAPI_PATH.read_text(encoding="utf-8"))
    assert openapi["openapi"] == "3.0.3"
    resolver_operation = openapi["paths"]["/resolve-crm-object"]["post"]
    assert resolver_operation["operationId"] == "resolve_crm_object"
    assert len(openapi["paths"]) == 1

    print(
        "VALIDATION_OK "
        f"nodes={len(nodes)} "
        f"code_nodes={len(code_nodes)} "
        f"planner_tools={tool_names} "
        f"openapi_operations={len(openapi['paths'])}"
    )


if __name__ == "__main__":
    main()
