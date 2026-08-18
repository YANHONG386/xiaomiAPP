---
name: security-audit
description: 代码安全审计——检查敏感信息泄露、注入风险、配置文件明文密钥、联网红线、其他安全隐患。当用户要求"安全检查"、"安全审计"、"审计"、"检查安全隐患"或"检查敏感信息"时使用。
---

# 安全审计技能 — 发现代码中的安全隐患

## 目的
对「时迹」项目代码进行安全审计，从五个维度排查隐患：
1. **联网红线** — 是否出现 INTERNET 权限或任何联网代码（本项目生命线）
2. **敏感信息泄露** — 代码中是否硬编码了密码、密钥、Token
3. **注入漏洞** — SQL 拼接、路径穿越等
4. **配置文件明文泄露** — 配置中是否有明文敏感信息
5. **其他安全隐患** — 导出组件、权限滥用等

## 检查维度详解

### 维度 1：联网红线（必查）
检查以下内容：
- `app/src/main/AndroidManifest.xml` 是否声明 `android.permission.INTERNET`（声明即 🔴 高危）
- 代码中是否有网络请求类：`HttpURLConnection`、`OkHttp`、`Retrofit`、`Socket`、`URL(` 等
- `build.gradle.kts` 依赖树是否有网络库（`./gradlew :app:dependencies | grep -iE "okhttp|retrofit|ktor|volley"`）
- WebView 是否加载远程地址
- 是否有自动更新、崩溃上报（如 Bugly、Firebase）等联网组件

### 维度 2：敏感信息泄露
- 密码：`password`, `passwd`, `pwd` 字段赋值
- 密钥/Token：`token`, `secret`, `api_key`, `apikey`, `auth`
- 密钥文件：`.env`, `.pem`, `.key`, `credentials`, `id_rsa`, `*.jks`, `*.keystore` 被提交
- 常见密钥格式：`sk-`、`AKIA`、`-----BEGIN RSA PRIVATE KEY-----`
- 硬编码的账号密码组合（如 `root/admin/123456`）

### 维度 3：注入漏洞
- SQL 注入：Room 中字符串拼接 SQL（`query("SELECT ... WHERE id = '" + 变量 + "'")`）
- 路径遍历：用户输入直接拼接到文件路径
- 动态执行：`eval`、`Reflection` 执行用户输入

### 维度 4：配置文件明文泄露
- `.gitignore` 是否忽略 `.jks`、`.keystore`、`local.properties`（local.properties 含 SDK 路径不算敏感，但一般不入库）
- 签名配置 `signingConfigs` 中密码是否硬编码（应放环境变量或 `keystore.properties` 且不入库）
- git 历史中是否曾提交过密钥（`git log` 搜索）

### 维度 5：其他安全隐患
- `AndroidManifest.xml` 中 `exported="true"` 的 Activity/Service/Receiver 是否有权限保护
- 是否滥用危险权限（`REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` 等商店敏感项）
- Room 数据库是否存储敏感个人信息（本项目纯使用统计，不收集个人信息）
- 调试日志是否泄露信息（`Log.d` 打印 token）

## 检查流程

### 第一步：快速扫描
用搜索工具扫描可疑模式：
```bash
# 联网红线
grep -rEn "INTERNET|HttpURLConnection|OkHttp|Retrofit|Socket\s*\(" app/src/ --include="*.{kt,xml}"
# 密钥格式
grep -rEn "(password|passwd|secret|token|api_key|apikey|auth)\s*[:=]\s*['\"][^'\"]{6,}['\"]" app/src/ --include="*.{kt,xml}"
grep -rEn "sk-[A-Za-z0-9]{20,}|AKIA[0-9A-Z]{16}|-----BEGIN" app/src/ --include="*.{kt,xml}"
```

### 第二步：检查配置文件
- 读取 `AndroidManifest.xml`，确认无 INTERNET 权限
- 读取 `.gitignore`，确认 `.jks`、`keystore.properties` 被忽略
- 检查 `app/build.gradle.kts` 签名配置、依赖树

### 第三步：检查代码模式
- 扫描 SQL 拼接、路径拼接、动态执行
- 检查 exported 组件、Log 打印

### 第四步：输出报告
按以下格式输出：

```
## 🔒 安全审计报告

### 检查范围
项目目录、配置文件、依赖包

### 1️⃣ 联网红线（必查）
| 位置 | 问题 | 风险等级 | 说明 |
|------|------|---------|------|
（或"✅ 未发现联网痕迹"）

### 2️⃣ 敏感信息泄露
| 位置 | 类型 | 风险等级 | 说明 |
|------|------|---------|------|

### 3️⃣ 注入漏洞
| 位置 | 类型 | 风险等级 | 说明 |
|------|------|---------|------|

### 4️⃣ 配置文件明文泄露
| 文件 | 内容 | 风险等级 | 说明 |
|------|------|---------|------|

### 5️⃣ 其他安全隐患
| 位置 | 问题 | 风险等级 | 说明 |
|------|------|---------|------|

### 总结
- 高风险问题：X 个
- 中风险问题：X 个
- 低风险问题：X 个
- 总体评价：...
```

## 风险等级说明
- 🔴 **高**：可能被利用的漏洞、已泄露的密钥、**发现 INTERNET 权限**（立即处理）
- 🟡 **中**：不安全实践、需加固的配置
- 🟢 **低**：建议改进，不紧急

## 注意事项
- 审计是**只读分析**，发现问题先报告，修复需用户确认
- 发现密钥泄露时，提醒用户立即**轮换密钥**
- 已提交到 git 的密钥，仅删除文件不够，历史记录中仍存在，需提示用户
- 报告用中文输出，问题精确定位到文件+行号
- 用户是技术小白，解释漏洞时用通俗语言说明危害
