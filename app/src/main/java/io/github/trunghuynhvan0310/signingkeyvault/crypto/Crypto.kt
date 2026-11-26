package io.github.trunghuynhvan0310.signingkeyvault.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.text.Normalizer
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

/** Tham số Argon2id; được ghi vào tiêu đề tệp sao lưu để khôi phục về sau. */
data class KdfParams(val memKb: Int, val iterations: Int, val parallelism: Int)

object Crypto {
    const val KEY_BYTES = 32
    const val NONCE_BYTES = 12
    const val TAG_BYTES = 16

    val DEFAULT_KDF = KdfParams(memKb = 65536, iterations = 3, parallelism = 1)

    private val random = SecureRandom()

    fun randomBytes(size: Int): ByteArray = ByteArray(size).also { random.nextBytes(it) }

    /** Argon2id trên mật khẩu đã chuẩn hóa NFC, trả về khóa 256-bit. */
    fun deriveKey(passphrase: CharArray, salt: ByteArray, kdf: KdfParams): ByteArray {
        val password = Normalizer.normalize(String(passphrase), Normalizer.Form.NFC)
            .toByteArray(Charsets.UTF_8)
        try {
            val params = Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(Argon2Parameters.ARGON2_VERSION_13)
                .withSalt(salt)
                .withIterations(kdf.iterations)
                .withMemoryAsKB(kdf.memKb)
                .withParallelism(kdf.parallelism)
                .build()
            val generator = Argon2BytesGenerator()
            generator.init(params)
            val out = ByteArray(KEY_BYTES)
            generator.generateBytes(password, out)
            return out
        } finally {
            password.fill(0)
        }
    }

    /** AES-256-GCM; kết quả = nonce (12 byte) + bản mã kèm thẻ xác thực. */
    fun encrypt(key: ByteArray, plain: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        val nonce = randomBytes(NONCE_BYTES)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(TAG_BYTES * 8, nonce))
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return nonce + cipher.doFinal(plain)
    }

    /** Ném GeneralSecurityException nếu sai khóa hoặc dữ liệu bị sửa. */
    fun decrypt(key: ByteArray, data: ByteArray, aad: ByteArray = ByteArray(0)): ByteArray {
        if (data.size < NONCE_BYTES + TAG_BYTES) throw GeneralSecurityException("Data too short")
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            GCMParameterSpec(TAG_BYTES * 8, data, 0, NONCE_BYTES),
        )
        if (aad.isNotEmpty()) cipher.updateAAD(aad)
        return cipher.doFinal(data, NONCE_BYTES, data.size - NONCE_BYTES)
    }
}
