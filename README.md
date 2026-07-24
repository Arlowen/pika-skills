# Pika Skills & MCP

个人 Codex Skill 与 MCP 扩展仓库。Skill 负责工作流、判断规则和安全约束，MCP
负责需要 IDE API 的确定性操作。

<p>
  <img alt="Codex Skills" src="https://img.shields.io/badge/Codex-Skills%20%26%20MCP-111827?style=flat-square" />
  <img alt="Index" src="https://img.shields.io/badge/Type-Workflow%20Extensions-2563eb?style=flat-square" />
  <img alt="Maintained" src="https://img.shields.io/badge/Status-Maintained-16a34a?style=flat-square" />
</p>

## 技能索引

| 技能 | 类型 | 说明 | 入口 |
| --- | --- | --- | --- |
| `gh-update-branch-pr` | GitHub 工作流 | 根据当前分支的实际变更和仓库 PR 模板更新已有 PR 的标题与正文，并处理 issue 关联。 | [`SKILL.md`](./skill/gh-update-branch-pr/SKILL.md) |
| `notify-feishu-completion` | 通知自动化 | 在 Codex 工作完成后，通过飞书应用机器人发送完成通知。 | [`SKILL.md`](./skill/notify-feishu-completion/SKILL.md) |
| `manage-idea-workflows` | IDEA 编排 | 通过 IDEA Control MCP 管理 Run/Debug 服务，并将改动按逻辑组织到 Changelist。 | [`SKILL.md`](./skill/manage-idea-workflows/SKILL.md) |

## MCP 索引

| MCP | 目标环境 | 说明 | 入口 |
| --- | --- | --- | --- |
| `idea-control-mcp` | IntelliJ IDEA 2024.2–2025.3 | `Pika MCP`：独立提供本地 MCP HTTP 服务，精确控制 Run/Debug execution，并操作 IDEA Changelist；不依赖 JetBrains MCP Server。 | [`README.md`](./mcp/idea-control-mcp/README.md) |

## 目录

```text
pika-skills/
├── skill/
│   ├── gh-update-branch-pr/
│   ├── manage-idea-workflows/
│   └── notify-feishu-completion/
└── mcp/
    └── idea-control-mcp/
```
