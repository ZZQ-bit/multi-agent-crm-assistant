import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { resolve } from 'node:path';

const yamlPath = resolve('chatflows/ai-deal-desk-v3.example.yml');

const pythonScript = `
from pathlib import Path
import json
import yaml

p = Path(r"""${yamlPath.replace(/\\/g, '\\\\')}""")
data = yaml.safe_load(p.read_text(encoding="utf-8"))
nodes = {node.get("id"): node for node in data["workflow"]["graph"]["nodes"]}

def load_main(node_id):
    ns = {}
    exec(nodes[node_id]["data"]["code"], ns)
    return ns["main"]

sample_tool_response = {
    "code": "OK",
    "message": "",
    "data": {
        "opportunity": {
            "id": "demo_o_064",
            "name": "南京数据治理中心低优先级小单1期",
            "stage": "CREATE",
            "stageName": "新建",
            "possible": "20",
            "amount": "85000",
            "customerId": "demo_c_027",
            "customerName": "南京数据治理中心",
        },
        "businessFacts": {
            "opportunityName": "南京数据治理中心低优先级小单1期",
            "stageName": "新建",
            "winRate": "20",
            "amount": "85000",
            "primaryContact": "邓明",
        },
        "riskSignals": ["当前商机阶段：新建", "赢率低于 50%，需要补充关键推进证据。"],
        "missingFields": ["paymentTerms", "deliveryDeadline", "acceptanceCriteria", "decisionMakers"],
    },
}

crm_main = load_main("crm_evidence")
ledger_main = load_main("evidence_ledger")
protocol_main = load_main("protocol_adapter")

crm = crm_main(response_body=json.dumps(sample_tool_response, ensure_ascii=False), crm_action="get_opportunity_context")
ledger = ledger_main(
    crm_evidence_json=crm.get("evidence_json", ""),
    knowledge_evidence_json="{}",
    external_evidence_json="{}",
    attachment_evidence_json="{}",
    target_object_json=json.dumps({"object_type": "opportunity", "object_id": "demo_o_064", "object_name": "南京数据治理中心低优先级小单1期"}, ensure_ascii=False),
    answer_goal="画销售推进漏斗图并解释当前卡点",
    success_criteria_json=json.dumps(["输出可渲染图表", "解释当前卡点"], ensure_ascii=False),
)

def call_protocol(query):
    try:
        raw = protocol_main(
            direct_answer="### 结论\\n该商机当前仍处于新建阶段。",
            gap_answer="",
            crm_light_answer="",
            task_type="progress_summary",
            resolution_status="resolved",
            target_object_json=ledger.get("target_object_json", "{}"),
            original_query=query,
            chart_blocks_text=ledger.get("chart_blocks_text", ""),
        )
        payload = json.loads(raw.get("protocol_answer", "{}"))
        return {"error": "", "answerText": payload.get("answerText", "")}
    except TypeError as exc:
        return {"error": str(exc), "answerText": ""}

print(json.dumps({
    "crm": crm,
    "ledger": ledger,
    "chart_request": call_protocol("基于南京数据治理中心低优先级小单1期的 CRM 信息，帮我画一个销售推进漏斗图，并解释当前卡点。"),
    "plain_request": call_protocol("总结南京数据治理中心低优先级小单1期当前推进情况。"),
}, ensure_ascii=True))
`;

const parsed = JSON.parse(execFileSync('python', ['-c', pythonScript], { encoding: 'utf8' }));

const crmEvidence = JSON.parse(parsed.crm.evidence_json || '{}');
assert.ok(Array.isArray(crmEvidence.charts), 'CRM evidence should expose chart candidates');
assert.equal(crmEvidence.charts[0]?.type, 'funnel', 'single-opportunity progress should use funnel chart');
assert.deepEqual(
  crmEvidence.charts[0]?.data?.map((item) => item.name),
  ['需求确认', '方案评审', '商务沟通', '合同推进', '赢单预测'],
  'single-opportunity funnel should keep the expected sales stage order'
);

const chartBlocksText = parsed.ledger.chart_blocks_text || '';
assert.match(chartBlocksText, /```chart/, 'evidence ledger should expose chart fenced blocks');
assert.match(chartBlocksText, /"type":\s*"funnel"/, 'chart fenced block should contain a funnel config');

assert.equal(parsed.chart_request.error, '', 'protocol adapter should accept chart_blocks_text for chart requests');
assert.match(parsed.chart_request.answerText, /```chart/, 'chart requests should be backfilled with a chart fence if the model omitted it');
assert.doesNotMatch(parsed.chart_request.answerText, /```text/, 'chart requests should not be rendered as text fences');

assert.equal(parsed.plain_request.error, '', 'protocol adapter should also accept ordinary requests');
assert.doesNotMatch(parsed.plain_request.answerText, /```chart/, 'ordinary requests should not receive chart fences as a side effect');

console.log('ai-deal-desk-v3 chart output smoke test passed');
