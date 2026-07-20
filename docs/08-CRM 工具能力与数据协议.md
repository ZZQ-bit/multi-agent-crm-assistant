# CRM 工具能力与数据协议

## 1. 文档目的

本文档定义当前 CRM 工具能力、Dify 调用方式、数据协议和写回边界。接口实现以 CordysCRM 后端 `AiDealDeskToolController`、`AiDealDeskDifyToolController` 和 `AiDealDeskToolService` 为准。

现行技术方案：

```text
Dify Chatflow
  -> HTTP Request 节点
  -> CordysCRM 多Agent智能助手 Tool API
  -> CordysCRM 现有客户、商机、联系人、跟进记录、跟进计划服务
```

工具业务能力属于 CordysCRM 后端。Dify 只负责意图识别、参数抽取、工具调用编排和回答生成，不承载 CRM 权限、写回白名单、幂等和审计。

当前 Dify 通过自定义工具和 HTTP 节点调用匿名工具网关；运行链路不依赖 MCP。

## 2. 现有能力映射

| 能力 | 现有入口 | 阶段 3 处置 |
| --- | --- | --- |
| 客户搜索 | `POST /account/page` | 通过 `search_customers` 薄适配复用 |
| 客户详情 | `GET /account/get/{id}` | 通过 `get_customer_context` 薄适配复用 |
| 客户下商机 | `POST /account/opportunity/page` | 作为客户上下文扩展能力，P0 可暂缓 |
| 商机搜索 | `POST /opportunity/page` | 通过 `search_opportunities` 薄适配复用 |
| 商机详情 | `GET /opportunity/get/{id}` | 通过 `get_opportunity_context` 薄适配复用 |
| 商机联系人 | `GET /opportunity/contact/list/{opportunityId}` | 纳入商机上下文包 |
| 跟进记录查询 | `POST /follow/record/page` | 纳入客户/商机上下文包 |
| 跟进计划查询 | `POST /follow/plan/page` | 纳入客户/商机上下文包 |
| 新建跟进记录 | `POST /follow/record/add` | 通过 `create_follow_record` 受控写回复用 |
| 新建跟进计划 | `POST /follow/plan/add` | 通过 `create_follow_plan` 受控写回复用 |
| 更新商机字段 | `POST /opportunity/update` | P1 扩展，P0 不开放给 AI 写回 |

## 3. 工具清单

多Agent智能助手 后端提供统一 Tool API：

```text
POST /ai/deal-desk/tools/search-customers
POST /ai/deal-desk/tools/search-opportunities
POST /ai/deal-desk/tools/resolve-crm-object
POST /ai/deal-desk/tools/get-customer-context
POST /ai/deal-desk/tools/get-opportunity-context
POST /ai/deal-desk/tools/create-follow-record
POST /ai/deal-desk/tools/create-follow-plan
POST /ai/deal-desk/tools/get-funnel-snapshot
POST /ai/deal-desk/tools/get-customer-l2c-health
POST /ai/deal-desk/tools/get-contract-revenue-snapshot
```

同一组接口同步支持 `/front/ai/deal-desk/tools/*`，保持与现有 多Agent智能助手 对话入口一致。

Dify 使用统一匿名网关：

```text
POST /anonymous/ai/deal-desk/dify-tools/{toolName}
Header: X-DIFY-TOOL-TOKEN
```

当前 YML 的 Planner 只绑定 `resolve-crm-object`。客户或商机上下文由后续 CRM 数据读取节点调用；统计工具和写回工具虽由后端提供，但当前 YML 未调用写回工具。

## 4. 统一请求与返回

### 4.1 请求字段

| 字段 | 说明 |
| --- | --- |
| `keyword` | 客户或商机搜索关键词 |
| `objectReference` | CRM 对象解析使用的原始客户或商机名称 |
| `customerId` | 已确认客户 ID |
| `opportunityId` | 已确认商机 ID |
| `contactId` | 可选联系人 ID |
| `ownerId` | 可选负责人 ID，缺省使用当前用户 |
| `content` | 待写入跟进内容或计划内容 |
| `followMethod` | 跟进方式 |
| `followTime` | 跟进记录时间 |
| `planMethod` | 跟进计划方式 |
| `planTime` | 跟进计划时间 |
| `idempotencyKey` | 写回幂等键 |
| `limit` | 搜索或上下文条数限制 |

### 4.2 返回 envelope

```json
{
  "success": true,
  "code": "OK",
  "message": "",
  "data": {},
  "warnings": []
}
```

错误码：

| code | 说明 |
| --- | --- |
| `OK` | 成功 |
| `INVALID_ARGUMENT` | 参数缺失或格式不合法 |
| `OBJECT_NOT_FOUND` | 未找到客户或商机 |
| `OBJECT_AMBIGUOUS` | 命中多个候选，需要 Chatflow 用 Markdown 文本澄清 |
| `PERMISSION_DENIED` | 当前用户无权读取或写入 |
| `CRM_READ_FAILED` | CRM 读取失败 |
| `WRITEBACK_NOT_ALLOWED` | 写回类型不在 P0 白名单 |
| `WRITEBACK_VALIDATION_FAILED` | 写回前校验失败 |
| `WRITEBACK_FAILED` | CRM 写回失败 |
| `DUPLICATE_WRITEBACK` | 重复写入请求 |

## 5. 查询工具协议

### resolve_crm_object

输入：`objectReference`、`limit`

解析器同时搜索客户和商机。唯一精确匹配优先于其他模糊候选；只有一个同等级候选时返回唯一对象；存在多个同等级候选时返回 `OBJECT_AMBIGUOUS` 和真实候选列表。

### search_customers

输入：`keyword`、`limit`

输出：

```json
{
  "candidates": [
    {
      "id": "customer-id",
      "name": "客户名称",
      "ownerId": "owner-id",
      "ownerName": "负责人",
      "departmentName": "部门"
    }
  ]
}
```

### search_opportunities

输入：`keyword`、`limit`

输出：

```json
{
  "candidates": [
    {
      "id": "opportunity-id",
      "name": "商机名称",
      "customerId": "customer-id",
      "customerName": "客户名称",
      "amountText": "880000",
      "stageName": "商务采购",
      "ownerName": "负责人"
    }
  ]
}
```

多结果澄清规则：

- 0 个候选：返回 `OBJECT_NOT_FOUND`。
- 1 个候选：返回 `OK`。
- 多个候选：返回 `OBJECT_AMBIGUOUS` 和候选数组，Chatflow 按 `docs/06` 的多结果 Markdown 协议展示。
- Agent 不允许在多个候选中猜测对象。

## 6. 标准上下文包

`get_opportunity_context` 是多 Agent 分析使用的主要商机上下文工具。

输出结构：

```json
{
  "opportunity": {},
  "customer": {},
  "contacts": [],
  "recentFollowRecords": [],
  "openFollowPlans": [],
  "riskSignals": [],
  "missingFields": [],
  "sourceRefs": []
}
```

字段说明：

| 字段 | 说明 |
| --- | --- |
| `opportunity` | 商机基础信息、金额、阶段、负责人、客户关系 |
| `customer` | 客户基础信息和负责人 |
| `contacts` | 商机关联联系人 |
| `recentFollowRecords` | 近期跟进记录 |
| `openFollowPlans` | 未完成跟进计划 |
| `riskSignals` | 后端可确定的基础风险信号，不替代 Agent 判断 |
| `missingFields` | 缺失的关键字段 |
| `sourceRefs` | 数据来源引用，如 `customer:{id}`、`opportunity:{id}` |

当前 YML 在对象唯一后由 CRM 数据读取节点调用上下文工具，再把标准上下文写入统一证据台账。专项 Agent 不各自重复查询 CRM。

## 7. 写回工具协议与当前边界

后端提供两类写回接口：

- `create_follow_record`
- `create_follow_plan`

当前 YML 不调用这两个接口。后续启用时必须满足：

- 用户明确表达保存、写入、生成跟进记录或创建跟进计划。
- 当前客户或商机唯一。
- 前端已展示待写入内容预览。
- 用户确认后才调用写回工具。
- `idempotencyKey` 必填。

禁止写回：

- 审批通过。
- 正式报价承诺。
- 合同可签结论。
- 财务已通过结论。
- 高风险商机字段更新。

写回成功返回：

```json
{
  "recordId": "follow-record-id"
}
```

或：

```json
{
  "planId": "follow-plan-id"
}
```

写回失败必须保留草稿，由前端允许用户重试、修改或取消，不能重复创建同一条记录或计划。

## 8. Dify 调用规则

当前 Chatflow 同时使用自定义工具和 HTTP Request 节点调用 Tool API。

建议：

- API Key 和 CRM 登录态由 CordysCRM 后端管理，不暴露给前端。
- Dify 只传工具所需参数，不传完整 CRM 原始页面响应。
- 当前版本不得调用写回工具。
- HTTP Request 节点的错误结果必须进入可恢复分支。
- 后续工具稳定后，可再补 OpenAPI Custom Tool 或 MCP 适配。

## 9. Chatflow 使用说明

`docs/09-Agent 与 Chatflow 设计.md` 基于本文档说明：

- Planner 如何判断是否需要 CRM 查询。
- Planner 如何调用 `resolve_crm_object` 查证新对象。
- CRM 数据读取节点如何调用上下文接口。
- 专项 Agent 如何消费标准上下文包。
- 内容生成任务如何生成草稿但不直接写入。

Chatflow 不应重新定义 CRM 工具字段；如需变更，应同步更新后端 DTO、OpenAPI Schema、YML 和本文档。

## 10. 当前实现状态

当前实现满足：

- 本文档成为 CRM 工具协议主依据。
- CordysCRM 后端具备最小 AI Tool API 薄适配层。
- Tool API 复用现有 CRM Service，不重写客户、商机、跟进业务逻辑。
- 查询、多候选、上下文包、写回校验和幂等有测试覆盖。
- Dify 可通过自定义工具或 HTTP Request 调用 Tool API。
- 写回执行权明确留在 CordysCRM 后端。
