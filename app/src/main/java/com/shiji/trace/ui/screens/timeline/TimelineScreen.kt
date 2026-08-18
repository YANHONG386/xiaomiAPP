// 时迹 —— 使用时间线页（骨架占位）
// M4 阶段接入：竖向时间轴、会话胶囊、并行应用展示

package com.shiji.trace.ui.screens.timeline

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
 * 使用时间线页
 * 骨架占位：M4 阶段替换为真实时间轴（会话胶囊 + 并行徽标）
 */
@Composable
fun TimelineScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("时间线", style = MaterialTheme.typography.headlineLarge)
        Text(
            "骨架占位（M4 接入时间轴）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
