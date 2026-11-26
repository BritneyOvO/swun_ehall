package cn.edu.swun.swun_ehall.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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
fun FeatureColumn(padding: PaddingValues, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        content = content,
    )
}

@Composable
fun FeatureLoadingPage(padding: PaddingValues) {
    val cs = MiuixTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        listOf(0.58f, 0.92f, 0.72f, 0.9f, 0.64f).forEach { fraction ->
            Box(
                Modifier
                    .fillMaxWidth(fraction)
                    .height(18.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(cs.surfaceContainerHighest),
            )
        }
    }
}

@Composable
fun FeatureLoading(card: Boolean = true) {
    val body = @Composable {
        Column(
            Modifier.fillMaxWidth().padding(vertical = 24.dp, horizontal = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            top.yukonga.miuix.kmp.basic.InfiniteProgressIndicator(modifier = Modifier.size(32.dp))
        }
    }
    if (card) {
        Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) { body() }
    } else {
        body()
    }
}

@Composable
fun FeatureHint(text: String) {
    val cs = MiuixTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CircleShape)
                .background(cs.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Text("—", color = cs.onSurfaceVariantSummary, fontSize = 18.sp)
        }
        Text(
            text,
            modifier = Modifier.padding(top = 14.dp),
            color = cs.onSurfaceVariantSummary,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun FeatureHint(
    title: String,
    summary: String,
    action: (@Composable () -> Unit)? = null,
) {
    val cs = MiuixTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(cs.surfaceContainerHighest),
            contentAlignment = Alignment.Center,
        ) {
            Text("空", color = cs.onSurfaceVariantSummary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(
            title,
            modifier = Modifier.padding(top = 14.dp),
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            summary,
            modifier = Modifier.padding(top = 5.dp).widthIn(max = 280.dp),
            color = cs.onSurfaceVariantSummary,
            fontSize = 13.sp,
            textAlign = TextAlign.Center,
        )
        action?.let { content ->
            Box(Modifier.padding(top = 14.dp)) { content() }
        }
    }
}

@Composable
fun FeatureSection(title: String, modifier: Modifier = Modifier) {
    Text(
        title,
        style = MiuixTheme.textStyles.title3,
        fontWeight = FontWeight.SemiBold,
        modifier = modifier.padding(start = 4.dp, top = 20.dp, end = 4.dp, bottom = 8.dp),
    )
}

@Composable
fun FeatureInlineNotice(
    title: String,
    summary: String? = null,
    tone: Color = MiuixTheme.colorScheme.primary,
    modifier: Modifier = Modifier,
) {
    val cs = MiuixTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(tone.copy(alpha = 0.09f))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(tone),
        )
        Column(Modifier.padding(start = 10.dp)) {
            Text(title, color = tone, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
            summary?.takeIf { it.isNotBlank() }?.let {
                Text(
                    it,
                    color = cs.onSurfaceVariantSummary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
fun FeatureMutedPanel(
    title: String,
    summary: String,
    modifier: Modifier = Modifier,
) {
    val cs = MiuixTheme.colorScheme
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(cs.surfaceContainerHighest)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(title, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        Text(
            summary,
            color = cs.onSurfaceVariantSummary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            modifier = Modifier.padding(top = 3.dp),
        )
    }
}

@Composable
fun FeatureInfoRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MiuixTheme.colorScheme.onBackground,
    strong: Boolean = false,
) {
    val cs = MiuixTheme.colorScheme
    Row(
        modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(0.38f), color = cs.onSurfaceVariantSummary, fontSize = 13.sp)
        Text(
            value.ifBlank { "—" },
            modifier = Modifier.weight(0.62f),
            color = valueColor,
            fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun FeatureProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val cs = MiuixTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .height(5.dp)
            .clip(CircleShape)
            .background(cs.surfaceContainerHighest),
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .height(5.dp)
                .clip(CircleShape)
                .background(cs.primary),
        )
    }
}

@Composable
fun FeatureStatus(text: String, tone: Color) {
    Text(text, color = tone, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
fun FeatureBottomSpace() {
    Spacer(Modifier.height(24.dp))
}
