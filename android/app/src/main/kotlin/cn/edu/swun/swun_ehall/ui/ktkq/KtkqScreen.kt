package cn.edu.swun.swun_ehall.ui.ktkq

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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.model.KtkqCourse
import cn.edu.swun.swun_ehall.data.model.SignActivity
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.common.BackNav
import cn.edu.swun.swun_ehall.ui.common.FeatureBottomSpace
import cn.edu.swun.swun_ehall.ui.common.FeatureColumn
import cn.edu.swun.swun_ehall.ui.common.FeatureHint
import cn.edu.swun.swun_ehall.ui.common.RefreshNav
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun KtkqScreen(session: Session, nav: NavHostController) {
    var loading by remember { mutableStateOf(!session.demoMode && session.ktkqCourses.isEmpty()) }
    var testing by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(session.loggedIn, session.demoMode) {
        loading = true
        session.refreshKtkq()
        loading = false
    }
    val cs = MiuixTheme.colorScheme
    val week = session.ktkqWeekNum
    val title = listOf(session.ktkqXnxqmc, "第 $week 周").filter { it.isNotBlank() }.joinToString("  ")
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "课堂考勤",
                navigationIcon = { BackNav { nav.popBackStack() } },
                actions = {
                    RefreshNav {
                        scope.launch {
                            loading = true
                            session.refreshKtkq()
                            loading = false
                        }
                    }
                },
            )
        },
    ) { padding ->
        FeatureColumn(padding) {
            if (loading) FeatureHint("正在拉取本周课程…")
            session.loadHint?.let { FeatureHint(it, error = true) }
            if (!loading) {
                Text(
                    title,
                    style = MiuixTheme.textStyles.title3,
                    modifier = Modifier.padding(top = 16.dp, start = 4.dp, end = 4.dp),
                )
                Text(
                    "点一门课进入签到。课表里点课程也可以。",
                    color = cs.onSurfaceVariantSummary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 6.dp, start = 4.dp, end = 4.dp, bottom = 4.dp),
                )
            }
            if (session.developerMode) {
                Button(
                    onClick = {
                        if (testing) return@Button
                        scope.launch {
                            testing = true
                            testResult = try {
                                session.testFencePunch()
                            } catch (e: Exception) {
                                e.message?.removePrefix("Exception: ") ?: "预检失败"
                            }
                            testing = false
                        }
                    },
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                    enabled = !testing,
                ) { Text(if (testing) "预检中…" else "测试课堂打卡") }
                testResult?.let { FeatureHint(it) }
            }
            when {
                !loading && session.ktkqCourses.isEmpty() && session.loadHint == null ->
                    FeatureHint("本周暂无课程")
                session.ktkqCourses.isNotEmpty() -> session.ktkqCourses.forEach { course ->
                    WeekCourseCard(course) { slot ->
                        nav.navigate(session.prepareKtkqSign(slot = slot, week = week))
                    }
                }
            }
            FeatureBottomSpace()
        }
    }
}

@Composable
private fun WeekCourseCard(course: KtkqCourse, onSlot: (SignActivity) -> Unit) {
    val cs = MiuixTheme.colorScheme
    Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Text(
                buildString {
                    append(course.name)
                    if (course.code.isNotEmpty()) append(" (${course.code})")
                },
                fontWeight = FontWeight.SemiBold,
            )
            val meta = listOf(
                course.credits.takeIf { it.isNotBlank() }?.let { "$it 学分" },
                course.hours.takeIf { it.isNotBlank() }?.let { "$it 学时" },
            ).filterNotNull()
            if (meta.isNotEmpty()) {
                Text(meta.joinToString(" · "), color = cs.onSurfaceVariantSummary, fontSize = 13.sp)
            }
            if (course.slots.isNotEmpty()) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                course.slots.forEachIndexed { i, slot ->
                    if (i > 0) HorizontalDivider()
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSlot(slot) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            if (slot.className.isNotEmpty()) {
                                Text(slot.className, color = cs.primary, fontSize = 13.sp)
                            }
                            Text(
                                listOf(
                                    slot.startTime,
                                    slot.classroom,
                                    if (slot.startNode > 0) "节次 ${slot.startNode}~${slot.endNode}" else "",
                                ).filter { it.isNotBlank() }.joinToString("  "),
                                color = cs.onSurfaceVariantSummary,
                                fontSize = 13.sp,
                            )
                        }
                        Text("›", color = cs.onSurfaceVariantSummary, fontSize = 18.sp)
                    }
                }
            }
        }
    }
}
