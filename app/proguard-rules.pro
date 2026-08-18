# 时迹 —— 混淆规则（R8）
# 本项目只用了 Room / WorkManager / Compose，通常无需额外规则
# Room 生成的代码已自带 @Keep 注解，WorkManager 通过 WorkerFactory 无需规则
# 如需新增第三方库，在此补充对应 keep 规则

# 防止调试信息丢失（便于线上排错）
-keepattributes SourceFile,LineNumberTable
