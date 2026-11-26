package cn.edu.swun.swun_ehall.data.http

import android.util.Base64
import cn.edu.swun.swun_ehall.data.model.LeaveApplyForm
import cn.edu.swun.swun_ehall.data.model.LeaveApprovalStep
import cn.edu.swun.swun_ehall.data.model.LeaveCancelForm
import cn.edu.swun.swun_ehall.data.model.LeaveCancelPreparation
import cn.edu.swun.swun_ehall.data.model.LeaveDetail
import cn.edu.swun.swun_ehall.data.model.LeaveEntry
import cn.edu.swun.swun_ehall.data.model.LeaveOption
import cn.edu.swun.swun_ehall.data.model.LeaveRecord
import cn.edu.swun.swun_ehall.data.model.LeaveRule
import cn.edu.swun.swun_ehall.data.model.LeaveStudentInfo
import cn.edu.swun.swun_ehall.data.model.LeaveSummary
import org.json.JSONArray
import org.json.JSONObject

const val K_STUDENT = "https://zxtjfx.swun.edu.cn"
const val K_STUDENT_SERVICE = "$K_STUDENT/warnvisit-api/authority/noToken/casLogin/swun-stu-manage-platform-mobile"
const val K_STUDENT_APP = "$K_STUDENT/swun-stu-manage-platform-mobile/#/leave/apply"

class StudentSessionExpiredException(message: String = "学生管理平台登录已过期") : Exception(message)

class StudentClient(private val rs: RsGateway) {
    private var cas: CasClient? = null
    private var ready = false

    fun attachCas(client: CasClient) {
        cas = client
    }

    fun reset() {
        ready = false
    }

    fun loginWithCas(client: CasClient = cas ?: error("统一身份未初始化")) {
        cas = client
        val ticket = client.ticketFor(K_STUDENT_SERVICE)
        rs.open(ticket, RsWait.Ready, RsSites.STUDENT)
        rs.open(K_STUDENT_APP, RsWait.Ready, RsSites.STUDENT)
        api("GET", "/warnvisit-api/authority/getUserByToken")
        ready = true
    }

    fun ensureReady() {
        if (!ready) loginWithCas()
    }

    fun leaveSummary(): LeaveSummary = parseLeaveSummary(
        api("GET", "/warnvisit-api/askLeave/getAskLeaveSummary") as? JSONObject ?: JSONObject(),
    )

    fun leaveRecords(): List<LeaveRecord> {
        val data = api(
            "GET",
            "/warnvisit-api/askLeave/getAskLeave",
            JSONObject().put("page", 1).put("pageSize", 100),
        ) as? JSONObject ?: return emptyList()
        return parseLeaveRecords(data.optJSONArray("list") ?: JSONArray())
    }

    fun studentInfo(): LeaveStudentInfo = parseLeaveStudentInfo(
        api("GET", "/warnvisit-api/askLeave/getStuInfo") as? JSONObject ?: JSONObject(),
    )

    fun leaveTypes(): List<LeaveOption> = options("zy_leave_type")

    fun specificLeaveTypes(): List<LeaveOption> = options("swnu_aff_leave_type")

    fun leaveRule(): LeaveRule = parseLeaveRule(
        api("GET", "/warnvisit-api/leaveConfig/getRuleConfig") as? JSONObject ?: JSONObject(),
    )

    fun checkCanApply() {
        api("GET", "/warnvisit-api/askLeave/checkTakeLeave")
    }

    fun leaveDays(startTime: String, endTime: String): Double {
        val value = api(
            "GET",
            "/warnvisit-api/common/getAskLeaveDays",
            JSONObject().put("leaveStartTime", startTime).put("leaveEndTime", endTime),
        )
        return jsonDouble(value)
    }

    fun leaveDetail(leaveNo: String): LeaveDetail {
        val info = api(
            "GET",
            "/warnvisit-api/askLeave/getAskLeaveDetail",
            JSONObject().put("leaveNo", leaveNo),
        ) as? JSONObject ?: JSONObject()
        val approvals = api(
            "GET",
            "/warnvisit-api/askLeave/getApprovalDetail",
            JSONObject().put("leaveNo", leaveNo),
        ) as? JSONArray ?: JSONArray()
        return parseLeaveDetail(info, approvals)
    }

    fun createLeave(form: LeaveApplyForm): String {
        api("POST", "/warnvisit-api/askLeave/createTakeLeave", leaveApplyBody(form))
        return "请假申请已提交"
    }

    fun cancelPreparation(leaveNo: String): LeaveCancelPreparation {
        val range = api(
            "GET",
            "/warnvisit-api/askLeave/getRenewStartEnd",
            JSONObject().put("leaveNo", leaveNo),
        ) as? JSONObject ?: JSONObject()
        val start = range.optCleanString("takeLeaveStartTime")
        val end = range.optCleanString("previousEndTime")
        val days = jsonDouble(
            api(
                "GET",
                "/warnvisit-api/common/getTotalLeaveDays",
                JSONObject().put("leaveStartTime", start).put("leaveEndTime", end),
            ),
        )
        return LeaveCancelPreparation(leaveNo, start, end, days)
    }

    fun createCancel(form: LeaveCancelForm): String {
        api("POST", "/warnvisit-api/askLeave/createCancelLeave", leaveCancelBody(form))
        return "销假申请已提交"
    }

    fun locationAllowed(latitude: Double, longitude: Double): Boolean {
        val value = api(
            "GET",
            "/warnvisit-api/common/getCorrectAddress",
            JSONObject().put("latitude", latitude).put("longitude", longitude),
        )
        return when (value) {
            is Boolean -> value
            is Number -> value.toInt() != 0
            else -> value?.toString()?.equals("true", true) == true || value?.toString() == "1"
        }
    }

    fun uploadImage(bytes: ByteArray, mimeType: String, fileName: String): String {
        if (bytes.isEmpty()) error("图片为空")
        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        val script = """
            var encoded = ${JSONObject.quote(encoded)};
            var binary = atob(encoded);
            var bytes = new Uint8Array(binary.length);
            for (var i = 0; i < binary.length; i++) bytes[i] = binary.charCodeAt(i);
            var form = new FormData();
            form.append('multipartFile', new Blob([bytes], {type: ${JSONObject.quote(mimeType)}}), ${JSONObject.quote(fileName)});
            return fetch('/warnvisit-api/common/upload', {
              method: 'POST',
              headers: {'Accept': 'application/json, text/plain, */*', 'zbp-role-no': 'xs'},
              body: form
            }).then(async function(response) {
              return JSON.stringify({http: response.status, body: await response.text()});
            });
        """.trimIndent()
        val raw = rs.evalJsAsync(RsSites.STUDENT.host, script, 120_000)
        val data = unwrap(raw)
        val url = (data as? JSONObject)?.optString("url").orEmpty()
        if (url.isBlank()) error("证明材料上传失败")
        return url
    }

    private fun options(dimClass: String): List<LeaveOption> {
        val data = api(
            "GET",
            "/warnvisit-api/common/getDim",
            JSONObject().put("dimClass", dimClass),
        ) as? JSONArray ?: return emptyList()
        return parseLeaveOptions(data)
    }

    private fun api(method: String, path: String, params: JSONObject = JSONObject()): Any? {
        val paramsJson = JSONObject.quote(params.toString())
        val pathJson = JSONObject.quote(path)
        val upper = method.uppercase()
        val script = if (upper == "GET") {
            """
            var values = JSON.parse($paramsJson);
            var url = new URL($pathJson, location.origin);
            Object.keys(values).forEach(function(key) {
              var value = values[key];
              if (value == null) return;
              if (Array.isArray(value)) value.forEach(function(item) { url.searchParams.append(key, String(item)); });
              else url.searchParams.set(key, String(value));
            });
            return fetch(url.toString(), {
              method: 'GET',
              headers: {'Accept': 'application/json, text/plain, */*', 'zbp-role-no': 'xs'}
            }).then(async function(response) {
              return JSON.stringify({http: response.status, body: await response.text()});
            });
            """.trimIndent()
        } else {
            """
            var values = JSON.parse($paramsJson);
            return fetch($pathJson, {
              method: ${JSONObject.quote(upper)},
              headers: {
                'Accept': 'application/json, text/plain, */*',
                'Content-Type': 'application/json;charset=UTF-8',
                'zbp-role-no': 'xs'
              },
              body: JSON.stringify(values)
            }).then(async function(response) {
              return JSON.stringify({http: response.status, body: await response.text()});
            });
            """.trimIndent()
        }
        val raw = rs.evalJsAsync(RsSites.STUDENT.host, script, 110_000)
        return unwrap(raw)
    }

    private fun unwrap(raw: String): Any? {
        val transport = runCatching { JSONObject(raw) }.getOrElse { error("学生管理平台响应异常") }
        transport.optString("__swunError").takeIf { it.isNotBlank() }?.let { error(it) }
        return try {
            parseStudentApiResponse(transport.optInt("http", 0), transport.optString("body"))
        } catch (e: StudentSessionExpiredException) {
            ready = false
            throw e
        }
    }
}

internal fun leaveApplyBody(form: LeaveApplyForm): JSONObject = JSONObject()
    .put("addrDetail", form.addressDetail)
    .put("internshipAddr", form.internshipAddress.takeIf { form.specificTypeCode == "5" }.orEmpty())
    .put("addrName", form.addressName)
    .put("addrCode", form.addressCode)
    .put("affLeaveTypeCode", form.specificTypeCode)
    .put("leaveDays", form.days)
    .put("leaveEndTime", form.endTime)
    .put("leaveStartTime", form.startTime)
    .put("leaveReason", form.reason)
    .put("prntMobile", form.parentMobile)
    .put("leaveTypeCode", form.leaveTypeCode)
    .put("provePics", JSONArray(form.proofUrls))

internal fun leaveCancelBody(form: LeaveCancelForm): JSONObject = JSONObject()
    .put("elimRemark", form.remark)
    .put("leaveNo", form.leaveNo)
    .put("leaveStartTime", form.startTime)
    .put("leaveEndTime", form.endTime)
    .put("leaveDays", form.days)
    .put("locaInfo", form.location)
    .put("selfiePics", JSONArray(form.selfieUrls))

internal fun parseStudentApiResponse(status: Int, body: String): Any? {
    if (status == 401) throw StudentSessionExpiredException()
    if (status !in 200..299) error("学生管理平台 HTTP $status")
    val envelope = runCatching { JSONObject(body) }.getOrElse { error("学生管理平台返回了非 JSON 数据") }
    val state = envelope.optInt("state", status)
    if (state == 401) {
        throw StudentSessionExpiredException(envelope.optString("msg").ifBlank { "学生管理平台登录已过期" })
    }
    if (state != 200 || !envelope.optBoolean("isSuccess", state == 200)) {
        error(envelope.optString("msg").ifBlank { envelope.optString("errorMsg").ifBlank { "学生管理平台请求失败" } })
    }
    return envelope.opt("data").takeUnless { it == JSONObject.NULL }
}

fun parseLeaveSummary(raw: JSONObject): LeaveSummary = LeaveSummary(
    count = raw.optInt("askLeaveNum"),
    days = raw.optDouble("askLeaveDays", 0.0),
    pendingCancel = raw.optInt("unCancelLeaveNum"),
)

fun parseLeaveOptions(raw: JSONArray): List<LeaveOption> = buildList {
    for (i in 0 until raw.length()) {
        val row = raw.optJSONObject(i) ?: continue
        val code = row.optCleanString("code").trim()
        val name = row.optCleanString("name").trim()
        if (code.isNotEmpty() && name.isNotEmpty()) add(LeaveOption(code, name))
    }
}

fun parseLeaveStudentInfo(raw: JSONObject): LeaveStudentInfo = LeaveStudentInfo(
    name = raw.optCleanString("stuName"),
    studentId = raw.optCleanString("stuNo"),
    college = raw.optCleanString("unitName"),
    klass = raw.optCleanString("className"),
    mobile = raw.optCleanString("stuMobile"),
    dorm = raw.optCleanString("dorm"),
)

fun parseLeaveRule(raw: JSONObject): LeaveRule = LeaveRule(
    allowRenew = raw.optInt("cntineFlag") == 1,
    maxRenewCount = raw.optInt("cntineCnt"),
    requireLocation = raw.optInt("locaFlag") == 1,
    requireSelfie = raw.optInt("selfieFlag") == 1,
    requireAddress = raw.optInt("addrFlag") == 1,
    hourCount = raw.optInt("hourCnt"),
)

fun parseLeaveRecords(raw: JSONArray): List<LeaveRecord> = buildList {
    for (i in 0 until raw.length()) {
        val row = raw.optJSONObject(i) ?: continue
        add(
            LeaveRecord(
                id = row.optCleanString("id"),
                leaveNo = row.optCleanString("leaveNo"),
                approvalNo = row.optCleanString("apprNo"),
                leaveCode = row.optInt("leaveCode"),
                leaveName = row.optCleanString("leaveName"),
                leaveTypeCode = row.optInt("leaveTypeCode"),
                leaveTypeName = row.optCleanString("leaveTypeName"),
                specificTypeCode = row.optNullableInt("affLeaveTypeCode"),
                specificTypeName = row.optCleanString("affLeaveTypeName"),
                startTime = row.optCleanString("leaveStartTime"),
                endTime = row.optCleanString("leaveEndTime"),
                days = row.optDouble("leaveDays", 0.0),
                totalDays = row.optDouble("totalLeaveDays", row.optDouble("leaveDays", 0.0)),
                reason = row.optCleanString("leaveReason"),
                statusCode = row.optInt("leaveStatusCode"),
                statusName = row.optCleanString("leaveStatusName"),
                canWithdraw = row.optInt("cancelButton") == 1,
                canEdit = row.optInt("modifyButton") == 1,
                canRenew = row.optInt("renewLeaveButton") == 1,
                canCancel = row.optInt("cancelLeaveButton") == 1,
            ),
        )
    }
}

fun parseLeaveDetail(info: JSONObject, approvals: JSONArray): LeaveDetail {
    val entries = buildList {
        info.optJSONObject("takeLeaveVO")?.let { add(parseLeaveEntry(it, 1)) }
        val renew = info.optJSONArray("renewLeaveVO") ?: JSONArray()
        for (i in 0 until renew.length()) renew.optJSONObject(i)?.let { add(parseLeaveEntry(it, 2)) }
        info.optJSONObject("cancelLeaveVO")?.let { add(parseLeaveEntry(it, 3)) }
    }
    val steps = buildList {
        for (i in 0 until approvals.length()) {
            val row = approvals.optJSONObject(i) ?: continue
            add(
                LeaveApprovalStep(
                    title = row.optCleanString("title"),
                    statusName = row.optCleanString("flowStatusName"),
                    roleName = row.optCleanString("optRoleName"),
                    operatorName = row.optCleanString("updateName").ifBlank { row.optCleanString("createName") },
                    time = row.optCleanString("updateTime").ifBlank { row.optCleanString("createTime") },
                    remark = row.optCleanString("flowRemark"),
                    active = row.optInt("litUp") == 1,
                ),
            )
        }
    }
    return LeaveDetail(entries, steps)
}

private fun parseLeaveEntry(raw: JSONObject, fallbackCode: Int): LeaveEntry {
    val proof = raw.optJSONArray("provePics")?.let { array ->
        buildList {
            for (i in 0 until array.length()) {
                array.opt(i)
                    ?.takeUnless { it == JSONObject.NULL }
                    ?.toString()
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
                    ?.let(::add)
            }
        }
    } ?: raw.optCleanString("proveMats").split(',').map { it.trim() }.filter { it.startsWith("http") }
    return LeaveEntry(
        leaveCode = raw.optInt("leaveType", fallbackCode),
        leaveTypeName = raw.optCleanString("leaveTypeName"),
        specificTypeName = raw.optCleanString("affLeaveTypeName"),
        startTime = raw.optCleanString("leaveStartTime").ifBlank { raw.optCleanString("takeLeaveStartTime") },
        endTime = raw.optCleanString("leaveEndTime").ifBlank { raw.optCleanString("previousEndTime") },
        days = raw.optDouble("leaveDays", 0.0),
        reason = raw.optCleanString("leaveReason").ifBlank { raw.optCleanString("elimRemark") },
        statusName = raw.optCleanString("leaveStatusName"),
        address = listOf(raw.optCleanString("addrName"), raw.optCleanString("addrDetail"))
            .filter { it.isNotBlank() }.joinToString(" "),
        proofUrls = proof,
    )
}

private fun JSONObject.optNullableInt(key: String): Int? {
    if (!has(key) || isNull(key)) return null
    return opt(key)?.toString()?.toIntOrNull()
}

private fun JSONObject.optCleanString(key: String): String {
    if (!has(key) || isNull(key)) return ""
    return opt(key)?.toString()?.trim()?.takeUnless { it.equals("null", ignoreCase = true) }.orEmpty()
}

private fun jsonDouble(value: Any?): Double = when (value) {
    is Number -> value.toDouble()
    is JSONObject -> value.optDouble("leaveDays", value.optDouble("days", 0.0))
    else -> value?.toString()?.toDoubleOrNull() ?: 0.0
}
