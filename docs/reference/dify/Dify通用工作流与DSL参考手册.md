# Dify 通用工作流与 DSL 参考手册

| 文档字段 | 内容 |
| --- | --- |
| 文档名称 | Dify 通用工作流与 DSL 参考手册 |
| 文档版本 | v0.1 |
| 更新时间 | 2026-06-15 |
| 适用范围 | Dify Chatflow / Workflow / 节点编排 / DSL 导入导出 |
| 主要读者 | AI 产品经理、AI 应用开发、后端开发、前端开发、测试、项目维护人员 |

## 1. 文档目的

本文档是一份通用参考手册，用于指导后续在 Dify 中设计、搭建、导出、修改和导入工作流。

适用场景包括：

- 客服问答机器人
- 企业知识库助手
- CRM / ERP / WMS / OMS 业务助手
- 工单分派与处理助手
- 审批、风控、合同、财务等规则判断类助手
- 多 Agent 协作型业务工作台
- 通过代码生成可导入 Dify DSL 的项目

本文档不绑定某一个具体业务项目。具体业务需求、Agent 职责、接口字段和页面交互，应在对应项目文档中单独定义。

---

## 2. Dify 应用类型选择

### 2.1 常见应用类型

| 类型 | 适用场景 | 是否适合复杂编排 |
| --- | --- | --- |
| Chatbot / 聊天助手 | 单 Agent 对话、知识库问答、轻量客服 | 低 |
| Agent | 单 Agent 自主调用工具、开放式任务执行 | 中 |
| Workflow / 工作流 | 单次运行、结构化输入输出、批处理、报告生成 | 高 |
| Chatflow | 对话入口 + 工作流编排、多轮交互型业务助手 | 高 |
| Text Generator / 文本生成应用 | 单轮文本生成、改写、摘要、模板化生成 | 低 |

### 2.2 Workflow 与 Chatflow 的选择

| 判断问题 | 推荐类型 |
| --- | --- |
| 用户是否需要在聊天窗口里连续提问 | Chatflow |
| 是否每条消息都要触发一套结构化流程 | Chatflow |
| 是否只需要一次输入、一次输出 | Workflow |
| 是否需要 API 调用一个确定流程并返回结构化结果 | Workflow |
| 是否需要 Webhook、定时或插件事件触发 | Workflow |
| 是否需要 Answer 节点直接回复用户 | Chatflow |
| 是否需要 Output 节点返回 API 结果 | Workflow |

官方能力边界：

- Workflow 从输入或触发器开始，适合一次性任务。
- Chatflow 从用户消息开始，适合对话场景。
- Workflow 使用 `Output` 节点返回结果。
- Chatflow 使用 `Answer` 节点返回结果。
- Chatflow 不使用 Trigger 作为入口。

### 2.3 推荐选型规则

| 业务形态 | 推荐 |
| --- | --- |
| 普通知识库问答 | Chatbot 或 Chatflow |
| 知识库 + 分类路由 | Chatflow |
| 表单输入 + 报告生成 | Workflow |
| 对话式业务工作台 | Chatflow |
| 多 Agent 业务评审 | Chatflow 或 Workflow，取决于是否需要聊天入口 |
| 批量数据处理 | Workflow |
| 需要长期自主工具调用 | Agent 或 Agent Node |

---

## 3. DSL 文件基础结构

### 3.1 顶层结构

Dify 导出的 YAML 通常包含：

```yaml
app:
  name: demo
  mode: advanced-chat
dependencies: []
kind: app
version: 0.6.0
workflow:
  conversation_variables: []
  environment_variables: []
  features:
  graph:
    edges: []
    nodes: []
    viewport:
  rag_pipeline_variables: []
```

### 3.2 关键字段说明

| 字段 | 说明 |
| --- | --- |
| `app` | 应用名称、图标、描述、模式等基础信息 |
| `app.mode` | 应用模式，例如 `advanced-chat` |
| `dependencies` | 模型、Marketplace 插件等依赖 |
| `kind` | Dify 导出对象类型 |
| `version` | DSL 结构版本 |
| `workflow.conversation_variables` | 会话变量 |
| `workflow.environment_variables` | 环境变量 |
| `workflow.features` | Web App 功能开关 |
| `workflow.graph.nodes` | 节点列表 |
| `workflow.graph.edges` | 节点连线 |
| `workflow.graph.viewport` | 画布视图位置 |

### 3.3 DSL 编辑原则

- 优先基于同一 Dify 版本导出的空白模板修改。
- 不建议从零手写完整 DSL。
- 节点 `id` 必须唯一。
- 连线的 `source` 和 `target` 必须能匹配节点 ID。
- 连线的 `sourceType` / `targetType` 应和节点 `data.type` 对齐。
- 复杂节点的字段结构应优先参考当前版本导出的样例。
- 模型、知识库、工具、插件依赖当前工作区资源，跨工作区导入后可能需要重新配置。

---

## 4. 变量体系

### 4.1 系统变量

常见系统变量：

| 变量 | 说明 |
| --- | --- |
| `sys.query` | 当前用户输入 |
| `sys.files` | 当前用户上传文件 |
| `sys.conversation_id` | 会话 ID |
| `sys.dialog_count` | 会话轮次 |
| `sys.user_id` | 用户 ID |
| `sys.app_id` | 应用 ID |
| `sys.workflow_id` | 工作流 ID |
| `sys.workflow_run_id` | 工作流运行 ID |

用途：

- 作为 LLM 输入。
- 作为知识库检索 query。
- 作为 HTTP 请求参数。
- 用于日志追踪和运行记录关联。

### 4.2 用户输入变量

在 Chatflow 的开始节点中，界面可能展示：

| 变量 | 类型 | 说明 |
| --- | --- | --- |
| `userinput.query` | String | 用户输入 |
| `userinput.files` | Array[File] | 用户上传文件 |

注意：

- UI 变量名和 DSL 引用名可能不完全一致。
- 实际生成 DSL 时，应以当前导出的模板为准。

### 4.3 节点输出变量

引用格式通常为：

```text
{{#node_id.variable_name#}}
```

示例：

```text
{{#llm_node.text#}}
{{#knowledge_node.result#}}
```

常见输出：

| 节点 | 常见输出 |
| --- | --- |
| LLM | `text` |
| Knowledge Retrieval | `result` |
| HTTP Request | 响应体、状态码、Header 等，具体以节点配置和 Dify 版本为准 |
| Code | 代码 return 的字段 |
| Parameter Extractor | 抽取字段；部分版本或配置还会提供抽取状态变量 |

### 4.4 会话变量

会话变量用于 Chatflow 多轮对话中的持久状态。

适合保存：

- 当前业务对象 ID
- 用户偏好
- 最近一次任务结果
- 多轮流程状态
- 待确认草稿

使用建议：

- 不要滥用会话变量保存大量文本。
- 只保存跨轮对话真正需要的信息。
- 通过 `Variable Assigner` 节点写入。
- 复杂业务系统应以后端数据库作为正式状态源，Dify 会话变量只作为辅助上下文。

### 4.5 环境变量

环境变量用于密钥、域名、环境配置。

适合保存：

- API Base URL
- API Token
- 环境标识
- 第三方服务配置

规则：

- 不要把生产密钥写死在 DSL。
- 不要在 Answer 节点或 LLM 输出中暴露环境变量。
- 多环境部署时，保持 DSL 一致，只替换环境变量值。

---

## 5. 功能开关

### 5.1 常见功能

| 功能 | 说明 | 建议 |
| --- | --- | --- |
| Opening Statement | 对话开场白 | 大多数 Chatflow 可开启 |
| Suggested Questions | 初始建议问题 | 适合引导用户 |
| Follow-up | 回答后自动建议下一问 | 业务动作敏感时谨慎开启 |
| Text to Speech | 文本转语音 | B 端工作台通常不需要 |
| Speech to Text | 语音输入 | 移动端或语音场景可启用 |
| File Upload | 文件上传 | 合同、知识、文档分析场景可启用 |
| Citations and Attributions | 引用和归属 | 知识库问答建议开启 |
| Content Moderation | 内容审查 | 生产场景建议评估启用 |
| Annotation Reply | 标注回复 | 高频标准问答可使用 |
| More Like This | 生成相似回答 | 内容创作场景可用 |

### 5.2 通用建议

- POC 阶段先关闭非必要功能，降低调试变量。
- 知识库项目建议开启引用归属。
- 企业业务系统中，写回、审批、合同、财务等动作不要依赖自动后续问题引导用户误操作。
- 文件上传开启后，需要同步设计文件大小、类型、解析失败、敏感信息处理规则。

---

## 6. 节点总览

| 节点 | DSL 类型 | 典型用途 |
| --- | --- | --- |
| User Input | `start` | 工作流起点，接收输入 |
| LLM | `llm` | 生成、总结、判断、结构化输出 |
| Answer | `answer` | Chatflow 输出回复 |
| Output | `output` | Workflow 输出结果 |
| Knowledge Retrieval | `knowledge-retrieval` | 检索知识库 |
| Agent | `agent` | 模型自主选择工具 |
| Question Classifier | `question-classifier` | 语义分类和互斥路由 |
| If-Else | `if-else` | 条件判断和分支 |
| Human Input | `human-input` | 暂停等待人工输入 |
| Iteration | `iteration` | 对数组逐项执行 |
| Loop | `loop` | 按条件循环执行 |
| Code | `code` | 复杂数据处理 |
| Template | `template-transform` | 模板化文本生成 |
| Variable Aggregator | `variable-aggregator` | 汇聚互斥分支输出 |
| Document Extractor | `document-extractor` | 从文件中提取文本 |
| Variable Assigner | `assigner` | 写入会话变量 |
| Parameter Extractor | `parameter-extractor` | 抽取结构化参数 |
| HTTP Request | `http-request` | 调用外部 API |
| List Operator | `list-operator` | 过滤、排序、选择数组 |
| Tool | 工具节点 | 使用插件、MCP、自定义工具 |

---

## 7. 关键节点使用指南

### 7.1 LLM 节点

适合：

- 任务理解
- 内容生成
- 业务分析
- 风险判断
- 总结输出
- 结构化 JSON 生成

建议：

- 单个 LLM 节点只承担一个清晰职责。
- 输出给下游节点使用时，应要求模型输出 JSON。
- 关键 JSON 后面最好接 Code 节点做解析和容错。
- 需要知识库时，通过 `context.variable_selector` 注入检索结果。

### 7.2 Parameter Extractor 节点

适合：

- 从自然语言中抽取字段。
- 为 API 调用准备参数。
- 将用户输入转成结构化对象。

常见 DSL 写法：

```yaml
data:
  instruction: ''
  model:
    completion_params:
      temperature: 0.7
    mode: chat
    name: gpt-4o
    provider: langgenius/openai/openai
  parameters:
  - description: 需要抽取的字段说明
    name: field_name
    required: true
    type: string
  query:
  - upstream_node_id
  - text
  reasoning_mode: prompt
  title: 参数提取器
  type: parameter-extractor
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `parameters` | 定义要抽取的字段列表 |
| `parameters.name` | 输出字段名，下游可通过节点 ID 引用 |
| `parameters.description` | 字段含义说明，帮助模型理解 |
| `parameters.required` | 是否必填 |
| `parameters.type` | 字段类型，例如 string、number、boolean、object、array |
| `query` | 输入变量选择器，通常为 `[上游节点 ID, 变量名]` |
| `reasoning_mode` | 推理模式，常见为 `prompt` |

常见字段：

- `intent`
- `object_name`
- `amount`
- `date`
- `priority`
- `category`
- `need_human_confirm`

建议：

- 字段定义要明确类型和含义。
- 状态变量是否可用取决于 Dify 版本和节点配置；主流程不要只依赖状态变量判断成功失败。
- 对必填字段，建议后接 If-Else 或 Code 做空值检查。
- 对强规则字段，可以后接 Code 做二次校验。

### 7.3 Question Classifier 节点

适合：

- 将用户问题分成互斥类别。
- 不同类别进入不同流程。

适合分类：

- 查询类
- 总结类
- 生成类
- 审批类
- 风险评估类
- 其他 / 无法处理

不适合：

- 一次请求同时触发多个角色或多个分支。
- 需要输出复杂结构化计划的路由。

DSL 注意点：

- 每个分类有独立 ID。
- 连线中通常使用分类 ID 作为 `sourceHandle`。

### 7.4 If-Else 节点

适合：

- 根据明确变量走不同路径。
- 判断是否缺参数。
- 判断是否需要人工确认。
- 判断 API 返回是否成功。

建议：

- 条件判断尽量基于结构化变量，不要基于长文本。
- 复杂条件可以先用 Code 节点计算成布尔值。
- 一个 If-Else 更适合单个判断点，复杂路由不要堆太深。

### 7.5 Knowledge Retrieval 节点

适合：

- 企业制度问答。
- 产品文档问答。
- 规则判断依据检索。
- SOP、FAQ、合同条款、技术文档等知识注入。

常见写法：

```yaml
dataset_ids:
- dataset_id_1
query_variable_selector:
- start_node_id
- sys.query
retrieval_mode: single
```

LLM 接上下文常见写法：

```yaml
context:
  enabled: true
  variable_selector:
  - knowledge_node_id
  - result
```

Prompt 中引用：

```text
<context>
{{#context#}}
</context>
```

注意：

- `dataset_ids` 依赖工作区知识库，跨工作区导入可能失效。
- 知识库项目建议开启引用归属。
- 不要把所有知识库都挂给所有节点，应按任务分配知识库。

### 7.6 Code 节点

适合：

- JSON 解析
- 数据清洗
- 多节点输出合并
- 规则计算
- API 响应标准化
- 构造前端展示结构

建议：

- 对 LLM 输出做 `try / except` 容错。
- 返回字段要稳定，便于下游引用。
- 不要在 Code 节点里写敏感密钥。
- 复杂业务逻辑如果需要复用，应放到后端服务，不长期塞在 Code 节点里。

### 7.7 Template 节点

适合：

- 把结构化变量转换成 Markdown。
- 拼接固定格式报告。
- 生成邮件、通知、摘要、审批说明。

建议：

- 结构化计算交给 Code。
- 文本排版交给 Template。
- 最终 Answer 节点尽量只引用 Template 输出。

### 7.8 Variable Aggregator 节点

适合：

- 汇聚互斥分支输出。
- 让多个分类分支共用同一个下游节点。

不适合：

- 合并并行分支。
- 合并多个同时执行的 Agent 输出。

官方边界：

- Variable Aggregator 面向一次只执行一条路径的互斥分支。
- 如果要合并并行分支结果，应使用 Code 或 Template 节点。

### 7.9 HTTP Request 节点

适合：

- 查询业务系统。
- 调用后端工具层。
- 写入或更新外部系统。
- 调用搜索、数据库、文档、审批等 API。

建议：

- API 地址、Token 放环境变量。
- 写操作必须有人工确认和后端校验。
- Dify Cloud 无法直接访问本机 `127.0.0.1`，需要公网地址或内网穿透。
- 请求失败要设计重试和兜底分支。

### 7.10 Agent 节点

适合：

- 需要模型自主选择工具的任务。
- 开放式探索。
- 多工具协同但流程不完全固定的场景。

不适合：

- 强规则、强审计、强结构化输出的核心业务流程。
- 未经确认的写操作。

建议：

- 企业业务场景中，优先用 Workflow/Chatflow 显式编排。
- Agent 节点可以局部使用，不必把所有能力都做成 Agent。
- 需要限制工具权限和最大迭代次数。

### 7.11 Human Input 节点

适合：

- 流程中需要人工补充信息。
- 内部审阅。
- 工作流暂停等待确认。

注意：

- 正式业务系统的写回确认通常应由业务前端和后端控制。
- Human Input 更适合 Dify WebApp 内的流程确认，不一定适合嵌入式 SaaS 页面。

### 7.12 Iteration 与 Loop

Iteration 适合：

- 批量处理数组。
- 逐个处理文件、记录、章节、候选项。

Loop 适合：

- 需要反复执行直到条件满足。
- 自动改写、评分、重试、递进生成。

建议：

- POC 阶段谨慎使用 Loop。
- Loop 需要明确退出条件和最大次数。
- B 端业务流程优先显式编排，避免不可控循环。

---

## 8. 常见编排模式

### 8.1 简单问答

```text
Start → LLM → Answer
```

适合：

- 普通聊天
- 简单问答
- 单轮总结

### 8.2 知识库问答

```text
Start → Knowledge Retrieval → LLM → Answer
```

适合：

- FAQ
- 产品文档问答
- 制度问答

### 8.3 分类知识库问答

```text
Start
→ Question Classifier
→ 分类 A Knowledge Retrieval → LLM → Answer
→ 分类 B Knowledge Retrieval → LLM → Answer
→ 其他 Answer
```

适合：

- 售后问题 / 产品问题 / 其他问题
- 不同业务线对应不同知识库

### 8.4 参数抽取 + API 调用

```text
Start
→ Parameter Extractor
→ If-Else 判断参数是否完整
→ HTTP Request
→ Code 标准化响应
→ Template
→ Answer
```

适合：

- 查询订单
- 查询客户
- 查询库存
- 查询工单

### 8.5 受控多 Agent 编排

```text
Start
→ Parameter Extractor / LLM 路由
→ Code 生成 task_context
→ Agent A LLM
→ Agent B LLM
→ Agent C LLM
→ Code 合并结果
→ Coordinator LLM
→ Template
→ Answer
```

适合：

- 多角色评审
- 多维风险判断
- 复杂业务方案分析

建议：

- 子 Agent 可以用 LLM 节点实现。
- 每个子 Agent 独立 Prompt 和输出 Schema。
- 多 Agent 合并使用 Code 或 Template，不使用 Variable Aggregator。

### 8.6 文件处理

```text
Start
→ List Operator 筛选文件
→ Document Extractor
→ LLM / Knowledge Retrieval
→ Answer
```

适合：

- 合同分析
- 简历分析
- 文档摘要
- 表格解释

### 8.7 人工确认

```text
Start
→ LLM 生成草稿
→ Human Input 或业务前端确认
→ HTTP Request 写入业务系统
→ Answer
```

建议：

- Dify 内部 Demo 可用 Human Input。
- 嵌入业务系统时，建议由业务前端确认、后端执行写入。

---

## 9. 多 Agent 设计原则

### 9.1 Dify 中的子 Agent

在 Dify Chatflow / Workflow 中，子 Agent 不一定是独立应用，也不一定是 Agent 节点。

常见实现：

| 子 Agent 形态 | 说明 |
| --- | --- |
| LLM 节点 | 最常用，职责清晰，输出可控 |
| Agent 节点 | 可自主调用工具，适合开放式任务 |
| Workflow Tool | 将一个工作流作为工具复用 |
| 外部 Agent API | 通过 HTTP Request 调用外部 Agent 服务 |

### 9.2 什么时候需要多 Agent

适合多 Agent：

- 不同角色目标存在冲突。
- 需要并行分析多个维度。
- 每个维度有独立知识库或规则。
- 汇总节点需要识别跨角色冲突。

不适合多 Agent：

- 简单查询。
- 单一知识库问答。
- 单次文本生成。
- 只需要固定模板填充。

### 9.3 启用策略

| 策略 | 适用场景 | 优缺点 |
| --- | --- | --- |
| 全量运行 | 子 Agent 数量少，演示或 POC | 简单稳定，但成本略高 |
| 条件启用 | 子 Agent 多，任务差异大 | 成本低，但 DSL 复杂 |
| 互斥分支 | 用户一次只会进入一个任务类别 | 简单清晰，但不能同时启用多个 Agent |
| 外部编排 | 需要复杂图结构、循环、状态机 | 能力强，但开发成本高 |

### 9.4 合并策略

| 场景 | 推荐 |
| --- | --- |
| 互斥分类分支合流 | Variable Aggregator |
| 并行 Agent 结果合并 | Code 或 Template |
| 结构化结果汇总 | Code |
| 自然语言报告生成 | Coordinator LLM + Template |

---

## 10. DSL 生成与导入流程

### 10.1 推荐流程

1. 在 Dify 中手动创建目标类型应用。
2. 放置最小节点并导出空白 DSL。
3. 再导出一个包含参考节点的 DSL。
4. 基于当前版本 DSL 修改，不从零手写。
5. 生成新 YAML。
6. 导入到 Dify 新应用中测试。
7. 若导入失败，先简化节点，再逐步增加。

### 10.2 生成前准备

需要确认：

- 应用类型：Workflow 还是 Chatflow。
- 模型 provider 和 name 是否要写入 DSL。
- 是否依赖知识库 ID。
- 是否依赖 Marketplace 插件。
- 是否依赖自定义工具或 MCP。
- 是否需要环境变量。
- 是否需要会话变量。
- 是否要保留原应用名称和图标。

### 10.3 导入失败常见原因

| 问题 | 原因 | 处理 |
| --- | --- | --- |
| 节点无法识别 | 节点类型或字段结构不兼容 | 参考当前版本导出样例 |
| 连线丢失 | source / target / handle 不匹配 | 检查节点 ID 和 handle |
| 模型不可用 | 工作区没有对应模型插件或凭证 | 导入后手动选择模型 |
| 知识库失效 | dataset_id 不存在 | 导入后重新绑定知识库 |
| 工具不可用 | 插件未安装或凭证缺失 | 安装插件并配置凭证 |
| API 调用失败 | 地址不可访问或认证失败 | 检查环境变量和网络 |

### 10.4 最小可导入原则

生成 DSL 时优先做到：

- 节点能导入。
- 连线正确。
- 模型可手动选择。
- 不绑定不存在的知识库和工具。
- 不写入敏感密钥。
- 先跑通主链路，再补复杂功能。

---

## 11. 安全与权限原则

### 11.1 密钥管理

- API Key 不写入 Prompt。
- API Key 不写入 Answer。
- API Key 不硬编码到 DSL。
- API Key 使用环境变量或 Dify 工具凭证。

### 11.2 写操作控制

涉及外部系统写操作时：

- 必须有人工确认。
- 必须有后端权限校验。
- 必须有字段白名单。
- 必须有失败重试和幂等控制。
- 不允许 LLM 自由决定写入任意字段。

### 11.3 数据最小化

- 只传入完成任务所需字段。
- 不传入无关敏感数据。
- 业务系统权限应在后端完成，不依赖 Prompt 约束。
- 输出中不得泄露系统变量、密钥、原始接口响应中的敏感字段。

---

## 12. 命名规范

### 12.1 节点命名

推荐格式：

```text
序号-职责-类型
```

示例：

- `01-任务分类-QuestionClassifier`
- `02-参数抽取-ParameterExtractor`
- `03-查询业务上下文-HTTPRequest`
- `04-整理上下文-Code`
- `05-风险分析-LLM`
- `06-结果汇总-Template`
- `07-最终回复-Answer`

### 12.2 变量命名

推荐：

- 使用英文小写和下划线。
- 表达业务含义。
- 避免过短缩写。

示例：

- `task_type`
- `customer_name`
- `order_id`
- `risk_level`
- `need_human_confirm`
- `writeback_draft`

### 12.3 节点 ID

手写或代码生成 DSL 时，节点 ID 可以使用：

- 短英文 ID：`extract_params`
- 带序号 ID：`node_01_start`
- 时间戳 ID：与 Dify 自动生成风格相近

建议：

- 人工维护文档时使用语义化 ID 更好读。
- 若 Dify 导入对 ID 格式敏感，应优先参考当前导出文件。

---

## 13. 测试清单

### 13.1 导入后检查

- 应用类型正确。
- 所有节点都显示。
- 连线没有断。
- 模型可用。
- Answer 或 Output 节点存在。
- 必要环境变量已配置。
- 必要知识库已绑定。
- 必要工具凭证已配置。

### 13.2 功能测试

- 正常输入能跑通。
- 缺少参数时能追问或兜底。
- 分类结果符合预期。
- API 失败时有错误提示。
- LLM 输出格式符合下游要求。
- 并行分支结果能正确合并。
- 最终回答可读。

### 13.3 安全测试

- 输出不包含密钥。
- 输出不包含内部 Prompt。
- 写操作不会自动执行。
- 无权限数据不会被暴露。
- 文件上传不会绕过限制。

---

## 14. 常见误区

| 误区 | 正确做法 |
| --- | --- |
| 把 Chatbot 当成复杂工作流 | 复杂流程用 Chatflow / Workflow |
| 用单个 If-Else 表达多个子 Agent 同时启用 | 使用并行分支或全量运行后汇总 |
| 用 Variable Aggregator 合并并行结果 | 并行结果用 Code 或 Template 合并 |
| 在 DSL 里写死生产密钥 | 使用环境变量或工具凭证 |
| 知识库 ID 跨工作区直接复用 | 导入后重新绑定知识库 |
| LLM 输出 JSON 后直接给下游使用 | 用 Code 做解析和容错 |
| 把业务权限写进 Prompt | 权限由后端控制，Prompt 只做辅助约束 |
| 一开始就接所有工具 | 先跑通最小链路，再逐步加工具 |

---

## 15. 推荐项目落地顺序

1. 明确业务任务和用户输入。
2. 判断应用类型。
3. 设计节点流程图。
4. 定义每个节点的输入输出。
5. 先搭最小可运行流程。
6. 加入分类、参数抽取和分支。
7. 加入知识库或 HTTP 工具。
8. 加入错误处理和人工确认。
9. 导出 DSL 版本化。
10. 通过测试集验证效果。

---

## 16. 官方文档参考

- Workflow & Chatflow：https://docs.dify.ai/en/use-dify/build/workflow-chatflow
- Orchestration Logic：https://docs.dify.ai/en/use-dify/build/orchestrate-node
- App Toolkit：https://docs.dify.ai/en/use-dify/build/additional-features
- LLM：https://docs.dify.ai/en/use-dify/nodes/llm
- Knowledge Retrieval：https://docs.dify.ai/en/use-dify/nodes/knowledge-retrieval
- Answer：https://docs.dify.ai/en/use-dify/nodes/answer
- Output：https://docs.dify.ai/en/use-dify/nodes/output
- Agent：https://docs.dify.ai/en/use-dify/nodes/agent
- Question Classifier：https://docs.dify.ai/en/use-dify/nodes/question-classifier
- If-Else：https://docs.dify.ai/en/use-dify/nodes/ifelse
- Human Input：https://docs.dify.ai/en/use-dify/nodes/human-input
- Iteration：https://docs.dify.ai/en/use-dify/nodes/iteration
- Loop：https://docs.dify.ai/en/use-dify/nodes/loop
- Code：https://docs.dify.ai/en/use-dify/nodes/code
- Template：https://docs.dify.ai/en/use-dify/nodes/template
- Variable Aggregator：https://docs.dify.ai/en/use-dify/nodes/variable-aggregator
- Document Extractor：https://docs.dify.ai/en/use-dify/nodes/doc-extractor
- Variable Assigner：https://docs.dify.ai/en/use-dify/nodes/variable-assigner
- Parameter Extractor：https://docs.dify.ai/en/use-dify/nodes/parameter-extractor
- HTTP Request：https://docs.dify.ai/en/use-dify/nodes/http-request
- List Operator：https://docs.dify.ai/en/use-dify/nodes/list-operator
- Tool：https://docs.dify.ai/en/use-dify/nodes/tools

---

## 17. 使用建议

后续新项目可以按以下方式使用本文档：

1. 先看第 2 章选择应用类型。
2. 再看第 8 章选择编排模式。
3. 根据第 6、7 章确定节点。
4. 按第 10 章生成或修改 DSL。
5. 按第 13 章测试。
6. 按第 14 章排查常见误区。

如果项目进入正式开发，应另行补充项目级文档：

- 业务需求说明
- Agent 职责说明
- 工具接口说明
- 知识库配置说明
- 测试用例说明
