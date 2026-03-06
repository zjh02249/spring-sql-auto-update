# 性能评估与性能测试

## 目标

本文档用于记录第二阶段的性能评估方案、执行方式和第一版基线结果。

当前采用“双层方案”：

1. **JUnit 性能烟测**：默认随 `mvn test` 执行，只记录结果，不做严格性能门禁。
2. **本地基准入口**：用于手动采集更大规模数据，不进入默认 `test/verify` 路径。

## 测试分层

### 1. CI 可跑性能烟测

性能烟测类：

- `com.cbkj.infrastructure.performance.PerformanceSmokeTest`

覆盖场景：

- 10 个 migration 的小规模全链路迁移
- 100 / 500 个 migration 的文件系统扫描
- 100 个 migration 的普通 JAR 扫描
- 100 / 500 个 migration 的 H2 全链路迁移
- classpath + 文件系统混合 location 扫描

特点：

- 默认纳入 `mvn test`
- 不对毫秒数设失败阈值
- 输出统一格式性能日志，便于人工比对

### 2. 本地基准入口

本地基准类：

- `com.cbkj.infrastructure.performance.PerformanceBenchmarkMain`

运行命令：

```bash
mvn -pl flyway-digital-core -Pperf-benchmark test-compile exec:java \
  -Dexec.mainClass=com.cbkj.infrastructure.performance.PerformanceBenchmarkMain \
  -Dperf.sizes=100,500,1000
```

特点：

- 使用测试类路径运行，不污染默认构建
- 可通过 `perf.sizes` 控制规模
- 重点观察不同规模下的增长趋势

## 测试环境

第一版基线采集环境：

- 操作系统：Windows（本地开发环境）
- JDK：Java 8 兼容运行环境
- 数据库：H2 内存数据库（`MODE=MySQL`）
- 文件数据集：运行时生成的临时 SQL 文件与临时 JAR

说明：

- 当前基线主要用于横向比较同机环境下的版本变化。
- 不同机器、不同磁盘和不同 JVM 负载下，绝对耗时会有明显波动。

## 执行命令

### 默认烟测

```bash
mvn -pl flyway-digital-core test
```

### 默认质量校验

```bash
mvn -pl flyway-digital-core verify
```

### 本地基准

```bash
mvn -pl flyway-digital-core -Pperf-benchmark test-compile exec:java \
  -Dexec.mainClass=com.cbkj.infrastructure.performance.PerformanceBenchmarkMain \
  -Dperf.sizes=100,500,1000
```

## 第一版基线结果

### JUnit 性能烟测

| 场景 | migration 数 | 扫描耗时(ms) | 首次执行(ms) | 二次启动(ms) | 备注 |
|------|--------------|--------------|--------------|--------------|------|
| small-chain | 10 | 11 | 18 | 10 | 小规模全链路 |
| filesystem-scan-100 | 100 | 60 | - | - | 文件系统扫描 |
| filesystem-scan-500 | 500 | 210 | - | - | 文件系统扫描 |
| jar-scan-100 | 100 | 9 | - | - | 普通 JAR 扫描 |
| migration-chain-100 | 100 | 72 | 318 | 94 | H2 全链路 |
| migration-chain-500 | 500 | 249 | 786 | 300 | H2 全链路 |
| mixed-scan | 22 | 17 | - | - | classpath + 文件系统 |

### 本地基准

| 场景 | migration 数 | 结果 |
|------|--------------|------|
| filesystem benchmark | 100 | `49ms`，平均 `0.49ms/文件` |
| filesystem benchmark | 500 | `159ms`，平均 `0.32ms/文件` |
| filesystem benchmark | 1000 | `299ms`，平均 `0.30ms/文件` |
| jar benchmark | 100 | `2ms`，平均 `0.02ms/文件` |
| jar benchmark | 500 | `7ms`，平均 `0.01ms/文件` |
| jar benchmark | 1000 | `12ms`，平均 `0.01ms/文件` |
| migration benchmark | 100 | `scan=41ms`，`firstRun=362ms`，`secondRun=44ms` |
| migration benchmark | 500 | `scan=162ms`，`firstRun=405ms`，`secondRun=176ms` |
| migration benchmark | 1000 | `scan=312ms`，`firstRun=491ms`，`secondRun=314ms` |

## 当前观察重点

第一版测试体系要回答以下问题：

1. 当前瓶颈更偏扫描还是执行。
2. 文件系统与 JAR 扫描差异是否明显。
3. 大规模 migration 下增长是否接近线性。
4. 重复启动场景是否主要消耗在重新扫描。

## 已知偏差来源

- 文件系统缓存会影响第二次执行速度。
- H2 与真实生产数据库的执行成本并不等价。
- 当前没有预热轮次和 JVM 隔离，不适合作为微基准绝对值依据。
- 扫描相关类存在源码编码异常痕迹，后续若重构扫描器，需重新采集基线。

## 初步结论模板

结合当前基线，可先得到以下结论：

- 当前主瓶颈更偏向**首次迁移执行链路**，而不是 JAR 扫描。
- 文件系统扫描与 migration 全链路在规模提升时整体呈**近似线性增长**，没有出现异常级别的放大。
- 普通 JAR 扫描当前开销明显低于文件系统扫描，短期内不需要优先优化。
- 二次启动场景仍然存在可见扫描成本，后续可以重点评估：
  - 是否缓存扫描结果
  - 是否减少重复排序与重复解析
  - 是否减少大量 migration 下的日志输出开销
- 下一轮优化优先级建议：
  1. 评估 `SqlScanner` 重复扫描与日志输出成本
  2. 评估 `FlywayDigital` 重复启动时的扫描与过滤成本
  3. 再决定是否需要做对象创建与缓存优化
