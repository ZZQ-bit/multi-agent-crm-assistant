# 多Agent智能助手

这是一个嵌入 CRM 的 AI 商机协同工作台示例项目。项目基于 CordysCRM 开源 CRM 底座扩展，围绕销售真实工作流完成“查询业务上下文、生成判断与建议、输出可执行草稿”的只读演示闭环。

## 核心能力

- 客户与商机查询：在 CRM 对话入口中选择客户、商机，读取客户画像、联系人、跟进记录、计划和商机阶段。
- 商机进展总结：基于 CRM 已有记录生成当前进展、风险点和下一步建议。
- 沟通内容生成：生成面向客户的邮件、微信、跟进话术等草稿。
- 跟进计划生成：给出下一步动作、负责人、时间建议，并生成可复制草稿；真实写回作为后续阶段能力。
- 复杂商机评审：从销售、财务、交付、合同 4 个视角进行多 Agent 分析，汇总结论、风险和行动建议。

## 目录结构

```text
CordysCRM/        CRM 前后端源码与 AI Deal Desk 扩展
chatflows/        Dify Chatflow 模板，已脱敏
demo-data/        演示数据 SQL
docs/             PRD、协议、Agent/Chatflow 设计、验收文档
knowledge-base/   AI Deal Desk 业务规则知识库
scripts/          本地启动、数据导入、冒烟测试脚本
tests/            Chatflow 与接口测试脚本
```

## Chatflow 配置

`chatflows/ai-deal-desk-v3.example.yml` 是公开模板，不包含个人 API Key。导入 Dify 后需要自行配置：

- 模型供应商 API Key
- `CRM_TOOL_BASE_URL`
- `DIFY_TOOL_TOKEN`

本地调试 Dify Cloud 时，`CRM_TOOL_BASE_URL` 需要是可公网访问的 HTTPS 地址，例如自己的 ngrok、cloudflared 或云服务器域名。

## 本地启动

在 Windows 环境下，可参考：

```powershell
powershell -ExecutionPolicy Bypass -File scripts\start-local-deal-desk.ps1
```

启动后访问：

```text
http://localhost:8081/#/login
http://localhost:8081/#/agent/deal-desk
```

演示账号和数据库初始化方式请参考 `docs/11-本地启动与联调经验.md` 与 `demo-data/`。

## 安全说明

本仓库只保留演示代码、模板配置、模拟数据和项目文档。真实部署时请不要把以下内容提交到 GitHub：

- `.env`、真实 API Key、Dify App Key、模型供应商 Key
- 真实服务器域名、个人 ngrok 域名、真实数据库密码
- MySQL/Redis 运行时数据目录
- 日志、构建产物、个人简历和本地临时文件

## 项目定位

这个项目重点展示 AI 产品设计与工程闭环能力，而不是重做 CRM 基础业务。CordysCRM 作为业务对象底座，多Agent智能助手负责在 CRM 内完成上下文读取、多角色判断、建议生成和草稿输出；真实 CRM 写回保留为后续扩展方向。
