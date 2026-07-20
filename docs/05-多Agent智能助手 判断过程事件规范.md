# 多Agent智能助手 判断过程事件规范

| 文档字段 | 内容 |
| --- | --- |
| 文档名称 | 多Agent智能助手 判断过程事件规范 |
| 所属系统 | CordysCRM / 多Agent智能助手 |
| 文档版本 | v2.0 |
| 更新时间 | 2026-07-16 |
| 当前阶段 | V3 Stable Enhanced 实时过程映射 |
| 主要读者 | 产品、设计、前端、后端、Chatflow 编排 |

> [!NOTE]
> 本文档是工作台“判断过程”的唯一业务口径。事件传输字段见 `docs/06-多Agent智能助手 Chatflow 与前端协议规范.md`。

## 1. 定义与目标

判断过程是 Dify 本轮真实执行状态的业务化摘要，用于让用户知道系统正在完成哪些关键工作。它不是模型思维链、Prompt、工具日志，也不是为了演示而播放的固定进度动画。

目标：

- 真实：只展示本轮实际开始运行的关键节点。
- 实时：基于 Dify SSE 的 `node_started`、`node_finished` 更新。
- 自然：用销售和 CRM 业务语言表达。
- 克制：隐藏路由、条件判断、代码整理和变量汇合等技术节点。
- 并行可信：多个节点同时运行时分别展示，不合并成一个假步骤。

唯一数据链路：

```text
Dify SSE workflow_started / node_started / node_finished / workflow_finished
-> CordysCRM 后端按稳定 node_id 选择并映射关键节点
-> 标准 process_event
-> 前端按事件 id 更新、排序、汇总和展示
```

## 2. 核心规则

1. 后端只按明确登记的 `node_id` 映射，不按节点标题、节点类型或关键词模糊猜测。
2. `node_started` 创建或更新一条 `running` 事件；对应 `node_finished` 更新为 `completed`、`warning` 或 `failed`。
3. 事件 `id` 使用 Dify 的 `node_id`。前端按事件 `id` 去重，不按事件 `type` 合并。
4. 同一种业务事件可以同时存在多条。例如销售、财务、交付和合同分析必须保留为四条独立事件。
5. 未登记的节点事件直接忽略，不补假事件。
6. 不显示百分比和预计剩余时间；Dify 没有提供可靠的节点完成百分比。
7. 执行中默认展开，工作流完成后默认折叠，用户可以再次展开。

## 3. 标准事件类型

```ts
type DealDeskProcessEventType =
  | 'task_understanding'
  | 'attachment_analysis'
  | 'crm_retrieval'
  | 'knowledge_retrieval'
  | 'external_research'
  | 'sales_analysis'
  | 'finance_analysis'
  | 'delivery_analysis'
  | 'legal_analysis'
  | 'analytics_analysis'
  | 'answer_generation'
  | 'object_required'
  | 'confirmation_required'
  | 'writeback_completed'
  | 'failed';
```

旧事件类型 `task_identified`、`context_loaded`、`rule_checked`、`risk_found`、`suggestion_generated`、`memory_used` 和 `object_selected` 仅作为历史会话兼容输入保留，不再由当前 V3 实时映射生成。

## 4. 当前 V3 关键节点映射

映射基线为根目录 `AI Deal Desk - V3 Stable Enhanced.yml`。

| Dify `node_id` | 标准事件类型 | 运行态文案 | 完成态文案 | 顺序 |
| --- | --- | --- | --- | ---: |
| `task_planner` | `task_understanding` | 正在理解本轮任务 | 已理解本轮任务 | 10 |
| `attachment_image_summary` | `attachment_analysis` | 正在解析附件内容 | 已解析附件内容 | 20 |
| `crm_read_request` | `crm_retrieval` | 正在读取 CRM 资料 | 已读取 CRM 资料 | 30 |
| `deal_rules_knowledge` | `knowledge_retrieval` | 正在检索业务规则 | 已检索业务规则 | 40 |
| `external_intel_agent` | `external_research` | 正在查询公开资料 | 已查询公开资料 | 50 |
| `sales_agent` | `sales_analysis` | 销售视角分析中 | 已完成销售视角分析 | 60 |
| `finance_agent` | `finance_analysis` | 财务视角分析中 | 已完成财务视角分析 | 61 |
| `delivery_agent` | `delivery_analysis` | 交付视角分析中 | 已完成交付视角分析 | 62 |
| `legal_agent` | `legal_analysis` | 合同视角分析中 | 已完成合同视角分析 | 63 |
| `analytics_agent` | `analytics_analysis` | 正在分析经营数据 | 已完成经营数据分析 | 64 |
| `simple_answer` | `answer_generation` | 正在整理回答 | 已生成回答 | 80 |
| `image_answer` | `answer_generation` | 正在整理回答 | 已生成回答 | 80 |
| `crm_light_answer` | `answer_generation` | 正在整理回答 | 已生成回答 | 80 |
| `gap_answer` | `answer_generation` | 正在整理回答 | 已生成回答 | 80 |
| `business_answer_agent` | `answer_generation` | 正在汇总结论 | 已生成结论 | 80 |

说明：

- 同一回合只会命中一个主要回答分支；多个回答节点虽然共享 `answer_generation` 类型，仍按 `node_id` 区分。
- Planner、CRM、知识库、外部情报、附件和领域 Agent 只有实际运行时才出现。
- 路由、证据汇总、结果整理、协议适配和最终 Answer 节点不展示。
- 节点标题可以调整，不影响映射；如果导出 YML 时 `node_id` 发生变化，必须同步更新本表、后端常量和测试。

## 5. 并行运行展示

多个关键节点并行时，前端同时保留多条 `running` 事件。例如完整商机评审：

```text
✓ 已理解本轮任务
✓ 已读取 CRM 资料
✓ 已检索业务规则
● 销售视角分析中
● 财务视角分析中
● 交付视角分析中
● 合同视角分析中
```

单个节点完成后只更新自身：

```text
✓ 已完成销售视角分析
● 财务视角分析中
● 交付视角分析中
✓ 已完成合同视角分析
```

并行展示规则：

- 列表按第 4 节的业务顺序稳定排列，不按事件到达或完成顺序跳动。
- 同时存在多个运行事件时，允许多个转圈状态。
- 不把四个领域 Agent 压缩成一个“业务分析”。
- 某个节点失败时只标记该节点；其他节点继续运行或保持完成。
- 若完成事件先于开始事件到达，状态优先级为 `failed > warning > completed > running`，不得从完成退回运行。

## 6. 摘要规则

摘要由前端根据当前事件状态实时生成，不由模型或 Chatflow 生成。

| 状态 | 摘要格式 |
| --- | --- |
| 只有 1 条运行 | `正在处理：{事件名称}` |
| 多条并行运行 | `正在并行处理 {N} 项：{事件名称列表}` |
| 部分完成、部分运行 | `已完成 {C} 项，正在处理 {R} 项` |
| 全部成功 | `已完成 {N} 项处理` |
| 存在异常 | `已完成 {C} 项，{F} 项异常` |

事件名称列表过长时只在摘要中显示前 3 项并追加“等”；展开区域始终展示全部事件。

## 7. 失败和工作流结束

- 关键节点失败：保留原事件类型，状态设为 `failed`，显示“{业务步骤}处理失败”。
- 未登记技术节点失败：不单独展示；若导致整轮失败，由统一 `failed` 事件说明“暂时无法完成本轮处理”。
- 收到 `workflow_finished` 后仍存在 `running` 的已登记事件时：成功工作流将其收口为 `completed`；失败工作流将最后运行的关键事件标记为 `failed`。
- 最终回答能够生成但部分节点失败时，正文必须说明结论边界，不能把整个回合伪装为完全成功。

## 8. 展示边界

以下内容不得进入用户可见判断过程：

- Dify 内部路由和条件分支名称。
- Prompt、模型名、token、输入输出 JSON。
- Tool 请求参数和完整响应。
- 证据汇总、变量聚合、协议适配等编排节点。
- 模型原始推理或“正在思考”类空泛文案。
- 虚假的百分比、预计剩余时间或固定步骤动画。

节点真实耗时保留在后端日志和 Dify 运行日志中，本轮不在工作台判断过程中展示。

## 9. 场景示例

### 9.1 商机进展总结

```text
理解任务 -> 读取 CRM 资料 -> 整理回答
```

### 9.2 财务专项判断

```text
理解任务 -> 读取 CRM 资料 + 检索业务规则 -> 财务视角分析 -> 汇总结论
```

### 9.3 外部公开资料查询

```text
理解任务 -> 查询公开资料 -> 汇总结论
```

### 9.4 完整商机评审

```text
理解任务
-> CRM / 知识库 / 外部情报 / 附件按实际需要并行读取
-> 销售 / 财务 / 交付 / 合同按 Planner 规划并行分析
-> 汇总结论
```

## 10. 跨层职责

| 层级 | 职责 |
| --- | --- |
| Dify Chatflow | 正常执行节点并输出原生 SSE；不生成用户可见过程文案 |
| CordysCRM 后端 | 按 `node_id` 过滤关键节点，映射类型、状态和固定文案 |
| 前端 adapter | 校验事件、兼容旧类型，不制造新业务事件 |
| 判断过程组件 | 按事件 `id` 去重、稳定排序、并行展示、生成摘要 |

## 11. 验收标准

- 商机总结不再从开始到结束只显示“任务识别”。
- 页面展示的每条运行事件都能在同一轮 Dify SSE 中找到对应节点事件。
- CRM、知识库、外部情报和附件并行时可同时显示多条运行状态。
- 四个领域 Agent 并行时分别显示，完成状态互不覆盖。
- 未运行的节点不出现，技术节点不出现。
- 完成后判断过程自动折叠，展开后状态与摘要一致。
- 普通短答没有关键事件时允许不显示判断过程，不补假过程。
