// 时迹 —— 主 Activity
// 单 Activity 架构：整个应用只有一个 Activity，页面切换全靠 Compose 导航

package com.shiji.trace

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.shiji.trace.core.theme.ShiJiTheme
import com.shiji.trace.ui.navigation.ShiJiNavGraph

/**
 * 主 Activity
 * - 开启边到边布局（安卓 15 强制要求，让内容延伸到状态栏/导航栏后面）
 * - 挂载主题与导航图
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 边到边布局：内容延伸至系统栏（时间线页刻度需注意留白）
        enableEdgeToEdge()
        setContent {
            ShiJiTheme {
                // 应用导航图（4 个主标签页 + 后续详情页）
                ShiJiNavGraph()
            }
        }
    }
}
