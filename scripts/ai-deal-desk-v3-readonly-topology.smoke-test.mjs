import assert from 'node:assert/strict';
import { resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

const yamlPath = resolve('AI Deal Desk - V3 Stable Enhanced.yml');

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
  'image_answer',
  'evidence_router',
  'crm_evidence',
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

assert.ok(hasEdge('task_type_gate', 'general_chat', 'simple_answer'), 'general_chat should use the light answer path');
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
assert.ok(hasEdge('simple_answer', 'source', 'protocol_adapter'), 'simple answers should use the unified protocol adapter');
assert.ok(hasEdge('image_answer', 'source', 'protocol_adapter'), 'image answers should use the unified protocol adapter');
assert.ok(hasEdge('gap_answer', 'source', 'protocol_adapter'), 'gap answers should use the unified protocol adapter');
assert.ok(hasEdge('business_answer_agent', 'source', 'protocol_adapter'), 'business answers should use the unified protocol adapter');
assert.ok(hasEdge('protocol_adapter', 'source', 'final_answer'), 'protocol adapter should be the only final answer source');

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
assert.match(taskPlanNormalizeCode, /crm_light_answer/, 'list/detail CRM reads should bypass domain agents');

const crmEvidenceCode = nodeById.get('crm_evidence')?.data?.code || '';
assert.match(crmEvidenceCode, /candidates/, 'CRM evidence should preserve candidate lists');
assert.match(crmEvidenceCode, /OBJECT_AMBIGUOUS/, 'CRM evidence should remain compatible with legacy ambiguous candidate responses');

const queryRewrite = nodeById.get('query_rewrite');
assert.equal(queryRewrite?.data?.type, 'code', 'knowledge query rewrite should be deterministic code, not an LLM');
assert.ok(queryRewrite?.data?.outputs?.text, 'knowledge query rewrite should expose text output');
assert.match(queryRewrite?.data?.code || '', /MAX_CHARS\s*=\s*260/, 'knowledge query rewrite should cap query length');
const knowledgeNode = nodeById.get('deal_rules_knowledge');
assert.deepEqual(knowledgeNode?.data?.query_variable_selector, ['query_rewrite', 'text']);

const plannerInstruction = nodeById.get('task_planner')?.data?.agent_parameters?.instruction?.value || '';
assert.match(plannerInstruction, /直接输出 stats_analysis/, 'planner should route management analytics without object search');
assert.match(plannerInstruction, /不要为了示例客户.*调用客户\/商机搜索工具/, 'planner should not search opportunity examples for analytics');
assert.match(plannerInstruction, /已绑定对象规则/, 'planner should define the bound-object no-detail-tool rule');
assert.match(plannerInstruction, /bound_object_id.*route_opportunity_id.*route_customer_id.*selected_object_id/s, 'planner should recognize existing bound CRM object identifiers');
assert.match(plannerInstruction, /不要调用 search_customers、search_opportunities、get_customer_context 或 get_opportunity_context/, 'planner should not call CRM search/detail tools when the object is already bound');
assert.match(plannerInstruction, /answer_mode/, 'planner should ask for answer_mode');
assert.match(plannerInstruction, /multi_object_strategy/, 'planner should ask for multi_object_strategy');

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
  'crm_evidence = load_main("crm_evidence")',
  'crm_light = load_main("crm_light_answer")',
  'protocol = load_main("protocol_adapter")',
  'plan = normalize(',
  '    planner_text=json.dumps({',
  '        "task_type": "object_query",',
  '        "target_object": {"object_type": "customer", "object_name": "华东智造集团"},',
  '        "required_sources": {"crm": True},',
  '        "required_agents": {},',
  '        "answer_goal": "列出客户名下商机"',
  '    }, ensure_ascii=False),',
  '    has_images="false",',
  '    bound_object_json="{}",',
  '    original_query="看一下华东智造集团有哪些商机"',
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
  '    simple_answer="", image_answer="", gap_answer="",',
  '    crm_light_answer=light["answer_text"], business_answer="",',
  '    task_type=plan["task_type"], target_object_json=plan["target_object_json"],',
  '    original_query="看一下华东智造集团有哪些商机", chart_blocks_text=""',
  ')',
  'print(json.dumps({"plan": plan, "legacy": json.loads(legacy["evidence_json"]), "light": light, "proto": json.loads(proto["protocol_answer"])}, ensure_ascii=True))',
].join('\n');

const behaviorResult = spawnSync('python', ['-c', behaviorScript, yamlPath], { encoding: 'utf8' });
assert.equal(behaviorResult.status, 0, behaviorResult.stderr || behaviorResult.stdout);
const behavior = JSON.parse(behaviorResult.stdout);
assert.equal(behavior.plan.answer_mode, 'list_lookup', 'opportunity list questions should infer list_lookup');
assert.equal(behavior.plan.multi_object_strategy, 'list', 'opportunity list questions should list multiple objects');
assert.equal(behavior.plan.agent_route, 'crm_light_answer', 'CRM list lookups should bypass domain agents');
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
