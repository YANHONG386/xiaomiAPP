---
name: unit-test
description: 为时迹项目创建并执行单元测试（JVM 测试），输出测试报告。当用户要求"写测试"、"跑测试"、"测试一下"、"创建单元测试"或"生成测试"时使用。
---

# 单元测试技能 — 创建 + 执行 + 报告

## 目的
为「时迹」项目（安卓原生 Kotlin）创建单元测试，执行测试并生成测试报告，最后向用户输出清晰的测试报告。

## 测试框架
- **JUnit 4 + kotlin.test**（配置在 `app/build.gradle.kts`，如用 JUnit5 以实际配置为准）
- 纯 JVM 测试，不需要安卓设备或模拟器
- 测试报告：`app/build/reports/tests/testDebugUnitTest/index.html`

## 工作流程

### 第一步：分析代码
先读取要测试的文件，理解其逻辑：
- `app/src/main/java/com/shiji/trace/domain/SessionBuilder.kt` — 事件流 → 会话区间（纯函数，重点）
- `app/src/main/java/com/shiji/trace/domain/ParallelDetector.kt` — 并行检测（纯函数，重点）
- `app/src/main/java/com/shiji/trace/data/sync/UsageSyncEngine.kt` — 同步引擎（游标推进、去重、时钟回拨）
- 其他纯逻辑文件（无安卓依赖）

### 第二步：编写测试
- 测试文件放在 `app/src/test/java/com/shiji/trace/` 对应包下，命名 `XxxTest.kt`
- 优先覆盖纯逻辑（不依赖安卓 API 的部分）
- 涉及安卓依赖的逻辑用接口抽象 + 测试替身（fake）替代，保证纯 JVM 可跑

**核心测试场景：**
1. SessionBuilder：正常开/关会话、连续事件去重、锁屏关闭全部、<1 秒丢弃、乱序与重复
2. ParallelDetector：纯切换、延迟 PAUSED、真分屏、分屏退出、崩溃截断、画中画低置信、三应用并行、系统包排除
3. UsageSyncEngine：游标推进、幂等去重、时钟回拨重置

### 第三步：执行测试
```bash
cd "d:/桌面/xiaomiAPP" && ./gradlew test --console=plain
```
（Windows 无 `./gradlew` 时用 `gradlew.bat test`）

### 第四步：输出测试报告
测试完成后，向用户输出以下格式的报告：

```
## 📋 测试报告

### 执行结果
- ✅ 通过：X 个
- ❌ 失败：X 个
- ⏱️ 用时：X 秒

### 测试内容
1. 会话构建：事件流 → 会话区间（X 个用例）
2. 并行检测：切换/分屏/画中画判定（X 个用例）
3. 同步引擎：游标/去重/时钟回拨（X 个用例）

### 结论
✅ 全部通过 / ❌ 存在失败项（列出失败原因和修复建议）
```

## 注意事项
- 纯 JVM 测试环境，**不能用安卓 API**（Context、Handler 等）；需要时用接口抽象 + fake
- 修改代码后要重新运行测试验证
- 如遇失败：先看错误信息定位（测试报告 HTML 或控制台输出），修复后再跑，直到全绿
- 新增功能时必须同步补充对应测试用例（提交门禁的 tester 会跑全量测试）
