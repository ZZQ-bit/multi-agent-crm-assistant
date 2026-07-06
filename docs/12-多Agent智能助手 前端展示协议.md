# 多Agent智能助手 前端展示协议

| 文档字段 | 内容 |
| --- | --- |
| 文档名称 | 多Agent智能助手 前端展示协议 |
| 所属系统 | CordysCRM / 多Agent智能助手 |
| 文档版本 | v1.0 |
| 更新时间 | 2026-07-02 |
| 当前阶段 | Markdown 增强 / 判断过程展示增强 / Dify adapter 对齐前置协议 |
| 主要读者 | 前端、AI 应用开发、测试、后续协作 Agent |

> 本文档是后续三个前端任务的展示层基线：Markdown 增强、判断过程/工具调用展示增强、Dify/adapter 字段对齐与 smoke test。它只定义前端内部最终消费什么字段、如何展示、哪些内容不得展示。前后端和 Chatflow 的完整协议继续以 `docs/06-多Agent智能助手 Chatflow 与前端协议规范.md` 为准；判断过程业务语义继续以 `docs/05-多Agent智能助手 判断过程事件规范.md` 为准。

## 1. 目标

本协议用于在开发前固定展示层边界，避免后续并行开发时出现以下偏差：

- Markdown 组件从结构化 payload 中自行解析业务对象、写回草稿或工具结果。
- 判断过程组件直接展示 Dify 节点名、Agent 名、tool_call、Prompt、token、耗时等内部信息。
- adapter 把同一份数据重复塞进 `answerText`、`process.events` 和 `writeback`，导致页面展示重复或状态不一致。
- Chatflow、后端、前端分别维护不同的判断过程文案。
- 为了演示效果补假事件、假工具调用或 mock 写回结果。

前端展示层只消费稳定页面模型，不消费 Dify 原始响应。

```text
Dify / 后端 payload
  -> dealDeskChatflowAdapter
  -> DealDeskAssistantReply / DealDeskTurn
  -> ConversationTurn
  -> AssistantMarkdownPreview + ProcessTimeline
```

## 2. 展示层字段总览

前端页面最终只关心下面这些字段：

| 字段 | 消费组件 | 用途 | 是否用户可见 |
| --- | --- | --- | --- |
| `turn.text` | `AssistantMarkdownPreview` | AI 正文回答，按 Markdown 渲染 | 是 |
| `turn.process.summary` | `ProcessTimeline` | 折叠态摘要，例如“已完成 4 项检查：资料读取、条件核对、建议生成” | 是 |
| `turn.process.events` | `ProcessTimeline` | 判断过程事件列表 | 是 |
| `turn.process.events[].type` | adapter / `ProcessTimeline` | 决定固定业务文案、排序、摘要词 | 间接可见 |
| `turn.process.events[].status` | `ProcessTimeline` | 决定运行、完成、警告、失败视觉状态 | 间接可见 |
| `turn.process.events[].text` | `ProcessTimeline` | 标准化后的业务文案 | 是 |
| `turn.process.events[].evidenceRefs` | `ProcessTimeline` 后续增强 | 业务依据摘要，可选展示 | 可选 |
| `turn.status` | `ConversationTurn` | 回合生成态，例如 `generating` | 间接可见 |
| `turn.references` | `ConversationTurn` | 用户消息引用的客户、商机、文件 chip | 是 |
| `reply.writeback` | session state / adapter | 写回确认和写回结果状态 | 不直接渲染 payload |
| `reply.boundObject` | session state | 会话记忆中的客户或商机 | 不固定展示 |
| `reply.kind` | hook / session state | 更新会话、写回状态和后续动作 | 不直接展示 |

不允许展示层消费：

- Dify 原始 SSE 节点名。
- Dify workflow node id。
- 原始 `tool_call` 请求体或返回体。
- Agent 内部名称。
- Prompt、模型名、token、耗时。
- 原始 JSON payload。
- CRM Tool API 原始响应体。

## 3. 字段职责边界

### 3.1 `answerText` / `turn.text`

`answerText` 是 AI 给用户看的正文。adapter 映射后进入 `turn.text`，并交给 `AssistantMarkdownPreview` 渲染。

职责：

- 承载普通问答、商机总结、风险分析、下一步建议、多结果澄清、写回确认正文、写回结果说明。
- 使用 Markdown 表达标题、段落、列表、表格、引用、代码块和图表 fence。
- 在写回确认场景中，用普通 Markdown 展示待写入内容。

禁止：

- 不从 `answerText` 中解析客户、商机、候选对象、写回 payload 或过程事件。
- 不在 `answerText` 中输出 JSON 代码块给用户看。
- 不把 `processEvents`、`writeback`、`boundObject` 再重复完整塞入正文。
- 不输出 `quick_answer`、`text_analysis`、`full_review`、`tool_call` 等内部标签。
- 不用代码块展示 CRM 待写入内容；写回确认正文使用普通文本或列表。

允许的多结果澄清示例：

```markdown
我查到多个匹配商机，请输入完整商机名继续：

- **智联云平台二期项目**：阶段 商务谈判，金额 2850000，负责人 张伟
- **智联数据中台续费项目**：阶段 方案报价，金额 680000，负责人 王芳
```

### 3.2 `process.events`

`process.events` 是判断过程/工具调用展示的唯一输入。它表达的是“用户可理解的业务检查”，不是工具日志。

事件结构：

```ts
interface DealDeskProcessEvent {
  id: string;
  type?: DealDeskProcessEventType;
  text: string;
  status: 'running' | 'completed' | 'warning' | 'failed';
  evidenceRefs?: string[];
}
```

字段规则：

| 字段 | 规则 |
| --- | --- |
| `id` | 本轮事件唯一 ID，只用于前端渲染 key 和去重 |
| `type` | 标准事件类型；前端按它生成固定业务文案、排序和摘要词 |
| `text` | adapter 标准化后的用户可见业务文案；不接受技术文案直出 |
| `status` | 只允许 `running`、`completed`、`warning`、`failed` |
| `evidenceRefs` | 只能放业务依据摘要，例如“付款条件：首付款 30%”“合同条款：验收标准缺失” |

标准事件类型以 `docs/05-多Agent智能助手 判断过程事件规范.md` 为准：

```ts
type DealDeskProcessEventType =
  | 'task_identified'
  | 'object_required'
  | 'object_selected'
  | 'context_loaded'
  | 'memory_used'
  | 'rule_checked'
  | 'risk_found'
  | 'suggestion_generated'
  | 'confirmation_required'
  | 'writeback_completed'
  | 'failed';
```

`memory_used` 默认不展示，只作为内部连续对话信号保留。前端当前应过滤它，除非后续明确设计“沿用上一轮对象”的用户提示。未知 `type` 同样过滤，不允许把 `event.text` 作为兜底文案直接展示。

### 3.3 `status`

这里有三个不同层级的 `status`，后续开发必须区分。

| 字段 | 可选值 | 含义 | 消费位置 |
| --- | --- | --- | --- |
| `turn.status` | `default` / `generating` | 整个 AI 回合是否生成中 | `ConversationTurn` |
| `process.events[].status` | `running` / `completed` / `warning` / `failed` | 单个判断过程事件状态 | `ProcessTimeline` |
| `writeback.status` | `awaiting_confirm` / `confirmed` / `cancelled` / `failed` | 写回草稿或写回结果状态 | hook / session state |

禁止把这三类 status 混用。例如：

- 不能用 `writeback.status = failed` 直接让整个 `turn.status` 变成失败。
- 不能用 `turn.status = generating` 替代 `process.events[].status = running`。
- 不能用 `process.events[].status = completed` 判断 CRM 写回已经成功，写回成功必须看 `writeback.status = confirmed` 或后端返回的写回结果。

同一标准事件重复出现时，前端按固定优先级保留最终展示状态：

```text
failed > warning > completed > running
```

因此某个步骤先 `running` 后 `completed` 会展示完成态；先 `completed` 后 `warning` 会展示警示态；任何 `failed` 都会优先保留，但它只代表该步骤异常，不直接代表整个回合失败。

### 3.4 `evidenceRefs` 与 `summary`

`evidenceRefs` 与 `summary` 都只能展示业务依据，不展示技术细节。

`process.summary` 用于折叠态摘要：

```text
已完成 4 项检查：任务识别、资料读取、条件核对、建议生成
```

摘要统一使用标准业务词，不再按 `quick_answer`、`text_analysis` 等回合类型切换另一套轻量词表。

`evidenceRefs` 用于后续增强每个过程事件的可复核依据：

```ts
{
  type: 'rule_checked',
  status: 'completed',
  text: '已核对关键业务条件',
  evidenceRefs: [
    '付款条件：首付款 30%',
    '验收标准：CRM 中未见明确描述'
  ]
}
```

允许的 `evidenceRefs`：

- CRM 字段摘要。
- 规则编号或规则名称。
- 用户上传附件中的业务摘要。
- 本轮会话中用户明确提供的条件。

禁止的 `evidenceRefs`：

- `node_id: 172...`
- `tool_call: get_opportunity_detail`
- `workflow route: finance_task`
- `prompt matched`
- `LLM output parsed`
- token、耗时、模型名。

## 4. Markdown 展示协议

Markdown 增强任务只负责把 `turn.text` 渲染得稳定、清晰、安全，不负责解释业务结构。

### 4.1 输入

Markdown 组件输入固定为：

```ts
interface AssistantMarkdownPreviewProps {
  source: string; // 等同于 turn.text
  turnId: string;
  animate?: boolean;
}
```

后续增强不得新增对 `processEvents`、`writeback`、`boundObject` 的依赖。

### 4.2 输出

Markdown 组件输出用户可见 HTML，必须经过安全处理。

必须支持：

- 标题。
- 段落。
- 加粗、斜体、行内代码。
- 无序列表、有序列表。
- 引用块。
- 表格。
- 链接。
- 图片。
- fenced code。
- 现有 `chartFencePlugin` 图表块。

P0 增强建议：

- 代码块显示语言名。
- 代码块提供复制按钮。
- 表格横向滚动，不能撑破会话宽度。
- 链接默认新窗口打开，并带 `rel="noreferrer noopener"`。
- 流式输出中修复未闭合 Markdown，避免半截内容破坏布局。
- 对异常 JSON 包裹 answer 的情况继续兼容解包。

### 4.3 安全约束

Markdown 渲染必须满足：

- `html: false` 或等价策略，默认不允许用户输入 HTML 直出。
- 渲染结果必须经过 `DOMPurify` 或等价白名单清洗。
- 链接必须过滤危险协议。
- 不允许通过 Markdown 注入脚本、事件属性或 iframe。
- 图表 fence 只能渲染受控 JSON 配置，解析失败时降级为空占位或普通文本。

### 4.4 写回确认正文

写回确认仍由 Markdown 渲染，但不能做成特殊表单或卡片。

推荐格式：

```markdown
即将写入商机跟进记录：

客户：上海智联科技股份有限公司
商机：智联云平台二期项目
跟进方式：电话沟通
负责人：张伟
内容：本次评审判断付款方案整体风险偏高，下一步重点确认首付款比例、验收范围与一期上线范围。

回复“确认”后写入 CRM；如果需要调整，直接告诉我你想改哪一部分。
```

禁止格式：

```json
{
  "type": "follow_record",
  "customerName": "上海智联科技股份有限公司"
}
```

## 5. 判断过程/工具调用展示协议

工具调用展示增强任务不展示“工具调用本身”，而是展示工具调用背后的业务过程。

### 5.1 展示定位

用户看到的名称固定为“判断过程”。不使用以下名称：

- 工具调用。
- Tool Call。
- Agent Trace。
- Workflow。
- 执行日志。
- 调试过程。

### 5.2 输入

判断过程组件输入固定为：

```ts
interface DealDeskProcessBlock {
  summary: string;
  expanded: boolean;
  events: DealDeskProcessEvent[];
}
```

组件不接收 Dify node、agent name、tool name、raw args、raw result。

### 5.3 展示规则

折叠态：

- 展示状态图标。
- 展示“判断过程”。
- 展示 `process.summary`。
- 展示展开/收起图标。
- 摘要一行展示，超长省略。

展开态：

- 按标准事件顺序展示。
- 每条展示状态图标和 `event.text`。
- `running` 显示轻量旋转或进行中状态。
- `warning` 使用业务警示色，但不做高危错误样式。
- `failed` 只表示某个步骤失败，不一定代表整个回合失败。
- 有 `evidenceRefs` 时，可在后续增强为次级说明，但默认不展开复杂明细。

### 5.4 固定文案

前端应按 `type + status` 生成固定业务文案。若后端传入 `text` 含技术词，adapter 必须覆盖为固定文案。

| 事件类型 | running 文案 | completed / warning / failed 文案 |
| --- | --- | --- |
| `task_identified` | 正在识别本轮任务 | 已识别本轮任务 |
| `object_required` | 需要先确认本轮分析对象 | 需要先确认本轮分析对象 |
| `object_selected` | 正在确认本轮分析对象 | 已确定本轮分析对象 |
| `context_loaded` | 正在读取相关业务资料 | 已读取相关业务资料 |
| `memory_used` | 正在参考当前会话信息 | 已参考当前会话信息 |
| `rule_checked` | 正在核对关键业务条件 | 已核对关键业务条件 |
| `risk_found` | 正在识别风险与信息缺口 | 已识别风险与信息缺口 |
| `suggestion_generated` | 正在生成结论和下一步建议 | 已生成结论和下一步建议 |
| `confirmation_required` | 等待你确认下一步操作 | 等待你确认下一步操作 |
| `writeback_completed` | 正在写入 CRM | 已完成 CRM 写入 |
| `failed` | 暂时无法完成本轮处理 | 暂时无法完成本轮处理 |

### 5.5 禁止展示

判断过程组件不得展示：

- `正在调用财务 Agent`
- `主 Agent - 路由判断`
- `route_finance_task`
- `tool_call success`
- `getOpportunityDetail`
- `HTTP 200`
- `LLM node completed`
- `Prompt 解析完成`
- `耗时 2.4s`
- `token: 1842`

如果这些内容从后端传来，adapter 必须过滤或映射为标准业务事件。

## 6. Adapter 对齐协议

adapter 是 Dify/后端 payload 到前端展示模型的唯一转换层。

### 6.1 输入

adapter 输入是 `DealDeskChatflowResponse`：

```ts
interface DealDeskChatflowPayload {
  protocolVersion: '1.0';
  turnType: DealDeskTurnType;
  answerText: string;
  processEvents?: DealDeskProcessEventPayload[];
  writeback?: DealDeskWritebackPayload;
  boundObject?: DealDeskBoundObjectPayload;
  suggestedSessionTitle?: string;
  warnings?: string[];
}
```

### 6.2 输出

adapter 输出是 `DealDeskAssistantReply`：

```ts
interface DealDeskAssistantReply {
  kind: DealDeskAssistantReplyKind;
  turn: DealDeskAssistantReplyPayload;
  writeback?: DealDeskWritebackPayload;
  boundObject?: DealDeskBoundObjectPayload;
  conversationId?: string;
  messageId?: string;
  suggestedSessionTitle?: string;
}
```

### 6.3 映射规则

| 输入字段 | 输出字段 | 规则 |
| --- | --- | --- |
| `answerText` | `turn.text` | 原样作为 Markdown source；只做空值兜底，不解析业务结构 |
| `processEvents` | `turn.process.events` | 过滤隐藏事件和未知类型、去重、排序、固定文案 |
| `processEvents` | `turn.process.summary` | 按统一标准摘要词生成，不接收模型自由生成摘要 |
| `writeback` | `reply.writeback` | 只在 payload 有合法 `id` 和 `status` 时保留 |
| `boundObject` | `reply.boundObject` | 只在有合法 `objectType` 和 `objectId` 时保留 |
| `turnType` | `reply.kind` | 映射为页面回合类型 |
| `warnings` | 日志或测试断言 | 不直接展示技术告警给用户 |

adapter 不做：

- 不把 `writeback` payload 拼成正文；正文由 `answerText` 提供。
- 不从 `answerText` 中提取 `writeback` payload。
- 不从 `answerText` 中解析候选对象。
- 不让未知 `processEvent.type` 直接展示技术文案。
- 不为缺失的 `processEvents` 补假事件。

## 7. 回合类型展示规则

| `turnType` | `reply.kind` | Markdown | 判断过程 | 写回状态 |
| --- | --- | --- | --- | --- |
| `quick_answer` | `quick-answer` | 展示 `answerText` | 有真实事件则展示，否则不展示 | 无 |
| `text_analysis` | `analysis` | 展示 `answerText` | 有真实事件则展示 | 无 |
| `writeback_confirm` | `writeback-confirm` | 展示待确认正文 | 建议展示 `confirmation_required` | `writeback.status = awaiting_confirm` |
| `writeback_result` | `writeback-success` / `writeback-cancel` | 展示结果说明 | 可展示 `writeback_completed` 或 `failed` | 按 `writeback.status` |
| `fallback` | `quick-answer` | 展示降级文本 | 不补过程 | 无 |
| `failed` | `quick-answer` 或后续失败类型 | 展示失败原因和可恢复建议 | 有真实失败事件则展示 | 无 |
| `object_select` | 兼容降级 | 按 `answerText` 展示 Markdown 澄清列表 | 可展示 `object_required` | 不消费旧候选卡 |

## 8. 并行开发边界

后续三个任务按以下边界执行。

### 8.1 Markdown 增强任务

可修改：

- `AssistantMarkdownPreview.vue`
- `dealDeskMarkdown.ts`
- `chartFencePlugin.ts`
- `ChartFence.vue`
- Markdown 相关 smoke test

不得修改：

- `DealDeskProcessEvent` 语义。
- `writeback` payload 结构。
- Chatflow adapter 的业务映射规则，除非发现 Markdown 输入字段不一致。

验收重点：

- 给定 `turn.text`，能稳定渲染 Markdown。
- 表格、代码块、链接、图表、流式半截内容不会破坏布局。
- 不解析业务结构。

### 8.2 判断过程/工具调用展示增强任务

可修改：

- `ProcessTimeline.vue`
- `dealDeskProcessFallback.ts`
- `types.ts` 中过程事件相关类型，保持与本文档一致
- 判断过程相关 smoke test

不得修改：

- Markdown 渲染逻辑。
- `answerText` 输出规范。
- 写回 payload 结构。

验收重点：

- 给定 `process.events`，能展示稳定业务过程。
- 不展示技术词。
- `running`、`completed`、`warning`、`failed` 状态清晰。
- 摘要由前端固定生成。

### 8.3 Dify/adapter 字段对齐与 smoke test

必须在前两项展示协议稳定后收口。

可修改：

- `dealDeskChatflowAdapter.ts`
- `dealDeskChatflowAdapter.smoke-test.mjs`
- provider 与后端响应解析相关代码
- 与协议一致的类型补齐

验收重点：

- Dify/后端 payload 能映射为本文档的展示模型。
- `answerText`、`process.events`、`writeback`、`boundObject` 各司其职。
- mock、旧 `objectSelect`、空对象、异常 payload 都能降级。
- smoke test 覆盖 Markdown source、过程事件、写回确认、字段缺失和技术字段过滤。

## 9. 开发验收清单

Markdown 增强完成时，应满足：

- `answerText` 是唯一 Markdown source。
- Markdown 输出经过安全清洗。
- 代码块、表格、链接、引用、列表展示稳定。
- 写回确认正文不被渲染成表单、卡片或 JSON。
- 异常 Markdown 不影响整个会话布局。

判断过程增强完成时，应满足：

- `process.events` 是唯一判断过程输入。
- 事件按标准顺序展示。
- 摘要由前端生成。
- 用户可见文案全部是业务语言。
- 技术字段传入时不会直出。
- 没有真实事件时不补假事件。

adapter 对齐完成时，应满足：

- `answerText -> turn.text`。
- `processEvents -> turn.process.events`。
- `processEvents -> turn.process.summary`。
- `writeback -> reply.writeback`。
- `boundObject -> reply.boundObject`。
- 旧字段和空字段能安全降级。
- smoke test 能覆盖关键场景。

## 10. 非目标

本轮不做：

- 替换整套 chat UI。
- 接入 Chatbox 源码。
- 引入 React Markdown 体系。
- 在前端展示原始工具调用。
- 做完整 Agent Trace 面板。
- 做右侧固定业务栏。
- 做写回编辑表单或抽屉。
- 做候选对象选择卡片。
- 做多模型、token、耗时、调试信息展示。

## 11. 最终约定

后续开发统一按下面四句话执行：

1. `answerText` 只给 Markdown 渲染。
2. `process.events` 只给“判断过程”展示。
3. `status` 必须区分回合状态、过程事件状态和写回状态。
4. `summary` 与 `evidenceRefs` 只展示业务依据，不展示技术字段。

如果实现中遇到本文档与 `docs/05-多Agent智能助手 判断过程事件规范.md` 或 `docs/06-多Agent智能助手 Chatflow 与前端协议规范.md` 冲突，以 `docs/05` 和 `docs/06` 的业务与链路定义为准，并同步更新本文档，不能只改代码。
