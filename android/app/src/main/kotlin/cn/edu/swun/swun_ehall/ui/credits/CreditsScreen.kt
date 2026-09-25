package cn.edu.swun.swun_ehall.ui.credits

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.model.formatXf
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.common.BackNav
import cn.edu.swun.swun_ehall.ui.common.FeatureBottomSpace
import cn.edu.swun.swun_ehall.ui.common.FeatureColumn
import cn.edu.swun.swun_ehall.ui.common.FeatureHint
import cn.edu.swun.swun_ehall.ui.common.FeatureLoadingPage
import cn.edu.swun.swun_ehall.ui.common.FeatureSection
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun CreditsScreen(session: Session, nav: NavHostController) {
    var loading by remember { mutableStateOf(session.credits == null) }
    var open by remember { mutableStateOf(setOf<String>()) }
    LaunchedEffect(session.loggedIn, session.demoMode) {
        loading = true
        session.refreshCredits()
        loading = false
    }
    val p = session.credits
    val cs = MiuixTheme.colorScheme
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "共修学分",
                navigationIcon = { BackNav { nav.popBackStack() } },
            )
        },
    ) { padding ->
        if (loading) {
            FeatureLoadingPage(padding)
            return@Scaffold
        }
        FeatureColumn(padding) {
            when {
                p == null -> FeatureHint("暂无学分数据")
                else -> {
                    Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                        Column(Modifier.padding(20.dp)) {
                            Text("已修学分", color = cs.onSurfaceVariantSummary)
                            Text("${formatXf(p.taken)} 学分", style = MiuixTheme.textStyles.title1)
                            Text(
                                buildString {
                                    if (p.required > 0) append("要求 ${formatXf(p.required)}  ·  ")
                                    p.gpa?.let { append("GPA ${"%.3f".format(it)}  ·  ") }
                                    append("已修 ${p.planPassed} 门")
                                    if (p.planFailed > 0) append("  ·  未过 ${p.planFailed} 门")
                                },
                                color = cs.onSurfaceVariantSummary,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                        }
                    }
                    FeatureSection("学分详情")
                    Card(modifier = Modifier.fillMaxWidth()) {
                        p.buckets.forEachIndexed { i, b ->
                            if (i > 0) HorizontalDivider()
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        open = if (b.name in open) open - b.name else open + b.name
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text(b.name)
                                        Text(
                                            "已修 ${formatXf(b.credits)} · ${b.courses} 门" +
                                                if (b.failed > 0) " · 未过 ${formatXf(b.failed)}" else "",
                                            color = cs.onSurfaceVariantSummary,
                                        )
                                    }
                                    Text(if (b.name in open) "收起" else "展开", color = cs.primary)
                                }
                                AnimatedVisibility(visible = b.name in open) {
                                    Column(Modifier.padding(top = 8.dp)) {
                                        b.items.forEach { c ->
                                            Text(
                                                "${c.name}  ${c.status}  ${c.score}",
                                                color = cs.onSurfaceVariantSummary,
                                                modifier = Modifier.padding(vertical = 2.dp),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            FeatureBottomSpace()
        }
    }
}
