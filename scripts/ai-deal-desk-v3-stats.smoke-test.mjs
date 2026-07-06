import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { resolve } from 'node:path';

const yamlPath = resolve('AI Deal Desk - V3 Stable Enhanced.yml');

const pythonScript = `
from pathlib import Path
import json
import yaml

p = Path(r"""${yamlPath.replace(/\\/g, '\\\\')}""")
data = yaml.safe_load(p.read_text(encoding="utf-8"))
nodes = {node.get("id"): node for node in data["workflow"]["graph"]["nodes"]}
edges = data["workflow"]["graph"]["edges"]

payload = {
    "task_plan_normalize": nodes["task_plan_normalize"]["data"]["code"],
    "evidence_router": nodes["evidence_router"]["data"]["code"],
    "agent_router_cases": nodes["agent_router"]["data"].get("cases", []),
    "protocol_adapter": nodes["protocol_adapter"]["data"]["code"],
    "edge_ids": [edge.get("id") for edge in edges],
}
print(json.dumps(payload, ensure_ascii=True))
`;

const parsed = JSON.parse(
  execFileSync('python', ['-c', pythonScript], { encoding: 'utf8' })
);

const taskPlanNormalize = parsed.task_plan_normalize ?? '';
const evidenceRouter = parsed.evidence_router ?? '';
const agentRouterCases = parsed.agent_router_cases ?? [];
const protocolAdapter = parsed.protocol_adapter ?? '';
const edgeIds = parsed.edge_ids ?? [];

assert.match(
  taskPlanNormalize,
  /task_type == 'stats_analysis'/,
  'task plan normalizer should recognize stats_analysis as a dedicated CRM business task'
);

assert.match(
  taskPlanNormalize,
  /required_agents\['analytics'\] = True/,
  'stats_analysis should request the analytics Agent'
);

assert.match(
  evidenceRouter,
  /task_type\) == 'stats_analysis'/,
  'evidence router should have a dedicated stats_analysis branch'
);

assert.match(
  evidenceRouter,
  /get-funnel-snapshot/,
  'stats_analysis should be able to call a CRM aggregate stats endpoint'
);

assert.ok(
  agentRouterCases.some((item) => item?.case_id === 'analytics'),
  'agent_router should include an analytics branch'
);

assert.ok(
  edgeIds.includes('agent_router-analytics-analytics_agent-target'),
  'analytics route should run analytics_agent'
);

assert.ok(
  edgeIds.includes('analytics_agent-source-agent_merge-target'),
  'analytics_agent output should merge with domain findings'
);

assert.ok(
  edgeIds.includes('agent_merge-source-business_answer_agent-target'),
  'merged domain findings should feed the business answer agent'
);

assert.match(
  protocolAdapter,
  /'stats_analysis': 'stats_query'/,
  'stats_analysis should map to turnType=stats_query for frontend compatibility'
);

console.log('ai-deal-desk-v3-stats smoke test passed');
