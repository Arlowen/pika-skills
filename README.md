# Pika Skills

个人 Codex 技能索引目录。这里收录可复用的工作流、自动化脚本和 Agent 指令，让常用能力可以被快速发现、安装、维护和复用。

<p>
  <img alt="Codex Skills" src="https://img.shields.io/badge/Codex-Skills-111827?style=flat-square" />
  <img alt="Index" src="https://img.shields.io/badge/Type-Index%20Directory-2563eb?style=flat-square" />
  <img alt="Maintained" src="https://img.shields.io/badge/Status-Maintained-16a34a?style=flat-square" />
</p>

## 目录

- [技能索引](#技能索引)
- [目录结构](#目录结构)
- [新增技能规范](#新增技能规范)
- [视觉与前端风格](#视觉与前端风格)

## 技能索引

| 技能 | 类型 | 说明 | 入口 |
| --- | --- | --- | --- |
| `notify-feishu-completion` | 通知自动化 | 在 Codex 工作完成后，通过飞书应用机器人发送完成通知。 | [`SKILL.md`](./notify-feishu-completion/SKILL.md) |

## 目录结构

```text
pika-skills/
├── README.md
└── notify-feishu-completion/
    ├── SKILL.md
    ├── agents/
    │   └── openai.yaml
    └── scripts/
        └── send_feishu_completion.py
```

## 新增技能规范

每个技能建议保持独立目录，并提供清晰的入口文件：

```text
skill-name/
├── SKILL.md          # 技能说明、触发条件、执行流程
├── scripts/          # 可执行脚本
├── assets/           # 模板、示例素材、配置样例
└── agents/           # Agent 配置
```

收录到索引时，请补齐：

- 技能名称：目录名和 `SKILL.md` 中的 `name` 保持一致。
- 使用场景：说明什么时候应该触发这个技能。
- 执行入口：优先链接到 `SKILL.md`，脚本放在技能目录内部。
- 依赖说明：环境变量、外部 API、CLI 工具需要写清楚。

## 视觉与前端风格

README 使用稳定的 GitHub Markdown 表达视觉层级：

- 顶部用徽章建立项目状态和类型识别。
- 索引用表格承载核心信息，方便扫描和扩展。
- 目录结构用代码块展示，避免描述性文字过重。
- 标题保持短句，内容按“入口 -> 结构 -> 规范”的阅读路径组织。

如果后续技能数量增加，可以把索引拆成更细的分组：

| 分组 | 适合放入的技能 |
| --- | --- |
| 通知与消息 | 飞书、邮件、Slack、IM 推送 |
| 文档与知识库 | FAQ、README、设计文档、模板生成 |
| 前端与设计 | UI 审核、页面实现、Figma 转代码 |
| 发布与运维 | 打包、部署、CI 修复、发布检查 |

## 维护建议

- 新增技能后，同步更新 [技能索引](#技能索引)。
- 删除或重命名目录后，检查 README 链接是否仍然可用。
- 技能脚本优先放在对应技能目录内，避免全局脚本难以追踪。
- 对外部服务的密钥只通过环境变量读取，不写入仓库。
