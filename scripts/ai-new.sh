#!/bin/bash

###############################################################################
# AI Session 启动脚本
# 
# 功能: 读取 .ai 目录下的所有上下文文件，拼接为完整 prompt，启动 AI session
# 用途: 新 session 或 token 超限后恢复上下文
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

# 检查 .ai 目录是否存在
if [ ! -d "${AI_DIR}" ]; then
    echo -e "${RED}错误: .ai 目录不存在！${NC}"
    echo -e "${YELLOW}请先初始化 .ai 目录。${NC}"
    exit 1
fi

# 检查必要文件
required_files=(
    "context.md"
    "decisions.md"
    "summary.md"
    "current-task.md"
    "constraints.md"
    "prompt-template.md"
)

for file in "${required_files[@]}"; do
    if [ ! -f "${AI_DIR}/${file}" ]; then
        echo -e "${RED}错误: ${file} 不存在！${NC}"
        exit 1
    fi
done

echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}  Flyway Digital - AI Session 启动器${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""

# 读取各个文件内容
echo -e "${GREEN}[1/6]${NC} 读取项目背景..."
CONTEXT=$(cat "${AI_DIR}/context.md")

echo -e "${GREEN}[2/6]${NC} 读取架构决策..."
DECISIONS=$(cat "${AI_DIR}/decisions.md")

echo -e "${GREEN}[3/6]${NC} 读取当前阶段总结..."
SUMMARY=$(cat "${AI_DIR}/summary.md")

echo -e "${GREEN}[4/6]${NC} 读取当前任务..."
CURRENT_TASK=$(cat "${AI_DIR}/current-task.md")

echo -e "${GREEN}[5/6]${NC} 读取技术约束..."
CONSTRAINTS=$(cat "${AI_DIR}/constraints.md")

echo -e "${GREEN}[6/6]${NC} 读取 Prompt 模板..."
TEMPLATE=$(cat "${AI_DIR}/prompt-template.md")

# 替换模板中的占位符
echo ""
echo -e "${BLUE}正在生成完整 Prompt...${NC}"

FINAL_PROMPT="${TEMPLATE}"
FINAL_PROMPT="${FINAL_PROMPT//\{\{context\}\}/${CONTEXT}}"
FINAL_PROMPT="${FINAL_PROMPT//\{\{decisions\}\}/${DECISIONS}}"
FINAL_PROMPT="${FINAL_PROMPT//\{\{summary\}\}/${SUMMARY}}"
FINAL_PROMPT="${FINAL_PROMPT//\{\{current_task\}\}/${CURRENT_TASK}}"
FINAL_PROMPT="${FINAL_PROMPT//\{\{constraints\}\}/${CONSTRAINTS}}"

# 保存到临时文件（可选，用于调试）
TEMP_PROMPT_FILE="/tmp/flyway-digital-ai-prompt.md"
echo "${FINAL_PROMPT}" > "${TEMP_PROMPT_FILE}"

echo -e "${GREEN}✓ Prompt 已生成${NC}"
echo -e "${YELLOW}文件位置: ${TEMP_PROMPT_FILE}${NC}"
echo ""

# 统计信息
CHAR_COUNT=$(echo "${FINAL_PROMPT}" | wc -c)
WORD_COUNT=$(echo "${FINAL_PROMPT}" | wc -w)

echo -e "${BLUE}Prompt 统计信息:${NC}"
echo -e "  字符数: ${CHAR_COUNT}"
echo -e "  单词数: ${WORD_COUNT}"
echo -e "  预估 Token: ~$((WORD_COUNT * 2))"
echo ""

# 检测可用的 AI CLI 工具
AI_CLI=""

if command -v opencode &> /dev/null; then
    AI_CLI="opencode"
    echo -e "${GREEN}✓ 检测到 OpenCode CLI${NC}"
elif command -v cursor &> /dev/null; then
    AI_CLI="cursor"
    echo -e "${GREEN}✓ 检测到 Cursor CLI${NC}"
elif command -v aider &> /dev/null; then
    AI_CLI="aider"
    echo -e "${GREEN}✓ 检测到 Aider CLI${NC}"
elif command -v gpt &> /dev/null; then
    AI_CLI="gpt"
    echo -e "${GREEN}✓ 检测到 GPT CLI${NC}"
else
    echo -e "${YELLOW}⚠ 未检测到 AI CLI 工具${NC}"
    echo -e "${YELLOW}支持的工具: opencode, cursor, aider, gpt${NC}"
    echo ""
    echo -e "${BLUE}Prompt 已保存到:${NC}"
    echo -e "${GREEN}${TEMP_PROMPT_FILE}${NC}"
    echo ""
    echo -e "${YELLOW}请手动复制粘贴到你的 AI 工具中。${NC}"
    exit 0
fi

echo ""
echo -e "${BLUE}================================================${NC}"
echo -e "${BLUE}  启动 AI Session...${NC}"
echo -e "${BLUE}================================================${NC}"
echo ""

# 根据不同的 CLI 工具调用
case "${AI_CLI}" in
    opencode)
        # OpenCode 是交互式工具，提示用户手动使用
        echo -e "${YELLOW}⚠ OpenCode 是交互式工具${NC}"
        echo -e "${YELLOW}请手动使用以下命令:${NC}"
        echo -e "  opencode --prompt \"${TEMP_PROMPT_FILE}\""
        echo ""
        echo -e "${BLUE}Prompt 已保存到:${NC}"
        echo -e "${GREEN}${TEMP_PROMPT_FILE}${NC}"
        echo ""
        echo -e "${YELLOW}或者直接运行:${NC}"
        echo -e "  opencode"
        ;;
    cursor)
        # Cursor 示例（可能需要调整）
        cursor --prompt "${FINAL_PROMPT}"
        ;;
    aider)
        # Aider 示例
        aider --message "${FINAL_PROMPT}"
        ;;
    gpt)
        # GPT CLI 示例
        gpt "${FINAL_PROMPT}"
        ;;
    *)
        echo -e "${RED}未知的 AI CLI: ${AI_CLI}${NC}"
        exit 1
        ;;
esac

echo ""
echo -e "${GREEN}✓ AI Session 已启动${NC}"
echo ""
