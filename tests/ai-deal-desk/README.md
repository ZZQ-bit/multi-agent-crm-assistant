# 多Agent智能助手 V3 测试目录

## 文件说明

- `v3-test-cases.json` - 40+ 条测试用例，覆盖 5 大场景
- `run-v3-tests.ps1` - 测试执行脚本
- `v3-quality-evaluation-dimensions.md` - 回复质量评估维度定义
- `v3-quality-report-template.md` - 质量评估报告模板

## 快速开始

### 1. 执行测试

```powershell
# 使用默认配置
.\run-v3-tests.ps1

# 自定义 API Key
$env:DIFY_API_KEY="your-api-key"
.\run-v3-tests.ps1

# Dry Run（只显示不执行）
.\run-v3-tests.ps1 -DryRun

# 覆盖结果文件路径
.\run-v3-tests.ps1 -OutputFile "custom-results.json"
```

### 2. 脚本参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `-TestCasesFile` | `./v3-test-cases.json` | 测试用例文件路径 |
| `-OutputFile` | `./v3-test-results.json` | 结果输出文件路径 |
| `-DifyApiKey` | 环境变量 | Dify API Key |
| `-DifyBaseUrl` | Dify 默认 | Dify Base URL |
| `-IntervalSeconds` | 2 | 请求间隔（秒），防止限流 |
| `-MaxRetries` | 3 | 每个用例最大重试次数 |
| `-DryRun` | `false` | 仅显示测试用例，不执行 |

## 测试场景

### 1. CRM 查询（8 条）
- 客户名模糊查询
- 商机名模糊查询
- 多候选对象澄清
- 无匹配结果
- 带金额/阶段筛选
- 绑定客户/商机后查询

### 2. 商机进展总结（6 条）
- 指定商机摘要
- 最近跟进记录
- 未完成计划
- 风险信号识别
- 决策人变更
- 长期未跟进

### 3. 沟通内容生成（6 条）
- 邮件模板
- 异议处理话术
- 报价说明
- 交付承诺沟通
- 合同条款沟通
- 写回草稿生成

### 4. 跟进计划生成（6 条）
- 标准跟进计划
- 紧急推进
- 资源协调
- 风险缓解
- 合同谈判
- 写回确认/取消

### 5. 复杂商机评审（14 条）
- 综合评审
- 财务专项
- 交付专项
- 合同专项
- 销售风险
- 缺失信息处理
- 多风险交叉
- 折扣+付款组合
- 定制+周期组合
- 验收+赔付组合
- 轻量问答边界
- 写回确认闭环
- 写回取消处理
- 证据边界清晰

## 质量评估

执行测试后会生成 `v3-test-results.json`，包含：

- 自动推断：知识库召回、CRM工具调用、写回草稿生成、Agent启用
- 耗时统计：平均、最大、最小、超出预期数量
- 两轮对话：草稿生成+确认/取消场景

后续使用 `v3-quality-report-template.md` 和 `v3-quality-evaluation-dimensions.md` 生成完整评估报告。

## Dify API 配置

脚本会自动从以下位置读取 API 配置：

1. 环境变量：`DIFY_API_KEY`、`DIFY_BASE_URL`
2. `../CordysCRM/backend/app/src/main/resources/commons.properties`
3. 默认：`https://api.dify.ai/v1` + 

## 注意事项

- 每次请求间隔 2 秒，防止触发 Dify 限流
- 复杂评审场景（14 条）单独标记 `requires_two_rounds`，需要等待用户确认/取消
- 脚本会完整保留两轮对话结果和会话 ID