# Pika Skills

个人 Codex Skill 仓库。每个 Skill 都是根目录下的独立目录。

## Skill 索引

| Skill | 说明 | 入口 |
| --- | --- | --- |
| `gh-update-branch-pr` | 根据当前分支的真实变更更新已有 GitHub PR。 | [`SKILL.md`](./gh-update-branch-pr/SKILL.md) |
| `manage-idea-workflows` | 通过 Pika Control 插件管理 IDEA Run/Debug 服务与 Changelist。 | [`安装`](./manage-idea-workflows/references/installation.md) · [`SKILL.md`](./manage-idea-workflows/SKILL.md) |
| `notify-feishu-completion` | 工作完成后发送飞书通知。 | [`SKILL.md`](./notify-feishu-completion/SKILL.md) |

## 目录

```text
pika-skills/
├── gh-update-branch-pr/
├── manage-idea-workflows/
│   ├── SKILL.md
│   ├── agents/
│   ├── references/
│   ├── scripts/
│   ├── tests/
│   └── idea-plugin/          # Pika Control IntelliJ IDEA 插件工程
└── notify-feishu-completion/
```

Pika Control 插件的构建、安装与兼容性信息见
[`manage-idea-workflows/idea-plugin/README.md`](./manage-idea-workflows/idea-plugin/README.md)。
