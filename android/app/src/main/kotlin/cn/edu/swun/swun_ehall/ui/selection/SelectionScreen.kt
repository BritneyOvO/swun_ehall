package cn.edu.swun.swun_ehall.ui.selection

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import cn.edu.swun.swun_ehall.data.model.XkCourse
import cn.edu.swun.swun_ehall.data.session.Session
import cn.edu.swun.swun_ehall.ui.common.BackNav
import cn.edu.swun.swun_ehall.ui.common.FeatureBottomSpace
import cn.edu.swun.swun_ehall.ui.common.FeatureColumn
import cn.edu.swun.swun_ehall.ui.common.FeatureHint
import cn.edu.swun.swun_ehall.ui.common.FeatureNoticeHost
import cn.edu.swun.swun_ehall.ui.common.rememberFeatureNotices
import cn.edu.swun.swun_ehall.ui.common.FeatureLoadingPage
import cn.edu.swun.swun_ehall.ui.common.FeatureSection
import cn.edu.swun.swun_ehall.ui.common.FeatureStatus
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.HorizontalDivider
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TabRow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun SelectionScreen(session: Session, nav: NavHostController) {
    var loading by remember { mutableStateOf(true) }
    var tab by remember { mutableIntStateOf(0) }
    var keyword by remember { mutableStateOf("") }
    var query by remember { mutableStateOf("") }
    var ready by remember(session.studentId, session.demoMode) { mutableStateOf(false) }
    var searchVersion by remember { mutableIntStateOf(0) }
    val notices = rememberFeatureNotices(session.studentId, session.demoMode)
    FeatureNoticeHost(notices)
    var picking by remember { mutableStateOf<XkCourse?>(null) }
    var classes by remember { mutableStateOf<List<XkCourse>>(emptyList()) }
    val scope = rememberCoroutineScope()
    val cs = MiuixTheme.colorScheme
    LaunchedEffect(session.loggedIn, session.demoMode) {
        ready = false
        loading = true
        try {
            session.refreshXk()
            session.xkError?.let { notices.error(it, "选课暂不可用") }
            ready = session.xkError == null
        } finally {
            loading = false
        }
    }
    LaunchedEffect(ready, tab, query, searchVersion) {
        if (!ready) return@LaunchedEffect
        val round = session.xkRounds.getOrNull(tab) ?: return@LaunchedEffect
        loading = true
        picking = null
        classes = emptyList()
        try {
            session.loadXkCourses(round, query)
            session.xkError?.let { notices.error(it, "课程加载失败") }
        } finally {
            loading = false
        }
    }
    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = "选课",
                navigationIcon = { BackNav { nav.popBackStack() } },
            )
        },
    ) { padding ->
        if (loading) {
            FeatureLoadingPage(padding)
            return@Scaffold
        }
        FeatureColumn(padding) {
            if (session.xkRounds.size > 1) {
                TabRow(
                    tabs = session.xkRounds.map { it.name.ifBlank { it.kklxdm } },
                    selectedTabIndex = tab.coerceAtMost(session.xkRounds.lastIndex),
                    onTabSelected = { tab = it },
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    TextField(
                        value = keyword,
                        onValueChange = { keyword = it },
                        label = "搜索课程名",
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = {
                        query = keyword.trim()
                        searchVersion++
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("搜索")
                    }
                }
            }
            when {
                session.xkRounds.isEmpty() -> FeatureHint(if (session.xkError == null) "当前没有开放的选课轮次" else "选课数据暂未加载")
                session.xkCourses.isEmpty() -> FeatureHint(if (session.xkError == null) "没有匹配的课程" else "课程暂未加载，请重新搜索")
                else -> Card(modifier = Modifier.padding(top = 12.dp).fillMaxWidth()) {
                    session.xkCourses.forEachIndexed { i, c ->
                        if (i > 0) HorizontalDivider()
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            onClick = {
                                scope.launch {
                                    val round = session.xkRounds.getOrNull(tab) ?: return@launch
                                    picking = c
                                    classes = try {
                                        session.loadXkClasses(round, c)
                                    } catch (e: Exception) {
                                        if (e is CancellationException) throw e
                                        notices.error(e.message ?: "教学班加载失败")
                                        picking = null
                                        emptyList()
                                    }
                                }
                            },
                        ) {
                            Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                                Text(c.name)
                                Text(
                                    buildString {
                                        if (c.credit.isNotEmpty()) append("${c.credit} 学分")
                                        if (c.teacher.isNotEmpty()) {
                                            if (isNotEmpty()) append(" · ")
                                            append(c.teacher)
                                        }
                                    },
                                    color = cs.onSurfaceVariantSummary,
                                )
                                if (c.time.isNotEmpty()) Text(c.time, color = cs.onSurfaceVariantSummary)
                                FeatureStatus(
                                    when {
                                        c.remain < 0 -> "余量未知"
                                        c.remain == 0 -> "已满"
                                        else -> "余 ${c.remain}"
                                    },
                                    if (c.remain == 0) cs.error else cs.primary,
                                )
                            }
                        }
                    }
                }
            }
            picking?.let { course ->
                FeatureSection("教学班 · ${course.name}")
                Card(modifier = Modifier.fillMaxWidth()) {
                    if (classes.isEmpty()) {
                        Text("没有教学班", modifier = Modifier.padding(16.dp), color = cs.onSurfaceVariantSummary)
                    } else {
                        classes.forEachIndexed { i, jxb ->
                            if (i > 0) HorizontalDivider()
                            Column(Modifier.padding(16.dp)) {
                                Text(jxb.jxbName.ifBlank { jxb.name })
                                Text(
                                    "${jxb.teacher} · ${jxb.time}",
                                    color = cs.onSurfaceVariantSummary,
                                )
                                FeatureStatus(
                                    if (jxb.remain < 0) "余量未知" else "余 ${jxb.remain}",
                                    if (jxb.remain == 0) cs.error else cs.primary,
                                )
                                Spacer(Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val round = session.xkRounds.getOrNull(tab) ?: return@launch
                                            try {
                                                notices.details("选课结果", session.submitXk(round, course, jxb))
                                            } catch (e: Exception) {
                                                if (e is CancellationException) throw e
                                                notices.error(e.message ?: "选课失败", "选课未完成")
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("提交选课") }
                            }
                        }
                    }
                }
            }
            FeatureBottomSpace()
        }
    }
}
