from __future__ import annotations

import copy
import json
from pathlib import Path

import yaml


YAML_PATH = Path("多Agent智能助手 - V3 Stable Enhanced.yml")


def _clone_node(nodes_by_id, source_id, new_id, title, node_type=None, x=0, y=0, width=242, height=120):
    node = copy.deepcopy(nodes_by_id[source_id])
    node["id"] = new_id
    node["position"] = {"x": x, "y": y}
    node["positionAbsolute"] = {"x": x, "y": y}
    node["width"] = width
    node["height"] = height
    node["selected"] = False
    node["data"]["selected"] = False
    node["data"]["title"] = title
    if node_type:
        node["data"]["type"] = node_type
    return node


def _code_node(template, new_id, title, desc, variables, outputs, code, x, y, height=130):
    node = _clone_node(template, "attachment_image_state", new_id, title, "code", x, y, height=height)
    node["data"].update(
        {
            "desc": desc,
            "variables": [{"variable": name, "value_selector": selector} for name, selector in variables],
            "outputs": {name: {"children": None, "type": typ} for name, typ in outputs.items()},
            "code_language": "python3",
            "code": code,
        }
    )
    return node


def _if_node(template, new_id, title, desc, cases, x, y, height=180):
    node = _clone_node(template, "main_route_gate", new_id, title, "if-else", x, y, height=height)
    rendered = []
    for case_id, variable_selector, value in cases:
        rendered.append(
            {
                "case_id": case_id,
                "id": case_id,
                "logical_operator": "and",
                "conditions": [
                    {
                        "comparison_operator": "contains",
                        "id": f"cond_{new_id}_{case_id}",
                        "value": value,
                        "varType": "string",
                        "variable_selector": variable_selector,
                    }
                ],
            }
        )
    node["data"].update({"desc": desc, "cases": rendered})
    return node


def _aggregator_node(template, new_id, title, desc, variables, x, y, height=120):
    node = _clone_node(template, "attachment_image_text", new_id, title, "variable-aggregator", x, y, height=height)
    node["data"].update({"desc": desc, "output_type": "string", "variables": variables})
    return node


def _edge(source, handle, target):
    return {
        "data": {"isInIteration": False, "sourceType": "", "targetType": ""},
        "id": f"{source}-{handle}-{target}-target",
        "selected": False,
        "source": source,
        "sourceHandle": handle,
        "target": target,
        "targetHandle": "target",
        "type": "custom",
        "zIndex": 0,
    }


INPUT_PARSE_CODE = r"""
import json


def _clean(value):
    if value is None:
        return ''
    return str(value).strip()


def _json(value):
    return json.dumps(value, ensure_ascii=False)


def main(
    user_query: str = '',
    has_images: str = '',
    attachment_names: str = '',
    attachments_summary: str = '',
    bound_object_type: str = '',
    bound_object_id: str = '',
    bound_object_name: str = '',
    bound_object_source: str = '',
    route_customer_id: str = '',
    route_opportunity_id: str = '',
    selected_object_type: str = '',
    selected_object_id: str = '',
    selected_object_name: str = '',
) -> dict:
    bound = {}
    if _clean(bound_object_type) or _clean(bound_object_id) or _clean(bound_object_name):
        bound = {
            'objectType': _clean(bound_object_type),
            'objectId': _clean(bound_object_id),
            'objectName': _clean(bound_object_name),
            'source': _clean(bound_object_source) or 'page_context',
        }
    elif _clean(selected_object_id):
        bound = {
            'objectType': _clean(selected_object_type) or 'opportunity',
            'objectId': _clean(selected_object_id),
            'objectName': _clean(selected_object_name),
            'source': 'user_selection',
        }
    elif _clean(route_customer_id):
        bound = {
            'objectType': 'customer',
            'objectId': _clean(route_customer_id),
            'objectName': '',
            'source': 'route_context',
        }
    elif _clean(route_opportunity_id):
        bound = {
            'objectType': 'opportunity',
            'objectId': _clean(route_opportunity_id),
            'objectName': '',
            'source': 'route_context',
        }

    return {
        'original_query': _clean(user_query),
        'has_images': 'true' if _clean(has_images).lower() == 'true' else 'false',
        'attachment_names': _clean(attachment_names),
        'attachments_summary': _clean(attachments_summary),
        'bound_object_json': _json(bound),
        'page_context_text': '\n'.join([
            f"当前绑定对象：{bound.get('objectType', 'none')} {bound.get('objectName', '')} {bound.get('objectId', '')}".strip(),
            f"附件名称：{_clean(attachment_names) or '无'}",
            f"附件摘要：{_clean(attachments_summary) or '无'}",
        ]),
    }
"""


CONVERSATION_CONTEXT_CODE = r"""
import json


def _clean(value):
    if value is None:
        return ''
    return str(value).strip()


def main(
    original_query: str = '',
    bound_object_json: str = '',
    page_context_text: str = '',
    attachments_summary: str = '',
) -> dict:
    return {
        'original_query': _clean(original_query),
        'bound_object_json': _clean(bound_object_json) or '{}',
        'context_text': '\n'.join([
            f"用户问题：{_clean(original_query)}",
            _clean(page_context_text),
            f"附件摘要：{_clean(attachments_summary) or '无'}",
        ]).strip(),
    }
"""


TASK_PLAN_NORMALIZE_CODE = r"""
import json
import re


VALID_TASK_TYPES = {
    'general_chat', 'image_answer', 'object_query', 'progress_summary',
    'sales_assist', 'finance_check', 'delivery_check', 'legal_check',
    'full_review', 'stats_analysis', 'content_draft', 'followup_plan',
    'external_research', 'unknown',
}


def _clean(value):
    if value is None:
        return ''
    return str(value).strip()


def _load_json(text):
    text = _clean(text)
    if not text:
        return {}
    try:
        return json.loads(text)
    except Exception:
        match = re.search(r'\{.*\}', text, flags=re.S)
        if not match:
            return {}
        try:
            return json.loads(match.group(0))
        except Exception:
            return {}


def _bool(value):
    if isinstance(value, bool):
        return value
    return _clean(value).lower() in ['true', '1', 'yes', 'y', '是', '需要']


def main(planner_text: str = '', has_images: str = '', bound_object_json: str = '') -> dict:
    payload = _load_json(planner_text)
    task_type = _clean(payload.get('task_type')) if isinstance(payload, dict) else 'unknown'
    if task_type not in VALID_TASK_TYPES:
        task_type = 'unknown'

    image_present = _bool(has_images)
    if task_type == 'image_answer' and not image_present:
        task_type = 'general_chat'

    business_tasks = VALID_TASK_TYPES - {'general_chat', 'image_answer', 'unknown'}
    if task_type == 'general_chat':
        route_group = 'general_chat'
    elif task_type == 'image_answer':
        route_group = 'image_answer'
    elif task_type in business_tasks:
        route_group = 'business_task'
    else:
        route_group = 'general_chat'

    required_sources = payload.get('required_sources') if isinstance(payload.get('required_sources'), dict) else {}
    required_agents = payload.get('required_agents') if isinstance(payload.get('required_agents'), dict) else {}
    target = payload.get('target_object') if isinstance(payload.get('target_object'), dict) else {}
    if not target:
        target = _load_json(bound_object_json)

    if task_type in ['finance_check', 'delivery_check', 'legal_check', 'full_review']:
        required_sources['knowledge'] = True
        required_sources['crm'] = True
    if task_type in ['object_query', 'progress_summary', 'sales_assist', 'stats_analysis', 'content_draft', 'followup_plan']:
        required_sources['crm'] = True
    if task_type == 'external_research':
        required_sources['external'] = True
    if task_type == 'full_review':
        required_agents.update({'sales': True, 'finance': True, 'delivery': True, 'legal': True})
    elif task_type == 'finance_check':
        required_agents['finance'] = True
    elif task_type == 'delivery_check':
        required_agents['delivery'] = True
    elif task_type == 'legal_check':
        required_agents['legal'] = True
    elif task_type == 'stats_analysis':
        required_agents['analytics'] = True
    elif task_type in ['progress_summary', 'sales_assist', 'content_draft', 'followup_plan', 'external_research']:
        required_agents['sales'] = True

    if _bool(required_agents.get('sales')) and _bool(required_agents.get('finance')) and _bool(required_agents.get('delivery')) and _bool(required_agents.get('legal')):
        agent_route = 'full_review'
    elif _bool(required_agents.get('finance')):
        agent_route = 'finance'
    elif _bool(required_agents.get('delivery')):
        agent_route = 'delivery'
    elif _bool(required_agents.get('legal')):
        agent_route = 'legal'
    elif _bool(required_agents.get('analytics')):
        agent_route = 'analytics'
    elif _bool(required_agents.get('sales')):
        agent_route = 'sales'
    else:
        agent_route = 'none'

    return {
        'task_type': task_type,
        'route_group': route_group,
        'target_object_json': json.dumps(target if isinstance(target, dict) else {}, ensure_ascii=False),
        'need_crm': 'true' if _bool(required_sources.get('crm')) else 'false',
        'need_knowledge': 'true' if _bool(required_sources.get('knowledge')) else 'false',
        'need_external': 'true' if _bool(required_sources.get('external')) else 'false',
        'need_attachment': 'true' if image_present or _bool(required_sources.get('attachment')) else 'false',
        'agent_route': agent_route,
        'answer_goal': _clean(payload.get('answer_goal')),
        'success_criteria_json': json.dumps(payload.get('success_criteria') or [], ensure_ascii=False),
        'planner_reason': _clean(payload.get('reason')),
        'planner_confidence': str(payload.get('confidence') or ''),
    }
"""


EVIDENCE_ROUTER_CODE = r"""
import json


def _clean(value):
    if value is None:
        return ''
    return str(value).strip()


def _loads(value, fallback):
    if isinstance(value, (dict, list)):
        return value
    text = _clean(value)
    if not text:
        return fallback
    try:
        return json.loads(text)
    except Exception:
        return fallback


def _bool(value):
    return _clean(value).lower() in ['true', '1', 'yes', 'y', '是', '需要']


def main(
    task_type: str = '',
    original_query: str = '',
    target_object_json: str = '',
    need_crm: str = '',
    need_knowledge: str = '',
    need_external: str = '',
    need_attachment: str = '',
) -> dict:
    target = _loads(target_object_json, {})
    object_type = _clean(target.get('object_type') or target.get('objectType'))
    object_id = _clean(target.get('object_id') or target.get('objectId'))
    object_name = _clean(target.get('object_name') or target.get('objectName'))
    query = _clean(original_query)

    endpoint = ''
    payload = {}
    action = 'none'
    if _bool(need_crm):
        if object_type == 'opportunity' and object_id:
            endpoint = 'get-opportunity-context'
            action = 'get_opportunity_context'
            payload = {'opportunityId': object_id, 'limit': 10}
        elif object_type == 'customer' and object_id:
            endpoint = 'get-customer-context'
            action = 'get_customer_context'
            payload = {'customerId': object_id, 'limit': 10}
        elif _clean(task_type) == 'stats_analysis':
            endpoint = 'get-funnel-snapshot'
            action = 'get_funnel_snapshot'
            payload = {}
        elif object_name:
            search_opportunity = any(word in (object_name + ' ' + query).lower() for word in ['商机', '项目', '机会', '报价', 'deal', 'opportunity'])
            endpoint = 'search-opportunities' if search_opportunity else 'search-customers'
            action = 'search_opportunities' if search_opportunity else 'search_customers'
            payload = {'keyword': object_name, 'limit': 5, 'includeContextOnUnique': True}
        elif query:
            endpoint = 'search-customers'
            action = 'search_customers'
            payload = {'keyword': query[:80], 'limit': 5, 'includeContextOnUnique': True}

    return {
        'need_crm': 'true' if endpoint else 'false',
        'need_knowledge': 'true' if _bool(need_knowledge) else 'false',
        'need_external': 'true' if _bool(need_external) else 'false',
        'need_attachment': 'true' if _bool(need_attachment) else 'false',
        'crm_action': action,
        'crm_tool_url_path': '/' + endpoint if endpoint else '',
        'crm_request_body': json.dumps(payload, ensure_ascii=False),
        'evidence_plan_text': '; '.join([
            f"task_type={_clean(task_type)}",
            f"crm={action}",
            f"knowledge={_bool(need_knowledge)}",
            f"external={_bool(need_external)}",
            f"attachment={_bool(need_attachment)}",
        ]),
    }
"""


CRM_EVIDENCE_CODE = r"""
import json


def _clean(value):
    if value is None:
        return ''
    return str(value).strip()


def _loads(value, fallback):
    if isinstance(value, (dict, list)):
        return value
    text = _clean(value)
    if not text:
        return fallback
    try:
        return json.loads(text)
    except Exception:
        return fallback


def _number(value, default=0):
    text = _clean(value).replace(',', '').replace('%', '')
    if not text:
        return default
    try:
        return int(round(float(text)))
    except Exception:
        return default


def _single_deal_progress_chart(data):
    if not isinstance(data, dict):
        return None
    opportunity = data.get('opportunity') if isinstance(data.get('opportunity'), dict) else {}
    facts = data.get('businessFacts') if isinstance(data.get('businessFacts'), dict) else {}
    if not opportunity:
        return None

    opportunity_name = (
        _clean(opportunity.get('name'))
        or _clean(facts.get('opportunityName'))
        or '当前商机'
    )
    stage = _clean(opportunity.get('stage'))
    stage_name = _clean(opportunity.get('stageName')) or _clean(facts.get('stageName'))
    win_rate = _number(opportunity.get('possible') or facts.get('winRate'), 0)

    stage_index = -1
    stage_key = (stage + ' ' + stage_name).lower()
    if 'success' in stage_key or '赢单' in stage_key or '成交' in stage_key or '签约' in stage_key:
        stage_index = 4
    elif 'contract' in stage_key or '合同' in stage_key:
        stage_index = 3
    elif 'business_procurement' in stage_key or '商务' in stage_key or '采购' in stage_key:
        stage_index = 2
    elif 'solution' in stage_key or '方案' in stage_key or '评审' in stage_key or '验证' in stage_key:
        stage_index = 1
    elif 'clear_requirements' in stage_key or '需求' in stage_key:
        stage_index = 0
    elif 'create' in stage_key or '新建' in stage_key:
        stage_index = -1

    stage_names = ['需求确认', '方案评审', '商务沟通', '合同推进']
    values = []
    for index, name in enumerate(stage_names):
        if stage_index >= index:
            value = 100
        elif index == max(stage_index + 1, 0):
            value = win_rate
        else:
            value = 0
        values.append({'name': name, 'value': value})
    values.append({'name': '赢单预测', 'value': win_rate})

    return {
        'type': 'funnel',
        'title': f'{opportunity_name} - 销售推进漏斗',
        'unit': '%',
        'data': values,
    }


def main(response_body: str = '', crm_action: str = '') -> dict:
    envelope = _loads(response_body, {})
    facts = []
    objects = []
    gaps = []
    tool_errors = []
    charts = []

    code = _clean(envelope.get('code'))
    message = _clean(envelope.get('message'))
    data = envelope.get('data') if isinstance(envelope.get('data'), dict) else {}
    if code == 'SKIPPED' or not envelope:
        gaps.append('本轮未调用 CRM 读取工具。')
    elif code and code != 'OK':
        tool_errors.append(message or f'CRM 工具返回 {code}')
    elif data:
        facts.append(json.dumps(data, ensure_ascii=False)[:3500])
        if isinstance(data.get('chart'), dict):
            charts.append(data.get('chart'))
        progress_chart = _single_deal_progress_chart(data)
        if progress_chart:
            charts.append(progress_chart)
        if isinstance(data.get('customer'), dict):
            cust = data.get('customer') or {}
            objects.append({'objectType': 'customer', 'objectId': _clean(cust.get('id')), 'objectName': _clean(cust.get('name'))})
        if isinstance(data.get('opportunity'), dict):
            opp = data.get('opportunity') or {}
            objects.append({'objectType': 'opportunity', 'objectId': _clean(opp.get('id')), 'objectName': _clean(opp.get('name'))})
        if isinstance(data.get('candidates'), list):
            for item in data.get('candidates')[:5]:
                if isinstance(item, dict):
                    objects.append({
                        'objectType': 'candidate',
                        'objectId': _clean(item.get('id')),
                        'objectName': _clean(item.get('name')),
                        'customerName': _clean(item.get('customerName')),
                    })
    else:
        gaps.append('CRM 工具未返回可展示数据。')

    evidence = {'source': 'crm', 'facts': facts, 'objects': objects, 'gaps': gaps, 'tool_errors': tool_errors, 'action': _clean(crm_action), 'charts': charts}
    return {'evidence_json': json.dumps(evidence, ensure_ascii=False), 'evidence_text': json.dumps(evidence, ensure_ascii=False)}
"""


KNOWLEDGE_EVIDENCE_CODE = r"""
import json


def _clean(value):
    if value is None:
        return ''
    return str(value).strip()


def _loads(value, fallback):
    if isinstance(value, (dict, list)):
        return value
    text = _clean(value)
    if not text:
        return fallback
    try:
        return json.loads(text)
    except Exception:
        return fallback


def main(knowledge_result=None, skipped: str = '') -> dict:
    rules = []
    gaps = []
    if _clean(skipped):
        gaps.append('本轮未检索知识库规则。')
    items = knowledge_result if isinstance(knowledge_result, list) else []
    for item in items[:8]:
        if not isinstance(item, dict):
            continue
        title = _clean(item.get('title'))
        content = _clean(item.get('content'))
        if title or content:
            rules.append({'title': title, 'content': content[:800], 'url': _clean(item.get('url'))})
    if not rules and not gaps:
        gaps.append('未召回直接相关的知识库规则。')
    evidence = {'source': 'knowledge', 'rules': rules, 'gaps': gaps, 'tool_errors': []}
    return {'evidence_json': json.dumps(evidence, ensure_ascii=False), 'evidence_text': json.dumps(evidence, ensure_ascii=False)}
"""


EXTERNAL_EVIDENCE_CODE = r"""
import json
import re


def _clean(value):
    if value is None:
        return ''
    return str(value).strip()


def main(external_text: str = '') -> dict:
    text = _clean(external_text)
    sources = []
    for match in re.finditer(r'\[([^\]]+)\]\((https?://[^)]+)\)', text):
        sources.append({'title': match.group(1), 'url': match.group(2)})
    gaps = [] if text else ['本轮未获取外部公开资料。']
    facts = [text] if text else []
    evidence = {'source': 'external', 'facts': facts, 'sources': sources[:5], 'gaps': gaps, 'tool_errors': []}
    return {'evidence_json': json.dumps(evidence, ensure_ascii=False), 'evidence_text': json.dumps(evidence, ensure_ascii=False)}
"""


ATTACHMENT_EVIDENCE_CODE = r"""
import json


def _clean(value):
    if value is None:
        return ''
    return str(value).strip()


def main(attachment_names: str = '', attachments_summary: str = '') -> dict:
    facts = []
    gaps = []
    if _clean(attachments_summary):
        facts.append(f"附件/图片摘要：{_clean(attachments_summary)}")
    else:
        gaps.append('本轮没有可用附件摘要。')
    evidence = {'source': 'attachment', 'facts': facts, 'gaps': gaps, 'tool_errors': [], 'attachment_names': _clean(attachment_names)}
    return {'evidence_json': json.dumps(evidence, ensure_ascii=False), 'evidence_text': json.dumps(evidence, ensure_ascii=False)}
"""


EVIDENCE_LEDGER_CODE = r"""
import json


def _clean(value):
    if value is None:
        return ''
    return str(value).strip()


def _loads(value, fallback):
    if isinstance(value, (dict, list)):
        return value
    text = _clean(value)
    if not text:
        return fallback
    try:
        return json.loads(text)
    except Exception:
        return fallback


def _number(value, default=0):
    try:
        return int(round(float(str(value).replace(',', '').replace('%', '').strip())))
    except Exception:
        return default


def _valid_chart(chart):
    if not isinstance(chart, dict):
        return None
    chart_type = _clean(chart.get('type') or chart.get('chartType'))
    if chart_type not in ['bar', 'line', 'pie', 'donut', 'funnel']:
        return None
    data = chart.get('data')
    if not isinstance(data, list) or not data:
        return None
    safe_data = []
    for item in data:
        if not isinstance(item, dict):
            continue
        name = _clean(item.get('name'))
        if not name:
            continue
        safe_data.append({'name': name[:40], 'value': _number(item.get('value'), 0)})
    if not safe_data:
        return None
    return {
        'type': chart_type,
        'title': _clean(chart.get('title'))[:80],
        'unit': _clean(chart.get('unit'))[:12],
        'data': safe_data,
    }


def _chart_fence(chart):
    safe_chart = _valid_chart(chart)
    if not safe_chart:
        return ''
    return '```chart\n' + json.dumps(safe_chart, ensure_ascii=False, separators=(',', ':')) + '\n```'


def main(
    crm_evidence_json: str = '',
    knowledge_evidence_json: str = '',
    external_evidence_json: str = '',
    attachment_evidence_json: str = '',
    target_object_json: str = '',
    answer_goal: str = '',
    success_criteria_json: str = '',
) -> dict:
    evidences = [
        _loads(crm_evidence_json, {}),
        _loads(knowledge_evidence_json, {}),
        _loads(external_evidence_json, {}),
        _loads(attachment_evidence_json, {}),
    ]
    facts, sources, rules, gaps, errors, objects, charts = [], [], [], [], [], [], []
    for evidence in evidences:
        if not isinstance(evidence, dict):
            continue
        facts.extend(evidence.get('facts') or [])
        sources.extend(evidence.get('sources') or [])
        rules.extend(evidence.get('rules') or [])
        gaps.extend(evidence.get('gaps') or [])
        errors.extend(evidence.get('tool_errors') or [])
        objects.extend(evidence.get('objects') or [])
        charts.extend(evidence.get('charts') or [])

    target = _loads(target_object_json, {})
    if not target and objects:
        target = objects[0]
    chart_blocks = [_chart_fence(chart) for chart in charts[:3]]
    chart_blocks_text = '\n\n'.join(block for block in chart_blocks if block)

    ledger = {
        'answer_goal': _clean(answer_goal),
        'target_object': target if isinstance(target, dict) else {},
        'facts': facts,
        'sources': sources,
        'rules': rules,
        'gaps': gaps,
        'tool_errors': errors,
        'charts': charts,
        'chart_blocks_text': chart_blocks_text,
        'success_criteria': _loads(success_criteria_json, []),
    }
    ledger_text = '\n'.join([
        f"回答目标：{_clean(answer_goal) or '未明确'}",
        f"目标对象：{json.dumps(ledger['target_object'], ensure_ascii=False)}",
        "CRM/公开/附件事实：\n" + ('\n'.join(f"- {item}" for item in facts[:10]) or "- 无"),
        "规则依据：\n" + ('\n'.join(f"- {item.get('title')}: {item.get('content')}" for item in rules[:5] if isinstance(item, dict)) or "- 无"),
        "来源链接：\n" + ('\n'.join(f"- {item.get('title')}: {item.get('url')}" for item in sources[:5] if isinstance(item, dict)) or "- 无"),
        "缺失信息：\n" + ('\n'.join(f"- {item}" for item in gaps[:8]) or "- 无"),
        "工具错误：\n" + ('\n'.join(f"- {item}" for item in errors[:5]) or "- 无"),
    ])
    ledger['ledger_text'] = ledger_text
    return {
        'ledger_json': json.dumps(ledger, ensure_ascii=False),
        'ledger_text': ledger_text,
        'target_object_json': json.dumps(ledger['target_object'], ensure_ascii=False),
        'chart_blocks_text': chart_blocks_text,
    }
"""


GAP_CHECK_CODE = r"""
import json


def _clean(value):
    if value is None:
        return ''
    return str(value).strip()


def _loads(value, fallback):
    if isinstance(value, (dict, list)):
        return value
    text = _clean(value)
    if not text:
        return fallback
    try:
        return json.loads(text)
    except Exception:
        return fallback


def main(task_type: str = '', target_object_json: str = '', original_query: str = '') -> dict:
    target = _loads(target_object_json, {})
    requires_object = _clean(task_type) in [
        'object_query', 'progress_summary', 'sales_assist', 'finance_check',
        'delivery_check', 'legal_check', 'full_review', 'content_draft',
        'followup_plan', 'external_research',
    ]
    query = _clean(original_query)
    has_target = bool(target and (_clean(target.get('objectId')) or _clean(target.get('object_id')) or _clean(target.get('objectName')) or _clean(target.get('object_name'))))
    if requires_object and not has_target and any(word in query for word in ['当前商机', '这个商机', '当前客户', '这个客户']):
        answer = '我需要先知道你指的是哪个客户或商机。请在当前页面绑定对象，或直接告诉我客户/商机名称。'
        return {'gap_status': 'need_clarification', 'gap_answer': answer, 'reason': 'missing_target_object'}
    return {'gap_status': 'answerable', 'gap_answer': '', 'reason': ''}
"""


GAP_ANSWER_CODE = r"""
def _clean(value):
    if value is None:
        return ''
    return str(value).strip()


def main(gap_answer: str = '') -> dict:
    return {'answer_text': _clean(gap_answer) or '我还需要更多信息才能继续，请补充客户、商机或你希望分析的具体内容。'}
"""


PROTOCOL_ADAPTER_CODE = r"""
import json
import re


def _clean(value):
    if value is None:
        return ''
    return str(value).strip()


def _strip_think(text):
    return re.sub(r'<think>.*?</think>', '', _clean(text), flags=re.S).strip()


def _loads(value, fallback):
    if isinstance(value, (dict, list)):
        return value
    text = _clean(value)
    if not text:
        return fallback
    try:
        return json.loads(text)
    except Exception:
        return fallback


def _expects_chart(query):
    text = _clean(query).lower()
    return any(word in text for word in [
        '图表', '图形', '画图', '可视化', '漏斗图', '漏斗', '柱状图', '折线图', '饼图',
        'chart', 'funnel', 'bar chart', 'visualize',
    ])


def _remove_plain_chart_fences(answer):
    text = _clean(answer)
    pattern = r'```(?:text|txt|plain)?\s*\n(?=[\s\S]{0,1200}(?:漏斗|需求确认|方案评审|商务沟通|合同推进|赢单预测))[\s\S]*?```'
    return re.sub(pattern, '', text, flags=re.I).strip()


def _turn_type(task_type):
    mapping = {
        'general_chat': 'quick_answer',
        'image_answer': 'quick_answer',
        'object_query': 'object_select',
        'progress_summary': 'text_analysis',
        'sales_assist': 'text_analysis',
        'finance_check': 'text_analysis',
        'delivery_check': 'text_analysis',
        'legal_check': 'text_analysis',
        'full_review': 'deep_deal_review_brief',
        'stats_analysis': 'stats_query',
        'content_draft': 'text_analysis',
        'followup_plan': 'text_analysis',
        'external_research': 'text_analysis',
    }
    return mapping.get(_clean(task_type), 'text_analysis')


def main(
    simple_answer: str = '',
    image_answer: str = '',
    gap_answer: str = '',
    business_answer: str = '',
    task_type: str = '',
    target_object_json: str = '',
    original_query: str = '',
    chart_blocks_text: str = '',
) -> dict:
    answer = _strip_think(business_answer) or _strip_think(gap_answer) or _strip_think(image_answer) or _strip_think(simple_answer)
    if not answer:
        answer = '我还没有拿到足够信息来回答这个问题。请补充客户、商机或你希望我分析的具体内容。'
    chart_blocks = _clean(chart_blocks_text)
    if _expects_chart(original_query) and chart_blocks:
        answer = _remove_plain_chart_fences(answer)
        if '```chart' not in answer:
            answer = (answer.rstrip() + '\n\n' + chart_blocks).strip()
    bound = _loads(target_object_json, {})
    payload = {
        'protocolVersion': '1.0',
        'turnType': _turn_type(task_type),
        'answerText': answer,
        'processEvents': [],
        'writeback': {},
        'boundObject': bound if isinstance(bound, dict) else {},
    }
    return {'protocol_answer': json.dumps(payload, ensure_ascii=False)}
"""


BUSINESS_ANSWER_PROMPT = """你是 多Agent智能助手 的 CRM 业务回答生成器。你不直接调用工具，只基于 Planner、证据台账和领域 Agent 结论生成给销售看的回答。

回答要求：
- 先给结论，再给依据。
- 能回答多少回答多少，缺什么说清楚。
- 不暴露节点名、路由名、工具 payload、JSON 字段。
- 如果用户要求草稿、邮件、话术或跟进计划，直接给可复制内容。
- 如果用户要求保存或写入 CRM，说明当前版本只生成内容，不执行写入。
- 不声称已审批、已写回、已完成 CRM 操作。
- 如果用户明确要求画图、图表、漏斗图或可视化，禁止用 ASCII 图、Mermaid 或 text 代码块模拟图表；只输出业务解释，图表由系统 chart fence 承载。
- 输出标准 Markdown，保持简洁、业务可用。

用户问题：{{#conversation_context.original_query#}}
回答目标：{{#task_plan_normalize.answer_goal#}}
成功标准：{{#task_plan_normalize.success_criteria_json#}}

证据台账：
{{#evidence_ledger.ledger_text#}}

领域 Agent 结论：
{{#agent_merge.output#}}
"""


DOMAIN_PROMPTS = {
    "sales_agent": "你是销售协同 Agent，只从销售推进、客户意向、决策链、跟进动作角度判断。基于证据台账输出固定字段：domain, verdict, risk_level, key_evidence, risks, missing, actions。\n\n证据台账：\n{{#evidence_ledger.ledger_text#}}\n\n回答目标：{{#task_plan_normalize.answer_goal#}}",
    "finance_agent": "你是财务判断 Agent，只从折扣、付款、账期、回款风险角度判断。基于证据台账输出固定字段：domain, verdict, risk_level, key_evidence, risks, missing, actions。\n\n证据台账：\n{{#evidence_ledger.ledger_text#}}\n\n回答目标：{{#task_plan_normalize.answer_goal#}}",
    "delivery_agent": "你是交付判断 Agent，只从上线周期、交付范围、资源、集成、验收准备角度判断。基于证据台账输出固定字段：domain, verdict, risk_level, key_evidence, risks, missing, actions。\n\n证据台账：\n{{#evidence_ledger.ledger_text#}}\n\n回答目标：{{#task_plan_normalize.answer_goal#}}",
    "legal_agent": "你是合同判断 Agent，只从验收、赔付、责任边界、数据安全、特殊承诺角度判断。基于证据台账输出固定字段：domain, verdict, risk_level, key_evidence, risks, missing, actions。\n\n证据台账：\n{{#evidence_ledger.ledger_text#}}\n\n回答目标：{{#task_plan_normalize.answer_goal#}}",
    "analytics_agent": "你是经营分析 Agent，只从漏斗、转化、赢单、收入、回款、阶段卡点角度判断。基于证据台账输出固定字段：domain, verdict, risk_level, key_evidence, risks, missing, actions。\n\n证据台账：\n{{#evidence_ledger.ledger_text#}}\n\n回答目标：{{#task_plan_normalize.answer_goal#}}",
}


def main():
    data = yaml.safe_load(YAML_PATH.read_text(encoding="utf-8"))
    graph = data["workflow"]["graph"]
    old_nodes = {node["id"]: node for node in graph["nodes"]}

    # Keep file upload / image handling and start variables as-is.
    start = copy.deepcopy(old_nodes["start"])
    start["position"] = {"x": 0, "y": 0}
    start["positionAbsolute"] = {"x": 0, "y": 0}

    nodes = [
        start,
        _clone_node(old_nodes, "attachment_image_state", "attachment_image_state", "附件 - 图片状态判断", x=320, y=120),
        _clone_node(old_nodes, "attachment_image_gate", "attachment_image_gate", "附件 - 是否有图片", x=640, y=110, height=180),
        _clone_node(old_nodes, "attachment_image_summary", "attachment_image_summary", "附件 - 图片/截图识别", x=960, y=0, height=170),
        _clone_node(old_nodes, "attachment_image_text", "attachment_image_text", "附件 - 图片摘要汇合", x=1280, y=110),
        _clone_node(old_nodes, "attachment_context", "attachment_context", "附件上下文整理", x=1600, y=110),
        _code_node(
            old_nodes,
            "input_parse",
            "输入解析",
            "整理用户输入、页面绑定对象和附件摘要。",
            [
                ("user_query", ["sys", "query"]),
                ("has_images", ["attachment_image_state", "has_images"]),
                ("attachment_names", ["attachment_context", "attachment_names"]),
                ("attachments_summary", ["attachment_context", "attachments_summary"]),
                ("bound_object_type", ["start", "bound_object_type"]),
                ("bound_object_id", ["start", "bound_object_id"]),
                ("bound_object_name", ["start", "bound_object_name"]),
                ("bound_object_source", ["start", "bound_object_source"]),
                ("route_customer_id", ["start", "route_customer_id"]),
                ("route_opportunity_id", ["start", "route_opportunity_id"]),
                ("selected_object_type", ["start", "selected_object_type"]),
                ("selected_object_id", ["start", "selected_object_id"]),
                ("selected_object_name", ["start", "selected_object_name"]),
            ],
            {
                "original_query": "string",
                "has_images": "string",
                "attachment_names": "string",
                "attachments_summary": "string",
                "bound_object_json": "string",
                "page_context_text": "string",
            },
            INPUT_PARSE_CODE,
            1920,
            110,
            height=160,
        ),
        _code_node(
            old_nodes,
            "conversation_context",
            "上下文整理",
            "形成 Planner 和下游证据链共用的会话上下文。",
            [
                ("original_query", ["input_parse", "original_query"]),
                ("bound_object_json", ["input_parse", "bound_object_json"]),
                ("page_context_text", ["input_parse", "page_context_text"]),
                ("attachments_summary", ["input_parse", "attachments_summary"]),
            ],
            {"original_query": "string", "bound_object_json": "string", "context_text": "string"},
            CONVERSATION_CONTEXT_CODE,
            2240,
            110,
        ),
    ]

    planner = _clone_node(old_nodes, "main_route_decider", "task_planner", "Planner - 任务理解与规划", x=2560, y=110, height=260)
    planner["data"]["desc"] = "只做任务规划、对象识别和能力选择，不直接回答用户。"
    planner["data"]["agent_parameters"]["instruction"]["value"] = """你是 多Agent智能助手 的 Planner。你的任务不是回答用户，而是判断本轮 CRM 工作任务类型、目标对象、需要哪些证据来源、需要哪些领域 Agent，以及回答目标。

你可以使用 CRM 只读工具辅助识别客户或商机。不要调用写入工具，不要生成最终答案。

工具调用边界：只有用户问题需要识别具体客户或商机时才调用 CRM 搜索工具。遇到漏斗、转化、赢单率、收入、回款统计、经营分析、排优先级这类销售管理问题时，直接输出 stats_analysis，不要为了示例客户或“这类商机”调用客户/商机搜索工具。

已绑定对象规则：如果页面上下文或输入变量已经提供 bound_object_id、route_opportunity_id、route_customer_id 或 selected_object_id，说明对象已绑定或已选择完成。此时不要调用 search_customers、search_opportunities、get_customer_context 或 get_opportunity_context；直接使用已绑定对象输出规划，详情统一交给后续 CRM 数据读取节点。

任务类型 task_type 只能取：
general_chat, image_answer, object_query, progress_summary, sales_assist, finance_check, delivery_check, legal_check, full_review, stats_analysis, content_draft, followup_plan, external_research, unknown。

证据来源 required_sources 包括 crm, knowledge, external, attachment。
领域 Agent required_agents 包括 sales, finance, delivery, legal, analytics。

路由规则：
- 普通闲聊、身份介绍、概念解释：general_chat。
- 用户只要求读取图片/截图内容：image_answer。
- 查客户/商机/联系人/跟进记录：object_query。
- 总结当前客户或商机进展：progress_summary。
- 推进建议、销售动作、拜访提纲：sales_assist。
- 折扣、付款、账期、回款：finance_check。
- 上线周期、交付范围、资源、实施：delivery_check。
- 合同、验收、赔付、责任边界：legal_check。
- 同时要求销售、财务、交付、合同多视角：full_review。
- 漏斗、转化、赢单率、收入、回款统计：stats_analysis。
- 邮件、话术、周报、汇报材料：content_draft。
- 跟进计划：followup_plan。
- 官网、新闻、招投标、公开资料、政策、舆情：external_research。

输出硬约束：只能输出一个 JSON 对象，不要 Markdown，不要代码块，不要解释。
JSON 结构：
{
  "task_type": "general_chat",
  "target_object": {
    "object_type": "none",
    "object_name": "",
    "object_id": "",
    "relation_target": "none"
  },
  "required_sources": {
    "crm": false,
    "knowledge": false,
    "external": false,
    "attachment": false
  },
  "required_agents": {
    "sales": false,
    "finance": false,
    "delivery": false,
    "legal": false,
    "analytics": false
  },
  "answer_goal": "",
  "success_criteria": [],
  "reason": "",
  "confidence": 0.0
}"""
    planner["data"]["agent_parameters"]["query"]["value"] = """用户问题：{{#conversation_context.original_query#}}

页面与附件上下文：
{{#conversation_context.context_text#}}"""
    nodes.append(planner)

    nodes.extend(
        [
            _code_node(
                old_nodes,
                "task_plan_normalize",
                "任务计划标准化",
                "解析 Planner JSON，输出稳定任务路由和能力开关。",
                [
                    ("planner_text", ["task_planner", "text"]),
                    ("has_images", ["input_parse", "has_images"]),
                    ("bound_object_json", ["conversation_context", "bound_object_json"]),
                ],
                {
                    "task_type": "string",
                    "route_group": "string",
                    "target_object_json": "string",
                    "need_crm": "string",
                    "need_knowledge": "string",
                    "need_external": "string",
                    "need_attachment": "string",
                    "agent_route": "string",
                    "answer_goal": "string",
                    "success_criteria_json": "string",
                    "planner_reason": "string",
                    "planner_confidence": "string",
                },
                TASK_PLAN_NORMALIZE_CODE,
                2880,
                110,
                height=170,
            ),
            _if_node(
                old_nodes,
                "task_type_gate",
                "任务类型路由",
                "普通问答、图片直答和 CRM 业务任务分流。",
                [
                    ("general_chat", ["task_plan_normalize", "route_group"], "general_chat"),
                    ("image_answer", ["task_plan_normalize", "route_group"], "image_answer"),
                    ("business_task", ["task_plan_normalize", "route_group"], "business_task"),
                ],
                3200,
                110,
                height=210,
            ),
        ]
    )

    simple = _clone_node(old_nodes, "preflight_quick_answer", "simple_answer", "轻量回答", x=3520, y=-120, height=150)
    simple["data"]["desc"] = "普通问答、身份介绍和概念解释，直接回答用户问题。"
    image = _clone_node(old_nodes, "image_direct_answer", "image_answer", "图片理解与回答", x=3520, y=80, height=150)
    image["data"]["desc"] = "只基于图片/截图识别内容回答。"
    nodes.extend([simple, image])

    nodes.extend(
        [
            _code_node(
                old_nodes,
                "evidence_router",
                "证据收集调度",
                "按任务计划决定 CRM、知识库、外部情报和附件证据是否启用。",
                [
                    ("task_type", ["task_plan_normalize", "task_type"]),
                    ("original_query", ["conversation_context", "original_query"]),
                    ("target_object_json", ["task_plan_normalize", "target_object_json"]),
                    ("need_crm", ["task_plan_normalize", "need_crm"]),
                    ("need_knowledge", ["task_plan_normalize", "need_knowledge"]),
                    ("need_external", ["task_plan_normalize", "need_external"]),
                    ("need_attachment", ["task_plan_normalize", "need_attachment"]),
                ],
                {
                    "need_crm": "string",
                    "need_knowledge": "string",
                    "need_external": "string",
                    "need_attachment": "string",
                    "crm_action": "string",
                    "crm_tool_url_path": "string",
                    "crm_request_body": "string",
                    "evidence_plan_text": "string",
                },
                EVIDENCE_ROUTER_CODE,
                3520,
                330,
                height=180,
            ),
            _if_node(
                old_nodes,
                "crm_read_gate",
                "是否读取 CRM",
                "按需调用 CRM 只读工具。",
                [("call_crm", ["evidence_router", "need_crm"], "true")],
                3840,
                210,
            ),
        ]
    )

    crm_request = _clone_node(old_nodes, "crm_tool_request", "crm_read_request", "CRM 数据读取", x=4160, y=120, height=160)
    crm_request["data"]["title"] = "CRM 数据读取"
    crm_request["data"]["desc"] = "调用 CordysCRM 只读 Tool API。"
    crm_request["data"]["url"] = "{{#env.CRM_TOOL_BASE_URL#}}{{#evidence_router.crm_tool_url_path#}}"
    crm_request["data"]["body"]["data"][0]["value"] = "{{#evidence_router.crm_request_body#}}"
    nodes.append(crm_request)

    nodes.extend(
        [
            _code_node(
                old_nodes,
                "crm_read_skip",
                "CRM 读取跳过响应",
                "未启用 CRM 时输出空证据 envelope。",
                [],
                {"output": "string"},
                "def main() -> dict:\n    return {'output': '{\"success\": true, \"code\": \"SKIPPED\", \"data\": {}, \"message\": \"\"}'}\n",
                4160,
                310,
            ),
            _aggregator_node(
                old_nodes,
                "crm_read_merge",
                "CRM 读取结果汇合",
                "汇合 CRM HTTP 响应或跳过响应。",
                [["crm_read_request", "body"], ["crm_read_skip", "output"]],
                4480,
                220,
            ),
            _code_node(
                old_nodes,
                "crm_evidence",
                "CRM 证据包整理",
                "把 CRM 工具 envelope 整理为统一证据包。",
                [("response_body", ["crm_read_merge", "output"]), ("crm_action", ["evidence_router", "crm_action"])],
                {"evidence_json": "string", "evidence_text": "string"},
                CRM_EVIDENCE_CODE,
                4800,
                220,
                height=170,
            ),
            _if_node(
                old_nodes,
                "knowledge_gate",
                "是否检索知识库",
                "按需召回销售、财务、交付、合同规则。",
                [("need_knowledge", ["evidence_router", "need_knowledge"], "true")],
                3840,
                520,
            ),
        ]
    )

    query_rewrite = _code_node(
        old_nodes,
        "query_rewrite",
        "规则检索查询改写",
        "用稳定代码生成短检索串，避免 LLM 改写失控导致知识库查询超长。",
        [
            ("original_query", ["conversation_context", "original_query"]),
            ("task_type", ["task_plan_normalize", "task_type"]),
            ("answer_goal", ["task_plan_normalize", "answer_goal"]),
        ],
        {"text": "string"},
        r"""
import re


MAX_CHARS = 260
TASK_TERMS = {
    'full_review': '商机评审 销售 财务 交付 合同 风险 下一步动作',
    'finance_check': '付款 首付 尾款 账期 回款 折扣 财务风险',
    'delivery_check': '上线周期 交付范围 资源 验收 交付风险',
    'legal_check': '合同条款 验收 赔付 责任边界 数据安全',
    'sales_assist': '销售推进 决策链 客户意向 跟进动作',
    'followup_plan': '跟进计划 动作 负责人 时间建议',
    'progress_summary': '商机进展 已确认事实 风险 下一步',
    'content_draft': '沟通话术 邮件 微信 客户沟通',
    'stats_analysis': '销售漏斗 阶段转化 赢单率 经营分析',
}


def _clean(value):
    if value is None:
        return ''
    return re.sub(r'\s+', ' ', str(value)).strip()


def _shorten(text, limit):
    text = _clean(text)
    return text if len(text) <= limit else text[:limit]


def main(original_query: str = '', task_type: str = '', answer_goal: str = '') -> dict:
    task = _clean(task_type)
    parts = [
        _shorten(original_query, 120),
        TASK_TERMS.get(task, ''),
        _shorten(answer_goal, 80),
    ]
    seen = set()
    tokens = []
    for part in parts:
        for token in re.split(r'[，。；;、\s]+', part):
            token = _clean(token)
            if not token or token in seen:
                continue
            seen.add(token)
            tokens.append(token)
    query = ' '.join(tokens)
    return {'text': query[:MAX_CHARS]}
""",
        4160,
        440,
        height=150,
    )
    knowledge = _clone_node(old_nodes, "deal_rules_knowledge", "deal_rules_knowledge", "知识库检索", x=4480, y=440, height=150)
    knowledge["data"]["query_variable_selector"] = ["query_rewrite", "text"]
    nodes.extend([query_rewrite, knowledge])

    nodes.extend(
        [
            _code_node(
                old_nodes,
                "knowledge_skip",
                "知识库跳过响应",
                "未启用知识库时输出空数组。",
                [],
                {"output": "string"},
                "def main() -> dict:\n    return {'output': '[]'}\n",
                4160,
                630,
            ),
            _aggregator_node(
                old_nodes,
                "knowledge_merge",
                "知识库结果汇合",
                "汇合知识库召回或跳过响应。",
                [["deal_rules_knowledge", "result"], ["knowledge_skip", "output"]],
                4800,
                520,
            ),
            _code_node(
                old_nodes,
                "knowledge_evidence",
                "知识库证据包整理",
                "把召回规则整理为统一证据包。",
                [("knowledge_result", ["deal_rules_knowledge", "result"]), ("skipped", ["knowledge_skip", "output"])],
                {"evidence_json": "string", "evidence_text": "string"},
                KNOWLEDGE_EVIDENCE_CODE,
                5120,
                520,
            ),
            _if_node(
                old_nodes,
                "external_gate",
                "是否检索外部情报",
                "按需调用 Tavily 检索官网、新闻、招投标和公开资料。",
                [("need_external", ["evidence_router", "need_external"], "true")],
                3840,
                830,
            ),
        ]
    )

    external = _clone_node(old_nodes, "external_intel_agent", "external_intel_agent", "外部情报检索 Agent", x=4160, y=760, height=170)
    external["data"]["agent_parameters"]["query"]["value"] = """用户问题：{{#conversation_context.original_query#}}
回答目标：{{#task_plan_normalize.answer_goal#}}
页面上下文：{{#conversation_context.context_text#}}

请只检索与本轮问题有关的公开资料，保留 1-3 个来源链接。"""
    nodes.append(external)
    nodes.extend(
        [
            _code_node(
                old_nodes,
                "external_skip",
                "外部情报跳过响应",
                "未启用外部情报时输出空字符串。",
                [],
                {"output": "string"},
                "def main() -> dict:\n    return {'output': ''}\n",
                4160,
                940,
            ),
            _aggregator_node(
                old_nodes,
                "external_merge",
                "外部情报结果汇合",
                "汇合 Tavily Agent 输出或跳过响应。",
                [["external_intel_agent", "text"], ["external_skip", "output"]],
                4480,
                830,
            ),
            _code_node(
                old_nodes,
                "external_evidence",
                "外部情报证据包整理",
                "提取公开资料摘要和来源链接。",
                [("external_text", ["external_merge", "output"])],
                {"evidence_json": "string", "evidence_text": "string"},
                EXTERNAL_EVIDENCE_CODE,
                4800,
                830,
            ),
            _code_node(
                old_nodes,
                "attachment_evidence",
                "附件内容整理",
                "把图片/附件摘要整理为统一证据包。",
                [("attachment_names", ["input_parse", "attachment_names"]), ("attachments_summary", ["input_parse", "attachments_summary"])],
                {"evidence_json": "string", "evidence_text": "string"},
                ATTACHMENT_EVIDENCE_CODE,
                4800,
                1080,
            ),
        ]
    )

    nodes.extend(
        [
            _code_node(
                old_nodes,
                "evidence_ledger",
                "证据汇总",
                "汇总 CRM 事实、规则依据、外部信息、附件内容、缺失信息和工具错误。",
                [
                    ("crm_evidence_json", ["crm_evidence", "evidence_json"]),
                    ("knowledge_evidence_json", ["knowledge_evidence", "evidence_json"]),
                    ("external_evidence_json", ["external_evidence", "evidence_json"]),
                    ("attachment_evidence_json", ["attachment_evidence", "evidence_json"]),
                    ("target_object_json", ["task_plan_normalize", "target_object_json"]),
                    ("answer_goal", ["task_plan_normalize", "answer_goal"]),
                    ("success_criteria_json", ["task_plan_normalize", "success_criteria_json"]),
                ],
                {"ledger_json": "string", "ledger_text": "string", "target_object_json": "string", "chart_blocks_text": "string"},
                EVIDENCE_LEDGER_CODE,
                5440,
                620,
                height=190,
            ),
            _code_node(
                old_nodes,
                "gap_check",
                "证据完整度判断",
                "只有目标对象或必要输入完全缺失时追问，否则继续回答。",
                [
                    ("task_type", ["task_plan_normalize", "task_type"]),
                    ("target_object_json", ["evidence_ledger", "target_object_json"]),
                    ("original_query", ["conversation_context", "original_query"]),
                ],
                {"gap_status": "string", "gap_answer": "string", "reason": "string"},
                GAP_CHECK_CODE,
                5760,
                620,
            ),
            _if_node(
                old_nodes,
                "gap_check_gate",
                "缺口路由",
                "缺关键对象时输出缺口回答，否则进入业务 Agent 路由。",
                [
                    ("need_clarification", ["gap_check", "gap_status"], "need_clarification"),
                    ("answerable", ["gap_check", "gap_status"], "answerable"),
                ],
                6080,
                620,
            ),
            _code_node(
                old_nodes,
                "gap_answer",
                "缺口回答",
                "说明缺什么，等待用户补充。",
                [("gap_answer", ["gap_check", "gap_answer"])],
                {"answer_text": "string"},
                GAP_ANSWER_CODE,
                6400,
                460,
            ),
            _if_node(
                old_nodes,
                "agent_router",
                "业务 Agent 路由",
                "按需选择一个或多个领域 Agent。",
                [
                    ("full_review", ["task_plan_normalize", "agent_route"], "full_review"),
                    ("sales", ["task_plan_normalize", "agent_route"], "sales"),
                    ("finance", ["task_plan_normalize", "agent_route"], "finance"),
                    ("delivery", ["task_plan_normalize", "agent_route"], "delivery"),
                    ("legal", ["task_plan_normalize", "agent_route"], "legal"),
                    ("analytics", ["task_plan_normalize", "agent_route"], "analytics"),
                    ("no_domain_agent", ["task_plan_normalize", "agent_route"], "none"),
                ],
                6400,
                740,
                height=300,
            ),
        ]
    )

    for node_id, title, y in [
        ("sales_agent", "销售协同 Agent", 1120),
        ("finance_agent", "财务判断 Agent", 1320),
        ("delivery_agent", "交付判断 Agent", 1520),
        ("legal_agent", "合同判断 Agent", 1720),
    ]:
        node = _clone_node(old_nodes, node_id, node_id, title, x=6720, y=y, height=150)
        node["data"]["prompt_template"] = [{"id": f"{node_id}-system", "role": "system", "text": DOMAIN_PROMPTS[node_id]}]
        nodes.append(node)
    analytics = _clone_node(old_nodes, "sales_task_agent", "analytics_agent", "经营分析 Agent", x=6720, y=1920, height=150)
    analytics["data"]["prompt_template"] = [{"id": "analytics-agent-system", "role": "system", "text": DOMAIN_PROMPTS["analytics_agent"]}]
    nodes.append(analytics)

    nodes.append(
        _code_node(
            old_nodes,
            "agent_skip",
            "无需领域 Agent",
            "无需领域 Agent 时输出占位结论，保证 CRM 业务回答始终经过领域结论汇总。",
            [],
            {"output": "string"},
            "def main() -> dict:\n    return {'output': '本轮不需要单独的销售、财务、交付、合同或经营分析 Agent，直接基于证据台账生成回答。'}\n",
            6720,
            2120,
            height=130,
        )
    )

    nodes.extend(
        [
            _aggregator_node(
                old_nodes,
                "agent_merge",
                "领域结论汇总",
                "汇总销售、财务、交付、合同和经营分析 Agent 结论。",
                [
                    ["sales_agent", "text"],
                    ["finance_agent", "text"],
                    ["delivery_agent", "text"],
                    ["legal_agent", "text"],
                    ["analytics_agent", "text"],
                    ["agent_skip", "output"],
                ],
                7040,
                1500,
                height=170,
            )
        ]
    )

    business_answer = _clone_node(old_nodes, "sales_task_agent", "business_answer_agent", "CRM 业务回答生成", x=7360, y=900, height=220)
    business_answer["data"]["desc"] = "统一生成 CRM 业务回答、判断、建议、沟通草稿或跟进计划。"
    business_answer["data"]["prompt_template"] = [{"id": "business-answer-system", "role": "system", "text": BUSINESS_ANSWER_PROMPT}]
    nodes.append(business_answer)

    protocol = _code_node(
        old_nodes,
        "protocol_adapter",
        "输出协议适配",
        "补齐协议字段并做基础格式修正。",
        [
            ("simple_answer", ["simple_answer", "text"]),
            ("image_answer", ["image_answer", "text"]),
            ("gap_answer", ["gap_answer", "answer_text"]),
            ("business_answer", ["business_answer_agent", "text"]),
            ("task_type", ["task_plan_normalize", "task_type"]),
            ("target_object_json", ["evidence_ledger", "target_object_json"]),
            ("original_query", ["conversation_context", "original_query"]),
            ("chart_blocks_text", ["evidence_ledger", "chart_blocks_text"]),
        ],
        {"protocol_answer": "string"},
        PROTOCOL_ADAPTER_CODE,
        7680,
        520,
        height=170,
    )
    nodes.append(protocol)

    final = _clone_node(old_nodes, "final_answer", "final_answer", "输出到 CRM 工作台", x=8000, y=520, height=110)
    final["data"]["desc"] = "统一协议 JSON 最终输出。"
    final["data"]["variables"] = [{"value_selector": ["protocol_adapter", "protocol_answer"], "variable": "protocol_answer"}]
    final["data"]["answer"] = "{{#protocol_adapter.protocol_answer#}}"
    nodes.append(final)

    edges = [
        _edge("start", "source", "attachment_image_state"),
        _edge("attachment_image_state", "source", "attachment_image_gate"),
        _edge("attachment_image_gate", "has_images", "attachment_image_summary"),
        _edge("attachment_image_gate", "false", "attachment_image_text"),
        _edge("attachment_image_summary", "source", "attachment_image_text"),
        _edge("attachment_image_text", "source", "attachment_context"),
        _edge("attachment_context", "source", "input_parse"),
        _edge("input_parse", "source", "conversation_context"),
        _edge("conversation_context", "source", "task_planner"),
        _edge("task_planner", "source", "task_plan_normalize"),
        _edge("task_plan_normalize", "source", "task_type_gate"),
        _edge("task_type_gate", "general_chat", "simple_answer"),
        _edge("task_type_gate", "image_answer", "image_answer"),
        _edge("task_type_gate", "business_task", "evidence_router"),
        _edge("task_type_gate", "false", "simple_answer"),
        _edge("evidence_router", "source", "crm_read_gate"),
        _edge("crm_read_gate", "call_crm", "crm_read_request"),
        _edge("crm_read_gate", "false", "crm_read_skip"),
        _edge("crm_read_request", "source", "crm_read_merge"),
        _edge("crm_read_skip", "source", "crm_read_merge"),
        _edge("crm_read_merge", "source", "crm_evidence"),
        _edge("evidence_router", "source", "knowledge_gate"),
        _edge("knowledge_gate", "need_knowledge", "query_rewrite"),
        _edge("knowledge_gate", "false", "knowledge_skip"),
        _edge("query_rewrite", "source", "deal_rules_knowledge"),
        _edge("deal_rules_knowledge", "source", "knowledge_merge"),
        _edge("knowledge_skip", "source", "knowledge_merge"),
        _edge("knowledge_merge", "source", "knowledge_evidence"),
        _edge("evidence_router", "source", "external_gate"),
        _edge("external_gate", "need_external", "external_intel_agent"),
        _edge("external_gate", "false", "external_skip"),
        _edge("external_intel_agent", "source", "external_merge"),
        _edge("external_skip", "source", "external_merge"),
        _edge("external_merge", "source", "external_evidence"),
        _edge("evidence_router", "source", "attachment_evidence"),
        _edge("crm_evidence", "source", "evidence_ledger"),
        _edge("knowledge_evidence", "source", "evidence_ledger"),
        _edge("external_evidence", "source", "evidence_ledger"),
        _edge("attachment_evidence", "source", "evidence_ledger"),
        _edge("evidence_ledger", "source", "gap_check"),
        _edge("gap_check", "source", "gap_check_gate"),
        _edge("gap_check_gate", "need_clarification", "gap_answer"),
        _edge("gap_check_gate", "answerable", "agent_router"),
        _edge("gap_check_gate", "false", "agent_router"),
        _edge("agent_router", "full_review", "sales_agent"),
        _edge("agent_router", "full_review", "finance_agent"),
        _edge("agent_router", "full_review", "delivery_agent"),
        _edge("agent_router", "full_review", "legal_agent"),
        _edge("agent_router", "sales", "sales_agent"),
        _edge("agent_router", "finance", "finance_agent"),
        _edge("agent_router", "delivery", "delivery_agent"),
        _edge("agent_router", "legal", "legal_agent"),
        _edge("agent_router", "analytics", "analytics_agent"),
        _edge("agent_router", "no_domain_agent", "agent_skip"),
        _edge("agent_router", "false", "agent_skip"),
        _edge("sales_agent", "source", "agent_merge"),
        _edge("finance_agent", "source", "agent_merge"),
        _edge("delivery_agent", "source", "agent_merge"),
        _edge("legal_agent", "source", "agent_merge"),
        _edge("analytics_agent", "source", "agent_merge"),
        _edge("agent_skip", "source", "agent_merge"),
        _edge("agent_merge", "source", "business_answer_agent"),
        _edge("simple_answer", "source", "protocol_adapter"),
        _edge("image_answer", "source", "protocol_adapter"),
        _edge("gap_answer", "source", "protocol_adapter"),
        _edge("business_answer_agent", "source", "protocol_adapter"),
        _edge("protocol_adapter", "source", "final_answer"),
    ]

    graph["nodes"] = nodes
    graph["edges"] = edges
    YAML_PATH.write_text(yaml.safe_dump(data, allow_unicode=True, sort_keys=False, width=120), encoding="utf-8")


if __name__ == "__main__":
    main()
