import assert from 'node:assert/strict';
import { mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { join, resolve } from 'node:path';
import { spawnSync } from 'node:child_process';

const yamlPath = resolve('chatflows/ai-deal-desk-v3.example.yml');

const extractResult = spawnSync(
  'python',
  [
    '-c',
    [
      'import json, sys, yaml',
      'with open(sys.argv[1], encoding="utf-8") as f:',
      '    data = yaml.safe_load(f)',
      'nodes = data.get("workflow", {}).get("graph", {}).get("nodes", [])',
      'edges = data.get("workflow", {}).get("graph", {}).get("edges", [])',
      'blocks = [node.get("data", {}).get("code") for node in nodes if node.get("data", {}).get("code")]',
      'payload = {"blocks": blocks, "nodes": nodes, "edges": edges, "raw": data}',
      'print(json.dumps(payload, ensure_ascii=False))',
    ].join('\n'),
    yamlPath,
  ],
  { encoding: 'utf8' }
);

assert.equal(
  extractResult.status,
  0,
  `expected PyYAML extraction to succeed, but failed with:\n${extractResult.stderr || extractResult.stdout}`
);

const workflow = JSON.parse(extractResult.stdout);
const blocks = workflow.blocks;
const nodes = workflow.nodes;
const edges = workflow.edges;
const raw = workflow.raw;

assert.ok(blocks.length > 0, 'expected to find embedded python code blocks in the v3 Dify YAML');

const tempDir = mkdtempSync(join(tmpdir(), 'ai-deal-desk-v3-python-'));

try {
  blocks.forEach((code, index) => {
    const scriptPath = join(tempDir, `block-${index + 1}.py`);
    writeFileSync(scriptPath, code, 'utf8');

    const result = spawnSync('python', ['-m', 'py_compile', scriptPath], {
      encoding: 'utf8',
    });

    assert.equal(
      result.status,
      0,
      `python code block ${index + 1} should compile cleanly, but failed with:\n${result.stderr || result.stdout}`
    );
  });

  const nodeIds = new Set(nodes.map((node) => node.id));
  const edgeIds = new Set(edges.map((edge) => edge.id));
  const nodeById = new Map(nodes.map((node) => [node.id, node]));
  const hasEdge = (source, handle, target) =>
    edges.some((edge) => edge.source === source && edge.sourceHandle === handle && edge.target === target);

  assert.equal(nodeIds.size, nodes.length, 'node ids should be unique');
  assert.equal(edgeIds.size, edges.length, 'edge ids should be unique');

  for (const edge of edges) {
    assert.ok(nodeIds.has(edge.source), `edge should reference existing source node: ${edge.id}`);
    assert.ok(nodeIds.has(edge.target), `edge should reference existing target node: ${edge.id}`);
  }

  for (const requiredNode of [
    'attachment_image_state',
    'attachment_image_gate',
    'attachment_image_summary',
    'attachment_image_text',
    'attachment_context',
    'input_parse',
    'conversation_context',
    'task_planner',
    'task_plan_normalize',
    'task_type_gate',
    'evidence_router',
    'crm_read_request',
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
    'crm_light_answer',
    'business_answer_agent',
    'protocol_adapter',
    'final_answer',
  ]) {
    assert.ok(nodeIds.has(requiredNode), `readonly chatflow node should exist: ${requiredNode}`);
  }

  for (const removedNode of [
    'normalize_context',
    'route_gate',
    'rule_route_gate',
    'writeback_memory_state',
    'writeback_memory_assigner',
    'sales_task_protocol_adapter',
    'finance_task_protocol_adapter',
    'delivery_task_protocol_adapter',
    'legal_task_protocol_adapter',
    'coordinator_protocol_adapter',
  ]) {
    assert.ok(!nodeIds.has(removedNode), `old mixed-route node should be removed: ${removedNode}`);
  }

  assert.ok(hasEdge('attachment_context', 'source', 'input_parse'), 'attachment context should feed input parsing');
  assert.ok(hasEdge('conversation_context', 'source', 'task_planner'), 'conversation context should feed Planner');
  assert.ok(hasEdge('task_plan_normalize', 'source', 'task_type_gate'), 'normalized plan should feed task gate');
  assert.ok(hasEdge('task_type_gate', 'general_chat', 'protocol_adapter'), 'Planner direct general answer should bypass the business chain');
  assert.ok(hasEdge('task_type_gate', 'image_answer', 'image_answer'), 'image answer should bypass business chain');
  assert.ok(hasEdge('task_type_gate', 'business_task', 'evidence_router'), 'business tasks should enter evidence router');
  assert.ok(hasEdge('agent_router', 'crm_light_answer', 'crm_light_answer'), 'CRM lightweight reads should route to the light answer node');
  assert.ok(hasEdge('crm_light_answer', 'source', 'protocol_adapter'), 'CRM light answers should use the unified protocol adapter');
  assert.ok(hasEdge('protocol_adapter', 'source', 'final_answer'), 'final output should come from unified protocol adapter');
  assert.ok(hasEdge('crm_evidence', 'source', 'knowledge_context_prepare'), 'CRM evidence should feed knowledge context preparation');
  assert.ok(hasEdge('attachment_evidence', 'source', 'knowledge_context_prepare'), 'attachment evidence should feed knowledge context preparation');
  assert.ok(hasEdge('knowledge_context_prepare', 'source', 'knowledge_policy_gate'), 'knowledge policy should be evaluated only after CRM and attachment evidence are ready');
  assert.ok(hasEdge('knowledge_policy_gate', 'need_knowledge', 'knowledge_query_decider'), 'eligible Planner policies should enter the semantic knowledge decision');
  assert.ok(hasEdge('knowledge_query_decider', 'source', 'query_rewrite'), 'semantic knowledge decision should feed deterministic validation');
  assert.ok(hasEdge('query_rewrite', 'source', 'knowledge_gate'), 'validated decision should gate actual retrieval');

  const queryValidator = nodeById.get('query_rewrite');
  assert.doesNotMatch(queryValidator?.data?.code || '', /TASK_TERMS/, 'knowledge validation must not use a fixed task-term dictionary');
  assert.match(queryValidator?.data?.code || '', /MAX_CHARS\s*=\s*260/, 'knowledge query should remain capped at 260 characters');

  const imageSummaryNode = nodeById.get('attachment_image_summary');
  assert.deepEqual(
    imageSummaryNode?.data?.vision?.configs?.variable_selector,
    ['start', 'uploaded_files'],
    'attachment_image_summary should read uploaded images from the Start node file-list input'
  );

  const uploadedFilesVariable = raw.workflow.graph.nodes
    .find((node) => node.id === 'start')
    ?.data?.variables?.find((item) => item.variable === 'uploaded_files');

  assert.equal(
    uploadedFilesVariable?.allowed_file_upload_methods?.includes('local_file'),
    true,
    'start.uploaded_files should accept Dify platform local file uploads'
  );

  assert.equal(
    uploadedFilesVariable?.type,
    'file-list',
    'start.uploaded_files should remain a file-list variable'
  );

  const protocolAdapter = nodeById.get('protocol_adapter');
  assert.match(protocolAdapter?.data?.code || '', /'writeback': \{\}/, 'protocol adapter should keep writeback as an empty object');
  assert.match(protocolAdapter?.data?.code || '', /'boundObject':/, 'protocol adapter should emit boundObject');

  const finalAnswer = nodeById.get('final_answer');
  assert.equal(
    finalAnswer?.data?.answer,
    '{{#protocol_adapter.protocol_answer#}}',
    'single final answer node should emit the unified protocol adapter output'
  );
} finally {
  rmSync(tempDir, { recursive: true, force: true });
}

console.log('ai-deal-desk-v3 python code blocks smoke test passed');
