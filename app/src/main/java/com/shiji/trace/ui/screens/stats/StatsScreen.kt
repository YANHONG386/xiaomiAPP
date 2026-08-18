// 时迹 —— 统计页（骨架占位）
// M5 阶段接入：日/周/月统计、柱状图、应用排行

package com.shiji.trace.ui.screens.stats

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
 * 统计页
 * 骨架占位：M5 阶段替换为真实图表（柱状图 + 排行）
 */
@Composable
fun StatsScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("统计", style = MaterialTheme.typography.headlineLarge)
        Text(
            "骨架占位（M5 接入图表）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
