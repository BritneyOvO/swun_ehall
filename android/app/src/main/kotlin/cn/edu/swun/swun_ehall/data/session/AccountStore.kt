package cn.edu.swun.swun_ehall.data.session

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

data class SavedAccount(
    val id: String,
    val name: String = "",
) {
    val label: String get() = name.trim().ifBlank { id }
}

class AccountStore(app: Context, private val creds: CredentialStore) {
    private val file = File(app.filesDir, "accounts.json")
    val items = mutableStateListOf<SavedAccount>()
    var currentId by mutableStateOf("")
        private set

    fun load() {
        if (!file.exists()) {
            migrateLegacy()
            return
        }
        try {
            val raw = JSONObject(file.readText())
            currentId = raw.optString("current").trim()
            val list = raw.optJSONArray("accounts") ?: JSONArray()
            items.clear()
            for (i in 0 until list.length()) {
                val o = list.optJSONObject(i) ?: continue
                val id = o.optString("id").trim()
                if (id.isEmpty()) continue
                items.add(SavedAccount(id, o.optString("name")))
            }
        } catch (_: Exception) {
            migrateLegacy()
            return
        }
        if (currentId.isNotEmpty() && items.none { it.id == currentId }) {
            currentId = items.firstOrNull()?.id.orEmpty()
        }
        migrateLegacy()
    }

    fun upsert(id: String, name: String = "", password: String? = null) {
        val sid = id.trim()
        if (sid.isEmpty()) return
        val i = items.indexOfFirst { it.id == sid }
        val nextName = name.trim()
        if (i >= 0) {
            val old = items[i]
            items[i] = SavedAccount(sid, if (nextName.isEmpty()) old.name else nextName)
        } else {
            items.add(SavedAccount(sid, nextName))
        }
        currentId = sid
        creds.username = sid
        if (!password.isNullOrEmpty()) creds.setPasswordFor(sid, password)
        save()
    }

    fun setCurrent(id: String) {
        currentId = id.trim()
        if (currentId.isNotEmpty()) creds.username = currentId
        save()
    }

    fun password(id: String): String = creds.passwordFor(id)

    fun remove(id: String) {
        val sid = id.trim()
        items.removeAll { it.id == sid }
        creds.removePasswordFor(sid)
        if (currentId == sid) currentId = items.firstOrNull()?.id.orEmpty()
        if (currentId.isNotEmpty()) creds.username = currentId
        save()
    }

    private fun migrateLegacy() {
        if (items.isNotEmpty()) return
        val id = creds.username.trim()
        if (id.isEmpty()) return
        items.add(SavedAccount(id, ""))
        currentId = id
        val legacy = creds.password
        if (legacy.isNotEmpty()) creds.setPasswordFor(id, legacy)
        save()
    }

    private fun save() {
        val arr = JSONArray()
        items.forEach { a ->
            arr.put(JSONObject().put("id", a.id).put("name", a.name))
        }
        file.writeText(
            JSONObject()
                .put("current", currentId)
                .put("accounts", arr)
                .toString(),
        )
    }
}
