package cn.edu.swun.swun_ehall

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import io.flutter.embedding.engine.FlutterEngine
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class VaultPlugin : MethodChannel.MethodCallHandler {
    companion object {
        const val CHANNEL = "cn.edu.swun.swun_ehall/vault"
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val ALIAS = "cn.edu.swun.swun_ehall.account_vault"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128

        fun registerWith(engine: FlutterEngine) {
            MethodChannel(engine.dartExecutor.binaryMessenger, CHANNEL).setMethodCallHandler(VaultPlugin())
        }
    }

    override fun onMethodCall(call: MethodCall, result: MethodChannel.Result) {
        try {
            when (call.method) {
                "encrypt" -> {
                    val plain = call.argument<String>("plain").orEmpty()
                    if (plain.isEmpty()) {
                        result.error("VAULT", "empty", null)
                        return
                    }
                    result.success(encrypt(plain))
                }
                "decrypt" -> {
                    val iv = call.argument<String>("iv").orEmpty()
                    val ct = call.argument<String>("ct").orEmpty()
                    result.success(decrypt(iv, ct))
                }
                else -> result.notImplemented()
            }
        } catch (e: Exception) {
            result.error("VAULT", e.message ?: "keystore", null)
        }
    }

    private fun secret(): SecretKey {
        val ks = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = ks.getKey(ALIAS, null) as? SecretKey
        if (existing != null) return existing
        val gen = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        gen.init(
            KeyGenParameterSpec.Builder(
                ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)
                .build(),
        )
        return gen.generateKey()
    }

    private fun encrypt(plain: String): HashMap<String, String> {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secret())
        val iv = cipher.iv
        val ct = cipher.doFinal(plain.toByteArray(Charsets.UTF_8))
        return hashMapOf(
            "iv" to Base64.encodeToString(iv, Base64.NO_WRAP),
            "ct" to Base64.encodeToString(ct, Base64.NO_WRAP),
        )
    }

    private fun decrypt(ivB64: String, ctB64: String): String {
        val iv = Base64.decode(ivB64, Base64.NO_WRAP)
        val ct = Base64.decode(ctB64, Base64.NO_WRAP)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secret(), GCMParameterSpec(GCM_TAG_BITS, iv))
        return String(cipher.doFinal(ct), Charsets.UTF_8)
    }
}
