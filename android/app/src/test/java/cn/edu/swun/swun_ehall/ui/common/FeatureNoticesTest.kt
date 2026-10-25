package cn.edu.swun.swun_ehall.ui.common

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class FeatureNoticesTest {
    @Test fun repeatedResultsAreDeliveredOncePerOperation() = runBlocking {
        val notices = FeatureNotices()
        try {
            notices.message("已刷新")
            notices.message("已刷新")
            val results = withTimeout(1_000) { notices.events.take(2).toList() }
            assertEquals(listOf(FeatureNotice.Toast("已刷新"), FeatureNotice.Toast("已刷新")), results)
            val next = async(start = CoroutineStart.UNDISPATCHED) { notices.events.first() }
            assertFalse(next.isCompleted)
            next.cancelAndJoin()
        } finally { notices.close() }
    }

    @Test fun detailsAndErrorsRemainReadableAndOrdered() = runBlocking {
        val notices = FeatureNotices()
        try {
            notices.error(" 服务暂不可用 ", "加载失败")
            notices.details("预检结果", "第一行\n第二行")
            val results = withTimeout(1_000) { notices.events.take(2).toList() }
            assertEquals(
                listOf(
                    FeatureNotice.Dialog("加载失败", "服务暂不可用"),
                    FeatureNotice.Dialog("预检结果", "第一行\n第二行"),
                ),
                results,
            )
        } finally { notices.close() }
    }

    @Test fun longOrMultilineResultsUseDialogs() = runBlocking {
        val notices = FeatureNotices()
        try {
            val long = "详细结果".repeat(20)
            notices.message(long)
            notices.message("第一行\n第二行")
            val results = withTimeout(1_000) { notices.events.take(2).toList() }
            assertEquals(
                listOf(FeatureNotice.Dialog("操作结果", long), FeatureNotice.Dialog("操作结果", "第一行\n第二行")),
                results,
            )
        } finally { notices.close() }
    }

    @Test fun blankMessagesProduceNoNotice() = runBlocking {
        val notices = FeatureNotices()
        try {
            notices.message(" ")
            notices.details("结果", "\n\t")
            notices.error("")
            val next = async(start = CoroutineStart.UNDISPATCHED) { notices.events.first() }
            assertFalse(next.isCompleted)
            next.cancelAndJoin()
        } finally { notices.close() }
    }

    @Test fun hiddenScreenDiscardsPendingAndLateResults() = runBlocking {
        val notices = FeatureNotices()
        try {
            notices.error("旧错误")
            notices.message("旧结果")
            notices.pause()
            notices.message("后台完成")
            notices.error("后台错误")
            notices.resume()
            val next = async(start = CoroutineStart.UNDISPATCHED) { notices.events.first() }
            assertFalse(next.isCompleted)
            notices.message("重新刷新")
            assertEquals(FeatureNotice.Toast("重新刷新"), withTimeout(1_000) { next.await() })
        } finally { notices.close() }
    }

    @Test fun oldAccountResultsCannotReachNewScreen() = runBlocking {
        val old = FeatureNotices()
        val current = FeatureNotices()
        try {
            old.message("旧账号结果")
            old.close()
            old.resume()
            old.error("迟到的旧账号错误")
            val next = async(start = CoroutineStart.UNDISPATCHED) { current.events.first() }
            assertFalse(next.isCompleted)
            current.message("当前账号结果")
            assertEquals(FeatureNotice.Toast("当前账号结果"), withTimeout(1_000) { next.await() })
        } finally { old.close(); current.close() }
    }
}
