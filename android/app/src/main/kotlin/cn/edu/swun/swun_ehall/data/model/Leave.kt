package cn.edu.swun.swun_ehall.data.model

data class LeaveSummary(
    val count: Int = 0,
    val days: Double = 0.0,
    val pendingCancel: Int = 0,
)

data class LeaveOption(
    val code: String,
    val name: String,
)

data class LeaveStudentInfo(
    val name: String = "",
    val studentId: String = "",
    val college: String = "",
    val klass: String = "",
    val mobile: String = "",
    val dorm: String = "",
)

data class LeaveRule(
    val allowRenew: Boolean = false,
    val maxRenewCount: Int = 0,
    val requireLocation: Boolean = false,
    val requireSelfie: Boolean = false,
    val requireAddress: Boolean = false,
    val hourCount: Int = 0,
)

data class LeaveRecord(
    val id: String,
    val leaveNo: String,
    val approvalNo: String,
    val leaveCode: Int,
    val leaveName: String,
    val leaveTypeCode: Int,
    val leaveTypeName: String,
    val specificTypeCode: Int?,
    val specificTypeName: String,
    val startTime: String,
    val endTime: String,
    val days: Double,
    val totalDays: Double,
    val reason: String,
    val statusCode: Int,
    val statusName: String,
    val canWithdraw: Boolean,
    val canEdit: Boolean,
    val canRenew: Boolean,
    val canCancel: Boolean,
)

data class LeaveEntry(
    val leaveCode: Int,
    val leaveTypeName: String,
    val specificTypeName: String,
    val startTime: String,
    val endTime: String,
    val days: Double,
    val reason: String,
    val statusName: String,
    val address: String = "",
    val proofUrls: List<String> = emptyList(),
)

data class LeaveApprovalStep(
    val title: String,
    val statusName: String,
    val roleName: String,
    val operatorName: String,
    val time: String,
    val remark: String,
    val active: Boolean,
)

data class LeaveDetail(
    val entries: List<LeaveEntry> = emptyList(),
    val approvals: List<LeaveApprovalStep> = emptyList(),
)

data class LeaveApplyForm(
    val leaveTypeCode: String,
    val specificTypeCode: String,
    val startTime: String,
    val endTime: String,
    val days: Double,
    val reason: String,
    val parentMobile: String,
    val addressCode: String,
    val addressName: String,
    val addressDetail: String,
    val internshipAddress: String = "",
    val proofUrls: List<String> = emptyList(),
)

data class LeaveCancelPreparation(
    val leaveNo: String,
    val startTime: String,
    val endTime: String,
    val days: Double,
)

data class LeaveCancelForm(
    val leaveNo: String,
    val startTime: String,
    val endTime: String,
    val days: Double,
    val remark: String,
    val location: String = "",
    val selfieUrls: List<String> = emptyList(),
)
