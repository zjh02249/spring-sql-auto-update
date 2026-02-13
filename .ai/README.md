# .ai 目录说明

## 📋 目录用途

本目录用于实现 **AI 会话持久化协作框架**，解决 AI 会话 token 超限导致上下文丢失的问题。

## 🎯 核心目标

- ✅ AI 无状态情况下能够恢复上下文
- ✅ 支持 session 重启后继续开发
- ✅ 支持长期工程演进
- ✅ 可多人协作
- ✅ 所有文件可 git 管理
- ✅ 可扩展为 RAG 系统

## 📁 文件职责

| 文件 | 用途 | 更新频率 | 说明 |
|------|------|---------|------|
| `context.md` | 项目背景与技术栈 | 很少 | 长期不变的基础信息 |
| `decisions.md` | 架构决策记录 | 偶尔 | 每次架构变化时更新 |
| `current-task.md` | 当前任务状态 | 频繁 | 每个任务开始/结束时更新 |
| `roadmap.md` | 项目路线图 | 偶尔 | 阶段性规划 |
| `summary.md` | 当前阶段压缩总结 | 频繁 | Token 即将超限时生成 |
| `constraints.md` | 技术约束 | 很少 | 技术限制与规范 |
| `prompt-template.md` | 统一启动模板 | 很少 | 新 session 启动模板 |

## 🔄 更新规则

### 自动更新
- `summary.md` - 通过 `ai-summarize.sh` 自动生成

### 手动更新
- `current-task.md` - 每次开始新任务时更新
- `decisions.md` - 架构变化时追加
- `context.md` - 项目重大变化时更新
- `roadmap.md` - 阶段规划调整时更新
- `constraints.md` - 技术约束变化时更新

## 🚀 快速使用

### 第一次使用
```bash
# 1. 初始化所有文档（填写项目信息）
vim .ai/context.md
vim .ai/current-task.md

# 2. 启动 AI 会话
./scripts/ai-new.sh
```

### Session 即将超限时
```bash
# 1. 生成压缩总结
./scripts/ai-summarize.sh

# 2. 提交到 git
git add .ai/summary.md
git commit -m "chore: update AI session summary"

# 3. 启动新 session
./scripts/ai-new.sh
```

### 恢复上下文
```bash
# 直接运行，自动加载所有上下文
./scripts/ai-new.sh
```

## 📝 Git 管理建议

### 需要提交的文件
```
.ai/
├── README.md          ✅ 提交
├── context.md         ✅ 提交
├── decisions.md       ✅ 提交
├── current-task.md    ✅ 提交
├── roadmap.md         ✅ 提交
├── summary.md         ✅ 提交
├── constraints.md     ✅ 提交
└── prompt-template.md ✅ 提交
```

### 提交建议
```bash
# 任务完成时提交
git add .ai/current-task.md .ai/summary.md
git commit -m "chore: update AI context - completed task X"

# 架构变化时提交
git add .ai/decisions.md
git commit -m "docs: record architecture decision - XXX"
```

## 🔧 工具脚本

| 脚本 | 用途 |
|------|------|
| `scripts/ai-new.sh` | 加载上下文启动新 AI session |
| `scripts/ai-summarize.sh` | 生成当前阶段压缩总结 |

## 🌟 工作流程

### 标准开发流程
```
1. 开始新任务
   ├─ 更新 current-task.md
   └─ ./scripts/ai-new.sh

2. 开发中...
   ├─ 遇到架构决策 → 记录到 decisions.md
   └─ Token 即将超限 → ./scripts/ai-summarize.sh

3. 任务完成
   ├─ 更新 current-task.md (标记完成)
   ├─ ./scripts/ai-summarize.sh
   └─ git commit
```

### 多人协作流程
```
1. Pull 最新代码
   git pull origin main

2. 检查上下文
   cat .ai/summary.md
   cat .ai/current-task.md

3. 启动 AI 协助
   ./scripts/ai-new.sh

4. 完成后提交
   git add .ai/
   git commit -m "chore: update AI context"
   git push
```

## 📚 扩展为 RAG

未来可以扩展为向量数据库：

```bash
# 将所有 .ai/*.md 文件向量化
# 使用 LangChain / LlamaIndex 进行语义检索
# 自动加载相关上下文片段
```

---

**维护者**: 项目开发团队  
**最后更新**: 2026-02-13  
**版本**: 1.0.1
