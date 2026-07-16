import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

const yamlPath = resolve('chatflows/ai-deal-desk-v3.example.yml');

const result = spawnSync(
  'python',
  [
    '-c',
    [
      'import json, sys, yaml',
      'with open(sys.argv[1], encoding="utf-8") as f:',
      '    data = yaml.safe_load(f)',
      'nodes = data.get("workflow", {}).get("graph", {}).get("nodes", [])',
      'edges = data.get("workflow", {}).get("graph", {}).get("edges", [])',
      'print(json.dumps({"nodes": nodes, "edges": edges}, ensure_ascii=True))',
    ].join('\n'),
    yamlPath,
  ],
  { encoding: 'utf8' }
);

assert.equal(result.status, 0, result.stderr || result.stdout);

const workflow = JSON.parse(result.stdout);
const nodes = workflow.nodes;
const edges = workflow.edges;
const nodeIds = new Set(nodes.map((node) => node.id));
const nodeById = new Map(nodes.map((node) => [node.id, node]));
const hasEdge = (source, handle, target) =>
  edges.some((edge) => edge.source === source && edge.sourceHandle === handle && edge.target === target);

for (const requiredNode of [
  'input_parse',
  'conversation_context',
  'task_planner',
  'task_plan_normalize',
  'task_type_gate',
  'simple_answer',
  'simple_answer_stream',
  'image_answer',
  'image_answer_stream',
  'evidence_router',
  'crm_evidence',
  'knowledge_context_prepare',
  'knowledge_policy_gate',
  'knowledge_query_decider',
  'query_rewrite',
  'knowledge_gate',
  'knowledge_evidence',
  'external_evidence',
  'attachment_evidence',
  'evidence_ledger',
  'gap_check',
  'gap_check_gate',
  'gap_answer',
  'agent_router',
  'crm_light_answer',
  'agent_skip',
  'agent_merge',
  'business_answer_agent',
  'business_answer_stream',
  'protocol_adapter',
  'final_answer',
]) {
  assert.ok(nodeIds.has(requiredNode), `expected readonly node to exist: ${requiredNode}`);
}

for (const removedNode of [
  'writeback_memory_state',
  'writeback_memory_assigner',
  'sales_task_protocol_adapter',
  'finance_task_protocol_adapter',
  'delivery_task_protocol_adapter',
  'legal_task_protocol_adapter',
  'coordinator_protocol_adapter',
]) {
  assert.ok(!nodeIds.has(removedNode), `old writeback or branch-specific adapter should be removed: ${removedNode}`);
}

assert.ok(hasEdge('task_type_gate', 'general_chat', 'protocol_adapter'), 'general_chat should use the Planner direct-answer path');
assert.ok(hasEdge('task_type_gate', 'image_answer', 'image_answer'), 'image_answer should use the direct image path');
assert.ok(hasEdge('task_type_gate', 'business_task', 'evidence_router'), 'business_task should enter evidence collection');
assert.ok(hasEdge('evidence_ledger', 'source', 'gap_check'), 'evidence ledger should feed gap check');
assert.ok(hasEdge('gap_check', 'source', 'gap_check_gate'), 'gap check code should feed the Dify branch gate');
assert.ok(hasEdge('gap_check_gate', 'need_clarification', 'gap_answer'), 'hard gaps should produce a gap answer');
assert.ok(hasEdge('gap_check_gate', 'answerable', 'agent_router'), 'answerable tasks should enter agent routing');
assert.ok(!hasEdge('evidence_router', 'source', 'evidence_ledger'), 'evidence ledger should wait for normalized evidence packages');
assert.ok(hasEdge('agent_router', 'crm_light_answer', 'crm_light_answer'), 'CRM list/detail lookups should use the lightweight answer path');
assert.ok(hasEdge('crm_light_answer', 'source', 'protocol_adapter'), 'CRM lightweight answers should use the unified protocol adapter');
assert.ok(hasEdge('agent_router', 'no_domain_agent', 'agent_skip'), 'no-domain tasks should create an explicit empty domain conclusion');
assert.ok(hasEdge('agent_router', 'false', 'agent_skip'), 'fallback domain route should create an explicit empty domain conclusion');
assert.ok(hasEdge('agent_skip', 'source', 'agent_merge'), 'no-domain tasks should still pass through domain conclusion merge');
assert.ok(!hasEdge('agent_router', 'no_domain_agent', 'business_answer_agent'), 'business answer should not bypass domain conclusion merge');
assert.ok(!hasEdge('agent_router', 'false', 'business_answer_agent'), 'fallback business answer should not bypass domain conclusion merge');
assert.ok(hasEdge('agent_merge', 'source', 'business_answer_agent'), 'domain findings should feed the business answer agent');
assert.ok(!hasEdge('evidence_ledger', 'source', 'business_answer_agent'), 'business answer should not execute before domain conclusions are merged');
assert.ok(!hasEdge('task_plan_normalize', 'source', 'business_answer_agent'), 'business answer should not execute directly from planner output');
assert.ok(hasEdge('simple_answer', 'source', 'simple_answer_stream'), 'simple answers should use the native streaming answer path');
assert.ok(hasEdge('image_answer', 'source', 'image_answer_stream'), 'image answers should use the native streaming answer path');
assert.ok(hasEdge('gap_answer', 'source', 'protocol_adapter'), 'gap answers should use the unified protocol adapter');
assert.ok(hasEdge('business_answer_agent', 'source', 'business_answer_stream'), 'business answers should use the native streaming answer path');
assert.ok(hasEdge('protocol_adapter', 'source', 'final_answer'), 'protocol adapter should be the only final answer source');
assert.ok(hasEdge('crm_evidence', 'source', 'knowledge_context_prepare'), 'knowledge context should wait for CRM evidence');
assert.ok(hasEdge('attachment_evidence', 'source', 'knowledge_context_prepare'), 'knowledge context should wait for attachment evidence');
assert.ok(hasEdge('knowledge_context_prepare', 'source', 'knowledge_policy_gate'), 'Planner knowledge policy should be evaluated only after evidence context is ready');
assert.ok(hasEdge('knowledge_policy_gate', 'need_knowledge', 'knowledge_query_decider'), 'eligible Planner policies should invoke the semantic knowledge decision');
assert.ok(hasEdge('knowledge_query_decider', 'source', 'query_rewrite'), 'knowledge decision should feed deterministic validation');
assert.ok(hasEdge('query_rewrite', 'source', 'knowledge_gate'), 'validated knowledge decision should gate retrieval');
assert.ok(hasEdge('knowledge_gate', 'need_knowledge', 'deal_rules_knowledge'), 'positive knowledge decision should execute retrieval');
assert.ok(hasEdge('knowledge_gate', 'false', 'knowledge_skip'), 'negative conditional decision should skip retrieval');

const finalAnswer = nodeById.get('final_answer');
assert.equal(finalAnswer?.data?.answer, '{{#protocol_adapter.protocol_answer#}}');

const protocolAdapterCode = nodeById.get('protocol_adapter')?.data?.code || '';
assert.match(protocolAdapterCode, /protocolVersion/, 'protocol adapter should emit protocolVersion');
assert.match(protocolAdapterCode, /answerText/, 'protocol adapter should emit answerText');
assert.match(protocolAdapterCode, /processEvents/, 'protocol adapter should emit processEvents');
assert.match(protocolAdapterCode, /writeback/, 'protocol adapter should keep writeback as an empty compatibility object');
assert.match(protocolAdapterCode, /boundObject/, 'protocol adapter should emit boundObject');
assert.match(protocolAdapterCode, /crm_light_answer/, 'protocol adapter should accept CRM lightweight answers');

const taskPlanNormalizeCode = nodeById.get('task_plan_normalize')?.data?.code || '';
assert.match(taskPlanNormalizeCode, /answer_mode/, 'task plan normalizer should expose answer_mode');
assert.match(taskPlanNormalizeCode, /multi_object_strategy/, 'task plan normalizer should expose multi_object_strategy');
assert.match(taskPlanNormalizeCode, /answer_scope/, 'task plan normalizer should expose answer_scope');
assert.match(taskPlanNormalizeCode, /crm_light_answer/, 'list/detail CRM reads should bypass domain agents');
assert.doesNotMatch(taskPlanNormalizeCode, /list_words|有哪些|列出|看一下/, 'task plan normalizer should not use keyword lists to infer CRM read routes');

const crmEvidenceCode = nodeById.get('crm_evidence')?.data?.code || '';
assert.match(crmEvidenceCode, /candidates/, 'CRM evidence should preserve candidate lists');
assert.match(crmEvidenceCode, /OBJECT_AMBIGUOUS/, 'CRM evidence should remain compatible with legacy ambiguous candidate responses');

const queryRewrite = nodeById.get('query_rewrite');
assert.equal(queryRewrite?.data?.type, 'code', 'knowledge query validation should remain deterministic code');
assert.ok(queryRewrite?.data?.outputs?.text, 'knowledge query validation should expose text output');
assert.ok(queryRewrite?.data?.outputs?.need_knowledge, 'knowledge query validation should expose the final retrieval decision');
assert.match(queryRewrite?.data?.code || '', /MAX_CHARS\s*=\s*260/, 'knowledge query validation should cap query length');
assert.doesNotMatch(queryRewrite?.data?.code || '', /TASK_TERMS/, 'knowledge query validation should not contain fixed business terms');
const knowledgeNode = nodeById.get('deal_rules_knowledge');
assert.deepEqual(knowledgeNode?.data?.query_variable_selector, ['query_rewrite', 'text']);
assert.equal(knowledgeNode?.data?.multiple_retrieval_config?.top_k, 8, 'knowledge retrieval should keep the stage-1 Top K baseline');
assert.equal(knowledgeNode?.data?.multiple_retrieval_config?.reranking_model?.model, 'Qwen/Qwen3-Reranker-4B');

const plannerInstruction = nodeById.get('task_planner')?.data?.agent_parameters?.instruction?.value || '';
assert.match(plannerInstruction, /经营统计\/图表链路/, 'planner should define the management analytics route option');
assert.match(plannerInstruction, /task_type=stats_analysis/, 'planner should map management analytics to stats_analysis');
assert.match(plannerInstruction, /不要为了示例对象调用工具/, 'planner should not resolve example objects for analytics');
assert.match(plannerInstruction, /直接复用页面提供的 bound_object，不调用工具/, 'planner should reuse the current bound object');
assert.match(plannerInstruction, /对象详情统一交给后续 CRM 数据读取节点/, 'planner should delegate full CRM context reads downstream');
assert.match(plannerInstruction, /knowledge_policy.*none、required、conditional/s, 'planner should expose semantic knowledge policy choices');
assert.match(plannerInstruction, /禁止用关键词命中决定/, 'planner knowledge routing should not use keyword hits');
assert.match(plannerInstruction, /knowledge_goal/, 'planner should state the knowledge retrieval goal');
assert.match(plannerInstruction, /answer_mode/, 'planner should ask for answer_mode');
assert.match(plannerInstruction, /multi_object_strategy/, 'planner should ask for multi_object_strategy');
assert.match(plannerInstruction, /answer_scope/, 'planner should ask for answer_scope');
assert.match(plannerInstruction, /可选工作链路/, 'planner should explain the available route options');
assert.match(plannerInstruction, /CRM 事实读取链路/, 'planner should define the CRM read route by capability');
assert.doesNotMatch(plannerInstruction, /用户问“有哪些、列出/, 'planner should not describe routing as keyword-trigger rules');

const behaviorScript = [
  'import json, sys, yaml',
  'with open(sys.argv[1], encoding="utf-8") as f:',
  '    data = yaml.safe_load(f)',
  'nodes = {node.get("id"): node for node in data["workflow"]["graph"]["nodes"]}',
  'def load_main(node_id):',
  '    ns = {}',
  '    exec(nodes[node_id]["data"]["code"], ns)',
  '    return ns["main"]',
  'normalize = load_main("task_plan_normalize")',
  'evidence_router = load_main("evidence_router")',
  'crm_evidence = load_main("crm_evidence")',
  'crm_light = load_main("crm_light_answer")',
  'protocol = load_main("protocol_adapter")',
  'plan = normalize(',
  '    planner_text=json.dumps({',
  '        "task_type": "object_query",',
  '        "target_object": {"object_type": "customer", "object_name": "华东智造集团", "object_id": "392650858002653187", "relation_target": "opportunities"},',
  '        "required_sources": {"crm": True},',
  '        "required_agents": {},',
  '        "answer_mode": "list_lookup",',
  '        "multi_object_strategy": "list",',
  '        "answer_scope": "concise",',
  '        "answer_goal": "列出客户名下商机"',
  '    }, ensure_ascii=False),',
  '    has_images="false",',
  '    bound_object_json="{}",',
  '    original_query="看一下华东智造集团有哪些商机"',
  ')',
  'router = evidence_router(',
  '    task_type=plan["task_type"],',
  '    original_query="看一下华东智造集团有哪些商机",',
  '    target_object_json=plan["target_object_json"],',
  '    need_crm=plan["need_crm"],',
  '    need_knowledge=plan["need_knowledge"],',
  '    need_external=plan["need_external"],',
  '    need_attachment=plan["need_attachment"],',
  ')',
  'legacy = crm_evidence(',
  '    response_body=json.dumps({',
  '        "success": False,',
  '        "code": "OBJECT_AMBIGUOUS",',
  '        "message": "找到多个匹配商机，请先选择。",',
  '        "data": {',
  '            "candidates": [',
  '                {"id": "opp-1", "name": "智造一期", "customerName": "华东智造集团", "stageName": "需求确认", "amount": "100000", "possible": "30", "ownerName": "周雨晴"},',
  '                {"id": "opp-2", "name": "智造二期", "customerName": "华东智造集团", "stageName": "方案评审", "amount": "200000", "possible": "50", "ownerName": "周雨晴"}',
  '            ]',
  '        }',
  '    }, ensure_ascii=False),',
  '    crm_action="search_opportunities"',
  ')',
  'light = crm_light(',
  '    crm_evidence_json=legacy["evidence_json"],',
  '    original_query="看一下华东智造集团有哪些商机",',
  '    answer_mode=plan["answer_mode"],',
  '    multi_object_strategy=plan["multi_object_strategy"]',
  ')',
  'proto = protocol(',
  '    direct_answer="", gap_answer="", crm_light_answer=light["answer_text"],',
  '    task_type=plan["task_type"], resolution_status=plan["resolution_status"],',
  '    target_object_json=plan["target_object_json"],',
  '    original_query="看一下华东智造集团有哪些商机", chart_blocks_text=""',
  ')',
  'print(json.dumps({"plan": plan, "router": router, "legacy": json.loads(legacy["evidence_json"]), "light": light, "proto": json.loads(proto["protocol_answer"])}, ensure_ascii=True))',
].join('\n');

const behaviorResult = spawnSync('python', ['-c', behaviorScript, yamlPath], { encoding: 'utf8' });
assert.equal(behaviorResult.status, 0, behaviorResult.stderr || behaviorResult.stdout);
const behavior = JSON.parse(behaviorResult.stdout);
assert.equal(behavior.plan.answer_mode, 'list_lookup', 'CRM read plans should respect planner-selected list_lookup');
assert.equal(behavior.plan.multi_object_strategy, 'list', 'CRM read plans should respect planner-selected multi-object strategy');
assert.equal(behavior.plan.answer_scope, 'concise', 'CRM read plans should preserve planner-selected answer scope');
assert.equal(behavior.plan.agent_route, 'crm_light_answer', 'CRM list lookups should bypass domain agents');
assert.equal(behavior.router.crm_action, 'search_opportunities', 'customer opportunity-list plans should call opportunity search, not customer context');
assert.equal(behavior.router.crm_tool_url_path, '/search-opportunities', 'customer opportunity-list plans should use the opportunity search endpoint');
assert.match(behavior.router.crm_request_body, /华东智造集团/, 'opportunity search should use the target customer name as keyword');
assert.equal(behavior.legacy.tool_status, 'ok', 'legacy OBJECT_AMBIGUOUS candidate responses should be usable evidence');
assert.equal(behavior.legacy.candidates.length, 2, 'legacy ambiguous responses should preserve candidates');
assert.match(behavior.light.answer_text, /智造一期/, 'light answer should list the first opportunity');
assert.match(behavior.light.answer_text, /智造二期/, 'light answer should list the second opportunity');
assert.doesNotMatch(behavior.light.answer_text, /请先选择/, 'list lookup should not ask the user to choose before listing');
assert.match(behavior.proto.answerText, /智造一期/, 'protocol adapter should emit CRM light answer text');

for (const edge of edges) {
  assert.ok(nodeIds.has(edge.source), `edge has missing source: ${edge.id}`);
  assert.ok(nodeIds.has(edge.target), `edge has missing target: ${edge.id}`);
}

console.log('ai-deal-desk-v3 readonly topology smoke test passed');
