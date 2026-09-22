package cn.edu.swun.swun_ehall.ui.mine

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import cn.edu.swun.swun_ehall.data.session.Session
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.min
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back

@Composable
fun AvatarCropScreen(session: Session, onClose: () -> Unit) {
    val context = LocalContext.current
    val path = session.pendingCropPath
    val src = remember(path) { path?.let { decodeSampledBitmap(File(it), 1080) } }
    BackHandler { onClose() }
    if (src == null) {
        androidx.compose.runtime.LaunchedEffect(Unit) { onClose() }
        Box(Modifier.fillMaxSize().background(Color.Black))
        return
    }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var viewSize by remember { mutableFloatStateOf(1f) }
    var busy by remember { mutableStateOf(false) }
    Column(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        SmallTopAppBar(
            title = "裁剪头像",
            color = Color.Black,
            titleColor = Color.White,
            navigationIcon = {
                IconButton(onClick = onClose) {
                    Icon(MiuixIcons.Back, contentDescription = "返回", tint = Color.White)
                }
            },
        )
        BoxWithConstraints(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center,
        ) {
            val sidePx = with(LocalDensity.current) { minOf(maxWidth, maxHeight).toPx() }
            if (viewSize != sidePx) viewSize = sidePx
            val sideDp = with(LocalDensity.current) { sidePx.toDp() }
            Box(
                Modifier
                    .size(sideDp)
                    .pointerInput(src) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            val next = (scale * zoom).coerceIn(1f, 4f)
                            val maxPan = (next - 1f) * sidePx / 2f
                            scale = next
                            offset = Offset(
                                (offset.x + pan.x).coerceIn(-maxPan, maxPan),
                                (offset.y + pan.y).coerceIn(-maxPan, maxPan),
                            )
                        }
                    },
            ) {
                Image(
                    bitmap = src.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        },
                )
                Canvas(Modifier.fillMaxSize()) {
                    val hole = Path().apply {
                        addOval(Rect(Offset.Zero, Size(size.width, size.height)))
                    }
                    clipPath(hole, ClipOp.Difference) {
                        drawRect(Color.Black.copy(alpha = 0.45f))
                    }
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            TextButton(
                text = "取消",
                onClick = onClose,
                modifier = Modifier.weight(1f),
                enabled = !busy,
            )
            Spacer(Modifier.width(12.dp))
            Button(
                onClick = {
                    if (busy) return@Button
                    busy = true
                    try {
                        val cropped = cropAvatar(src, scale, offset, viewSize)
                        session.setLocalAvatar(cropped)
                        Toast.makeText(context, "头像已更新", Toast.LENGTH_SHORT).show()
                        onClose()
                    } catch (e: Exception) {
                        busy = false
                        Toast.makeText(context, e.message ?: "裁剪失败", Toast.LENGTH_SHORT).show()
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !busy,
                colors = ButtonDefaults.buttonColorsPrimary(),
            ) { Text(if (busy) "处理中" else "完成") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

internal fun decodeSampledBitmap(file: File, maxSide: Int): Bitmap? {
    if (!file.exists()) return null
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.absolutePath, bounds)
    val w = bounds.outWidth
    val h = bounds.outHeight
    if (w <= 0 || h <= 0) return null
    var sample = 1
    while (w / sample > maxSide || h / sample > maxSide) sample *= 2
    return BitmapFactory.decodeFile(
        file.absolutePath,
        BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        },
    )
}

internal fun cropAvatar(src: Bitmap, scale: Float, offset: Offset, view: Float): ByteArray {
    val side = view.coerceAtLeast(1f)
    val cover = max(side / src.width, side / src.height)
    val t = (cover * scale).coerceAtLeast(0.001f)
    val vis = side / t
    val cx = src.width / 2f - offset.x / t
    val cy = src.height / 2f - offset.y / t
    var left = (cx - vis / 2f).toInt()
    var top = (cy - vis / 2f).toInt()
    var size = vis.toInt().coerceAtLeast(1)
    left = left.coerceIn(0, (src.width - 1).coerceAtLeast(0))
    top = top.coerceIn(0, (src.height - 1).coerceAtLeast(0))
    size = min(size, min(src.width - left, src.height - top)).coerceAtLeast(1)
    val square = Bitmap.createBitmap(src, left, top, size, size)
    val out = if (square.width == 512 && square.height == 512) {
        square
    } else {
        Bitmap.createScaledBitmap(square, 512, 512, true).also {
            if (it != square) square.recycle()
        }
    }
    val bos = ByteArrayOutputStream()
    out.compress(Bitmap.CompressFormat.PNG, 90, bos)
    if (out != src && out != square) out.recycle()
    return bos.toByteArray()
}
