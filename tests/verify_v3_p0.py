# -*- coding: utf-8 -*-
import sys
import yaml

YAML_FILE = '多Agent智能助手 - V3 Stable Enhanced.yml'
REPORT_FILE = '多Agent智能助手 V3 改动验证报告.md'
OUT_FILE = 'verify_v3_p0.out'


def collect_strings(value):
    if isinstance(value, str):
        yield value
    elif isinstance(value, list):
        for item in value:
            yield from collect_strings(item)
    elif isinstance(value, dict):
        for item in value.values():
            yield from collect_strings(item)


def node_text(node):
    data = node.get('data', {})
    keys = ['prompt_template', 'system_prompt', 'prompt', 'context_prompt', 'desc', 'title', 'answer']
    parts = []
    for key in keys:
        parts.extend(collect_strings(data.get(key)))
    return '\n'.join(parts)


def variables_of(node):
    variables = []
    for item in node.get('data', {}).get('variables', []) or []:
        if isinstance(item, dict):
            variables.append(item.get('variable', ''))
    return variables


def add(results, name, ok, detail):
    results.append({'name': name, 'status': 'PASS' if ok else 'FAIL', 'detail': detail})


def main():
    with open(YAML_FILE, encoding='utf-8') as f:
        data = yaml.safe_load(f)

    graph = data.get('workflow', {}).get('graph', {})
    nodes_list = graph.get('nodes', [])
    edges = graph.get('edges', [])
    nodes = {node.get('id'): node for node in nodes_list}
    answer_nodes = [n for n in nodes_list if n.get('data', {}).get('type') == 'answer']

    results = []

    route_text = node_text(nodes['route_gate'])
    route_branches = ['full_review', 'sales_task', 'finance_task', 'delivery_task', 'legal_task', 'stats_task', 'quick_answer']
    missing_routes = [branch for branch in route_branches if branch not in route_text]
    add(results, 'P0-1 route_gate 7 分支描述', not missing_routes,
        '已覆盖: ' + ', '.join(route_branches) if not missing_routes else '缺失: ' + ', '.join(missing_routes))

    expert_agents = ['sales_agent', 'finance_agent', 'delivery_agent', 'legal_agent', 'coordinator_agent']
    missing_counter = []
    missing_semantics = []
    for agent_id in expert_agents:
        text = node_text(nodes[agent_id])
        if '反例约束' not in text:
            missing_counter.append(agent_id)
        if not any(keyword in text for keyword in ['拒收', '取消', '修正']):
            missing_semantics.append(agent_id)
    add(results, 'P0-2 5 个专家/协调 Agent 反例约束', not missing_counter and not missing_semantics,
        f'缺失反例约束: {", ".join(missing_counter) or "-"}; 缺失拒收/取消/修正语义: {", ".join(missing_semantics) or "-"}')

    user_agents = ['quick_answer', 'finance_task_agent', 'delivery_task_agent', 'legal_task_agent']
    missing_length = [agent_id for agent_id in user_agents if '长度自适应' not in node_text(nodes[agent_id])]
    add(results, 'P0-3 4 个面用户 Agent 长度自适应', not missing_length,
        '缺失: ' + (', '.join(missing_length) if missing_length else '-'))

    quality_nodes = ['quick_answer', 'finance_task_agent', 'delivery_task_agent', 'legal_task_agent', 'sales_task_agent', 'coordinator_agent']
    quality_requirements = {
        '禁止面向用户自称 Agent': '不要自称“Agent”',
        '隐藏规则编号': '不输出规则编号',
        '隐藏路由字段': 'route_label',
        '日期不解释内部来源': '不解释内部时间来源',
    }
    quality_missing = []
    for node_id in quality_nodes:
        text = node_text(nodes[node_id])
        for label, keyword in quality_requirements.items():
            if keyword not in text:
                quality_missing.append(f'{node_id}:{label}')
    quick_text = node_text(nodes['quick_answer'])
    coordinator_text = node_text(nodes['coordinator_agent'])
    sales_text = node_text(nodes['sales_task_agent'])
    focused_requirements = {
        'quick_answer:30字短答': '严格按短句回答',
        'quick_answer:多Agent智能助手 JSON 示例': 'sample- 前缀 ID',
        'quick_answer:公开资料无结果降级': '未获取可靠公开信息',
        'coordinator:缺信息短输出': '待确认字段 + 可判断部分 + 下一步补齐清单',
        'coordinator:完整评审压缩': '总长度优先控制在 900 字以内',
        'sales_task_agent:话术不夸大痛点': '最紧迫痛点',
        'sales_task_agent:跟进计划时间窗口': '可执行时间窗口',
    }
    focused_text = {
        'quick_answer': quick_text,
        'coordinator': coordinator_text,
        'sales_task_agent': sales_text,
    }
    for key, keyword in focused_requirements.items():
        node_id = key.split(':', 1)[0]
        if keyword not in focused_text[node_id]:
            quality_missing.append(key)
    add(results, '回复质量红线 - 19 用例人工复核项', not quality_missing,
        '缺失: ' + (', '.join(quality_missing) if quality_missing else '-'))

    sales_task_text = node_text(nodes['sales_task_agent'])
    has_output_type = '按 output_type 回答' in sales_task_text and 'output_type' in sales_task_text
    add(results, 'P0-4 sales_task_agent 按 output_type 回答', has_output_type,
        '含 “按 output_type 回答” 段' if has_output_type else '未找到 “按 output_type 回答” 段')

    object_select_text = node_text(nodes['object_select_final'])
    object_select_answer = nodes['object_select_final'].get('data', {}).get('answer', '') or ''
    has_deprecated_date = 'deprecated（2026-07-03）' in object_select_text
    has_deprecated_tag = 'object_select_deprecated' in object_select_text
    answer_has_deprecated_tag = 'object_select_deprecated' in object_select_answer
    add(results, 'P0-5 object_select_final deprecated 标注', has_deprecated_date and has_deprecated_tag and answer_has_deprecated_tag,
        f'desc 日期: {has_deprecated_date}; desc 标签: {has_deprecated_tag}; answer JSON 标签: {answer_has_deprecated_tag}')

    app_desc = data.get('app', {}).get('description', '') or ''
    has_matrix = '模型 / 工具选型矩阵' in app_desc
    has_glm = 'GLM-5.1' in app_desc
    has_qwen = 'qwen3.6-plus' in app_desc
    has_gpt = 'gpt-4o' in app_desc
    add(results, 'P0-6 app.description 选型矩阵', has_matrix and has_glm and has_qwen,
        f'矩阵: {has_matrix}; GLM-5.1: {has_glm}; qwen3.6-plus: {has_qwen}; gpt-4o(评估中): {has_gpt}')

    # 协议契约回归：避免误判引用式 answer 节点。
    required_object_select_fields = ['protocolVersion', 'turnType', 'answerText', 'boundObject', 'warnings']
    missing_object_select_fields = [field for field in required_object_select_fields if field not in object_select_answer]
    optional_object_select_fields = [field for field in ['processEvents', 'writeback'] if field in object_select_answer]
    sales_task_vars = variables_of(nodes['sales_task_final'])
    sales_task_protocol_ref = 'protocol_answer' in sales_task_vars
    protocol_claim = all(keyword in app_desc for keyword in ['protocol JSON', 'protocolVersion', 'turnType', 'answerText', 'boundObject', 'writeback', 'warnings'])
    text_answer_refs = {node_id: variables_of(nodes[node_id]) for node_id in ['quick_final', 'finance_task_final', 'delivery_task_final', 'legal_task_final', 'final_answer']}
    text_refs_ok = all(vars_ for vars_ in text_answer_refs.values())
    protocol_ok = not missing_object_select_fields and sales_task_protocol_ref and protocol_claim and text_refs_ok
    protocol_detail = [
        'object_select_final 必含字段缺失: ' + (', '.join(missing_object_select_fields) if missing_object_select_fields else '-'),
        'object_select_final 可选字段存在: ' + (', '.join(optional_object_select_fields) if optional_object_select_fields else '-'),
        f'sales_task_final 引用 protocol_answer: {sales_task_protocol_ref}',
        f'app.description 明示 protocol JSON 契约: {protocol_claim}',
        '纯文本 answer 节点变量: ' + '; '.join(f'{key}={",".join(value)}' for key, value in text_answer_refs.items()),
    ]
    add(results, '协议契约回归 - answer 字段未破坏', protocol_ok, '\n'.join(protocol_detail))


    # 外部情报 Agent 架构回归：确保旧固定搜索链已被一个 FunctionCalling Agent 替代。
    old_external_ids = [
        '1783001777913', 'external_intel_plan', '1783001538062', 'external_search_query_state',
        'external_search_query_gate', '1783001504330', 'external_extract_urls', 'external_extract_gate',
        'external_extract_skip', '1783001557551', 'external_extract_text', 'external_intel_context',
    ]
    remaining_old = [node_id for node_id in old_external_ids if node_id in nodes]
    external_agent = nodes.get('1783057617112')
    external_agent_ok = bool(external_agent and external_agent.get('data', {}).get('type') == 'agent')
    external_agent_title_ok = bool(external_agent and external_agent.get('data', {}).get('title') == '外部情报检索 Agent')
    external_agent_params = external_agent.get('data', {}).get('agent_parameters', {}) if external_agent else {}
    external_agent_tools = [tool.get('tool_name') for tool in external_agent_params.get('tools', {}).get('value', [])] if external_agent else []
    expected_tools = ['current_time', 'tavily_search', 'tavily_extract']
    external_tools_ok = all(tool_name in external_agent_tools for tool_name in expected_tools)
    external_summary_vars = nodes['external_intel_summary']['data'].get('variables', [])
    external_summary_ok = ['1783057617112', 'text'] in external_summary_vars and ['external_intel_skip', 'output'] in external_summary_vars
    external_edges = {(edge.get('source'), edge.get('target'), edge.get('sourceHandle')) for edge in edges}
    external_edges_ok = (
        ('external_intel_gate', '1783057617112', 'need_search') in external_edges
        and ('1783057617112', 'external_intel_summary', 'source') in external_edges
        and ('external_intel_skip', 'external_intel_summary', 'source') in external_edges
    )
    external_dep_ok = not any('langgenius/json_process' in dep.get('value', {}).get('marketplace_plugin_unique_identifier', '') for dep in data.get('dependencies', []))
    external_agent_arch_ok = not remaining_old and external_agent_ok and external_agent_title_ok and external_tools_ok and external_summary_ok and external_edges_ok and external_dep_ok
    external_agent_detail = [
        '旧外部情报固定链残留: ' + (', '.join(remaining_old) if remaining_old else '-'),
        f'Agent 节点存在且类型正确: {external_agent_ok}',
        f'Agent 中文标题正确: {external_agent_title_ok}',
        'Agent 工具: ' + (', '.join(external_agent_tools) if external_agent_tools else '-'),
        f'external_intel_summary 聚合 Agent text + skip output: {external_summary_ok}',
        f'Agent 主链路连线正确: {external_edges_ok}',
        f'json_process 依赖已移除: {external_dep_ok}',
    ]
    add(results, '外部情报 Agent 架构回归', external_agent_arch_ok, '\n'.join(external_agent_detail))

    # 图片上传参数回归：v3 当前只支持本地图片上传 / 粘贴，不支持 URL 或非图片文件。
    file_upload = data.get('workflow', {}).get('features', {}).get('file_upload', {})
    feature_upload_methods = file_upload.get('allowed_file_upload_methods', [])
    image_transfer_methods = file_upload.get('image', {}).get('transfer_methods', [])
    start_uploaded_file_var = None
    for item in nodes['start'].get('data', {}).get('variables', []) or []:
        if isinstance(item, dict) and item.get('variable') == 'uploaded_files':
            start_uploaded_file_var = item
            break
    start_upload_methods = (start_uploaded_file_var or {}).get('allowed_file_upload_methods', [])
    start_file_types = (start_uploaded_file_var or {}).get('allowed_file_types', [])
    start_file_extensions = (start_uploaded_file_var or {}).get('allowed_file_extensions', [])
    upload_config_ok = (
        feature_upload_methods == ['local_file']
        and image_transfer_methods == ['local_file']
        and start_upload_methods == ['local_file']
        and start_file_types == ['image']
        and all(str(ext).upper() in ['.JPG', '.JPEG', '.PNG', '.GIF', '.WEBP', '.SVG'] for ext in start_file_extensions)
    )
    upload_detail = [
        'features.file_upload.allowed_file_upload_methods: ' + ', '.join(feature_upload_methods),
        'features.file_upload.image.transfer_methods: ' + ', '.join(image_transfer_methods),
        'start.uploaded_files.allowed_file_upload_methods: ' + ', '.join(start_upload_methods),
        'start.uploaded_files.allowed_file_types: ' + ', '.join(start_file_types),
        'start.uploaded_files.allowed_file_extensions: ' + ', '.join(start_file_extensions),
    ]
    add(results, '图片上传参数回归 - 仅本地图片', upload_config_ok, '\n'.join(upload_detail))

    pass_count = sum(1 for item in results if item['status'] == 'PASS')
    fail_count = sum(1 for item in results if item['status'] == 'FAIL')

    out_lines = [
        'YAML parse OK',
        f'kind={data.get("kind")}',
        f'version={data.get("version")}',
        f'app.name={data.get("app", {}).get("name")}',
        f'nodes={len(nodes_list)}',
        f'edges={len(edges)}',
        f'answer_nodes={len(answer_nodes)}',
        '---',
    ]
    for item in results:
        out_lines.append(f'[{item["status"]}] {item["name"]}')
        for line in item['detail'].splitlines():
            out_lines.append('        ' + line)
    out_lines.append('---')
    out_lines.append(f'PASS={pass_count} FAIL={fail_count}')
    with open(OUT_FILE, 'w', encoding='utf-8') as f:
        f.write('\n'.join(out_lines) + '\n')

    report_lines = [
        '# 多Agent智能助手 V3 Stable Enhanced 改动验证报告',
        '',
        '> 验证时间: 2026-07-03  ',
        '> 验证范围: Claude 一轮 v3 改动的 6 个 P0 项目 + 协议契约回归',
        '',
        '## 一、基础信息',
        '',
        f'- YAML 解析: OK (kind={data.get("kind")}, version={data.get("version")})',
        f'- 应用名称: {data.get("app", {}).get("name")}',
        f'- 节点总数: {len(nodes_list)}',
        f'- 边总数: {len(edges)}',
        f'- answer 类型节点数: {len(answer_nodes)}',
        '',
        '## 二、P0 改动逐条验证',
        '',
        '| 验证项 | 状态 | 详情 |',
        '| --- | --- | --- |',
    ]
    for item in results[:-1]:
        detail = item['detail'].replace('|', '\\|').replace('\n', '<br>')
        report_lines.append(f'| {item["name"]} | {item["status"]} | {detail} |')
    report_lines.extend([
        '',
        '## 三、协议契约回归',
        '',
        f'- 状态: **{results[-1]["status"]}**',
        '',
        '口径说明：',
        '',
        '1. `object_select_final` 是静态 JSON answer 节点，本轮只要求保留可解析所需的必含字段：`protocolVersion / turnType / answerText / boundObject / warnings`；`writeback` 对该兜底节点不是必需字段。',
        '2. `sales_task_final` 通过 `protocol_answer` 引用上游 `sales_task_protocol_adapter` 组装好的协议 JSON。',
        '3. `quick_final / finance_task_final / delivery_task_final / legal_task_final / final_answer` 是 `{{#text#}}` 引用式 Markdown 输出，后端 `AiDealDeskService` 会从 Dify outputs 兜底组装 `DealDeskChatResponse`。',
        '4. `app.description` 仍明示所有 answer 节点输出走现有 protocol JSON 契约。',
        '',
        '验证详情：',
        '',
    ])
    for line in results[-1]['detail'].splitlines():
        report_lines.append('- ' + line)
    report_lines.extend([
        '',
        '## 四、结论',
        '',
        f'- PASS: {pass_count}',
        f'- FAIL: {fail_count}',
        '',
        '全部 P0 改动已落地，协议契约未破坏，YAML 可正常解析并可用于重新导入 Dify。' if fail_count == 0 else '仍存在失败项，需要修复后再导入。',
    ])
    with open(REPORT_FILE, 'w', encoding='utf-8') as f:
        f.write('\n'.join(report_lines) + '\n')

    print(f'PASS={pass_count} FAIL={fail_count}')
    print(f'Wrote {OUT_FILE}')
    print(f'Wrote {REPORT_FILE}')
    return 0 if fail_count == 0 else 1


if __name__ == '__main__':
    raise SystemExit(main())


