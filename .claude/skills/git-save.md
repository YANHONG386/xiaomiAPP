---
name: git-save
description: 执行时迹项目的标准 git 提交（存档）流程。执行前会检查提交门禁合格证，通过后执行 git add + commit（可选 push）。当用户要求"提交"、"存档"、"提交代码"或 gitcommit-agent 调用时使用。
---

# git-save — 标准存档流程

## 目的
执行代码存档（git commit）。**必须**先验证提交门禁（测试合格证 + 质量合格证），通过后才执行提交。

## 工作流程

### 第一步：检查合格证（门禁）
检查两个合格证文件是否存在且有效：
```bash
cat .claude/gate/tests.pass.json 2>/dev/null || echo "缺少测试合格证"
cat .claude/gate/quality.pass.json 2>/dev/null || echo "缺少质量合格证"
```

**判定规则：**
1. 两个文件都存在？
2. 两个的 `passed` 都是 true？
3. 两个的 `codeFingerprint` 都等于当前 `git diff HEAD | git hash-object --stdin`？（防过期）

**全部满足 → 放行；任一不满足 → 拒绝提交**，提示先运行 gitcommit-agent 重新检查。

### 第二步：查看改动
```bash
git status
git diff --stat
```

### 第三步：提交
```bash
git add .
git commit -m "<清晰的提交说明>"
```

**提交说明规范**：
- 简短概括本次改动（如"修复：同步引擎时钟回拨处理"）
- 用中文，20~50 字
- 一次提交只做一件事

### 第四步（可选）：推送
如果用户要求推送到远程仓库，执行：
```bash
git push
```

## 提交后的清理
提交成功后，删除合格证（避免下次误用旧凭证）：
```bash
rm -f .claude/gate/tests.pass.json .claude/gate/quality.pass.json
```

## 注意事项
- **绝不跳过门禁直接提交**
- 提交信息必须清晰，不能是"update"、"fix"这类无意义文字
- 如果门禁不通过，绝不强制提交（可以使用 `git commit --no-verify` 绕过，但必须先告知用户并获得同意）
