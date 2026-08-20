// 时迹 —— 应用图标加载组件
// 用包管理器加载应用图标（内存缓存避免重复查询）

package com.shiji.trace.ui.components

import android.content.Context
import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

/** 应用图标内存缓存（约 200 条，LRU） */
private val iconCache = object : android.util.LruCache<String, Drawable>(200) {}

/**
 * 应用图标
 * @param packageName 应用包名
 * @param size 图标尺寸
 */
@Composable
fun AppIcon(packageName: String, size: Dp = 40.dp) {
    val context = LocalContext.current
    // 先用缓存，没有则异步加载
    var drawable by remember(packageName) {
        mutableStateOf(iconCache.get(packageName))
    }

    LaunchedEffect(packageName) {
        if (drawable == null) {
            val loaded = try {
                context.packageManager.getApplicationIcon(packageName)
            } catch (e: Exception) {
                // 包不可见（未授权时）→ 用默认图标占位
                null
            }
            if (loaded != null) {
                iconCache.put(packageName, loaded)
                drawable = loaded
            }
        }
    }

    Box(
        modifier = Modifier.size(size).clip(RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center,
    ) {
        val d = drawable
        if (d != null) {
            Image(
                bitmap = d.toBitmap(width = 64, height = 64).asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(size)
            )
        } else {
            // 占位：圆角橙色块 + 首字母
            Box(
                modifier = Modifier
                    .size(size)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer)
            )
        }
    }
}

/**
 * 应用显示名（内存缓存避免重复查询；解析失败回退包名）
 * 注：包管理器在未授权时不可见应用详情，回退包名可正常展示
 */
@Composable
fun rememberAppLabel(context: Context, packageName: String): String =
    remember(packageName) {
        try {
            context.packageManager.getApplicationLabel(
                context.packageManager.getApplicationInfo(packageName, 0)
            ).toString()
        } catch (e: Exception) {
            packageName
        }
    }
