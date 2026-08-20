// 时迹 —— 统计页
// 日/周/月切换：总时长卡片、自绘渐变柱状图、应用排行 Top10、洞察卡片

package com.shiji.trace.ui.screens.stats

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
 * 统计页
 * @param container 依赖容器
 * @param onAppClick 点击排行应用 → 打开应用详情页
 */
@Composable
fun StatsScreen(
    container: AppContainer,
    onAppClick: (String) -> Unit = {},
) {
    val viewModel: StatsViewModel = viewModel(factory = StatsViewModel.factory(container))
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    // 空数据判断（首次加载中或确实无数据）
    val empty = !state.loading && state.ranking.isEmpty()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
    ) {
        Spacer(Modifier.height(12.dp))

        // —— 顶部标题行：标题 + 周期分段 ——
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "统计",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            PeriodSelector(state.period, onSelect = viewModel::selectPeriod)
        }

        Spacer(Modifier.height(16.dp))

        // —— 总时长卡片 ——
        if (state.loading) {
            Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (empty) {
            EmptyState()
        } else {
            TotalCard(state)
            Spacer(Modifier.height(12.dp))
            // —— 柱状图卡片 ——
            BarChartCard(state)
            Spacer(Modifier.height(12.dp))
            // —— 应用排行 ——
            RankingCard(state, onAppClick)
            Spacer(Modifier.height(12.dp))
            // —— 洞察卡片 ——
            InsightCard(state)
            Spacer(Modifier.height(24.dp))
        }
    }
}

// ============ 周期选择 ============

/** 周期分段选择（小米风格圆角 pill） */
@Composable
private fun PeriodSelector(selected: StatsPeriod, onSelect: (StatsPeriod) -> Unit) {
    val shape = RoundedCornerShape(50)
    Surface(shape = shape, color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(Modifier.padding(4.dp)) {
            StatsPeriod.entries.forEach { period ->
                val isSelected = period == selected
                Text(
                    text = period.label(),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.onPrimary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .clip(shape)
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable { onSelect(period) }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                )
            }
        }
    }
}

/** 周期显示名 */
private fun StatsPeriod.label(): String = when (this) {
    StatsPeriod.DAY -> "日"
    StatsPeriod.WEEK -> "周"
    StatsPeriod.MONTH -> "月"
}

// ============ 总时长卡片 ============

/** 总时长卡片：段内累计使用时长 + 会话数 */
@Composable
private fun TotalCard(state: StatsUiState) {
    val periodText = when (state.period) {
        StatsPeriod.DAY -> "今日已用"
        StatsPeriod.WEEK -> "近 7 天已用"
        StatsPeriod.MONTH -> "近 30 天已用"
    }
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(periodText, color = MaterialTheme.colorScheme.onPrimary, fontSize = 14.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                TimeFormat.formatDuration(state.totalTimeMs),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "共 ${state.sessionCount} 次使用",
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                fontSize = 13.sp,
            )
        }
    }
}

// ============ 柱状图 ============

/** 柱状图卡片：自绘橙色渐变柱 */
@Composable
private fun BarChartCard(state: StatsUiState) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                when (state.period) {
                    StatsPeriod.DAY -> "今日应用时长"
                    else -> "每日使用时长"
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            if (state.barData.isEmpty()) {
                Text(
                    "暂无数据",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 24.dp),
                )
            } else {
                UsageBarChart(state.barData, state.period)
            }
        }
    }
}

/** 自绘柱状图（渐变柱 + 底部标签） */
@Composable
private fun UsageBarChart(bars: List<BarEntry>, period: StatsPeriod) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer

    Column {
        Canvas(Modifier.fillMaxWidth().height(130.dp)) {
            // 柱高按最大值归一化
            val max = bars.maxOf { it.valueMs }.coerceAtLeast(1L)
            val slot = size.width / bars.size
            val barWidth = slot * 0.55f
            val corner = CornerRadius(barWidth / 2, barWidth / 2)
            bars.forEachIndexed { i, bar ->
                val barHeight = (bar.valueMs.toFloat() / max) * size.height
                // 顶部留出 4dp 呼吸空间，柱从底部生长
                val left = i * slot + (slot - barWidth) / 2
                val top = size.height - barHeight
                // 垂直渐变：顶橙 → 底浅橙
                drawRoundRect(
                    brush = Brush.verticalGradient(listOf(primary, primaryContainer)),
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = corner,
                )
            }
        }
        // 底部标签（柱多时省略，只显示部分）
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            val showCount = bars.size.coerceAtMost(7)
            bars.take(showCount).forEach { bar ->
                Text(
                    shortLabel(bar, period),
                    fontSize = 10.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** 柱状图标签：日期简化（M/d），包名截断（取显示名或包名短形式） */
@Composable
private fun shortLabel(bar: BarEntry, period: StatsPeriod): String {
    val context = LocalContext.current
    return when {
        period == StatsPeriod.DAY -> {
            // 应用显示名截取前 4 字
            val label = rememberAppLabel(context, bar.label)
            if (label.length > 4) label.take(4) else label
        }
        else -> {
            // 日期 "yyyy-MM-dd" → "M/d"
            val parts = bar.label.split("-")
            if (parts.size == 3) "${parts[1].toInt()}/${parts[2].toInt()}" else bar.label
        }
    }
}

// ============ 应用排行 ============

/** 应用排行卡片（Top10） */
@Composable
private fun RankingCard(state: StatsUiState, onAppClick: (String) -> Unit) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "应用排行",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(8.dp))
            val top = state.ranking.take(10)
            top.forEachIndexed { index, entry ->
                RankingRow(index, entry, top[0].totalTimeMs, onAppClick)
                if (index != top.lastIndex) {
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
    }
}

/** 单行排行：序号 + 图标 + 名称 + 时长 + 占比条 */
@Composable
private fun RankingRow(
    index: Int,
    entry: RankEntry,
    maxTimeMs: Long,
    onAppClick: (String) -> Unit,
) {
    val context = LocalContext.current
    val label = rememberAppLabel(context, entry.packageName)
    // 占比条宽度 = 该应用时长 / 榜首时长（视觉对比）
    val ratio = if (maxTimeMs > 0) entry.totalTimeMs.toFloat() / maxTimeMs else 0f

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onAppClick(entry.packageName) }
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 序号（前三名橙色加粗）
        Text(
            "${index + 1}",
            fontSize = 14.sp,
            fontWeight = if (index < 3) FontWeight.Bold else FontWeight.Normal,
            color = if (index < 3) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.width(22.dp),
        )
        AppIcon(entry.packageName, size = 34.dp)
        Spacer(Modifier.width(10.dp))
        // 名称 + 占比条
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    label,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    TimeFormat.formatDurationShort(entry.totalTimeMs),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(4.dp))
            // 橙色占比条（渐变，圆角）
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(2.dp)
                    )
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(ratio)
                        .height(4.dp)
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primary,
                                    MaterialTheme.colorScheme.primaryContainer,
                                )
                            ),
                            RoundedCornerShape(2.dp),
                        )
                )
            }
        }
    }
}

// ============ 洞察卡片 ============

/** 洞察卡片：最长连续使用 + 深夜使用占比 */
@Composable
private fun InsightCard(state: StatsUiState) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(
                "使用洞察",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                InsightItem(
                    title = "最长单次使用",
                    value = state.longestSession?.let {
                        val context = LocalContext.current
                        rememberAppLabel(context, it.first)
                    } ?: "—",
                    sub = state.longestSession?.let {
                        TimeFormat.formatDurationShort(it.second)
                    } ?: "暂无记录",
                    modifier = Modifier.weight(1f),
                )
                InsightItem(
                    title = "深夜使用占比",
                    value = TimeFormat.formatPercent(
                        (state.nightRatio * 100).toLong(),
                        100
                    ),
                    sub = "22:00 - 次日 06:00",
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/** 洞察单项 */
@Composable
private fun InsightItem(title: String, value: String, sub: String, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(
            value,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(sub, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ============ 空态 ============

/** 无数据空态 */
@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("暂无统计", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))
        Text(
            "在系统设置中开启「使用情况访问权限」后，\n使用数据会在这里呈现",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
