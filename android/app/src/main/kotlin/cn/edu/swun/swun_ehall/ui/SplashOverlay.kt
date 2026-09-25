package cn.edu.swun.swun_ehall.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import cn.edu.swun.swun_ehall.R
import kotlinx.coroutines.delay

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
        Image(
            painter = painterResource(R.drawable.splash_logo),
            contentDescription = null,
            modifier = Modifier.size(160.dp),
        )
    }
}
