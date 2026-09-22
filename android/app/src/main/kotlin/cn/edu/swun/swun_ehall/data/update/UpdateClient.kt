package cn.edu.swun.swun_ehall.data.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import cn.edu.swun.swun_ehall.data.http.CampusHttp
import cn.edu.swun.swun_ehall.data.model.AppRelease
import java.io.File
import okhttp3.Request
import org.json.JSONObject

const val K_GITHUB_REPO = "BritneyOvO/swun_ehall"
const val K_GITHUB_LATEST = "https://api.github.com/repos/$K_GITHUB_REPO/releases/latest"

object UpdateClient {
    fun latest(): AppRelease? {
        val r = CampusHttp.get(
            K_GITHUB_LATEST,
            mapOf("Accept" to "application/vnd.github+json", "User-Agent" to "swun-ehall"),
        )
        val text = CampusHttp.text(r)
        if (r.code != 200) error("检查更新失败 HTTP ${r.code}")
        return parse(JSONObject(text))
    }

    fun parse(raw: JSONObject): AppRelease? {
        if (raw.optBoolean("draft")) return null
        var tag = raw.optString("tag_name").trim()
        if (tag.isEmpty()) return null
        if (tag.startsWith("v") || tag.startsWith("V")) tag = tag.substring(1)
        var apk: String? = null
        var apkName = ""
        var fromName: ApkAssetMeta? = null
        val assets = raw.optJSONArray("assets")
        if (assets != null) {
            var fallback: Pair<String, String>? = null
            for (i in 0 until assets.length()) {
                val a = assets.optJSONObject(i) ?: continue
                val name = a.optString("name")
                val url = a.optString("browser_download_url")
                if (!name.lowercase().endsWith(".apk") || url.isEmpty()) continue
                val meta = parseApkAssetName(name)
                if (name.contains("release", ignoreCase = true) && !name.contains("debug", ignoreCase = true)) {
                    apk = url
                    apkName = name
                    fromName = meta
                    break
                }
                if (fallback == null) fallback = name to url
                if (fromName == null) fromName = meta
            }
            if (apk == null && fallback != null) {
                apkName = fallback.first
                apk = fallback.second
            }
        }
        var html = raw.optString("html_url").trim()
        if (html.isEmpty()) html = "https://github.com/$K_GITHUB_REPO/releases/tag/${raw.optString("tag_name")}"
        val version = fromName?.version?.takeIf { it.isNotBlank() } ?: tag
        return AppRelease(
            version = version,
            htmlUrl = html,
            apkUrl = apk,
            notes = raw.optString("body").trim(),
            prerelease = raw.optBoolean("prerelease"),
            versionCode = fromName?.versionCode ?: 0,
            apkName = apkName,
        )
    }

    fun apkFile(context: Context, version: String): File {
        val dir = File(context.filesDir, "update")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "swun_ehall-$version.apk")
    }

    fun cached(context: Context, rel: AppRelease): File? {
        val f = apkFile(context, rel.version)
        return if (f.exists() && f.length() > 1024 * 1024) f else null
    }

    fun download(context: Context, rel: AppRelease, onProgress: (Float) -> Unit): File {
        val url = rel.apkUrl?.trim().orEmpty()
        if (url.isEmpty()) error("这个版本没有 Android 安装包")
        cached(context, rel)?.let {
            onProgress(1f)
            return it
        }
        val file = apkFile(context, rel.version)
        val part = File("${file.path}.part")
        if (part.exists()) part.delete()
        val req = Request.Builder().url(url).header("User-Agent", "swun-ehall").header("Accept", "*/*").build()
        CampusHttp.followClient.newCall(req).execute().use { r ->
            if (r.code !in 200..399) error("下载失败 HTTP ${r.code}")
            val body = r.body ?: error("下载失败：空响应")
            val total = body.contentLength()
            var got = 0L
            part.outputStream().use { out ->
                body.byteStream().use { inp ->
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = inp.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                        got += n
                        if (total > 0) onProgress((got.toFloat() / total).coerceIn(0f, 1f))
                    }
                }
            }
        }
        if (file.exists()) file.delete()
        part.renameTo(file)
        onProgress(1f)
        return file
    }

    fun install(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    fun purgeStale(context: Context) {
        val dir = File(context.filesDir, "update")
        if (!dir.exists()) return
        dir.listFiles()?.forEach { f ->
            if (f.name.endsWith(".part")) {
                f.delete()
                return@forEach
            }
            val m = Regex("""swun_ehall-(\d+(?:\.\d+)*)""").find(f.name) ?: return@forEach
            if (compareOrCode(m.groupValues[1]) <= 0) f.delete()
        }
    }

    private fun compareOrCode(fileVer: String): Int = Versions.compare(fileVer, Versions.APP)
}

data class ApkAssetMeta(val version: String, val versionCode: Int)

fun parseApkAssetName(name: String): ApkAssetMeta? {
    val n = name.trim()
    if (!n.endsWith(".apk", ignoreCase = true)) return null
    Regex("""v(\d+(?:\.\d+)*)_(\d+)[-_.]""", RegexOption.IGNORE_CASE).find(n)?.let {
        return ApkAssetMeta(it.groupValues[1], it.groupValues[2].toInt())
    }
    Regex("""swun_ehall-(\d+(?:\.\d+)*)(?:-(\d+))?-arm64-(release|debug)\.apk""", RegexOption.IGNORE_CASE).find(n)?.let {
        val code = it.groupValues[2].toIntOrNull() ?: 0
        return ApkAssetMeta(it.groupValues[1], code)
    }
    Regex("""(\d+\.\d+\.\d+)(?:[_-](\d+))?""").find(n)?.let {
        return ApkAssetMeta(it.groupValues[1], it.groupValues[2].toIntOrNull() ?: 0)
    }
    return null
}
