// 时迹 —— 今日概览页（骨架占位）
// M3 阶段接入真实数据：今日总时长、应用使用列表、正在使用卡片

package com.shiji.trace.ui.screens.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 今日概览页
 * 骨架占位：M3 阶段替换为真实数据（今日总时长、应用列表）
 */
@Composable
fun TodayScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("今日", style = MaterialTheme.typography.headlineLarge)
        Text(
            "骨架占位（M3 接入数据）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
