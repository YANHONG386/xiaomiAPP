// 时迹 —— 设置页（骨架占位）
// M3 起接入：授权状态卡、电池白名单引导；M6 接入隐私政策、关于

package com.shiji.trace.ui.screens.settings

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
 * 设置页
 * 骨架占位：M3 起接入授权状态、电池白名单；M6 接入隐私政策、关于
 */
@Composable
fun SettingsScreen() {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("设置", style = MaterialTheme.typography.headlineLarge)
        Text(
            "骨架占位（M3 接入授权引导）",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
