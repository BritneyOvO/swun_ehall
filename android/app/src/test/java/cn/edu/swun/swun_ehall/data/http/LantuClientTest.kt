package cn.edu.swun.swun_ehall.data.http

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LantuClientTest {
    @Test fun expiredCourseSessionWithSuccessfulHttpAndEmptyBodyIsRejected() {
        for (body in listOf("", " \n\t")) {
            assertTrue(runCatching { parseLantuResponse(200, body, authenticated = true) }
                .exceptionOrNull() is LantuSessionExpiredException)
        }
    }

    @Test fun invalidTokenInsideGenericServerErrorIsRecognized() {
        val response = """{"msgState":0,"msg":"系统错误,请联系管理员!","msgContent":"接口发生错误:invalid token"}"""
        val failure = runCatching { parseLantuResponse(200, response, authenticated = true) }.exceptionOrNull()
        assertTrue(failure is LantuSessionExpiredException)
        assertEquals("蓝图登录已过期，请重新登录", failure?.message)
    }

    @Test fun authenticationHttpFailureIsRecognized() {
        assertTrue(runCatching { parseLantuResponse(401, "", authenticated = true) }
            .exceptionOrNull() is LantuSessionExpiredException)
    }

    @Test fun validEmptyCourseListRemainsSuccessful() {
        val result = parseLantuResponse(200, """{"msgState":1,"courseList":[],"curWeek":13}""", authenticated = true)
        assertEquals(0, result.getJSONArray("courseList").length())
        assertEquals(13, result.getInt("curWeek"))
    }

    @Test fun successfulCourseResponseRetainsCoursesAndCalendar() {
        val response = """{"msgState":1,"courseList":[{"kcmc":"测试课程","skxq":4,"skjc":1,"cxjc":2,"skzc":"1111111111111111"}],"curWeek":13,"totalWeek":16}"""
        val result = parseLantuResponse(200, response, authenticated = true)
        assertEquals("测试课程", LantuClient().mapLessons(result).single().name)
        assertEquals(13, result.getInt("curWeek"))
        assertEquals(16, result.getInt("totalWeek"))
    }

    @Test fun networkAndBusinessFailuresDoNotTriggerSessionRenewal() {
        val httpFailure = runCatching { parseLantuResponse(503, "", authenticated = true) }.exceptionOrNull()
        assertFalse(httpFailure is LantuSessionExpiredException)
        assertEquals("蓝图请求失败 HTTP 503", httpFailure?.message)
        val businessFailure = runCatching {
            parseLantuResponse(200, """{"msgState":0,"msg":"课程服务维护中"}""", authenticated = true)
        }.exceptionOrNull()
        assertFalse(businessFailure is LantuSessionExpiredException)
        assertEquals("课程服务维护中", businessFailure?.message)
    }

    @Test fun emptyPublicResponseIsNotAnExpiredAuthenticatedSession() {
        val failure = runCatching { parseLantuResponse(200, "", authenticated = false) }.exceptionOrNull()
        assertFalse(failure is LantuSessionExpiredException)
        assertEquals("蓝图返回了空响应，请重试", failure?.message)
    }
}
