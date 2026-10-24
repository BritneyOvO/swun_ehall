package cn.edu.swun.swun_ehall.data.session

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

interface AccountCredentials {
    var username: String
    var password: String
    fun passwordFor(id: String): String
    fun setPasswordFor(id: String, value: String)
    fun removePasswordFor(id: String)
    fun clearPassword()
}

class CredentialStore(app: Context) : AccountCredentials {
    private val prefs = app.getSharedPreferences("swun_session", 0)

    override var username: String
        get() = prefs.getString("user", "").orEmpty()
        set(value) {
            prefs.edit().putString("user", value).apply()
        }

    override var password: String
        get() = decrypt(prefs.getString("pass_blob", "").orEmpty())
        set(value) {
            prefs.edit().putString("pass_blob", encrypt(value)).apply()
        }

    override fun passwordFor(id: String): String {
        val key = "pass_blob_${id.trim()}"
        val blob = prefs.getString(key, "").orEmpty()
        if (blob.isNotEmpty()) return decrypt(blob)
        return if (id.trim() == username) password else ""
    }

    override fun setPasswordFor(id: String, value: String) {
        prefs.edit().putString("pass_blob_${id.trim()}", encrypt(value)).apply()
        if (id.trim() == username) password = value
    }

    override fun removePasswordFor(id: String) {
        prefs.edit().remove("pass_blob_${id.trim()}").apply()
        if (id.trim() == username) clearPassword()
    }

    override fun clearPassword() {
        prefs.edit().remove("pass_blob").apply()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): String {
        if (plain.isEmpty()) return ""
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key())
        val iv = c.iv
        val out = c.doFinal(plain.toByteArray(Charsets.UTF_8))
        return Base64.encodeToString(iv + out, Base64.NO_WRAP)
    }

    private fun decrypt(blob: String): String {
        if (blob.isBlank()) return ""
        return try {
            val raw = Base64.decode(blob, Base64.NO_WRAP)
            val iv = raw.copyOfRange(0, 12)
            val data = raw.copyOfRange(12, raw.size)
            val c = Cipher.getInstance("AES/GCM/NoPadding")
            c.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
            String(c.doFinal(data), Charsets.UTF_8)
        } catch (_: Exception) {
            ""
        }
    }

    companion object {
        private const val ALIAS = "swun_session_aes"
    }
}
