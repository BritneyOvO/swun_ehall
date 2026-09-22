@file:OptIn(ExperimentalHazeApi::class)

package cn.edu.swun.swun_ehall.ui.shell

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.ExperimentalHazeApi
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import dev.chrisbanes.haze.hazeSource

fun Modifier.hazeBehind(state: HazeState): Modifier = hazeSource(state)

@Composable
fun Modifier.liquidGlass(hazeState: HazeState): Modifier {
    val dark = isSystemInDarkTheme()
    val style = remember(dark) {
        GlassStyle.regular.then {
            shape(RoundedCornerShape(50))
            backgroundColor(if (dark) Color(0xB31C1C1E) else Color(0xB8F2F2F4))
            tint(if (dark) Color.Black.copy(alpha = 0.08f) else Color.Black.copy(alpha = 0.035f))
            specularIntensity(0.58f)
            ambientResponse(0.2f)
        }
    }
    return hazeGlass(input = HazeInput.Sources(hazeState), style = style)
}
