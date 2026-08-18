# test-unit — 单元测试：执行 + 报告

## 说明
运行时迹项目的单元测试（纯 JVM 测试，无需设备），并输出测试报告。

## 触发方式
- 输入 `/test-unit`
- 用户说"测试"、"跑测试"、"单元测试"、"测试一下"

## 执行步骤

### 第一步：运行全部单元测试
```bash
cd "d:/桌面/xiaomiAPP" && ./gradlew test --console=plain
```
（Windows 无 `./gradlew` 时用 `gradlew.bat test`）

### 第二步：查看结果
- 控制台会输出测试统计（通过/失败数量）
- 详细报告：`app/build/reports/tests/testDebugUnitTest/index.html`

### 第三步：输出测试报告
测试完成后，向用户输出以下格式：

```
## 📋 测试报告

### 执行结果
- ✅ 通过：X 个
- ❌ 失败：X 个
- ⏱️ 用时：X 秒

### 结论
✅ 全部通过 / ❌ 存在失败项（列出失败原因）
```

### 第四步（有失败时）：定位并修复
- 先看控制台失败的用例名和错误信息
- 打开测试报告 HTML 看详细堆栈
- 修复后重新运行，直到全绿

## 注意事项
- 测试在 `app/src/test/`（纯 JVM），不需要安卓设备
- 修改代码后重新运行测试验证
- 核心测试场景：会话构建（SessionBuilder）、并行检测（ParallelDetector）、同步引擎（UsageSyncEngine）
