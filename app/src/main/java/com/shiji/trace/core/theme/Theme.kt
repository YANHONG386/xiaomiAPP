// 时迹 —— 小米澎湃系统风格主题
// 主色 #FF6900 小米橙，浅色背景 #F7F7F7，深色模式跟随系统

package com.shiji.trace.core.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// —— 小米橙主色系（浅色模式）——
// 主色 #FF6900，配白色前景；次级色偏亮橙
private val LightColors = lightColorScheme(
    primary = Color(0xFFFF6900),        // 小米橙：主按钮、选中态、强调
    onPrimary = Color.White,            // 主色上的文字（白）
    primaryContainer = Color(0xFFFFDBC8), // 主色容器（浅橙，用于选中背景）
    onPrimaryContainer = Color(0xFF3B1C05),
    secondary = Color(0xFF8A5A3A),      // 次级色：棕橙，用于次要强调
    onSecondary = Color.White,
    background = Color(0xFFF7F7F7),     // 页面背景：浅灰
    onBackground = Color(0xFF1A1A1A),
    surface = Color.White,              // 卡片背景：纯白
    onSurface = Color(0xFF1A1A1A),
    surfaceVariant = Color(0xFFF2F2F2), // 次级表面（输入框、分割）
    onSurfaceVariant = Color(0xFF6E6E6E),
    outline = Color(0xFFE0E0E0),        // 极浅描边
    error = Color(0xFFB3261E),
)

// —— 深色模式（跟随系统，品牌色保持橙调）——
private val DarkColors = darkColorScheme(
    primary = Color(0xFFFF8A4D),        // 深色下橙色调亮一档保证可读性
    onPrimary = Color(0xFF3B1C05),
    primaryContainer = Color(0xFF5C3208),
    onPrimaryContainer = Color(0xFFFFDBC8),
    secondary = Color(0xFFE8B496),
    onSecondary = Color(0xFF4A2A14),
    background = Color(0xFF121212),     // 深色背景
    onBackground = Color(0xFFE4E4E4),
    surface = Color(0xFF1E1E1E),        // 深色卡片
    onSurface = Color(0xFFE4E4E4),
    surfaceVariant = Color(0xFF2A2A2A),
    onSurfaceVariant = Color(0xFFB0B0B0),
    outline = Color(0xFF3A3A3A),
    error = Color(0xFFF2B8B5),
)

/**
 * 时迹主题入口
 * @param darkTheme 是否使用深色模式（默认跟随系统设置）
 */
@Composable
fun ShiJiTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(
        // 根据系统深浅模式选择配色方案
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = ShiJiTypography,
        shapes = ShiJiShapes,
        content = content
    )
}
