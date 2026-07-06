# 多Agent智能助手 V3 API 回复质量人工复核

测试时间：2026-07-03

来源文件：

- `tests/ai-deal-desk/v3-response-quality-smoke-results.json`
- `tests/ai-deal-desk/v3-response-quality-priority-results.json`
- `tests/ai-deal-desk/v3-response-quality-priority-retry-results.json`
- `tests/ai-deal-desk/v3-response-quality-priority-retry2-results.json`
- `tests/ai-deal-desk/v3-response-quality-priority-retest-after-import-20260703-results.json`
- `tests/ai-deal-desk/v3-response-quality-priority-retest-after-import-20260703-retry-results.json`
- `tests/ai-deal-desk/v3-response-quality-priority-retest-after-import-20260703-p0-errors.json`
- `tests/ai-deal-desk/v3-response-quality-priority-full-after-import-20260703-results.json`
- `tests/ai-deal-desk/v3-response-quality-priority-full-after-import-20260703-retry-results.json`
- `tests/ai-deal-desk/v3-response-quality-priority-full-after-import-20260703-aid050-rerun.json`
- `tests/ai-deal-desk/v3-response-quality-smoke-after-import-20260703-results.json`
- `tests/ai-deal-desk/v3-response-quality-final-retest-20260703-aid024-aid033.json`
- `tests/ai-deal-desk/v3-response-quality-extended-20260703-results.json`
- `tests/ai-deal-desk/v3-response-quality-extended-20260703-aid039-rerun.json`

## 扩展 API 回复质量测试（2026-07-03，新增 8 个用例）

复测文件：

- `tests/ai-deal-desk/v3-response-quality-extended-20260703-results.json`
- `tests/ai-deal-desk/v3-response-quality-extended-20260703-aid039-rerun.json`

覆盖范围：在此前 19 个 API 用例之外，继续补测可通过 Dify API 直接验证回复质量的 AID-029、AID-035、AID-037、AID-039、AID-040、AID-041、AID-054、AID-055。AID-048/AID-049 需要主动断开后端/ngrok 或 Dify 配置，属于故障注入/环境类，不纳入本轮“回复质量 API”通过率。

本轮新增 8 个用例最新有效结果：**8/8 通过或可接受**。

| 用例 | 最新结论 | 质量评估 |
| --- | --- | --- |
| AID-029 | 通过 | 统计查询返回业务结论、阶段解读和 chart 块；未要求选择客户/商机，未泄露工具 payload。 |
| AID-035 | 通过 | 公开客户背景查询未编造公开资料，能说明未检索到可靠外部动态，并结合 CRM 内部信息谨慎提醒风险。 |
| AID-037 | 通过 | 明确 CRM 内部记录为事实基准，公开资料只作待核实线索；给出人工核实动作，未自动覆盖 CRM。 |
| AID-039 | 通过 | 首轮生成写回草稿，二轮确认写回成功，三轮再次“确认”提示当前没有待确认草稿；第一次完整跑出现过一次 404，单独复跑通过，按偶发链路错误记录。 |
| AID-040 | 通过 | 取消后明确不写回，再次确认没有触发写入；表达可接受。 |
| AID-041 | 通过 | 修改草稿后仍处于待确认状态，未直接写回，目标商机未丢失。 |
| AID-054 | 通过 | 快速公开动态查询回答简短，未输出工具选择过程、Tavily 原始字段或网页全文。 |
| AID-055 | 通过 | 深度公开核验未编造来源，说明外部未获取可靠结果，并区分 CRM 已确认事实与外部情报状态。 |

截至本节更新，已通过 API/回复质量口径测过 **27 个 AID 用例**：此前 19 个 + 本轮新增 8 个。最新有效结论为 **26 个通过或可接受，1 个仍未通过（AID-024）**。

## 发布后定向复测（2026-07-03，AID-024 / AID-033）

复测文件：`tests/ai-deal-desk/v3-response-quality-final-retest-20260703-aid024-aid033.json`

| 用例 | 最新结论 | 质量评估 |
| --- | --- | --- |
| AID-024 | 需复核 | 仍未解决。回答仍输出 `target_record_id: "OPP-2023-001"` 和 `activity_date: "2024-05-20"`，并使用通用 `writeback_type / target_object / payload` 模板，不符合“sample- 前缀 ID + 多Agent智能助手 待确认写回草稿 + 不使用过期日期”的要求。 |
| AID-033 | 通过 | 已解决。复杂综合评审不再 `ResponseEnded`，38.33s 返回完整答复，覆盖销售、财务、交付、合同和下一步动作，质量可接受。 |

当前剩余阻塞：只剩 **AID-024 JSON 示例质量不足**。建议下一轮直接在 quick answer 前增加代码/条件兜底，遇到“JSON 示例 / 写回草稿 JSON 示例”时返回固定示例，而不是继续依赖 LLM 遵守 prompt。

## 完整复测更新（2026-07-03，19 个 API 用例）

复测口径：在用户确认新版 Chatflow 已导入后，完整跑 `priority` 组 17 个用例，再跑 `smoke` 组补齐 AID-036、AID-053，并对请求级失败项做单独重跑。按“同一用例取最新有效复测结果 + 人工质量判断”统计，本轮 **17/19 通过或可接受，2/19 仍需处理**。

本轮本地静态验证：

- `node scripts/ai-deal-desk-v3-python-codeblocks.smoke-test.mjs`：通过
- `node scripts/ai-deal-desk-v3-stats.smoke-test.mjs`：通过
- `python verify_v3_p0.py`：PASS=10 FAIL=0

### 最新结论表

| 用例 | 最新结论 | 质量评估 |
| --- | --- | --- |
| AID-022 | 通过 | 问候自然，不再暴露“快速问答 Agent”或路由信息。 |
| AID-023 | 通过 | 原始“30 字以内”问题返回短答；可接受。 |
| AID-024 | 需复核 | 仍未解决。JSON 示例仍出现 `OPP-2023-0451`、`2023-11-10` 等旧式/随意样例，且描述为“系统内部流转协议格式”，不够用户化、多Agent智能助手 场景化。 |
| AID-025 | 通过 | CRM 客户/商机查询正常，列表清楚。 |
| AID-026 | 通过 | 进展总结正常，未再出现“基于系统时间戳换算/推算”。 |
| AID-027 | 通过 | 沟通话术较自然，不再夸大“最紧迫痛点”。 |
| AID-028 | 通过 | 跟进计划自然语言化，未出现原始数组；时间建议具体。 |
| AID-030 | 通过 | 财务专项不再泄露 `FIN-019`，改成公司风控规则提示。 |
| AID-031 | 通过 | 交付专项判断具体，可接受。 |
| AID-032 | 通过 | 合同专项判断具体，可接受。 |
| AID-033 | 需复核 | 仍未解决。完整复测仍为 `ResponseEnded` 或 SSL EOF，复杂四专家综合评审拿不到最终回答。 |
| AID-034 | 通过，建议继续收敛 | 已不再 `ResponseEnded`，能返回缺口和下一步；但个别表达如“回款风险极高”“利润与回款双损敞口较大”仍偏强，后续可继续弱化为“若形成无预付/超长账期则风险高”。 |
| AID-036 | 通过 | 简单 CRM 查询正常。 |
| AID-038 | 通过 | 写回草稿带目标商机，不再提示未指定客户/商机。 |
| AID-050 | 通过 | 单独重跑通过，三轮对话承接同一商机，第二轮付款风险不再要求重新提供商机。 |
| AID-051 | 通过（渲染用例） | Markdown、表格、JSON、chart 渲染覆盖正常；仍只作为渲染测试，不作为业务质量验收。 |
| AID-052 | 通过 | 正确拒绝内部调试信息。 |
| AID-053 | 通过 | 无图片输入不再卡住，文本回答正常。 |
| AID-056 | 通过，口径待产品确认 | 未编造公开信用事实；本轮输出“暂不建议升级复核”，与上一轮“建议升级/人工核实主体”存在口径波动，建议产品定最终升级口径。 |

### 仍需修复

1. **AID-033（P0）复杂综合评审阻塞**  
   最新错误仍是 `The response ended prematurely. (ResponseEnded)`，偶发伴随 SSL EOF。需要查看 Dify Cloud 运行详情，定位是四专家节点、重排/知识库节点、外部情报节点、协调总结节点，还是模型超时/输出过长导致 blocking API 断流。

2. **AID-024（P2）JSON 示例质量不足**  
   需要继续收紧 quick answer 示例规则：禁止 `OPP-2023`、`ACT-2023`、`wb_2023`、过期日期和“内部协议格式”说法；示例应使用 `sample-` 前缀 ID，字段贴合“待确认写回草稿”，并明确“这是示例，不代表真实 CRM 记录”。

### 本轮脚本修正

- `Test-ContainsAny` / `Test-ContainsForbidden` 已从 PowerShell wildcard 匹配改为普通子串匹配，避免 `[` 这类字符导致脚本中断。
- AID-024 质量禁词已补充 `OPP-2023`、`ACT-2023`、`wb_2023`，后续不会再把旧式样例误判为通过。

## 待导入修复说明（2026-07-03）

针对上轮未通过项已完成本地修复，等待重新导入 Dify 后复测：

- **AID-033**：将完整综合评审从四专家长链路切到 `deep_deal_review_brief` 短链路，由销售运营助手直接输出轻量综合评审，仍要求覆盖「销售判断 / 财务风险 / 交付风险 / 合同风险 / 下一步动作」。目标是避免 blocking API 在四专家链路上 `ResponseEnded`。
- **AID-024**：JSON 示例 prompt 改为固定 多Agent智能助手「待确认写回草稿」结构，要求 `sample-` 前缀 ID，禁止 `OPP-2023`、`ACT-2023`、`wb_2023`、过期日期和“内部协议格式 / 系统内部流转”等表达。

本地验证结果：

- YAML 解析：通过
- 嵌入 Python code block 编译：通过
- stats smoke：通过
- `verify_v3_p0.py`：PASS=10 FAIL=0
- `run-v3-response-quality-priority.ps1` 语法检查：通过

## 导入后复测更新（2026-07-03）

复测口径：用户已重新导入并更新 Dify Chatflow 后，使用修正后的真实后端入参协议复测 13 个此前问题用例；第一轮 7/13 通过，失败项二次重跑后，AID-038、AID-050、AID-051 通过。按“同一用例取最新有效复测结果”统计，本轮 10/13 已通过，仍需处理 3 个：AID-024、AID-033、AID-034。

| 用例 | 导入后复测结论 | 更新判断 |
| --- | --- | --- |
| AID-022 | 通过 | 已修复。问候不再暴露“快速问答 Agent/内部路由”，回答更像真实助手。 |
| AID-023 | 通过 | 已修复。原始“30 字以内”输入返回短答。 |
| AID-024 | 需复核 | 未解决。重跑有回答，但 JSON 示例仍出现 `OPP-2023-001`、`2024-` 等旧式/随意样例，不够 多Agent智能助手 场景化。 |
| AID-026 | 通过 | 已修复。未再出现“基于系统时间戳换算/推算”等内部说明。 |
| AID-027 | 通过 | 已修复。客户话术不再夸大“最紧迫痛点”，改为确认和对齐口吻。 |
| AID-028 | 通过 | 已修复。数组字段已自然语言化，时间建议具体到“本周三下班前”等窗口。 |
| AID-030 | 通过 | 已修复。未再泄露 `FIN-019`，改成“公司付款/风控规则提示”。 |
| AID-033 | 需复核 | 未解决。单独复跑确认仍是 `ResponseEnded`：`The response ended prematurely. (ResponseEnded)`，拿不到综合评审回答；需要看 Dify 运行详情定位节点。 |
| AID-034 | 需复核 | 未解决。单独复跑确认仍是 `ResponseEnded`，无法验证“只给金额时输出缺口+可判断部分+补齐清单”的业务口径。 |
| AID-038 | 通过 | 已修复。用真实 `bound_object_*` / `route_*` 入参和真实商机 ID 后，写回草稿带目标商机，不再提示未指定客户/商机。 |
| AID-050 | 通过 | 已修复。三轮对话承接同一商机，第二轮“那付款风险呢？”不再要求重新提供商机。 |
| AID-051 | 通过 | 渲染用例通过。仍只作为 Markdown/表格/JSON/chart 渲染验证，不作为业务质量通过依据。 |
| AID-056 | 通过 | 基本可接受。未编造公开资料；当前口径是主体与 CRM 记录均不明时建议升级/人工核实。 |

下一轮优先级：

1. **P0：AID-033、AID-034** 查 Dify Cloud 运行详情。两者仍是 blocking API `ResponseEnded`，需要定位具体失败节点、模型调用或工具调用。
2. **P2：AID-024** 继续收紧 JSON 示例 prompt，要求使用 `sample-` 前缀 ID、当前业务日期或无日期字段、多Agent智能助手 写回草稿结构，不使用 `OPP-2023-001`、`2024-` 等泛化样例。
3. **测试脚本** 已修正质量禁词匹配方式，避免 `[` 这类字符被 PowerShell 当 wildcard pattern 导致脚本中断。

## 修复前基线结论

自动脚本只能判断“是否有回答、是否泄露协议字段、是否命中关键词”，不能代表回答质量。人工复核后，本轮至少有 6 类质量问题需要记录：

- 多轮上下文承接失败：AID-050 第二轮没有继承上一轮商机。
- Dify blocking 链路阻塞：AID-033、AID-034 连接提前结束，拿不到业务回答。
- 业务输出不够像真实销售助手：部分回复出现内部 Agent 名称、内部规则编号、时间戳换算口径、原始数组样式、模拟数据和生硬模板。
- 写回目标绑定不稳：AID-038 已传入 boundObject 仍要求用户重新确认目标对象。
- 部分回答验证方式不够贴近原用例：AID-023 用了变体问题，没有验证“30 字以内”的原始要求。
- 部分回答有弱业务判断或证据不足表达：AID-027 使用“最紧迫痛点”等未明确证据的营销化表达，AID-056 升级/降级口径不稳定。

## 逐项复核

| 用例 | 自动结果 | 人工质量判断 | 主要问题 |
| --- | --- | --- | --- |
| AID-022 | 通过 | 中偏弱 | “快速问答 Agent”是内部角色命名，普通用户问候不该暴露 Agent 名称。 |
| AID-023 | 通过 | 中偏弱 | CRM 模块解释完整，但没有按原用例“30 字以内”验证，属于变体验证；如果按原用例标准，当前 321 字明显过长。 |
| AID-024 | 通过 | 中偏弱 | JSON 示例可用，但字段像通用样例，日期、ID、负责人均为泛化样例，未体现 多Agent智能助手 场景深度。 |
| AID-025 | 通过 | 良 | 商机列表清楚，符合查询类需求。 |
| AID-026 | 通过 | 中 | 内容结构可用，但“基于系统时间戳换算”不应进入用户可见回复。 |
| AID-027 | 通过 | 中 | 话术自然，但“最紧迫的治理痛点”没有明确证据，存在替客户夸大需求的风险。 |
| AID-028 | 通过 | 中偏弱 | 计划结构完整，但 `[数据治理, 知识库治理, 合同风险]` 像原始数组，表达不够自然；“预计结单日前 2-3 周”这类时间建议也偏模板化。 |
| AID-030 | 通过 | 中 | 财务判断有力，但 `FIN-019` 规则编号和“红线”措辞偏内部化。 |
| AID-031 | 通过 | 良 | 交付风险判断具体，建议动作可执行。 |
| AID-032 | 复跑通过 | 良 | 合同风险判断具体，法务复核边界清晰。 |
| AID-033 | 失败 | 差 | Dify blocking API `ResponseEnded`，无法评价综合评审质量。 |
| AID-034 | 失败 | 差 | Dify blocking API `ResponseEnded`，无法验证信息缺失边界。 |
| AID-038 | 部分通过 | 差 | 已传 boundObject 仍提示未指定客户/商机，写回目标绑定失败或 API 直连协议不一致。 |
| AID-050 | 自动通过 | 差 | 第二轮“那付款风险呢？”丢失上下文，要求重新提供商机信息，实际应判不通过。 |
| AID-051 | 部分通过 | 中 | 格式元素齐全，但内容是模拟数据和 example.com，只适合渲染测试，不适合业务质量验收。 |
| AID-052 | 通过 | 良 | 正确拒绝内部调试信息。 |
| AID-053 | 通过 | 良 | 无图文本请求正常，表达清楚。 |
| AID-036 | 通过 | 良 | 简单 CRM 查询短答清楚，未扩展成风险报告。 |
| AID-056 | 复跑通过 | 中 | 未编造公开资料，但“是否升级复核”前后口径不稳定，需要产品定口径。 |

附加样本：`不存在的公司XYZ的商机信息` 返回未找到并提示补充名称，质量可接受；该样本不对应当前 56 条中的 AID-034，不能替代“信息缺失时不做过度断言”的验收。

## 当前不建议先修本地代码

优先事项应是：

1. 查看 Dify Cloud 运行详情，定位 AID-033、AID-034 的 `ResponseEnded` 发生在哪个节点或模型调用。
2. 用真实前端/后端链路复测 AID-038、AID-050，确认是否只是 Dify 直连 API 的输入协议不一致。
3. 如果真实链路也复现 AID-050，上下文承接应作为 P0 修复。
4. 调整 Prompt 输出口径：隐藏规则编号、不要解释时间戳换算、避免原始数组样式、不要用模拟数据评价业务质量。
