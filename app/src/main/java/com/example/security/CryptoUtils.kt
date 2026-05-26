package com.example.security

import android.util.Base64
import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

// PIN-derived encryption for ntfy.sh transport. The PIN is the only shared secret
// between sender and receiver, so brute-forcing 1M six-digit PINs is theoretically
// possible — PBKDF2 (100k iterations) makes that expensive, and the topic name is
// derived from the PIN so attackers must enumerate PIN-space twice (topic + key).
object CryptoUtils {
    private const val APP_SALT = "AlarmToss/v1/topic-salt"
    private const val KEY_SALT_TEXT = "AlarmToss/v1/key-salt"
    private const val PBKDF2_ITERATIONS = 100_000
    private const val KEY_LENGTH_BITS = 256
    private const val GCM_TAG_BITS = 128
    private const val IV_BYTES = 12

    fun deriveTopic(pin: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest("$APP_SALT|$pin".toByteArray(Charsets.UTF_8))
        return "atoss_" + bytes.joinToString("") { "%02x".format(it) }.substring(0, 24)
    }

    private fun deriveKey(pin: String): SecretKeySpec {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(
            pin.toCharArray(),
            KEY_SALT_TEXT.toByteArray(Charsets.UTF_8),
            PBKDF2_ITERATIONS,
            KEY_LENGTH_BITS
        )
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    fun encrypt(plaintext: String, pin: String): String {
        val key = deriveKey(pin)
        val iv = ByteArray(IV_BYTES).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        val combined = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, combined, 0, iv.size)
        System.arraycopy(ciphertext, 0, combined, iv.size, ciphertext.size)
        return Base64.encodeToString(combined, Base64.NO_WRAP)
    }

    fun decrypt(payload: String, pin: String): String? {
        return try {
            val key = deriveKey(pin)
            val combined = Base64.decode(payload, Base64.NO_WRAP)
            if (combined.size <= IV_BYTES) return null
            val iv = combined.copyOfRange(0, IV_BYTES)
            val ciphertext = combined.copyOfRange(IV_BYTES, combined.size)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_BITS, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            null
        }
    }
}
