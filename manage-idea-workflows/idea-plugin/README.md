# Pika Control

此目录是 `manage-idea-workflows` Skill 配套的 IntelliJ IDEA 插件工程。插件在
IDEA 进程内调用 Run/Debug 与 Changelist API，并只在 `127.0.0.1` 暴露本地
REST 接口；上一级 Skill 通过标准库 Python 脚本调用该接口。

它不依赖 JetBrains MCP Server，也不实现或注册 MCP 协议。

## 能力

- 列出 IDEA Run Configuration 与当前 execution。
- 按精确配置名以 `RUN` 或 `DEBUG` 模式启动服务，并默认防止重复实例。
- 按精确 `executionId` 停止服务。
- 列出 IDEA Changelist 与 tracked change。
- 将显式路径原子地移动到指定 Changelist。
- 删除非默认 Changelist；存在改动时先全部移入当前默认 Changelist。
- 永远拒绝删除默认 Changelist。

## 兼容性

- 插件版本：`0.2.0`
- IntelliJ IDEA：2024.2–2025.3
- 构建 JDK：21
- Python 调用器：Python 3.10+，仅使用标准库

## 与 Skill 的关系

```text
manage-idea-workflows/
  ├─ SKILL.md
  ├─ scripts/pika_idea.py
  └─ idea-plugin/
      └─ Pika Control plugin
          └─ http://127.0.0.1:8765/api/v1
              └─ IntelliJ Platform APIs
```

Python 文件是 Skill 的内部实现，不是需要单独安装、发布或注册的 CLI 产品。

## 安装

### IDEA 插件

1. 从 GitHub Release 下载 `Pika-IDEA-Control-0.2.0.zip`。
2. 在 IDEA 的 `Settings | Plugins` 中选择从磁盘安装 `Pika Control`。
3. 启用插件后重启 IDEA。

插件默认监听 `127.0.0.1:8765`。可在启动 IDEA 前通过 `PIKA_IDEA_PORT` 修改端口，
或使用 JVM 属性 `-Dpika.idea.port=<port>`。如果端口改变，同时给 Skill 脚本设置
`PIKA_IDEA_URL=http://127.0.0.1:<port>`。

### Codex Skill

Skill 入口位于 [`../SKILL.md`](../SKILL.md)，Python helper 位于
[`../scripts/pika_idea.py`](../scripts/pika_idea.py)。
完整安装步骤见 [`../references/installation.md`](../references/installation.md)。

快速检查插件连通性：

```bash
python3 ../scripts/pika_idea.py health
python3 ../scripts/pika_idea.py projects
```

所有命令和 REST 字段见
[`../references/idea-rest-api.md`](../references/idea-rest-api.md)。

## 构建与测试

```bash
./gradlew clean test buildPlugin \
  verifyPluginStructure \
  verifyPluginProjectConfiguration \
  verifyPlugin \
  -PtargetIdeaVersion=2024.2

python3 -m unittest discover -s ../tests -p 'test_*.py' -v
```

插件包生成在 `build/distributions/Pika-IDEA-Control-0.2.0.zip`。

## 自动发布

推送与 `build.gradle.kts` 版本一致的稳定标签（例如 `v0.2.0`）后，GitHub Actions
会分别用 IDEA 2025.3.2 和 2024.2 运行测试与兼容性校验，然后创建 GitHub Release，
上传插件 ZIP 和 SHA-256 校验文件。
