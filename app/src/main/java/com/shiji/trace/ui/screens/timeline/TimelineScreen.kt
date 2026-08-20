// 时迹 —— 使用时间线页
// 布局：顶部图例（色块+应用名）→ 可折叠的"时间轴总览"（默认收起）→ 每个应用独立的同款时间轴卡片
// 时间胶囊为纯色块（无文字），并行段用橙色描边区分；点击胶囊 → 底部弹出会话详情

package com.shiji.trace.ui.screens.timeline

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ViewTimeline
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shiji.trace.core.di.AppContainer
import com.shiji.trace.core.util.TimeFormat
import com.shiji.trace.data.db.entity.AppSessionEntity
import com.shiji.trace.data.db.entity.ParallelGroupEntity
import com.shiji.trace.ui.components.AppIcon
import com.shiji.trace.ui.components.rememberAppLabel
import org.json.JSONArray
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 一天的长度（毫秒） */
private const val DAY_MS = 86_400_000L

/** 一小时的毫秒数（整点刻度用） */
private const val HOUR_MS = 3_600_000L

/** 一条泳道：某应用当天全部会话（按总时长降序排列泳道） */
private data class AppLane(
    val packageName: String,
    val sessions: List<AppSessionEntity>,
    val totalMs: Long,
)

/**
 * 时间线页
 * - 顶部日期条：左右切换日期（未来不可翻），点击日期回到今天
 * - 图例条：所有应用的颜色色块 + 应用名（说明每个色块代表哪个应用）
 * - 时间轴总览：可折叠区块（默认收起），展开后展示全部应用的泳道
 * - 单应用卡片：每个应用独立的同款时间轴（默认收起，点击展开）
 * - 时间胶囊为纯色块；并行段橙色描边；点击胶囊 → 底部弹窗详情
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimelineScreen(container: AppContainer) {
    val viewModel: TimelineViewModel = viewModel(factory = TimelineViewModel.factory(container))
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val sessions by viewModel.sessions.collectAsStateWithLifecycle()
    val parallelGroups by viewModel.parallelGroups.collectAsStateWithLifecycle()

    // 当前选中的会话（点击胶囊后弹详情）
    var detailSessionId by remember { mutableStateOf<Long?>(null) }

    // 当天的起止毫秒（泳道定位用）
    val dayStartMs = container.repository.parseDate(selectedDate)
    val dayEndMs = dayStartMs + DAY_MS
    val isToday = selectedDate == container.repository.today()

    // 应用泳道（按总时长降序；过滤系统桌面等）
    val lanes = remember(sessions) {
        sessions.filter { !it.isSystem }
            .groupBy { it.packageName }
            .map { (pkg, list) -> AppLane(pkg, list, list.sumOf { it.durationMs }) }
            .sortedByDescending { it.totalMs }
    }

    // 总览轨道范围：今天最早打开的应用时间 ~ 最晚结束时间（总览各泳道共用此范围，
    // 即"总图从今天最早打开的应用时间开始计算"）
    val overviewStartMs = lanes.minOfOrNull { it.sessions.minOf { s -> s.startTimeMs } } ?: 0L
    val overviewEndMs = lanes.maxOfOrNull { it.sessions.maxOf { s -> s.endTimeMs } } ?: 0L

    // 并行组索引：会话 id → 它所属的并行组（区间重叠 + 包名命中）
    val groupBySession = remember(sessions, parallelGroups) {
        buildMap<Long, ParallelGroupEntity> {
            sessions.forEach { s ->
                parallelGroups.firstOrNull { g ->
                    g.startMs <= s.endTimeMs && g.endMs >= s.startTimeMs &&
                        parsePackages(g.packagesJson).contains(s.packageName)
                }?.let { put(s.id, it) }
            }
        }
    }

    val detailSession = sessions.find { it.id == detailSessionId }
    val detailGroup = detailSession?.let { groupBySession[it.id] }

    Column(modifier = Modifier.fillMaxSize()) {
        // —— 日期条 ——
        DateSelectorBar(
            dateText = dateLabel(selectedDate, isToday),
            canGoForward = !isToday,
            onPrev = { viewModel.moveDay(-1) },
            onNext = { viewModel.moveDay(1) },
            onToday = { viewModel.selectDate(container.repository.today()) },
        )

        // —— 小统计 ——
        Text(
            "${sessions.size} 次使用 · ${lanes.size} 个应用" +
                if (groupBySession.isNotEmpty()) " · ${groupBySession.values.size} 组并行" else "",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
        )

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (lanes.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(top = 80.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (isToday) "今天还没有使用记录\n数据会在使用手机时自动记录"
                            else "这一天没有使用记录",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                return@LazyColumn
            }

            // —— 顶部图例：色块 + 应用名 ——
            item {
                LegendBar(lanes = lanes, isDark = isSystemInDarkTheme())
            }

            // —— 时间轴总览（可折叠，默认收起）——
            item {
                var expanded by rememberSaveable { mutableStateOf(false) }
                CollapsibleCard(
                    title = {
                        Icon(
                            Icons.Filled.ViewTimeline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(10.dp))
                        Text("时间轴总览", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "${lanes.size} 个应用",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                ) {
                    // 总览刻度尺：范围 = 全部泳道合并的使用时段，左侧缩进对齐泳道轨道起点
                    HourScale(
                        rangeStartMs = overviewStartMs,
                        rangeEndMs = overviewEndMs,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 92.dp, end = 10.dp, top = 4.dp)
                    )
                    lanes.forEach { lane ->
                        AppLaneRow(
                            lane = lane,
                            dayStartMs = overviewStartMs,
                            dayEndMs = overviewEndMs,
                            isDark = isSystemInDarkTheme(),
                            groupBySession = groupBySession,
                            onSessionClick = { detailSessionId = it.id },
                        )
                    }
                }
            }

            // —— 每个应用独立的同款时间轴（默认收起）——
            items(lanes, key = { "app-${it.packageName}" }) { lane ->
                var expanded by rememberSaveable(lane.packageName) { mutableStateOf(false) }
                // 该应用轨道范围：自己的最早开始 ~ 最晚结束（每个应用各自从最左端开始计算）
                val laneStartMs = lane.sessions.minOf { it.startTimeMs }
                val laneEndMs = lane.sessions.maxOf { it.endTimeMs }
                CollapsibleCard(
                    title = {
                        AppIcon(packageName = lane.packageName, size = 24.dp)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            rememberAppLabel(LocalContext.current, lane.packageName),
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            TimeFormat.formatDurationShort(lane.totalMs),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    expanded = expanded,
                    onToggle = { expanded = !expanded },
                ) {
                    // 该应用的时间轴（全宽色块，样式与总览一致）
                    // 顶部先放刻度尺（范围 = 该应用使用时段），再画时间带
                    Column(modifier = Modifier.fillMaxWidth()) {
                        HourScale(
                            rangeStartMs = laneStartMs,
                            rangeEndMs = laneEndMs,
                            modifier = Modifier.fillMaxWidth()
                        )
                        TimelineTrack(
                            sessions = lane.sessions,
                            dayStartMs = laneStartMs,
                            dayEndMs = laneEndMs,
                            isDark = isSystemInDarkTheme(),
                            groupBySession = groupBySession,
                            onSessionClick = { detailSessionId = it.id },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }

    // —— 会话详情底部弹窗 ——
    val session = detailSession
    if (session != null) {
        SessionDetailSheet(
            session = session,
            group = detailGroup,
            onDismissGroup = {
                viewModel.dismissParallelGroup(it)
                detailSessionId = null
            },
            onClose = { detailSessionId = null },
        )
    }
}

/** 日期选择条：← 日期（点击回今天） →（今天时右箭头禁用） */
@Composable
private fun DateSelectorBar(
    dateText: String,
    canGoForward: Boolean,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "前一天")
        }
        Text(
            dateText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onToday), // 点击日期回到今天
            textAlign = TextAlign.Center
        )
        IconButton(onClick = onNext, enabled = canGoForward) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "后一天")
        }
    }
}

/** 图例条：每个应用一个色块 + 应用名，说明时间轴上每个颜色代表哪个应用 */
@Composable
private fun LegendBar(lanes: List<AppLane>, isDark: Boolean) {
    val context = LocalContext.current
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        lanes.forEach { lane ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 色块：与时间轴胶囊同一套颜色（appColor）
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(appColor(lane.packageName, isDark))
                )
                Spacer(Modifier.width(5.dp))
                Text(
                    rememberAppLabel(context, lane.packageName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * 可折叠卡片：标题行（点击切换）+ 展开内容（带动画）
 * 时间轴总览与单应用时间轴共用此结构，保证交互一致
 */
@Composable
private fun CollapsibleCard(
    title: @Composable () -> Unit,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        // 标题行：整个可点击，展开/收起切换
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            title()
            Spacer(Modifier.width(8.dp))
            // 展开指示箭头（收起 ▸ / 展开 ▾）
            Icon(
                if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (expanded) "收起" else "展开",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        AnimatedVisibility(visible = expanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 8.dp, end = 8.dp, bottom = 10.dp)
            ) {
                content()
            }
        }
    }
}

/** 单条应用泳道（总览展开态）：左侧图标+名称，右侧纯色块时间带 */
@Composable
private fun AppLaneRow(
    lane: AppLane,
    dayStartMs: Long,
    dayEndMs: Long,
    isDark: Boolean,
    groupBySession: Map<Long, ParallelGroupEntity>,
    onSessionClick: (AppSessionEntity) -> Unit,
) {
    val context = LocalContext.current
    val label = rememberAppLabel(context, lane.packageName)

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧：图标 + 应用名（固定宽，超长省略）
        Column(
            modifier = Modifier.width(72.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(packageName = lane.packageName, size = 26.dp)
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))

        // 右侧：纯色块时间带（范围 = 总览共用时段，从最早打开时间开始，无文字）
        TimelineTrack(
            sessions = lane.sessions,
            dayStartMs = dayStartMs,
            dayEndMs = dayEndMs,
            isDark = isDark,
            groupBySession = groupBySession,
            onSessionClick = onSessionClick,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * 时间带：横轴 = 该泳道使用时段（dayStartMs 最早开始 → dayEndMs 最晚结束），
 * 会话 = 纯色块胶囊（按起止时间在时段内的比例定位，首个会话从最左端开始）
 * - 胶囊内不显示任何文字（观看更清爽）；并行段用橙色描边区分
 * - 每个胶囊右侧强制留 2dp 空隙：连续会话也保持可见边界，点击不误触
 * - 时段内画整点淡竖线（刻度参照，帮助对照上方刻度尺读时间）
 */
@Composable
private fun TimelineTrack(
    sessions: List<AppSessionEntity>,
    dayStartMs: Long,
    dayEndMs: Long,
    isDark: Boolean,
    groupBySession: Map<Long, ParallelGroupEntity>,
    onSessionClick: (AppSessionEntity) -> Unit,
    modifier: Modifier = Modifier,
    trackHeight: Dp = 32.dp,
) {
    if (sessions.isEmpty()) return
    // 轨道跨度 = 使用时段（结束必晚于开始，正常数据恒 > 0，杜绝除零）
    val daySpan = (dayEndMs - dayStartMs).toFloat()
    BoxWithConstraints(
        modifier = modifier.height(trackHeight)
    ) {
        val trackWidth = maxWidth
        // 时间带底色（细条）
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .align(Alignment.Center)
                .background(
                    MaterialTheme.colorScheme.surfaceVariant,
                    RoundedCornerShape(2.dp)
                )
        )
        // 整点淡竖线（背景层，后画的色块会压住它）：画时段内所有整点
        // 用 < 而非 <=：dayEndMs 恰为整点时末根线会画在轨道右缘外 1dp，去掉它
        var tickMs = (dayStartMs / HOUR_MS + 1) * HOUR_MS
        while (tickMs < dayEndMs) {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = trackWidth * ((tickMs - dayStartMs).toFloat() / daySpan))
                    .width(1.dp)
                    .height(26.dp)
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.10f))
            )
            tickMs += HOUR_MS
        }
        sessions.forEach { s ->
            val startRatio = ((s.startTimeMs - dayStartMs).toFloat() / daySpan)
                .coerceIn(0f, 1f)
            val widthRatio = (s.durationMs.toFloat() / daySpan).coerceIn(0.035f, 1f)
            val group = groupBySession[s.id]
            // 纯色块（无文字）：并行段 = 主题橙描边 + 淡橙底，普通段 = 应用色
            // 注意：必须 align(Center) 与底条同心，否则色块会贴顶盖住细条（视觉偏差约 3dp）
            // 宽度 = 时长比例 - 2dp 留缝：相邻胶囊之间永远有可见空隙，点错概率大幅降低
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(x = trackWidth * startRatio)
                    .width(((trackWidth * widthRatio) - 2.dp).coerceAtLeast(6.dp))
                    .height(26.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .then(
                        if (group != null) {
                            Modifier
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .border(
                                    width = 1.5.dp,
                                    color = MaterialTheme.colorScheme.primary,
                                    shape = RoundedCornerShape(10.dp)
                                )
                        } else {
                            Modifier.background(
                                appColor(s.packageName, isDark).copy(alpha = 0.55f)
                            )
                        }
                    )
                    .clickable { onSessionClick(s) }
            )
        }
    }
}

/**
 * 刻度尺：标注轨道两端时间（范围 = 该泳道使用时段，起点在最左、终点在最右）
 * 与 TimelineTrack 同一套"时间→像素"换算，展开卡片时帮助把色块和具体时间对应
 * 轨道宽度即时段跨度，起止标签天然在两端，无需防重叠处理
 */
@Composable
private fun HourScale(
    rangeStartMs: Long,
    rangeEndMs: Long,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier.height(16.dp)) {
        val timeStyle = MaterialTheme.typography.labelSmall
        val timeColor = MaterialTheme.colorScheme.onSurfaceVariant
        // 起始标签：轨道最左端（即该应用/总览最早打开时间）
        Text(
            TimeFormat.formatTime(rangeStartMs),
            style = timeStyle,
            color = timeColor,
            modifier = Modifier.align(Alignment.TopStart)
        )
        // 结束标签：轨道最右端（即最晚结束时间）
        Text(
            TimeFormat.formatTime(rangeEndMs),
            style = timeStyle,
            color = timeColor,
            modifier = Modifier.align(Alignment.TopEnd)
        )
    }
}

/** 会话详情底部弹窗：起止时间 / 时长 / 并行应用列表 / 解除并行标记 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun SessionDetailSheet(
    session: AppSessionEntity,
    group: ParallelGroupEntity?,
    onDismissGroup: (Long) -> Unit,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    val label = rememberAppLabel(context, session.packageName)
    val groupPackages = group?.let { parsePackages(it.packagesJson) } ?: emptyList()

    ModalBottomSheet(onDismissRequest = onClose) {
        Column(
            modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 32.dp)
        ) {
            // 头部：图标 + 名称
            Row(verticalAlignment = Alignment.CenterVertically) {
                AppIcon(packageName = session.packageName, size = 44.dp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(label, style = MaterialTheme.typography.titleLarge)
                    Text(
                        session.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // 时间信息
            DetailRow("开始", TimeFormat.formatTimeSeconds(session.startTimeMs))
            DetailRow("结束", TimeFormat.formatTimeSeconds(session.endTimeMs))
            DetailRow("时长", TimeFormat.formatDurationShort(session.durationMs))

            // 并行信息（存在并行组时）
            if (group != null && groupPackages.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.height(16.dp))
                Text(
                    "并行使用",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    groupPackages.filter { it != session.packageName }
                        .forEach { pkg ->
                            ParallelChip(label = rememberAppLabel(context, pkg))
                        }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "这段时间同时使用了这些应用，并行持续 " +
                        TimeFormat.formatDurationShort(group.durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onDismissGroup(group.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("解除并行标记")
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "若这是误判（如画中画视频），可解除标记，该段将不再显示为并行",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 详情行：字段名 + 值 */
@Composable
private fun DetailRow(name: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(56.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

/** 并行应用小标签 */
@Composable
private fun ParallelChip(label: String) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// —— 工具 ——

/** 按包名哈希生成固定颜色（同一应用不同日期颜色一致；深色模式加深） */
private fun appColor(packageName: String, isDark: Boolean): androidx.compose.ui.graphics.Color {
    val hue = ((packageName.hashCode() % 360) + 360) % 360
    return androidx.compose.ui.graphics.Color.hsl(
        hue.toFloat(),
        saturation = 0.58f,
        lightness = if (isDark) 0.5f else 0.62f
    )
}

/** 日期文本："今天 · 8月18日 周一" / "8月17日 周日" */
private fun dateLabel(date: String, isToday: Boolean): String {
    val d = SimpleDateFormat("M月d日 EEE", Locale.CHINA)
        .format(Date(SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(date)!!.time))
    return if (isToday) "今天 · $d" else d
}

/** 解析并行组 JSON 包名列表（容错：解析失败返回空） */
private fun parsePackages(json: String): List<String> = try {
    val arr = JSONArray(json)
    List(arr.length()) { arr.getString(it) }
} catch (e: Exception) {
    emptyList()
}
