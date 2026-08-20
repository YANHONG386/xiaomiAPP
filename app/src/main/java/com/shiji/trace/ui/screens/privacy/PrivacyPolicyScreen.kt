// 时迹 —— 隐私政策页
// 内置静态文本页（纯单机应用：无需联网加载，商店审核/软著合规必需）

package com.shiji.trace.ui.screens.privacy

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * 隐私政策页
 * 内容要点：纯本地处理声明、权限用途说明、数据生命周期、画中画误判说明
 * （商店审核重点：PACKAGE_USAGE_STATS 特殊权限必须说明用途）
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PrivacyPolicyScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("隐私政策", fontSize = 17.sp, fontWeight = FontWeight.SemiBold) },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
        )

        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Section("一、总则")
            Paragraph("「时迹」（以下简称本应用）是一款记录手机各应用使用时间的工具软件。" +
                "本应用为纯单机应用：不联网、不申请联网权限、不上传任何数据，所有信息仅保存在您的手机本机。")

            Section("二、权限说明")
            Paragraph("本应用需要「使用情况访问权限」（系统特殊权限）。该权限仅用于读取" +
                "应用使用统计信息（如某应用的使用起止时间），以生成使用时间线、今日概览和统计图表。" +
                "本应用不会读取您的通讯录、短信、位置、照片等任何个人隐私数据，不会读取其他应用的内容。")

            Section("三、数据处理")
            Paragraph("1. 数据来源：使用统计信息来自手机系统的「使用情况访问」接口（UsageStatsManager），" +
                "全部在您的设备本地处理。\n" +
                "2. 数据存储：使用记录存储在应用本地数据库（本机），不经过任何服务器。\n" +
                "3. 数据保留：原始事件与使用会话保留 30 天后自动清理；每日使用摘要长期保留，同样仅保存在本机。\n" +
                "4. 数据删除：卸载本应用即删除全部数据。")

            Section("四、并行使用检测说明")
            Paragraph("本应用会检测「同一时间段使用的应用」（如分屏场景）。" +
                "需要说明的是：手机系统的使用统计在画中画等场景下可能将背景应用一并计入前台，" +
                "因此本应用对时长悬殊、重合度低的情况标注为「疑似画中画」而非并行，" +
                "并提供手动修正入口。检测结果全部在本地计算，不上传任何信息。")

            Section("五、未成年人保护")
            Paragraph("本应用不涉及任何形式的个人信息收集，适用于各年龄段用户。")

            Section("六、政策更新")
            Paragraph("本隐私政策如有更新，将在应用内展示新版本。由于本应用不联网，" +
                "更新后的政策随应用版本更新而生效。")

            Section("七、联系我们")
            Paragraph("本应用为个人开发者作品。如对本隐私政策有任何疑问，" +
                "可通过应用商店本应用详情页的开发者联系方式与我们联系。")

            Spacer(Modifier.height(32.dp))
            Text(
                "更新日期：2026 年 8 月",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** 章节标题 */
@Composable
private fun Section(title: String) {
    Text(
        title,
        fontSize = 15.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 18.dp, bottom = 6.dp),
    )
}

/** 段落正文 */
@Composable
private fun Paragraph(text: String) {
    Text(
        text,
        fontSize = 13.sp,
        lineHeight = 20.sp,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
