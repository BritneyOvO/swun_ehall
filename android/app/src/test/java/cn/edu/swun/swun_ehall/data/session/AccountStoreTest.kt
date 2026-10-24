package cn.edu.swun.swun_ehall.data.session

import java.io.File
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class AccountStoreTest {
    @get:Rule val temp = TemporaryFolder()

    private class Credentials : AccountCredentials {
        override var username = ""
        override var password = ""
        private val passwords = mutableMapOf<String, String>()
        override fun passwordFor(id: String) = passwords[id].orEmpty()
        override fun setPasswordFor(id: String, value: String) { passwords[id] = value }
        override fun removePasswordFor(id: String) {
            passwords.remove(id)
            if (username == id) clearPassword()
        }
        override fun clearPassword() { password = "" }
    }

    @Test fun deletedLastAccountStaysDeletedAfterRestart() {
        val file = File(temp.root, "accounts.json")
        val creds = Credentials()
        val store = AccountStore(file, creds)
        store.upsert("student-a", password = "password-a")
        creds.password = "legacy-password"
        store.remove("student-a")
        assertEquals("", creds.username)
        assertEquals("", creds.password)
        val restored = AccountStore(file, creds)
        restored.load()
        assertTrue(restored.items.isEmpty())
        assertEquals("", restored.currentId)
    }

    @Test fun existingEmptyStoreDoesNotMigrateStaleUsername() {
        val file = File(temp.root, "accounts.json")
        file.writeText("""{"current":"","accounts":[]}""")
        val creds = Credentials().apply { username = "deleted-student" }
        val store = AccountStore(file, creds)
        store.load()
        assertTrue(store.items.isEmpty())
        assertEquals("", creds.username)
    }

    @Test fun firstInstallStillMigratesLegacyCredentials() {
        val creds = Credentials().apply { username = "legacy"; password = "saved" }
        val store = AccountStore(File(temp.root, "accounts.json"), creds)
        store.load()
        assertEquals("legacy", store.currentId)
        assertEquals("saved", store.password("legacy"))
    }

    @Test fun deletingCurrentAccountKeepsOtherAccountCredentials() {
        val creds = Credentials()
        val file = File(temp.root, "accounts.json")
        val store = AccountStore(file, creds)
        store.upsert("a", password = "password-a")
        store.upsert("b", password = "password-b")
        store.remove("b")
        val restored = AccountStore(file, creds)
        restored.load()
        assertEquals("a", restored.currentId)
        assertEquals("a", creds.username)
        assertEquals("password-a", restored.password("a"))
        assertEquals("", restored.password("b"))
    }
}
