package cn.edu.swun.swun_ehall.data.http

import cn.edu.swun.swun_ehall.data.model.LeaveApplyForm
import cn.edu.swun.swun_ehall.data.model.LeaveCancelForm
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class StudentClientTest {
    @Test fun parsesLeaveSummaryStudentRulesAndOptions() {
        assertEquals(
            cn.edu.swun.swun_ehall.data.model.LeaveSummary(3, 4.5, 1),
            parseLeaveSummary(JSONObject().put("askLeaveNum", 3).put("askLeaveDays", 4.5).put("unCancelLeaveNum", 1)),
        )
        val student = parseLeaveStudentInfo(
            JSONObject()
                .put("stuName", "测试同学")
                .put("stuNo", "20260001")
                .put("unitName", "计算机学院")
                .put("className", "网络一班")
                .put("stuMobile", "13800000000")
                .put("dorm", "10栋101"),
        )
        assertEquals("测试同学", student.name)
        assertEquals("20260001", student.studentId)
        assertEquals("10栋101", student.dorm)

        val rule = parseLeaveRule(
            JSONObject()
                .put("cntineFlag", 1)
                .put("cntineCnt", 2)
                .put("locaFlag", 1)
                .put("selfieFlag", 1)
                .put("addrFlag", 0)
                .put("hourCnt", 6),
        )
        assertTrue(rule.allowRenew)
        assertTrue(rule.requireLocation)
        assertTrue(rule.requireSelfie)
        assertFalse(rule.requireAddress)
        assertEquals(6, rule.hourCount)

        val options = parseLeaveOptions(
            JSONArray()
                .put(JSONObject().put("code", "1").put("name", "事假"))
                .put(JSONObject().put("code", "").put("name", "忽略"))
                .put(JSONObject().put("code", "2").put("name", "病假")),
        )
        assertEquals(listOf("1", "2"), options.map { it.code })
    }

    @Test fun parsesLeaveRecordsAndApprovalDetail() {
        val records = parseLeaveRecords(
            JSONArray().put(
                JSONObject()
                    .put("id", "row-1")
                    .put("leaveNo", "L-1")
                    .put("apprNo", "A-1")
                    .put("leaveCode", 1)
                    .put("leaveName", "请假")
                    .put("leaveTypeCode", 1)
                    .put("leaveTypeName", "事假")
                    .put("affLeaveTypeCode", 2)
                    .put("affLeaveTypeName", JSONObject.NULL)
                    .put("leaveStartTime", "2026-11-26 08:00:00")
                    .put("leaveEndTime", "2026-11-27 18:00:00")
                    .put("leaveDays", 2)
                    .put("totalLeaveDays", 2.5)
                    .put("leaveReason", "家庭事务")
                    .put("leaveStatusCode", 1)
                    .put("leaveStatusName", "审批中")
                    .put("cancelLeaveButton", 1),
            ),
        )
        val record = records.single()
        assertEquals("L-1", record.leaveNo)
        assertEquals(2, record.specificTypeCode)
        assertEquals("", record.specificTypeName)
        assertEquals(2.5, record.totalDays, 0.001)
        assertTrue(record.canCancel)

        val info = JSONObject().put(
            "takeLeaveVO",
            JSONObject()
                .put("leaveTypeName", "事假")
                .put("affLeaveTypeName", "离蓉回家")
                .put("leaveStartTime", "2026-11-26 08:00:00")
                .put("leaveEndTime", "2026-11-27 18:00:00")
                .put("leaveDays", 2.5)
                .put("leaveReason", "家庭事务")
                .put("addrName", "四川省成都市双流区")
                .put("addrDetail", "测试地址")
                .put("provePics", JSONArray().put("https://example.invalid/proof.jpg")),
        )
        val approvals = JSONArray().put(
            JSONObject()
                .put("title", "辅导员审批")
                .put("flowStatusName", "已通过")
                .put("optRoleName", "辅导员")
                .put("updateName", "张老师")
                .put("updateTime", "2026-11-26 09:00:00")
                .put("flowRemark", JSONObject.NULL)
                .put("litUp", 1),
        )
        val detail = parseLeaveDetail(info, approvals)
        assertEquals("四川省成都市双流区 测试地址", detail.entries.single().address)
        assertEquals(1, detail.entries.single().proofUrls.size)
        assertTrue(detail.approvals.single().active)
        assertEquals("张老师", detail.approvals.single().operatorName)
        assertEquals("", detail.approvals.single().remark)
    }

    @Test fun applyAndCancelBodiesKeepOfficialFieldSet() {
        val apply = leaveApplyBody(
            LeaveApplyForm(
                leaveTypeCode = "1",
                specificTypeCode = "5",
                startTime = "2026-11-26 08:00:00",
                endTime = "2026-11-27 18:00:00",
                days = 2.5,
                reason = "校外实习",
                parentMobile = "13800000000",
                addressCode = "510116",
                addressName = "四川省成都市双流区",
                addressDetail = "测试地址",
                internshipAddress = "实习单位",
                proofUrls = listOf("proof-1", "proof-2"),
            ),
        )
        assertEquals(
            setOf(
                "addrDetail", "internshipAddr", "addrName", "addrCode", "affLeaveTypeCode", "leaveDays",
                "leaveEndTime", "leaveStartTime", "leaveReason", "prntMobile", "leaveTypeCode", "provePics",
            ),
            apply.keys().asSequence().toSet(),
        )
        assertEquals("实习单位", apply.getString("internshipAddr"))
        assertEquals(2, apply.getJSONArray("provePics").length())

        val sick = leaveApplyBody(
            LeaveApplyForm("2", "", "s", "e", 1.0, "病假", "1", "", "", ""),
        )
        assertTrue(sick.has("internshipAddr"))
        assertEquals("", sick.getString("internshipAddr"))

        val cancel = leaveCancelBody(
            LeaveCancelForm("L-1", "s", "e", 1.0, "已返校", "GCJ-02 1, 2", listOf("selfie")),
        )
        assertEquals(
            setOf("elimRemark", "leaveNo", "leaveStartTime", "leaveEndTime", "leaveDays", "locaInfo", "selfiePics"),
            cancel.keys().asSequence().toSet(),
        )
        assertEquals("selfie", cancel.getJSONArray("selfiePics").getString(0))
    }

    @Test fun parsesStudentApiEnvelopeAndFailures() {
        val data = parseStudentApiResponse(
            200,
            JSONObject().put("state", 200).put("isSuccess", true).put("data", JSONObject().put("value", 7)).toString(),
        ) as JSONObject
        assertEquals(7, data.getInt("value"))
        assertNull(parseStudentApiResponse(200, JSONObject().put("state", 200).put("isSuccess", true).put("data", JSONObject.NULL).toString()))

        assertTrue(runCatching { parseStudentApiResponse(401, "") }.exceptionOrNull() is StudentSessionExpiredException)
        assertTrue(
            runCatching {
                parseStudentApiResponse(200, JSONObject().put("state", 401).put("isSuccess", false).put("msg", "令牌过期").toString())
            }.exceptionOrNull() is StudentSessionExpiredException,
        )
        assertEquals(
            "当前不能申请",
            runCatching {
                parseStudentApiResponse(200, JSONObject().put("state", 500).put("isSuccess", false).put("msg", "当前不能申请").toString())
            }.exceptionOrNull()?.message,
        )
        assertEquals("学生管理平台 HTTP 503", runCatching { parseStudentApiResponse(503, "") }.exceptionOrNull()?.message)
        assertEquals("学生管理平台返回了非 JSON 数据", runCatching { parseStudentApiResponse(200, "<html>") }.exceptionOrNull()?.message)
    }
}
