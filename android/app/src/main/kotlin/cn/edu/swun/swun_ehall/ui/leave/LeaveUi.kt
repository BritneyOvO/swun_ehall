package cn.edu.swun.swun_ehall.ui.leave

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import cn.edu.swun.swun_ehall.BuildConfig
import java.io.File
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.theme.MiuixTheme
import top.yukonga.miuix.kmp.window.WindowDialog

internal val leaveDateTimeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

internal data class UploadedLeaveImage(
    val url: String,
    val label: String,
)

internal fun formatLeaveDays(days: Double): String {
    if (!days.isFinite()) return "—"
    val rounded = kotlin.math.round(days)
    return if (kotlin.math.abs(days - rounded) < 0.0001) {
        "${rounded.toInt()} 天"
    } else {
        "${String.format(Locale.US, "%.2f", days).trimEnd('0').trimEnd('.')} 天"
    }
}

internal fun leaveStatusTone(statusCode: Int, primary: Color): Color = when (statusCode) {
    2, 20, 200 -> Color(0xFF2E9E5B)
    -1, 3, 30, 400, 500 -> Color(0xFFD94A4A)
    else -> primary
}

@Composable
internal fun LeaveSelectionField(
    label: String,
    value: String?,
    enabled: Boolean = true,
    emptyText: String = "请选择",
    onClick: () -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    Card(
        modifier = Modifier.padding(top = 8.dp).fillMaxWidth(),
        onClick = { if (enabled) onClick() },
    ) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(label, color = cs.onSurfaceVariantSummary, fontSize = 12.sp)
            Text(
                value ?: if (enabled) emptyText else "请先完成上一步",
                color = if (enabled) cs.onBackground else cs.onSurfaceVariantSummary,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

@Composable
internal fun LeaveOptionDialog(
    title: String,
    options: List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onSelected: (String) -> Unit,
) {
    WindowDialog(show = true, title = title, onDismissRequest = onDismiss) {
        if (options.isEmpty()) {
            Text("暂无可选项", color = MiuixTheme.colorScheme.onSurfaceVariantSummary, modifier = Modifier.padding(16.dp))
        } else {
            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 480.dp)) {
                items(options, key = { it.first }) { option ->
                    Text(
                        option.second,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelected(option.first) }
                            .padding(vertical = 12.dp),
                    )
                }
            }
        }
    }
}

@Composable
internal fun LeaveInfoRow(label: String, value: String, strong: Boolean = false) {
    val cs = MiuixTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = cs.onSurfaceVariantSummary, fontSize = 13.sp, modifier = Modifier.width(92.dp))
        Text(value.ifBlank { "—" }, fontWeight = if (strong) FontWeight.SemiBold else FontWeight.Normal, modifier = Modifier.weight(1f))
    }
}

@Composable
internal fun UploadedImages(
    title: String,
    images: List<UploadedLeaveImage>,
    uploading: Boolean,
    addLabel: String,
    onAdd: () -> Unit,
    onRemove: (UploadedLeaveImage) -> Unit,
) {
    val cs = MiuixTheme.colorScheme
    Card(modifier = Modifier.padding(top = 10.dp).fillMaxWidth()) {
        Column(Modifier.padding(14.dp)) {
            Text(title, fontWeight = FontWeight.SemiBold)
            if (images.isEmpty()) {
                Text("尚未添加", color = cs.onSurfaceVariantSummary, fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp))
            } else {
                images.forEachIndexed { index, image ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
                        Text("${index + 1}. ${image.label}", modifier = Modifier.weight(1f), fontSize = 13.sp)
                        Text(
                            "移除",
                            color = cs.primary,
                            fontSize = 13.sp,
                            modifier = Modifier.clickable { onRemove(image) }.padding(start = 12.dp),
                        )
                    }
                }
            }
            TextButton(
                text = if (uploading) "正在上传…" else addLabel,
                onClick = onAdd,
                enabled = !uploading,
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            )
        }
    }
}

@Composable
internal fun LeaveConfirmDialog(
    title: String,
    summary: String,
    confirmText: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    WindowDialog(show = true, title = title, onDismissRequest = onDismiss) {
        Text(summary, modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp))
        Row(Modifier.fillMaxWidth().padding(top = 16.dp)) {
            TextButton("返回检查", onDismiss, modifier = Modifier.weight(1f))
            Spacer(Modifier.width(12.dp))
            TextButton(confirmText, onConfirm, modifier = Modifier.weight(1f))
        }
    }
}

internal fun showLeaveDateTimePicker(
    context: Context,
    initial: LocalDateTime,
    onPicked: (LocalDateTime) -> Unit,
) {
    DatePickerDialog(
        context,
        { _, year, month, day ->
            TimePickerDialog(
                context,
                { _, hour, minute -> onPicked(LocalDateTime.of(year, month + 1, day, hour, minute)) },
                initial.hour,
                initial.minute,
                true,
            ).show()
        },
        initial.year,
        initial.monthValue - 1,
        initial.dayOfMonth,
    ).show()
}

internal fun createLeavePhotoUri(context: Context): Uri {
    val directory = File(context.cacheDir, "leave").apply { mkdirs() }
    val file = File.createTempFile("selfie_", ".jpg", directory)
    return FileProvider.getUriForFile(context, "${BuildConfig.APPLICATION_ID}.fileprovider", file)
}
