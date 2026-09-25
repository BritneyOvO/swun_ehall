package cn.edu.swun.swun_ehall.data.update

fun updateNotesMarkdown(body: String): String {
    val text = body.replace("\r\n", "\n").trim()
    if (text.isEmpty()) return ""
    val start = Regex("(?m)^#{1,3}[ \\t]+更新内容[ \\t]*$").find(text)
    val slice = if (start != null) {
        val rest = text.substring(start.range.last + 1)
        val end = Regex("(?m)^#{1,3}[ \\t]+(What's New|下载)\\b").find(rest)
        val section = if (end != null) rest.substring(0, end.range.first) else rest
        "### 更新内容\n$section"
    } else {
        val cut = Regex("(?m)^#{1,3}[ \\t]+下载\\b").find(text)
        if (cut != null) text.substring(0, cut.range.first) else text
    }
    return slice
        .lineSequence()
        .filterNot { line ->
            line.contains("Full Changelog", ignoreCase = true) ||
                line.contains("仅学生账号") ||
                line.contains("非官方应用") ||
                line.contains("Student accounts only") ||
                line.contains("Unofficial app")
        }
        .joinToString("\n")
        .trim()
}
