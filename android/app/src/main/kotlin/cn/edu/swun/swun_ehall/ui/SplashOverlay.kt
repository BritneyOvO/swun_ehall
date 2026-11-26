package cn.edu.swun.swun_ehall.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.swun.swun_ehall.R
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.basic.Text

@Composable
fun SplashOverlay(onFinished: () -> Unit) {
    val cover = remember { Animatable(1f) }
    var gone by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(700)
        cover.animateTo(0f, tween(220))
        gone = true
        onFinished()
    }
    if (gone) return
    Box(
        Modifier
            .fillMaxSize()
            .alpha(cover.value)
            .background(colorResource(R.color.splash_bg)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier.size(92.dp).background(Color(0xFF9B1B30), RoundedCornerShape(22.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text("民", color = Color.White, fontSize = 38.sp, fontWeight = FontWeight.SemiBold)
            }
            Text("民大助手", fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 18.dp))
            Text("西南民族大学", color = Color(0xFF77777F), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
        }
    }
}
