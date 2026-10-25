package cn.edu.swun.swun_ehall.data.http

/** Only transfer a newly signed P together with its matching server-side O. */
internal fun rsRequestCookies(
    before: String,
    after: String,
    stored: Map<String, String>,
): Map<String, String>? {
    fun parts(header: String) = header.split(';').mapNotNull { part ->
        val name = part.substringBefore('=').trim()
        val value = part.substringAfter('=', "").trim()
        if (CampusHttp.isWafCookieName(name) && value.isNotEmpty()) name to value else null
    }
    val previous = parts(before).toSet()
    val signed = parts(after).lastOrNull { (name, value) ->
        name.endsWith("P") && value.length >= 300 && (name to value) !in previous
    } ?: return null
    val serverName = signed.first.dropLast(1) + "O"
    val serverValue = stored[serverName]?.takeIf { it.isNotBlank() } ?: return null
    return linkedMapOf(serverName to serverValue, signed).apply {
        val enableName = "enable_" + signed.first.dropLast(1)
        stored[enableName]?.takeIf { it.isNotBlank() }?.let { put(enableName, it) }
    }
}
