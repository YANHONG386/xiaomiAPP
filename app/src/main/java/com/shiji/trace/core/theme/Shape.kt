// 时迹 —— 圆角规范
// 小米澎湃风格：卡片大圆角 16dp，胶囊全圆，按钮中等圆角

package com.shiji.trace.core.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * 全局形状规范
 * - 卡片：16dp 大圆角（小米风格特征）
 * - 按钮：12dp
 * - 徽标/胶囊：全圆
 */
val ShiJiShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp),
)
