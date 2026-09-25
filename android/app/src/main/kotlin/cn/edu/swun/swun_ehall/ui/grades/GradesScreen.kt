package cn.edu.swun.swun_ehall.ui.grades

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.swun.swun_ehall.data.http.gradePassed
import cn.edu.swun.swun_ehall.data.model.SchoolTerm
import cn.edu.swun.swun_ehall.data.model.buildGradeTerms
import cn.edu.swun.swun_ehall.data.model.currentSchoolTerm
import cn.edu.swun.swun_ehall.data.model.matches
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.common.FeatureBottomSpace
import cn.edu.swun.swun_ehall.ui.common.FeatureColumn
import cn.edu.swun.swun_ehall.ui.common.FeatureHint
import cn.edu.swun.swun_ehall.ui.common.FeatureLoadingPage
import cn.edu.swun.swun_ehall.ui.common.RefreshNav
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.preference.OverlayDropdownPreference
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun GradesScreen(session: Session) {
    var loading by remember { mutableStateOf(!session.demoMode && session.grades.isEmpty()) }
    var term by remember { mutableStateOf(currentSchoolTerm()) }
    val terms = remember(session.studentId, session.profile.grade) {
        buildGradeTerms(session.studentId, session.profile.grade)
    }
    val scope = rememberCoroutineScope()
    LaunchedEffect(session.loggedIn, session.demoMode) {
        if (!session.demoMode && session.grades.isEmpty()) {
            loading = true
            session.refreshGrades()
            loading = false
        }
    }
    val shown = session.grades.filter { it.matches(term) }
    var xf = 0.0
    var jdXf = 0.0
    var passed = 0
    shown.forEach { g ->
        val c = g.credit.toDoubleOrNull() ?: 0.0
        val jd = g.gpa.toDoubleOrNull()
        if (gradePassed(g)) {
            passed++
            if (jd != null && c > 0) {
                xf += c
                jdXf += jd * c
            }
        }
    }
    val gpa = if (xf > 0) jdXf / xf else null
    val cs = MiuixTheme.colorScheme
    val termIndex = terms.indexOfFirst { it.xnm == term.xnm && it.xqm == term.xqm }.let { if (it < 0) 0 else it }
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "成绩",
                actions = {
                    RefreshNav {
                        scope.launch {
                            loading = true
                            session.refreshGrades()
                            loading = false
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (loading) {
            FeatureLoadingPage(padding)
            return@Scaffold
        }
        FeatureColumn(padding) {
            Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                OverlayDropdownPreference(
                    title = "学期",
                    items = terms.map { it.label },
                    selectedIndex = termIndex,
                    onSelectedIndexChange = { term = terms[it] },
                )
            }
            Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                Column(Modifier.padding(20.dp)) {
                    Text(term.label, color = cs.onSurfaceVariantSummary)
                    Text(
                        gpa?.let { "GPA  ${"%.3f".format(it)}" } ?: "GPA  —",
                        style = MiuixTheme.textStyles.title1,
                    )
                    Text(
                        "${shown.size} 门 · 通过 $passed 门" + if (xf > 0) " · ${"%.1f".format(xf)} 学分" else "",
                        color = cs.onSurfaceVariantSummary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            when {
                shown.isEmpty() -> FeatureHint("该学期暂无成绩")
                else -> Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    shown.forEachIndexed { i, g ->
                        if (i > 0) HorizontalDivider()
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(g.name)
                                Text(
                                    buildString {
                                        if (g.kind.isNotEmpty()) append(g.kind)
                                        if (g.credit.isNotEmpty()) {
                                            if (isNotEmpty()) append(" · ")
                                            append("${g.credit} 学分")
                                        }
                                        if (g.gpa.isNotEmpty()) {
                                            if (isNotEmpty()) append(" · ")
                                            append("绩点 ${g.gpa}")
                                        }
                                    },
                                    color = cs.onSurfaceVariantSummary,
                                )
                            }
                            Text(
                                g.score.ifBlank { g.mark }.ifBlank { "—" },
                                color = if (gradePassed(g)) Color(0xFF2E7D32) else cs.error,
                                fontSize = 20.sp,
                            )
                        }
                    }
                }
            }
            FeatureBottomSpace()
        }
    }
}
