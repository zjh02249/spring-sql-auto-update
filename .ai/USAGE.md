# AI 持久化协作框架 - 使用指南

**版本**: 1.0.1  
**项目**: Flyway Digital  
**最后更新**: 2026-03-02

---

## 📖 概述

本指南详细说明如何使用 `.ai` 目录实现 AI 会话持久化协作。

---

## 🎯 核心价值

### 解决的问题

1. **Token 超限**: AI 会话达到 token 上限后无法继续
2. **上下文丢失**: 新会话无法继承之前的工作上下文
3. **协作困难**: 多人/多会话难以保持一致性
4. **知识流失**: 项目演进过程中决策记录缺失

### 提供的能力

1. ✅ **上下文恢复**: 新 session 快速加载完整上下文
2. ✅ **长期演进**: 支持项目长期迭代
3. ✅ **团队协作**: 多人共享统一的项目知识
4. ✅ **知识沉淀**: 架构决策和技术约束持久化

---

## 📁 目录结构

```
.ai/
├── README.md              # 目录说明
├── context.md             # 项目背景（很少修改）
├── decisions.md           # 架构决策（偶尔追加）
├── current-task.md        # 当前任务（频繁更新）
├── roadmap.md             # 项目路线图（偶尔修改）
├── summary.md             # 当前阶段总结（自动生成）
├── constraints.md         # 技术约束（很少修改）
└── prompt-template.md     # 启动模板（很少修改）

scripts/
├── ai-new.sh              # 启动新 session（可执行）
└── ai-summarize.sh        # 生成总结（可执行）
```

---

## 🚀 第一次使用流程

### Step 1: 初始化基础文档

```bash
# 1. 填写项目背景
vim .ai/context.md
```

**填写内容**:
- 项目名称和定位
- 核心问题和解决方案
- 技术栈
- 项目目标
- 核心设计原则

**示例**: 参考现有的 `context.md`

---

### Step 2: 记录架构决策

```bash
# 2. 记录关键架构决策
vim .ai/decisions.md
```

**记录内容**:
- 技术选型（如为什么选 JDBC）
- 设计原则（如事务策略）
- 兼容性决策（如 Flyway 兼容）

**格式**: 使用 ADR (Architecture Decision Record) 格式

**示例**:
```markdown
## ADR-001: 选择 JDBC 作为唯一数据库依赖

**日期**: 2025-01
**状态**: ✅ 已采纳

### 背景
...

### 决策
...

### 理由
...
```

---

### Step 3: 定义技术约束

```bash
# 3. 定义技术约束
vim .ai/constraints.md
```

**定义内容**:
- Java 版本要求
- 依赖限制
- 代码规范
- 禁止事项

**示例**: 参考现有的 `constraints.md`

---

### Step 4: 创建当前任务

```bash
# 4. 创建当前任务
vim .ai/current-task.md
```

**填写内容**:
- 当前目标
- 子任务清单
- 遇到的问题
- 阻塞点

**格式**:
```markdown
# 当前任务

**任务状态**: 🔄 进行中
**负责人**: xxx

## 📋 当前目标
...

## 🎯 任务描述
...

## 🐛 遇到的问题
...
```

---

### Step 5: 规划路线图（可选）

```bash
# 5. 规划项目路线图
vim .ai/roadmap.md
```

**规划内容**:
- 第一阶段（已完成）
- 第二阶段（进行中）
- 第三阶段（计划中）

---

### Step 6: 测试启动脚本

```bash
# 6. 测试 AI session 启动
./scripts/ai-new.sh
```

**预期结果**:
- 脚本读取所有上下文文件
- 生成完整 Prompt
- 启动 AI CLI（如果可用）
- 或显示手动复制提示

---

## 🔄 日常使用流程

### 场景 1: 开始新任务

```bash
# 1. 更新当前任务
vim .ai/current-task.md

# 填写新任务:
# - 任务目标
# - 子任务清单
# - 预期结果

# 2. 启动 AI session
./scripts/ai-new.sh

# 3. 开始开发
# ... AI 会基于上下文协助你
```

---

### 场景 2: Token 即将超限

```bash
# 1. 生成压缩总结
./scripts/ai-summarize.sh

# 这会:
# - 调用 AI 生成当前状态总结
# - 自动写入 .ai/summary.md
# - 显示下一步操作提示

# 2. 检查生成的总结
cat .ai/summary.md

# 3. 提交到 Git
git add .ai/summary.md
git commit -m "chore: update AI session summary"
git push

# 4. 启动新 session
./scripts/ai-new.sh
```

---

### 场景 3: 新 Session 恢复上下文

```bash
# 1. 拉取最新代码（多人协作）
git pull origin main

# 2. 快速浏览总结
cat .ai/summary.md

# 3. 启动 AI session
./scripts/ai-new.sh

# AI 会自动加载:
# - 项目背景 (context.md)
# - 架构决策 (decisions.md)
# - 当前总结 (summary.md)
# - 当前任务 (current-task.md)
# - 技术约束 (constraints.md)
```

---

### 场景 4: 记录架构决策

```bash
# 1. 编辑 decisions.md
vim .ai/decisions.md

# 2. 追加新决策（使用 ADR 格式）
## ADR-XXX: 决策标题

**日期**: 2025-XX-XX
**状态**: ✅ 已采纳 / ⏳ 待定 / ❌ 已废弃

### 背景
...

### 决策
...

### 理由
...

### 影响
...

# 3. 提交到 Git
git add .ai/decisions.md
git commit -m "docs: record architecture decision - XXX"
git push
```

---

### 场景 5: 更新当前任务

```bash
# 开始任务时
vim .ai/current-task.md
# 状态改为: 🔄 进行中

# 任务完成时
vim .ai/current-task.md
# 状态改为: ✅ 已完成
# 更新任务历史

# 提交
git add .ai/current-task.md
git commit -m "chore: update current task status"
```

---

## 🤝 多人协作流程

### 协作原则

1. **定期同步**: 每天拉取最新 `.ai` 目录
2. **及时更新**: 完成任务后立即更新文档
3. **清晰记录**: 架构决策必须记录
4. **避免冲突**: 分工明确，减少同时编辑

### 协作工作流

```bash
# === 开始工作前 ===

# 1. 拉取最新代码
git pull origin main

# 2. 查看最新上下文
cat .ai/summary.md
cat .ai/current-task.md

# 3. 确认任务分工（避免冲突）
# 如果有冲突，与团队沟通

# === 工作中 ===

# 4. 启动 AI session
./scripts/ai-new.sh

# 5. 开发...

# 6. 遇到架构决策
vim .ai/decisions.md  # 记录决策

# === 工作结束 ===

# 7. 更新任务状态
vim .ai/current-task.md

# 8. 生成总结（如需要）
./scripts/ai-summarize.sh

# 9. 提交
git add .ai/
git commit -m "chore: update AI context - XXX"
git push
```

---

## 📝 文件更新时机

### context.md
**更新时机**: 很少  
**更新场景**:
- 项目定位重大变化
- 技术栈重大升级
- 核心目标调整

**不需要更新的情况**:
- 版本号变化
- 小功能增加
- BUG 修复

---

### decisions.md
**更新时机**: 偶尔  
**更新场景**:
- 架构设计变化
- 技术选型变化
- 重要设计决策

**示例场景**:
- 决定使用某个新库
- 修改表结构设计
- 改变事务策略
- 增加新的技术约束

**格式**:
```markdown
## ADR-XXX: 决策标题

**日期**: YYYY-MM-DD
**状态**: ✅ 已采纳

### 背景
为什么需要这个决策？

### 决策
具体决定是什么？

### 理由
为什么这样决定？

### 影响
这个决策会带来什么影响？
```

---

### current-task.md
**更新时机**: 频繁  
**更新场景**:
- 开始新任务
- 任务进展
- 任务完成
- 遇到问题
- 解决阻塞

**示例**:
```markdown
# 当前任务

**任务状态**: 🔄 进行中
**最后更新**: 2025-02-13

## 📋 当前目标
实现 XXX 功能

## 🎯 任务描述
- [ ] 子任务 1
- [ ] 子任务 2
- [x] 子任务 3（已完成）

## 🐛 遇到的问题
### 问题 1: XXX
**描述**: ...
**解决方案**: ...
**结果**: ✅ 已解决

## 🚧 当前阻塞点
无阻塞 / 等待 XXX
```

---

### summary.md
**更新时机**: Token 超限前  
**更新方式**: 自动生成  
**更新命令**:
```bash
./scripts/ai-summarize.sh
```

**何时生成**:
- Token 使用量接近上限（如 80%+）
- 完成重大功能后
- 阶段性里程碑
- 准备结束当前 session

**不需要频繁生成**:
- 每次小改动
- BUG 修复

---

### roadmap.md
**更新时机**: 偶尔  
**更新场景**:
- 阶段规划调整
- 里程碑变化
- 优先级调整

---

### constraints.md
**更新时机**: 很少  
**更新场景**:
- 技术约束变化（如升级 Java 版本）
- 增加新的规范
- 废弃旧的限制

---

## 🛠 脚本使用详解

### ai-new.sh

**用途**: 加载上下文启动新 AI session

**使用方法**:
```bash
./scripts/ai-new.sh
```

**工作流程**:
1. 检查 `.ai` 目录和必要文件
2. 读取所有上下文文件
3. 使用模板替换占位符
4. 生成完整 Prompt
5. 保存到临时文件（调试用）
6. 检测 AI CLI 工具
7. 自动启动 AI session 或显示手动复制提示

**支持的 AI CLI**:
- `opencode`
- `cursor`
- `aider`
- `gpt`

**如果没有 CLI**:
- 脚本会将 Prompt 保存到 `/tmp/flyway-digital-ai-prompt.md`
- 提示手动复制粘贴

---

### ai-summarize.sh

**用途**: 生成当前阶段压缩总结

**使用方法**:
```bash
./scripts/ai-summarize.sh
```

**工作流程**:
1. 检查 `.ai` 目录
2. 构建总结 Prompt
3. 调用 AI CLI 生成总结
4. 自动写入 `.ai/summary.md`
5. 显示统计信息和下一步操作

**生成的总结包含**:
- 项目现状一览
- 最近完成的工作
- 核心架构
- 关键设计决策
- 已知问题
- 技术约束
- 部署流程
- 关键文件位置
- 恢复要点

**下一步操作**:
```bash
# 1. 检查总结
cat .ai/summary.md

# 2. 提交到 Git
git add .ai/summary.md
git commit -m "chore: update AI session summary"
git push

# 3. 启动新 session
./scripts/ai-new.sh
```

---

## 💡 最佳实践

### 1. 保持文档更新

**好习惯**:
- ✅ 架构决策立即记录
- ✅ 任务状态及时更新
- ✅ Token 超限前生成总结

**坏习惯**:
- ❌ 决策不记录，事后忘记原因
- ❌ Token 超限后才想起来总结
- ❌ 文档长期不更新

---

### 2. 合理使用 Git

**提交策略**:
```bash
# 架构决策单独提交
git add .ai/decisions.md
git commit -m "docs: record architecture decision - XXX"

# 任务更新单独提交
git add .ai/current-task.md
git commit -m "chore: update current task - XXX"

# 总结更新单独提交
git add .ai/summary.md
git commit -m "chore: update AI session summary"
```

---

### 3. Token 管理

**监控 Token 使用**:
- 使用 AI 工具的 token 计数器
- 估算规则: 单词数 × 2 ≈ Token 数

**何时生成总结**:
- Token 使用量 > 80%
- 完成重大功能
- 准备结束 session

**不要等到 100%**:
- 留出生成总结的 token 空间

---

### 4. 多人协作

**协作规范**:
- 定期同步（每天至少一次）
- 避免同时编辑同一文件
- 冲突时沟通解决

**分工建议**:
- 一人负责一个任务模块
- 架构决策集体讨论后记录
- 总结生成由当前开发者负责

---

## 🐛 常见问题

### Q1: 脚本执行失败？

**检查权限**:
```bash
chmod +x scripts/ai-new.sh
chmod +x scripts/ai-summarize.sh
```

**检查文件完整性**:
```bash
ls .ai/
# 应该看到所有必要文件
```

---

### Q2: 没有 AI CLI 工具？

**方案 1: 手动复制**
```bash
./scripts/ai-new.sh
# 脚本会生成 Prompt 并保存到临时文件
# 手动复制粘贴到你的 AI 工具

# Prompt 位置
cat /tmp/flyway-digital-ai-prompt.md
```

**方案 2: 安装 AI CLI**
- OpenCode: https://opencode.ai
- Aider: https://aider.chat
- Cursor: https://cursor.sh

---

### Q3: 总结生成失败？

**检查 AI CLI**:
```bash
which opencode  # 或其他 CLI
```

**手动生成**:
1. 复制 Prompt（脚本会显示）
2. 粘贴到 AI 工具
3. 获取回复
4. 保存到 `.ai/summary.md`

---

### Q4: 如何扩展为 RAG？

**未来可扩展**:
```bash
# 1. 将所有 .ai/*.md 向量化
# 2. 存储到向量数据库（如 Chroma, Pinecone）
# 3. 使用 LangChain / LlamaIndex 检索
# 4. 自动加载相关上下文片段
```

**目前阶段**: 手动管理即可

---

## 📚 参考资料

### 相关概念

- **ADR (Architecture Decision Record)**: 架构决策记录
- **RAG (Retrieval-Augmented Generation)**: 检索增强生成
- **Context Window**: AI 上下文窗口（Token 限制）

### 推荐阅读

- [ADR 最佳实践](https://adr.github.io/)
- [Token 管理策略](https://platform.openai.com/docs/guides/prompt-engineering)
- [AI 协作模式](https://cursor.sh/docs)

---

## 🎉 总结

通过 `.ai` 框架，你可以：

1. ✅ **永不丢失上下文** - Token 超限也能恢复
2. ✅ **团队协作无缝** - 共享统一的项目知识
3. ✅ **长期项目演进** - 支持数月乃至数年的迭代
4. ✅ **知识沉淀积累** - 架构决策和约束持久化

**开始使用吧！**

```bash
# 第一步
./scripts/ai-new.sh

# 开始你的 AI 协作之旅
```

---

**最后更新**: 2025-02-13
**版本**: 1.0.0  
**维护者**: Flyway Digital Team
