# 时迹（xiaomiAPP）项目规范

## 项目简介

「时迹」—— 记录手机各应用使用时间的安卓 APP（纯单机），目标上架小米应用市场（个人开发者）。

- **包名**：`com.shiji.trace`
- **技术栈**：安卓原生 Kotlin + Jetpack Compose + Room + WorkManager
- **界面风格**：小米澎湃系统风格（主色 #FF6900、16dp 圆角卡片、跟随系统深色模式）

## 核心功能

1. 使用时间线：各应用使用开始/结束时间、单次时长
2. 并行应用检测：同一时间段使用的应用（分屏场景）
3. 今日概览：今日各应用使用时长、总时长
4. 统计图表：日/周/月统计、应用排行
5. 本地历史回溯

## 红线（必须遵守）

- **纯单机**：不申请 INTERNET 权限、不接任何联网 SDK、代码中不写任何网络请求
  （免 ICP/APP 备案的硬性条件；商店审核会验证断网可用）
- 上架后包名、应用名「时迹」、软著名称三者一致，不可更改

## 沟通与代码规范

- **所有交流用中文**：提问、说明、总结、报告、决策选项全部中文
- **代码注释用中文**，密度 ≥30%（每 10 行代码约 3 行注释），注释要说明"为什么"
- 提交说明用中文，20~50 字，一次提交只做一件事
- 关键业务逻辑（同步、并行检测、权限）必须注释清楚
- 报告输出格式：问题定位到文件+行号，风险分 🔴 高 / 🟡 中 / 🟢 低

## 提交流程（门禁）

- 提交前必须先跑 `tester`（单元测试）+ `quality-engineer`（质量检查），通过后由 `gitcommit-agent` 执行提交
- 合格证写入 `.claude/gate/`（已被 .gitignore 忽略）
- 绝不跳过门禁直接提交

## 常用命令

- 运行/安装到真机：`/run-app`
- 单元测试：`/test-unit`
- 提交代码：`gitcommit-agent`（或直接说"提交代码"）
- 安全审计：`/security-audit`（或说"安全检查"）
- 注释检查：`/comments-check`（或说"检查注释"）

## 工程结构

- 单模块 `:app`，源码在 `app/src/main/java/com/shiji/trace/`
- 依赖版本统一管理在 `gradle/libs.versions.toml`，新增依赖必须走版本目录
- 数据层：Room 五张表（usage_event / app_session / daily_snapshot / app_info / parallel_group）
- 同步核心：`data/sync/UsageSyncEngine.kt`；并行检测核心：`domain/ParallelDetector.kt`
