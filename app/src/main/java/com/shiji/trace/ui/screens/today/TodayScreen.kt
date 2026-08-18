// 时迹 —— 今日概览页
// 展示：今日总时长卡片、未授权横幅、应用使用列表（图标+名称+时长条+占比）

package com.shiji.trace.ui.screens.today

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shiji.trace.core.di.AppContainer
import com.shiji.trace.core.util.TimeFormat
import com.shiji.trace.data.db.entity.DailySnapshotEntity
import com.shiji.trace.ui.components.AppIcon
import kotlinx.coroutines.delay

/**
 * 今日概览页
 * - 未授权：显示授权引导横幅（不阻塞其他功能浏览）
 * - 已授权：总时长卡 + 应用使用列表（按使用时长排序）
 */
@Composable
fun TodayScreen(container: AppContainer) {
    val viewModel: TodayViewModel = viewModel(factory = TodayViewModel.factory(container))
    val snapshots by viewModel.todaySnapshots.collectAsStateWithLifecycle()
    val hasAccess by viewModel.hasUsageAccess.collectAsStateWithLifecycle()
    // 当前时间（"正在使用"实时卡用，每秒刷新）
    var nowMs by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            nowMs = System.currentTimeMillis()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        // —— 未授权横幅 ——
        if (!hasAccess) {
            UsageAccessBanner(
                onClick = { viewModel.openUsageAccessSettings() }
            )
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // —— 总时长卡片 ——
            item {
                TotalDurationCard(
                    totalMs = snapshots.sumOf { it.totalTimeMs },
                    sessionCount = snapshots.size,
                )
            }

            // —— 应用使用列表 ——
            if (snapshots.isNotEmpty()) {
                item {
                    Text(
                        "今日使用",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                items(snapshots, key = { it.packageName }) { snapshot ->
                    AppUsageRow(snapshot, totalMs = snapshots.sumOf { it.totalTimeMs })
                }
            } else if (hasAccess) {
                // 已授权但暂无数据（刚装好/今天还没用过）
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 48.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "今天还没有使用记录\n数据会在使用手机时自动记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** 未授权引导横幅 */
@Composable
private fun UsageAccessBanner(onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "需要开启使用情况访问权限",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "点击前往系统设置开启后，才能记录应用使用时间",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
            Button(onClick = onClick) {
                Text("去开启")
            }
        }
    }
}

/** 总时长卡片（小米橙渐变） */
@Composable
private fun TotalDurationCard(totalMs: Long, sessionCount: Int) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        listOf(Color(0xFFFF6900), Color(0xFFFF8A4D))
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Text(
                    "今日已用",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    TimeFormat.formatDuration(totalMs),
                    style = MaterialTheme.typography.headlineLarge,
                    color = Color.White
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "共使用 $sessionCount 个应用",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f)
                )
            }
        }
    }
}

/** 单个应用使用行 */
@Composable
private fun AppUsageRow(snapshot: DailySnapshotEntity, totalMs: Long) {
    val context = LocalContext.current
    // 应用显示名：包管理器解析（取不到用包名）
    val label = remember(snapshot.packageName) {
        try {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(snapshot.packageName, 0)
            ).toString()
        } catch (e: Exception) {
            snapshot.packageName
        }
    }
    // 时长条比例（相对今日总时长）
    val ratio = if (totalMs > 0) snapshot.totalTimeMs.toFloat() / totalMs.toFloat() else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(packageName = snapshot.packageName, size = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
                Spacer(Modifier.height(6.dp))
                // 橙色时长条（相对总时长比例）
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(3.dp)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(ratio.coerceIn(0f, 1f))
                            .height(6.dp)
                            .background(
                                MaterialTheme.colorScheme.primary,
                                RoundedCornerShape(3.dp)
                            )
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "最后使用 ${TimeFormat.formatTime(snapshot.lastUsedMs)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    TimeFormat.formatDurationShort(snapshot.totalTimeMs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    TimeFormat.formatPercent(snapshot.totalTimeMs, totalMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
