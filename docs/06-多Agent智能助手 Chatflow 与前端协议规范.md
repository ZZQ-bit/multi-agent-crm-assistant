# 多Agent智能助手 Chatflow 与前端协议规范

| 文档字段 | 内容 |
| --- | --- |
| 文档名称 | 多Agent智能助手 Chatflow 与前端协议规范 |
| 所属系统 | CordysCRM / 多Agent智能助手 |
| 文档版本 | v1.0 |
| 更新时间 | 2026-07-06 |
| 当前阶段 | V3 Stable Enhanced 只读 Chatflow / 前端协议收口 |
| 主要读者 | 前端、AI 应用开发、产品 |

> [!NOTE]
> 本文档定义 `多Agent智能助手` 前端、后端适配层与 Dify Chatflow 之间的稳定协议。判断过程事件的业务语义、节点命名和展示边界，以 `docs/05-多Agent智能助手 判断过程事件规范.md` 为准；本文只规定这些事件如何在前后端之间传递。

## 1. 文档目的

当前前端已经有 多Agent智能助手 工作台页面、真实对话 hook、判断过程和对话式写入确认。当前公开版 Chatflow 以 `chatflows/ai-deal-desk-v3.example.yml` 为准，已经改为“Planner 任务规划、统一证据台账、按需多 Agent、统一协议适配”的只读链路；P0 不再依赖 mock provider 或候选对象卡片协议。

接下来接入真实 Chatflow 之前，必须先固定一份前后端协议，避免前端直接解析 Dify answer 文本，也避免 Chatflow、后端、前端分别生成不同的判断过程文案。

本文档解决四个问题：

- 前端请求 Chatflow 时，应该传哪些上下文。
- 后端返回前端时，必须给哪些结构化字段。
- 前端 adapter 如何把 Chatflow 输出映射成页面回合。
- 判断过程、多结果文本澄清、会话记忆、写入确认这些关键能力如何保持稳定。

## 2. 总体原则

- 前端不直接绑定 Dify 原始 answer 文本，而是通过 adapter 转成 `DealDeskTurn`。
- Chatflow 不向用户展示内部路由、Agent 调度、工具调用、Prompt、token 或耗时。
- 判断过程事件的主来源是 Dify SSE 节点状态，经后端统一映射为标准 `processEvents`。
- Chatflow 不负责生成用户可见的判断过程文案；Chatflow 只负责业务判断、正文回答和必要结构化结果。
- 普通回答也可以展示轻量判断过程，但事件由后端基于实时节点状态生成。
- 业务任务展示更完整的结构化判断过程事件，仍由同一映射规则生成。
- 客户/商机不唯一时，返回 Markdown 多结果列表并提示用户输入完整客户名或商机名，不提前输出最终业务结论。
- 当前 V3 不执行 CRM 写入；用户要求保存或写入时，只返回可复制草稿。写入确认协议保留为前端兼容和后续恢复能力使用。
- 所有结构化字段都必须允许前端降级：字段缺失时仍能展示普通文本回答。

## 3. 数据流

P0 推荐数据流：

```text
用户输入
  -> 前端收集文本、@对象、附件、会话状态
  -> DealDeskChatflowProvider 请求 CordysCRM 后端适配层
  -> 后端以 response_mode=streaming 调用 Dify Chatflow
  -> Chatflow 完成输入解析、Planner 规划、证据收集和按需 Agent 判断
  -> 后端将 Dify SSE 节点状态映射为标准 processEvents
  -> 后端合并 Chatflow 最终业务 payload
  -> DealDeskChatflowAdapter 解析后端 payload
  -> 转成页面可渲染的 DealDeskTurn
  -> ConversationTurn / ProcessTimeline 展示
```

关键边界：

| 层级 | 职责 |
| --- | --- |
| 页面组件 | 只负责展示、点击、输入，不解析 Chatflow 内部字段 |
| Provider | 只负责请求后端适配层、处理网络错误和基础重试 |
| 后端适配层 | 负责调用 Dify、消费 SSE、把节点状态映射为标准过程事件、合并最终 payload |
| Adapter | 负责把后端 payload 转成前端页面模型，并按事件类型生成固定用户文案和摘要 |
| Chatflow | 负责输入解析、Planner 规划、CRM/知识库/外部情报/附件证据收集、按需 Agent 编排、结论生成和业务结构化 payload |

CRM 查询工具不直接绑定 CordysCRM 原始页面接口。Dify Chatflow 通过 HTTP Request 调用 `docs/08-CRM 工具能力与数据协议.md` 定义的 多Agent智能助手 Tool API，由 CordysCRM 后端复用现有客户、商机、联系人、跟进记录和跟进计划服务，并统一处理权限和错误码。当前 V3 只读版不调用写回工具，写回相关字段只作为兼容协议保留。

## 4. 前端请求 Chatflow 输入

前端每次发送消息时，都应把用户原始输入和当前会话状态一起提交给 Chatflow。

### 4.1 必传字段

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `query` | string | 用户本轮输入的原始文本 |
| `conversationId` | string | 前端会话 ID，用于关联本地历史 |
| `messageId` | string | 前端本轮用户消息 ID |
| `userId` | string | 当前登录用户 ID |

### 4.2 业务对象输入

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `bound_object_type` | `customer` / `opportunity` / empty | 用户通过 `@`、路由或会话记忆绑定的对象类型 |
| `bound_object_id` | string | 绑定对象 ID |
| `bound_object_name` | string | 绑定对象名称 |
| `bound_object_source` | `mention` / `selection` / `route_query` / empty | 对象来源 |
| `route_customer_id` | string | 从客户详情或路由参数带入的客户 ID |
| `route_opportunity_id` | string | 从商机详情或路由参数带入的商机 ID |

对象优先级：

1. 用户本轮通过 `@` 绑定的对象。
2. 当前会话记忆中的对象。
3. 路由带入的初始对象。
4. Chatflow 从文本中自动识别出的对象。

如果 `@` 对象和文本描述明显冲突，Chatflow 不应直接给高置信业务结论，应要求用户确认。

### 4.3 附件输入

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `attachment_names` | string | 本轮上传文件名，多个文件用换行或逗号分隔 |
| `attachments_summary` | string | 附件解析后的业务摘要 |
| `files` | Dify file[] | 实际文件，由 Dify 文件能力接收 |

文件只作为本轮回答的补充材料，不改变本轮主对象归属。

### 4.4 写入确认输入

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `active_writeback_id` | string | 当前等待确认的写入草稿 ID |
| `active_writeback_status` | `awaiting_confirm` / empty | 当前待确认状态 |
| `active_writeback_type` | `follow_record` / `follow_plan` / `follow_record_and_plan` / empty | 待确认写入类型 |
| `active_writeback_payload_json` | string | 上一轮待确认写入 payload，JSON 字符串 |

用户回复 `确认`、`取消`、`修改` 或 `重试` 时，前端必须带上以上字段，让 Chatflow 能识别这是对上一轮写入草稿的操作。

### 4.5 多结果澄清与连续对话输入

P0 不再使用候选卡点击续跑。多客户/多商机场景由 `answerText` 输出 Markdown 列表，用户下一轮直接输入完整客户名或商机名继续；前端把当前会话记忆、`bound_object_*` 和原始用户文本继续传给 Chatflow。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `resume_query` | string | 兼容字段；P0 不依赖候选卡续跑 |
| `selected_object_type` | `customer` / `opportunity` | 兼容字段；仅历史对象选择协议使用 |
| `selected_object_id` | string | 兼容字段；仅历史对象选择协议使用 |
| `selected_object_name` | string | 兼容字段；仅历史对象选择协议使用 |

连续对话时的实际 `query` 使用用户最新输入即可。用户说“这个商机”“这个客户”时，前端继续携带当前 `boundObject`；用户输入完整名称时，Chatflow 重新搜索并在唯一命中后更新 `boundObject`。

## 5. 后端返回前端输出

后端返回前端时，应形成一个稳定的 `DealDeskChatflowPayload`。前端 adapter 只消费这个 payload，不从自然语言正文里抠业务结构。

```ts
interface DealDeskChatflowPayload {
  protocolVersion: '1.0';
  turnType:
    | 'quick_answer'
    | 'object_select'
    | 'text_analysis'
    | 'deep_deal_review_brief'
    | 'stats_query'
    | 'writeback_confirm'
    | 'writeback_result'
    | 'fallback'
    | 'failed';
  answerText: string;
  processEvents?: DealDeskProcessEventPayload[];
  writeback?: DealDeskWritebackPayload;
  boundObject?: DealDeskBoundObjectPayload;
  suggestedSessionTitle?: string;
  warnings?: string[];
}
```

字段要求：

| 字段 | 要求 |
| --- | --- |
| `protocolVersion` | 必须固定为 `1.0`，后续破坏性调整再升级 |
| `turnType` | 必填，决定前端展示形态；当前 DSL 的细分业务类型可由后端归一为页面支持的分析类展示 |
| `answerText` | 必填，普通 Markdown 文本；多结果、失败说明和长文本分析都通过它承载 |
| `processEvents` | 由后端基于 Dify SSE 节点状态统一映射生成；只有纯降级场景才允许为空 |
| `objectSelect` | 废弃兼容字段；P0 不再消费 |
| `writeback` | 仅写入确认或写入结果返回 |
| `boundObject` | 当前回合明确绑定对象时返回 |
| `suggestedSessionTitle` | 可选，用于前端更新历史会话标题 |
| `warnings` | 可选，给前端记录降级原因，不直接展示为技术错误 |

## 6. 回合类型

| `turnType` | 使用场景 | 前端展示 |
| --- | --- | --- |
| `quick_answer` | 普通规则问答、通用解释 | 用户问题 + Markdown 文本 + 轻量判断过程 |
| `object_select` | Chatflow 原始对象澄清语义；用于客户/商机不唯一或对象查询类回答 | 不展示候选卡，按文本分析类降级展示 Markdown 候选列表 |
| `text_analysis` | 商机总结、专项风险分析、多 Agent 评审 | 判断过程 + Markdown 文本结论 |
| `deep_deal_review_brief` | Chatflow 原始复杂商机评审类型 | 页面按分析类展示，不额外暴露内部类型名 |
| `stats_query` | Chatflow 原始经营统计、漏斗、收入或回款分析类型 | 页面按分析类展示，不额外暴露内部类型名 |
| `writeback_confirm` | 用户明确要求写入 CRM，等待确认 | Markdown 文本展示待写入内容，等待用户回复 |
| `writeback_result` | 用户确认、取消、重试后的写入结果 | Markdown 文本说明结果 |
| `fallback` | 结构化字段解析失败但有可读文本 | 只展示 Markdown 文本 |
| `failed` | 关键步骤失败，无法给出有效结论 | 展示失败原因和可继续操作建议 |

当前 `chatflows/ai-deal-desk-v3.example.yml` 的 `protocol_adapter` 会保留一部分 DSL 原始业务类型：`object_select`、`deep_deal_review_brief`、`stats_query`。这些类型有助于后端记录任务语义，但不要求前端新增用户可见标签；后端适配层可以把它们归一到分析类回合体验。

## 7. 判断过程事件协议

判断过程事件必须对齐 `docs/05-多Agent智能助手 判断过程事件规范.md`。

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

interface DealDeskProcessEventPayload {
  id: string;
  type: DealDeskProcessEventType;
  status: 'running' | 'completed' | 'warning' | 'failed';
  text: string;
  evidenceRefs?: string[];
}
```

生成规则：

- Dify SSE 节点状态是实时输入，不直接展示给用户。
- 后端根据节点开始、完成、失败映射 `status`，根据当前 Chatflow 的显式节点标题映射 `type`。
- 前端按 `type` 和 `status` 使用 13 号文档第 4 节的固定文案渲染；`text` 仅作为兼容字段，不作为最终展示口径。
- `text` 不得出现 Agent、workflow、route、tool_call、Prompt、token、耗时等内部词。
- 不再按 `quick_answer`、`text_analysis` 等回合类型二次压缩显示条数；显示多少条，只取决于本轮真实命中的标准事件。
- 后端和前端都不再补业务兜底过程；没有真实 `processEvents` 时，页面不展示假的“已识别 / 已读取 / 已生成”步骤。
- 如果有 `processEvents`，前端根据事件自动生成摘要条，不依赖 Chatflow 生成摘要。

当前 Chatflow 的节点标题映射明细以后端 `AiDealDeskService` 的映射常量和 `AiDealDeskStreamingEventParsingTest` 为准。本文档只定义 `processEvents` 字段协议、标准事件类型和跨层职责，不再长期双维护一份节点标题表。

补充说明：

- `suggestion_generated` 的完成态只允许由最终输出节点触发；中间 `Agent` 节点结束时不回传完成态事件。
- `failed` 只在后端已跟踪节点失败时产生，避免把无关技术节点暴露给用户。
- `risk_found` 仍保留在协议枚举中，但当前这版 Chatflow 没有单独映射它的用户节点，因此后端不会从任意 `Agent`、`LLM`、`answer` 节点自动推断它。
- `object_required`、`object_selected`、`confirmation_required`、`writeback_completed` 由对象选择和写回业务阶段驱动，不由分析阶段节点标题直接映射。
- 如果 Dify 中调整了节点标题，必须同步更新后端映射常量和对应测试；文档仅在业务语义或字段协议变化时更新。

前端摘要生成建议：

```ts
interface DealDeskProcessSummary {
  text: string; // 例如：已完成 5 项检查：任务识别、商机资料、付款条件、风险判断、建议生成
  expandedByDefault: boolean; // 生成中 true，完成后 false
}
```

摘要词映射由前端维护，避免模型每次生成不同口径。

| 事件类型 | 摘要词 |
| --- | --- |
| `task_identified` | 任务识别 |
| `object_required` | 对象确认 |
| `object_selected` | 对象确认 |
| `context_loaded` | 资料读取 |
| `memory_used` | 会话记忆 |
| `rule_checked` | 条件核对 |
| `risk_found` | 风险判断 |
| `suggestion_generated` | 建议生成 |
| `confirmation_required` | 等待确认 |
| `writeback_completed` | 写入结果 |
| `failed` | 异常处理 |

## 8. 多结果 Markdown 协议

当 Chatflow 判断用户提到客户或商机，但无法唯一确定对象时，当前 DSL 原始输出可能是 `turnType = object_select`，也可能由后端归一为 `text_analysis`。无论哪种情况，前端都只按普通 Markdown 渲染 `answerText`，不解析候选对象、不展示候选卡片、不触发 `selectCandidate` 续跑。

推荐输出：

```markdown
我查到多个匹配对象，请输入完整客户名或商机名继续：

- **华东智造集团 - AI 客服升级项目**：阶段 方案评审，金额 880000，负责人 周雨晴
- **华东智造集团 - 数据中台二期**：阶段 商务沟通，金额 520000，负责人 张伟
```

规则：

- 候选必须来自 CRM Tool API 返回值，不允许前端或后端本地 mock。
- 多结果回答只做澄清，不进入最终分析、不生成写回确认、不假装已绑定对象。
- 用户下一轮输入完整名称后，Chatflow 重新搜索；唯一命中后通过 `boundObject` 写入会话记忆。
- 表格不是默认输出；只有列表、对比或行动计划确实更清晰时才使用。
- 历史 `objectSelect` 字段只作为兼容字段保留，P0 前端不消费。

## 9. 绑定对象协议

```ts
interface DealDeskBoundObjectPayload {
  objectType: 'customer' | 'opportunity';
  objectId: string;
  objectName: string;
  customerId?: string;
  customerName?: string;
  source: 'mention' | 'selection' | 'auto_detected' | 'route_query';
}
```

前端使用规则：

- `boundObject` 写入当前会话记忆。
- P0 不额外展示固定对象提示行。
- 用户消息中的 `@客户`、`@商机` chip 仍由前端本地展示。
- 后续回合如果用户没有切换话题，可以继续带上该对象。

## 10. 写入确认协议

当前 V3 只读版不主动触发写入确认。用户明确提出保存、写入 CRM、生成跟进计划、保存为跟进记录时，Chatflow 只返回可复制草稿和说明；以下协议保留给后续恢复写回能力，或兼容历史前端状态。

```ts
type DealDeskWritebackType =
  | 'follow_record'
  | 'follow_plan'
  | 'follow_record_and_plan';

interface DealDeskWritebackPayload {
  id: string;
  type: DealDeskWritebackType;
  status:
    | 'awaiting_confirm'
    | 'confirmed'
    | 'cancelled'
    | 'failed';
  target: {
    customerId?: string;
    customerName: string;
    opportunityId?: string;
    opportunityName?: string;
    ownerId?: string;
    ownerName?: string;
    contactId?: string;
    contactName?: string;
  };
  recordDraft?: {
    followMethod?: string;
    followTimeText?: string;
    content: string;
  };
  planDraft?: {
    planMethod?: string;
    planTimeText?: string;
    content: string;
  };
  resultMessage?: string;
}
```

展示规则：

- 当前 V3 只读版显式写回类问法应降级为 `text_analysis` 草稿回答，`writeback` 为空对象或缺省。
- 后续恢复写回能力后，`awaiting_confirm` 时，`answerText` 必须用普通 Markdown 展示完整待写入内容。
- 前端不渲染写入确认卡、编辑弹窗或成功失败 banner。
- 用户回复 `确认` 后，前端带 `active_writeback_*` 字段请求 Chatflow。
- 后续恢复写回能力后，Chatflow 返回 `writeback_result`，`writeback.status` 为 `confirmed`、`cancelled` 或 `failed`。
- 写入失败时，`answerText` 说明失败原因和下一步，前端保留待确认 payload，允许用户回复 `重试` 或 `取消`。

## 11. Markdown 输出约束

`answerText` 是用户可见正文，前端按普通 Markdown 渲染。

允许：

- 段落。
- 无序列表。
- 有序列表。
- 加粗。
- 简短小标题。

不允许：

- 把结构化 payload 放进代码块。
- 输出 JSON 给用户看。
- 输出“运行状态”“启用 Agent”“未启用 Agent”等内部信息。
- 用代码块展示待写入 CRM 的内容。
- 主动追加追问引导按钮文案。

写入确认正文示例：

```text
即将写入商机跟进记录：

客户：上海智联科技股份有限公司
商机：智联云平台二期项目
跟进方式：电话沟通
负责人：张伟
内容：本次评审判断付款方案整体风险偏高，下一步重点确认首付款比例、验收范围与一期上线范围。

同时生成下一步跟进计划：

计划时间：2026-06-23 10:00
负责人：张伟
内容：与客户确认首付款比例、验收范围与一期上线范围；同步评估交付资源与计划可行性。

回复“确认”后写入 CRM；如果需要调整，直接告诉我你想改哪一部分。
```

## 12. Adapter 映射规则

前端 adapter 将 `DealDeskChatflowPayload` 转为当前页面可渲染的 `DealDeskTurn`。

推荐映射：

| Chatflow 字段 | 前端字段 | 说明 |
| --- | --- | --- |
| `answerText` | `turn.text` | 直接作为 AI 回答正文 |
| `processEvents` | `turn.process.events` | 标准事件类型、状态和展示顺序 |
| `processEvents` | `turn.process.summary` | 前端按事件类型生成摘要 |
| `objectSelect` | 不映射 | 废弃兼容字段，P0 前端不消费 |
| `boundObject` | `session.boundObject` | 写入会话记忆 |
| `writeback` | `session.activeWriteback` 或回合扩展字段 | 驱动确认状态 |
| `turnType = failed` | `turn.status` / `turn.text` | 保留失败文本 |

现有前端类型需要补齐：

- `DealDeskProcessEvent` 增加 `type` 和 `failed` 状态。
- `DealDeskSession` 增加当前活跃写入草稿状态。
- `DealDeskAssistantReplyKind` 增加 `writeback-result` 或统一为 `writeback-result`。
- 新增 `DealDeskChatflowPayload`、`DealDeskChatflowProvider`、`adaptChatflowPayloadToTurn`。

## 13. Chatflow 与后端输出改造要求

当前以 `chatflows/ai-deal-desk-v3.example.yml` 为准，并按本协议收口：

- Chatflow 不再生成用户可见的 `processEvents` 文案。
- `process_events_json` 作为迁移字段保留时应返回空数组或仅用于兼容，不能作为主展示来源。
- `rule_matched`、`draft_generated` 这类旧过程节点不再作为 Chatflow 输出协议的一部分。
- 移除用户可见 answer 中的“运行状态”“启用 Agent”“未启用 Agent”。
- 多结果不要输出 fenced JSON 代码块；只用普通 Markdown 列表或必要表格。
- 候选对象必须使用真实 CRM shape，不允许本地 mock。
- 当前只读版不调用写回工具；显式写回类问法只生成可复制草稿。后续恢复写回能力时，写入确认必须返回 `writeback` payload，不能只靠 `answerText`。
- 普通问答不需要 Chatflow 自己返回轻量过程，过程事件由后端基于 SSE 映射补齐。
- 后端保留 `processEvents` 作为前端唯一消费字段。
- `process_events_json`、`candidate_objects_json`、`writeback_payload_json` 等重复字段只作为过渡兼容字段，后续应删除。
- 当前这版 Chatflow 不需要为了判断过程再增加额外参数；节点标题变更时，同步更新后端映射常量和对应测试。
- `task_type_gate`、`evidence_router`、`crm_read_gate`、`knowledge_gate`、`external_gate`、`gap_check_gate`、`agent_router` 都是内部流转语义，不得直接出现在用户可见正文里。

## 14. 降级与异常处理

| 场景 | 前端处理 |
| --- | --- |
| Chatflow 无结构化 payload，但有 answer 文本 | 转为 `fallback`，只展示文本 |
| payload JSON 解析失败 | 展示 answer 文本，记录解析错误 |
| 历史 `turnType = object_select` | 当作 `text_analysis` 降级展示，记录兼容告警 |
| 当前 `turnType = deep_deal_review_brief` | 按分析类回合展示，保留长文本评审正文 |
| 当前 `turnType = stats_query` | 按分析类回合展示，保留统计分析正文 |
| `processEvents` 含内部词 | 前端按事件类型固定文案渲染；无法识别的事件过滤 |
| 用户要求写入 CRM | 当前只读版生成可复制草稿，不调用 CRM 写入 |
| 网络失败 | 保留用户消息，展示可重试文本 |

## 15. 验收标准

协议落地后，需要满足：

- 前端不从自然语言正文里解析对象候选或写入 payload。
- 普通问答展示由后端 SSE 映射生成的轻量判断过程。
- 业务任务判断过程全部符合 13 号文档。
- 多结果通过 Markdown 文本澄清，用户输入完整名称后能继续连续对话。
- 当前只读版显式写回类问法不会调用 CRM 写入；后续恢复写回时，写入确认不靠文本包含“确认”来判断状态。
- Chatflow 不向用户输出内部路由、Agent 调试、工具调用或 JSON 代码块。
- Chatflow 不再生成用户可见判断过程文案。
- 生产运行时不导入 mock provider；Dify 或后端失败时返回真实失败态。

## 16. 下一步落地顺序

建议按以下顺序推进：

1. 前端使用真实对话 hook 和 adapter，不再以 mock provider 作为运行时兜底。
2. 后端 SSE 映射继续跟随当前 Chatflow 节点标题，统一生成标准 `processEvents`。
3. 保持真实 Dify Chatflow API 接入，失败时返回失败态而不是 mock 成功。
4. 验证 Planner、证据台账、CRM 读取、知识库检索、外部情报和按需 Agent 分支在典型问题下可达。
5. 保留会话记忆、`@` 绑定、文件引用和历史写回确认兼容状态。
6. 当前先完成只读部署演示；真实写回接口恢复应作为后续独立阶段处理。

## 17. 最终结论

`多Agent智能助手` 不应该让前端追着 Chatflow 的自然语言输出跑，也不应该让 Chatflow 把内部编排过程直接展示给用户。

最稳的方案是：Chatflow 输出稳定业务 payload，后端基于 Dify SSE 统一生成标准过程事件，前端 adapter 按事件类型固定展示。判断过程的语义归 13 号文档统一管理，本协议负责保证它能稳定进入前端。
