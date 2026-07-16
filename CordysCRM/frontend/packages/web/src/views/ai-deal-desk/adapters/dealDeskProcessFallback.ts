import type { DealDeskProcessBlock, DealDeskProcessEvent, DealDeskTurnType } from '../types';

const VALID_PROCESS_EVENT_TYPES = new Set<DealDeskProcessEvent['type']>([
  'task_understanding',
  'attachment_analysis',
  'crm_retrieval',
  'knowledge_retrieval',
  'external_research',
  'sales_analysis',
  'finance_analysis',
  'delivery_analysis',
  'legal_analysis',
  'analytics_analysis',
  'answer_generation',
  'task_identified',
  'object_required',
  'object_selected',
  'context_loaded',
  'memory_used',
  'rule_checked',
  'risk_found',
  'suggestion_generated',
  'confirmation_required',
  'writeback_completed',
  'failed',
]);
const HIDDEN_PROCESS_EVENT_TYPES = new Set<DealDeskProcessEvent['type']>(['memory_used']);
const processStatusPriority: Record<DealDeskProcessEvent['status'], number> = {
  running: 1,
  completed: 2,
  warning: 3,
  failed: 4,
};
const processEventOrder: Partial<Record<NonNullable<DealDeskProcessEvent['type']>, number>> = {
  task_understanding: 10,
  attachment_analysis: 20,
  crm_retrieval: 30,
  knowledge_retrieval: 40,
  external_research: 50,
  sales_analysis: 60,
  finance_analysis: 61,
  delivery_analysis: 62,
  legal_analysis: 63,
  analytics_analysis: 64,
  answer_generation: 80,
  task_identified: 10,
  object_required: 20,
  object_selected: 30,
  context_loaded: 40,
  memory_used: 50,
  rule_checked: 60,
  risk_found: 70,
  suggestion_generated: 80,
  confirmation_required: 90,
  writeback_completed: 100,
  failed: 110,
};

const processSummaryLabels: Record<NonNullable<DealDeskProcessEvent['type']>, string> = {
  task_understanding: '理解任务',
  attachment_analysis: '附件解析',
  crm_retrieval: 'CRM 资料',
  knowledge_retrieval: '业务规则',
  external_research: '公开资料',
  sales_analysis: '销售分析',
  finance_analysis: '财务分析',
  delivery_analysis: '交付分析',
  legal_analysis: '合同分析',
  analytics_analysis: '经营分析',
  answer_generation: '回答生成',
  task_identified: '任务识别',
  object_required: '对象确认',
  object_selected: '对象确认',
  context_loaded: '资料读取',
  memory_used: '会话记忆',
  rule_checked: '条件核对',
  risk_found: '风险判断',
  suggestion_generated: '建议生成',
  confirmation_required: '等待确认',
  writeback_completed: '写入结果',
  failed: '异常处理',
};

const completedTextByType: Record<NonNullable<DealDeskProcessEvent['type']>, string> = {
  task_understanding: '已理解本轮任务',
  attachment_analysis: '已解析附件内容',
  crm_retrieval: '已读取 CRM 资料',
  knowledge_retrieval: '已检索业务规则',
  external_research: '已查询公开资料',
  sales_analysis: '已完成销售分析',
  finance_analysis: '已完成财务分析',
  delivery_analysis: '已完成交付分析',
  legal_analysis: '已完成合同分析',
  analytics_analysis: '已完成经营分析',
  answer_generation: '已生成回答',
  task_identified: '已识别本轮任务',
  object_required: '需要先确认本轮分析对象',
  object_selected: '已确定本轮分析对象',
  context_loaded: '已读取相关业务资料',
  memory_used: '已参考当前会话信息',
  rule_checked: '已核对关键业务条件',
  risk_found: '已识别风险与信息缺口',
  suggestion_generated: '已生成结论和下一步建议',
  confirmation_required: '等待你确认下一步操作',
  writeback_completed: '已完成 CRM 写入',
  failed: '暂时无法完成本轮处理',
};

const runningTextByType: Partial<Record<NonNullable<DealDeskProcessEvent['type']>, string>> = {
  task_understanding: '正在理解本轮任务',
  attachment_analysis: '正在解析附件内容',
  crm_retrieval: '正在读取 CRM 资料',
  knowledge_retrieval: '正在检索业务规则',
  external_research: '正在查询公开资料',
  sales_analysis: '正在进行销售分析',
  finance_analysis: '正在进行财务分析',
  delivery_analysis: '正在进行交付分析',
  legal_analysis: '正在进行合同分析',
  analytics_analysis: '正在进行经营分析',
  answer_generation: '正在整理回答',
  task_identified: '正在识别本轮任务',
  object_required: '需要先确认本轮分析对象',
  object_selected: '正在确认本轮分析对象',
  context_loaded: '正在读取相关业务资料',
  memory_used: '正在参考当前会话信息',
  rule_checked: '正在核对关键业务条件',
  risk_found: '正在识别风险与信息缺口',
  suggestion_generated: '正在生成结论和下一步建议',
  confirmation_required: '等待你确认下一步操作',
  writeback_completed: '正在写入 CRM',
};

function shouldPreferNewEvent(existing: DealDeskProcessEvent, incoming: DealDeskProcessEvent) {
  const existingPriority = processStatusPriority[existing.status] ?? 0;
  const incomingPriority = processStatusPriority[incoming.status] ?? 0;
  if (existingPriority !== incomingPriority) {
    return incomingPriority > existingPriority;
  }

  return incoming.text.length >= existing.text.length;
}

function buildDisplayEventText(event: DealDeskProcessEvent) {
  if (event.status === 'failed') {
    return event.text || completedTextByType[event.type!];
  }

  if (event.type === 'answer_generation' && event.id === 'business_answer_agent') {
    return event.status === 'running' ? '正在汇总结论' : '已生成结论';
  }

  if (event.status === 'running') {
    return runningTextByType[event.type!] || completedTextByType[event.type!];
  }

  return completedTextByType[event.type!];
}

function normalizeDisplayEvent(event: DealDeskProcessEvent): DealDeskProcessEvent {
  return {
    ...event,
    text: buildDisplayEventText(event),
  };
}

function sortProcessEvents(events: DealDeskProcessEvent[]) {
  return [...events].sort((left, right) => {
    const leftOrder = left.type ? processEventOrder[left.type] ?? 999 : 999;
    const rightOrder = right.type ? processEventOrder[right.type] ?? 999 : 999;
    return leftOrder - rightOrder;
  });
}

function compressProcessEvents(events: DealDeskProcessEvent[]) {
  if (!events.length) {
    return [];
  }

  const visibleEvents = events.filter(
    (event) => event.type && VALID_PROCESS_EVENT_TYPES.has(event.type) && !HIDDEN_PROCESS_EVENT_TYPES.has(event.type)
  );
  const compressed = visibleEvents.reduce<DealDeskProcessEvent[]>((result, event) => {
    const index = result.findIndex((item) => item.id === event.id);
    if (index >= 0) {
      if (shouldPreferNewEvent(result[index], event)) {
        result[index] = event;
      }
      return result;
    }

    return [...result, event];
  }, []);

  const sortedEvents = sortProcessEvents(compressed);
  const failedEvents = sortedEvents.filter((event) => event.status === 'failed' || event.type === 'failed');
  const normalEvents = sortedEvents.filter((event) => event.status !== 'failed' && event.type !== 'failed');
  return [...normalEvents, ...failedEvents].map(normalizeDisplayEvent);
}

function buildProcessSummary(events: DealDeskProcessEvent[]) {
  const runningEvents = events.filter((event) => event.status === 'running');
  const completedCount = events.filter((event) => event.status === 'completed').length;
  const abnormalCount = events.filter(
    (event) => event.status === 'warning' || event.status === 'failed' || event.type === 'failed'
  ).length;

  if (runningEvents.length) {
    if (completedCount > 0 || abnormalCount > 0) {
      return `已完成 ${completedCount} 项，正在处理 ${runningEvents.length} 项`;
    }

    const labels = runningEvents
      .map((event) => (event.type ? processSummaryLabels[event.type] : null))
      .filter((label): label is string => Boolean(label));
    if (runningEvents.length === 1) {
      return `正在处理：${labels[0] || '当前任务'}`;
    }

    const visibleLabels = labels.slice(0, 3);
    const labelText = visibleLabels.length ? `：${visibleLabels.join('、')}${labels.length > 3 ? '等' : ''}` : '';
    return `正在并行处理 ${runningEvents.length} 项${labelText}`;
  }

  if (abnormalCount > 0) {
    return `已完成 ${completedCount} 项，${abnormalCount} 项异常`;
  }

  return `已完成 ${completedCount} 项处理`;
}

export function buildProcessBlock(events?: DealDeskProcessEvent[] | null) {
  if (!events?.length) {
    return undefined;
  }

  const normalizedEvents = compressProcessEvents(events);
  if (!normalizedEvents.length) {
    return undefined;
  }

  return {
    summary: buildProcessSummary(normalizedEvents),
    expanded: false,
    events: normalizedEvents,
  } satisfies DealDeskProcessBlock;
}

export function normalizeProcessEvents(
  events?: DealDeskProcessEvent[] | null,
  _turnType?: DealDeskTurnType
): DealDeskProcessEvent[] {
  return compressProcessEvents(events || []);
}

export function buildProcessSummaryText(events: DealDeskProcessEvent[], _turnType?: DealDeskTurnType) {
  return buildProcessSummary(events);
}
