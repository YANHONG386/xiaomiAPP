// 时迹 —— 使用时间线页
// 泳道式竖向时间轴：每个应用一条泳道，胶囊 = 单次使用（位置按时间比例定位）
// 并行段橙色高亮 + "并行"徽标；点击胶囊 → 底部弹出会话详情，可解除误判的并行标记

package com.shiji.trace.ui.screens.timeline

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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

/** 一条泳道：某应用当天全部会话（按总时长降序排列泳道） */
private data class AppLane(
    val packageName: String,
    val sessions: List<AppSessionEntity>,
    val totalMs: Long,
)

/**
 * 时间线页
 * - 顶部日期条：左右切换日期（未来不可翻），点击日期回到今天
 * - 中部泳道时间轴：应用图标 + 时间带，胶囊按起止时间定位
 * - 并行段：橙色描边 + "并行"徽标
 * - 点击胶囊：底部弹窗详情（起止/时长/并行应用），支持解除并行标记
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
            }
            items(lanes, key = { it.packageName }) { lane ->
                AppLaneRow(
                    lane = lane,
                    dayStartMs = dayStartMs,
                    dayEndMs = dayEndMs,
                    isDark = isSystemInDarkTheme(),
                    groupBySession = groupBySession,
                    onSessionClick = { detailSessionId = it.id },
                )
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

/** 单条应用泳道：左侧图标+名称，右侧时间带内按比例定位胶囊 */
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
    val daySpan = (dayEndMs - dayStartMs).toFloat()

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 左侧：图标 + 应用名（固定宽，超长省略）
        Column(
            modifier = Modifier.width(84.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            AppIcon(packageName = lane.packageName, size = 30.dp)
            Spacer(Modifier.height(2.dp))
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(10.dp))

        // 右侧：时间带（0:00 → 24:00），胶囊按起止比例定位
        BoxWithConstraints(
            modifier = Modifier.weight(1f).height(40.dp)
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
            lane.sessions.forEach { s ->
                val startRatio = ((s.startTimeMs - dayStartMs).toFloat() / daySpan)
                    .coerceIn(0f, 1f)
                val widthRatio = (s.durationMs.toFloat() / daySpan).coerceIn(0.035f, 1f)
                val group = groupBySession[s.id]
                Box(
                    modifier = Modifier
                        .offset(x = trackWidth * startRatio)
                        .width(trackWidth * widthRatio)
                        .height(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .then(
                            if (group != null) {
                                // 并行段：主题橙描边 + 淡橙底 + 徽标
                                Modifier
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .border(
                                        width = 1.5.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(10.dp)
                                    )
                            } else {
                                Modifier.background(
                                    appColor(lane.packageName, isDark).copy(alpha = 0.55f)
                                )
                            }
                        )
                        .clickable { onSessionClick(s) }
                ) {
                    if (group != null) {
                        Text(
                            "并行",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }
                }
            }
        }
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

/** 应用显示名（包管理器解析，取不到用包名兜底） */
@Composable
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
