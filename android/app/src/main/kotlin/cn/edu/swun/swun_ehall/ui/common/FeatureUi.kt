package cn.edu.swun.swun_ehall.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.icon.extended.Refresh
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun BackNav(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(MiuixIcons.Back, contentDescription = "返回")
    }
}

@Composable
fun RefreshNav(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(MiuixIcons.Refresh, contentDescription = "刷新")
    }
}

@Composable
fun FeatureColumn(padding: androidx.compose.foundation.layout.PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 12.dp),
        content = content,
    )
}

@Composable
fun FeatureHint(text: String, error: Boolean = false) {
    val cs = MiuixTheme.colorScheme
    Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
        Text(
            text,
            modifier = Modifier.padding(16.dp),
            color = if (error) cs.error else cs.onSurfaceVariantSummary,
        )
    }
}

@Composable
fun FeatureSection(title: String, modifier: Modifier = Modifier) {
    Row(
        modifier.padding(start = 4.dp, top = 16.dp, end = 4.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(MiuixTheme.colorScheme.primary),
        )
        Text(title, style = MiuixTheme.textStyles.title3, modifier = Modifier.padding(start = 8.dp))
    }
}

@Composable
fun FeatureStatus(text: String, tone: Color) {
    Text(text, color = tone, fontSize = 13.sp)
}

@Composable
fun FeatureBottomSpace() {
    Spacer(Modifier.height(96.dp))
}


