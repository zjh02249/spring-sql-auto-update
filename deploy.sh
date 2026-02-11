#!/bin/bash
# Flyway Digital 1.2.0 部署脚本
# 用于构建、测试和部署新版本

set -e

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 版本信息
VERSION="1.2.0"
GROUP_ID="com.cbkj.infrastructure"

# 打印带颜色的消息
print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# 检查 Maven 环境
check_maven() {
    print_info "检查 Maven 环境..."
    if ! command -v mvn &> /dev/null; then
        print_error "Maven 未安装或未添加到 PATH"
        exit 1
    fi
    
    MVN_VERSION=$(mvn -version | head -1)
    print_success "Maven 版本: $MVN_VERSION"
}

# 检查 Java 环境
check_java() {
    print_info "检查 Java 环境..."
    if ! command -v java &> /dev/null; then
        print_error "Java 未安装或未添加到 PATH"
        exit 1
    fi
    
    JAVA_VERSION=$(java -version 2>&1 | head -1)
    print_success "Java 版本: $JAVA_VERSION"
}

# 清理项目
clean_project() {
    print_info "清理项目..."
    mvn clean -q
    print_success "项目清理完成"
}

# 编译项目
compile_project() {
    print_info "编译项目..."
    mvn compile -DskipTests -q
    print_success "项目编译完成"
}

# 运行测试
run_tests() {
    print_info "运行测试..."
    if mvn test -q; then
        print_success "所有测试通过"
    else
        print_error "测试失败，请检查测试日志"
        exit 1
    fi
}

# 打包项目
package_project() {
    print_info "打包项目..."
    mvn package -DskipTests -q
    print_success "项目打包完成"
}

# 部署到 Maven 仓库
deploy_to_maven() {
    print_info "部署到 Maven 仓库..."
    
    # 检查是否有部署配置
    if grep -q "<distributionManagement>" pom.xml 2>/dev/null; then
        print_info "发现部署配置，开始部署..."
        mvn deploy -DskipTests -q
        print_success "部署完成"
    else
        print_warning "未找到部署配置，跳过部署步骤"
        print_info "如需部署，请在 pom.xml 中添加 distributionManagement 配置"
    fi
}

# 生成分发文件
generate_distribution() {
    print_info "生成分发文件..."
    
    # 创建分发目录
    DIST_DIR="target/distribution"
    mkdir -p "$DIST_DIR"
    
    # 复制 JAR 文件
    find . -name "*.jar" -path "*/target/*" ! -name "*sources*" ! -name "*javadoc*" -exec cp {} "$DIST_DIR/" \;
    
    # 创建版本信息文件
    cat > "$DIST_DIR/VERSION.txt" << EOF
Flyway Digital Version: $VERSION
Build Date: $(date '+%Y-%m-%d %H:%M:%S')
Git Commit: $(git rev-parse --short HEAD 2>/dev/null || echo 'N/A')
Maven Version: $(mvn -version | head -1)
Java Version: $(java -version 2>&1 | head -1)
EOF
    
    print_success "分发文件已生成: $DIST_DIR/"
    ls -lh "$DIST_DIR/"
}

# 打印使用说明
print_usage() {
    cat << EOF

${GREEN}Flyway Digital $VERSION 部署脚本${NC}

${BLUE}用法:${NC}
    ./deploy.sh [选项]

${BLUE}选项:${NC}
    ${GREEN}all${NC}          执行完整的构建、测试和部署流程
    ${GREEN}build${NC}        仅编译和打包项目（不运行测试）
    ${GREEN}test${NC}         编译并运行所有测试
    ${GREEN}deploy${NC}       构建、测试并部署到 Maven 仓库
    ${GREEN}clean${NC}        清理项目（删除 target 目录）
    ${GREEN}dist${NC}         生成分发文件

${BLUE}示例:${NC}
    # 完整的构建和部署
    ./deploy.sh all
    
    # 仅构建（不运行测试）
    ./deploy.sh build
    
    # 运行测试
    ./deploy.sh test
    
    # 生成用于发布的分发文件
    ./deploy.sh dist

${YELLOW}注意:${NC}
    - 确保已安装 Maven 3.6+ 和 Java 8+
    - 部署到远程 Maven 仓库需要在 pom.xml 中配置 distributionManagement
    - 运行测试需要可用的数据库连接（默认使用 H2 内存数据库）

EOF
}

# 主函数
main() {
    # 显示使用说明
    if [ $# -eq 0 ] || [ "$1" == "help" ] || [ "$1" == "--help" ] || [ "$1" == "-h" ]; then
        print_usage
        exit 0
    fi
    
    local command=$1
    
    # 打印 banner
    echo -e "${BLUE}╔══════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║${NC}          ${GREEN}Flyway Digital v${VERSION} 部署脚本${NC}               ${BLUE}║${NC}"
    echo -e "${BLUE}╚══════════════════════════════════════════════════════════╝${NC}"
    echo ""
    
    # 执行命令
    case $command in
        "all")
            check_java
            check_maven
            clean_project
            compile_project
            run_tests
            package_project
            deploy_to_maven
            generate_distribution
            print_success "✅ 完整的构建和部署流程已完成！"
            ;;
        "build")
            check_java
            check_maven
            clean_project
            compile_project
            package_project
            print_success "✅ 构建完成（未运行测试）"
            ;;
        "test")
            check_java
            check_maven
            clean_project
            compile_project
            run_tests
            print_success "✅ 测试完成"
            ;;
        "deploy")
            check_java
            check_maven
            clean_project
            compile_project
            run_tests
            package_project
            deploy_to_maven
            print_success "✅ 部署完成"
            ;;
        "clean")
            clean_project
            print_success "✅ 清理完成"
            ;;
        "dist")
            check_java
            check_maven
            package_project
            generate_distribution
            print_success "✅ 分发文件已生成"
            ;;
        *)
            print_error "未知命令: $command"
            print_usage
            exit 1
            ;;
    esac
    
    echo ""
    echo -e "${GREEN}✨ 完成！${NC}"
}

# 运行主函数
main "$@"
