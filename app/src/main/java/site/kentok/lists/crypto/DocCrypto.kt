package site.kentok.lists.crypto

import android.util.Base64
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Шифрование документов на устройстве.
 * Ключ = PBKDF2(логин + "\n" + пароль), AES-256-GCM.
 */
object DocCrypto {
    private const val ITERATIONS = 120_000
    private const val KEY_BITS = 256
    private const val GCM_TAG = 128
    private const val IV_LEN = 12
    private const val SALT_LEN = 16

    fun deriveKey(login: String, password: String, salt: ByteArray): SecretKeySpec {
        val material = "$login\n$password".toCharArray()
        val spec = PBEKeySpec(material, salt, ITERATIONS, KEY_BITS)
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val raw = factory.generateSecret(spec).encoded
        spec.clearPassword()
        return SecretKeySpec(raw, "AES")
    }

    fun encrypt(plain: ByteArray, key: SecretKeySpec): ByteArray {
        val iv = ByteArray(IV_LEN).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG, iv))
        val enc = cipher.doFinal(plain)
        return iv + enc
    }

    fun decrypt(blob: ByteArray, key: SecretKeySpec): ByteArray {
        require(blob.size > IV_LEN) { "bad blob" }
        val iv = blob.copyOfRange(0, IV_LEN)
        val data = blob.copyOfRange(IV_LEN, blob.size)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG, iv))
        return cipher.doFinal(data)
    }

    fun newSalt(): ByteArray = ByteArray(SALT_LEN).also { SecureRandom().nextBytes(it) }

    fun b64(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)
    fun fromB64(s: String): ByteArray = Base64.decode(s, Base64.NO_WRAP)
}
