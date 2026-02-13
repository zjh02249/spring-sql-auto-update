#!/bin/bash

###############################################################################
# AI Session 总结生成脚本
# 
# 功能: 调用 AI 生成当前项目压缩总结，写入 .ai/summary.md
# 用途: Token 即将超限时，生成压缩总结供下次 session 恢复
# 作者: Flyway Digital Team
# 日期: 2025-02-12
###############################################################################

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目根目录
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
AI_DIR="${PROJECT_ROOT}/.ai"
SUMMARY_FILE="${AI_DIR}/summary.md"

# 检查 .ai 目录是否存在
if [ ! -d "${AI_DIR}" ]; then
    echo -e "${RED}错误: .ai 目录不存在！${NC}"
    exit 1
fi

echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}  Flyway Digital - AI Session 总结生成器${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""

# 生成总结 Prompt
SUMMARIZE_PROMPT="你是一个专业的技术文档总结专家。

**任务**: 为 Flyway Digital 项目生成一份压缩总结，用于下一次 AI session 快速恢复上下文。

**输出要求**:

1. **结构化输出** (Markdown 格式)
2. **包含以下部分**:
   - 📊 项目现状一览（版本、发布状态、Git 状态）
   - ✅ 最近完成的工作（最新版本的主要变更）
   - 🏗 核心架构（技术栈、模块结构、关键实现）
   - 🎯 关键设计决策（核心决策列表）
   - 🐛 已知问题（当前存在的问题）
   - 📋 技术约束（必须遵守的约束）
   - 🚀 部署流程（标准发布命令）
   - 📝 关键文件位置（核心源码、测试、文档）
   - 🔄 下次 Session 恢复要点（如何快速继续开发）
   - 📚 重要文档链接
   - ✨ 亮点功能（核心价值）

3. **压缩原则**:
   - 去除冗余信息
   - 保留关键上下文
   - 突出最新变化
   - 适合快速扫描

4. **格式要求**:
   - 使用 Emoji 增强可读性
   - 使用表格整理数据
   - 使用代码块展示命令
   - 使用清单标记状态

**当前项目状态**:

- **项目名称**: Flyway Digital
- **当前版本**: 1.2.4
- **最近工作**: 修复 SqlExecutor 语法错误并成功部署

**请基于以上要求，生成一份完整的压缩总结。**"

echo -e "${BLUE}生成总结 Prompt...${NC}"
echo ""

# 检测可用的 AI CLI 工具
AI_CLI=""
AI_OUTPUT=""

if command -v opencode &> /dev/null; then
    AI_CLI="opencode"
    echo -e "${GREEN}✓ 检测到 OpenCode CLI${NC}"
    echo -e "${YELLOW}⚠ OpenCode 是交互式工具${NC}"
    echo -e "${YELLOW}请手动使用以下命令:${NC}"
    TEMP_PROMPT_FILE="/tmp/flyway-digital-summarize-prompt.md"
    echo "${SUMMARIZE_PROMPT}" > "${TEMP_PROMPT_FILE}"
    echo -e "  opencode --prompt \"${TEMP_PROMPT_FILE}\""
    echo ""
    echo -e "${BLUE}Prompt 已保存到:${NC}"
    echo -e "${GREEN}${TEMP_PROMPT_FILE}${NC}"
    echo ""
    echo -e "${YELLOW}然后将输出保存到:${NC}"
    echo -e "  ${SUMMARY_FILE}"
    exit 0
    
elif command -v cursor &> /dev/null; then
    AI_CLI="cursor"
    echo -e "${GREEN}✓ 检测到 Cursor CLI${NC}"
    echo -e "${YELLOW}⚠ Cursor 可能不支持命令行输出，请手动生成总结${NC}"
    
elif command -v aider &> /dev/null; then
    AI_CLI="aider"
    echo -e "${GREEN}✓ 检测到 Aider CLI${NC}"
    
    # 调用 Aider
    echo -e "${BLUE}正在调用 Aider 生成总结...${NC}"
    AI_OUTPUT=$(aider --message "${SUMMARIZE_PROMPT}" --yes)
    
elif command -v gpt &> /dev/null; then
    AI_CLI="gpt"
    echo -e "${GREEN}✓ 检测到 GPT CLI${NC}"
    
    # 调用 GPT CLI
    echo -e "${BLUE}正在调用 GPT 生成总结...${NC}"
    AI_OUTPUT=$(gpt "${SUMMARIZE_PROMPT}")
    
else
    echo -e "${YELLOW}⚠ 未检测到 AI CLI 工具${NC}"
    echo ""
    echo -e "${BLUE}请手动完成以下步骤:${NC}"
    echo -e "${YELLOW}1. 将以下 Prompt 复制到你的 AI 工具中${NC}"
    echo -e "${YELLOW}2. 获得 AI 的回复${NC}"
    echo -e "${YELLOW}3. 将回复保存到: ${SUMMARY_FILE}${NC}"
    echo ""
    echo -e "${BLUE}======== Prompt 开始 ========${NC}"
    echo "${SUMMARIZE_PROMPT}"
    echo -e "${BLUE}======== Prompt 结束 ========${NC}"
    echo ""
    exit 0
fi

# 如果成功获取了 AI 输出
if [ -n "${AI_OUTPUT}" ]; then
    echo ""
    echo -e "${GREEN}✓ AI 总结生成成功${NC}"
    echo ""
    
    # 写入文件
    echo -e "${BLUE}写入总结文件...${NC}"
    echo "${AI_OUTPUT}" > "${SUMMARY_FILE}"
    
    echo -e "${GREEN}✓ 总结已保存到: ${SUMMARY_FILE}${NC}"
    echo ""
    
    # 显示统计信息
    LINE_COUNT=$(wc -l < "${SUMMARY_FILE}")
    CHAR_COUNT=$(wc -c < "${SUMMARY_FILE}")
    
    echo -e "${BLUE}总结统计:${NC}"
    echo -e "  行数: ${LINE_COUNT}"
    echo -e "  字符数: ${CHAR_COUNT}"
    echo ""
    
    # 提示下一步操作
    echo -e "${BLUE}================================================${NC}"
    echo -e "${BLUE}  下一步操作${NC}"
    echo -e "${BLUE}================================================${NC}"
    echo ""
    echo -e "${YELLOW}1. 检查生成的总结是否完整:${NC}"
    echo -e "   cat ${SUMMARY_FILE}"
    echo ""
    echo -e "${YELLOW}2. 提交到 Git:${NC}"
    echo -e "   git add .ai/summary.md"
    echo -e "   git commit -m \"chore: update AI session summary\""
    echo -e "   git push"
    echo ""
    echo -e "${YELLOW}3. 启动新 Session:${NC}"
    echo -e "   ./scripts/ai-new.sh"
    echo ""
    
else
    echo -e "${RED}✗ AI 未返回输出${NC}"
    echo -e "${YELLOW}请手动生成总结并保存到: ${SUMMARY_FILE}${NC}"
    exit 1
fi

echo -e "${GREEN}✓ 总结生成完成${NC}"
echo ""
