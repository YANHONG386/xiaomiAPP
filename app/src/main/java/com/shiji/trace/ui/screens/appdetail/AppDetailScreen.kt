// 时迹 —— 应用详情页
// 单应用近 14 天：每日时长曲线、24 小时时段分布、会话列表

package com.shiji.trace.ui.screens.appdetail

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shiji.trace.core.di.AppContainer
import com.shiji.trace.core.util.TimeFormat
import com.shiji.trace.ui.components.AppIcon
import com.shiji.trace.ui.components.rememberAppLabel

/**
 * 应用详情页
 * @param container 依赖容器
 * @param packageName 目标应用包名
 * @param onBack 返回上一页
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppDetailScreen(
    container: AppContainer,
    packageName: String,
    onBack: () -> Unit,
) {
    val viewModel: AppDetailViewModel = viewModel(
        key = "appdetail_$packageName",
        factory = AppDetailViewModel.factory(container, packageName),
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val label = rememberAppLabel(context, packageName)

    Column(Modifier.fillMaxSize()) {
        // —— 顶部栏：返回 + 图标 + 名称 + 总时长 ——
        TopAppBar(
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AppIcon(packageName, size = 32.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            label,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (!state.loading) {
                            Text(
                                "近 14 天共 ${TimeFormat.formatDuration(state.totalTimeMs)}",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                }
            },
        )

        if (state.loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(12.dp))

                // —— 每日时长曲线 ——
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            "每日使用时长",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(16.dp))
                        DailyBarChart(state.dailyTotals)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // —— 时段分布 ——
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            "时段分布（按开始时刻）",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(12.dp))
                        HourlyBars(state.hourly)
                    }
                }

                Spacer(Modifier.height(12.dp))

                // —— 会话列表 ——
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                ) {
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Text(
                            "使用记录（${state.sessions.size} 条）",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(8.dp))
                        if (state.sessions.isEmpty()) {
                            Text(
                                "暂无使用记录",
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp),
                            )
                        } else {
                            state.sessions.forEachIndexed { index, session ->
                                SessionRow(session)
                                if (index != state.sessions.lastIndex) {
                                    Spacer(Modifier.height(8.dp))
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

/** 每日时长柱状图（14 根细柱，标签隔日显示） */
@Composable
private fun DailyBarChart(daily: List<Pair<String, Long>>) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer

    if (daily.isEmpty()) {
        Text(
            "暂无数据",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 24.dp),
        )
        return
    }

    Column {
        Canvas(Modifier.fillMaxWidth().height(110.dp)) {
            val max = daily.maxOf { it.second }.coerceAtLeast(1L)
            val slot = size.width / daily.size
            val barWidth = slot * 0.6f
            val corner = CornerRadius(barWidth / 2, barWidth / 2)
            daily.forEachIndexed { i, (_, ms) ->
                val barHeight = (ms.toFloat() / max) * size.height
                val left = i * slot + (slot - barWidth) / 2
                val top = size.height - barHeight
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(primary, primaryContainer)),
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = corner,
                )
            }
        }
        // 标签：首日与末日显示日期（M/d），中间隔日显示
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            listOf(0, daily.lastIndex).distinct().forEach { i ->
                val parts = daily[i].first.split("-")
                val text = if (parts.size == 3) "${parts[1].toInt()}/${parts[2].toInt()}" else daily[i].first
                Text(
                    text,
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 24 小时时段分布（横向小柱，峰值高亮） */
@Composable
private fun HourlyBars(hourly: List<Pair<Int, Long>>) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val max = hourly.maxOfOrNull { it.second }?.coerceAtLeast(1L) ?: 1L

    Row(
        Modifier.fillMaxWidth().height(72.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom,
    ) {
        hourly.forEach { (hour, ms) ->
            // 柱高按最大值归一化；0 时长画 1dp 底标线（视觉连续）
            val h = if (ms > 0) (ms.toFloat() / max) * 60.dp.value else 2f
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f),
            ) {
                Box(
                    Modifier
                        .width(6.dp)
                        .height(h.dp)
                        // 有数据画橙色渐变柱；无数据画浅色底标线（视觉连续）
                        .background(
                            brush = if (ms > 0) {
                                Brush.verticalGradient(listOf(primary, primaryContainer))
                            } else {
                                Brush.verticalGradient(
                                    listOf(
                                        MaterialTheme.colorScheme.surfaceVariant,
                                        MaterialTheme.colorScheme.surfaceVariant,
                                    )
                                )
                            },
                            shape = RoundedCornerShape(3.dp),
                        )
                )
            }
        }
    }
    // 时标：0 点与 12 点标注
    Row(Modifier.fillMaxWidth().padding(top = 6.dp)) {
        Text("0", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text("12", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text("24", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** 会话行：时间段 + 时长 */
@Composable
private fun SessionRow(session: com.shiji.trace.data.db.entity.AppSessionEntity) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                "${TimeFormat.formatTime(session.startTimeMs)} - " +
                    TimeFormat.formatTime(session.endTimeMs),
                fontSize = 14.sp,
            )
        }
        Text(
            TimeFormat.formatDurationShort(session.durationMs),
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
