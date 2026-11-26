package io.github.trunghuynhvan0310.signingkeyvault.crypto

import java.nio.ByteBuffer
import java.security.GeneralSecurityException

/** Khóa sao lưu đã suy ra từ mật khẩu, kèm muối và tham số để ghi vào tiêu đề tệp. */
class BackupKey(val salt: ByteArray, val kdf: KdfParams, val key: ByteArray)

class OpenedBackup(val plain: ByteArray, val key: BackupKey)

class WrongPassphraseException : Exception("Wrong passphrase or damaged file")

class InvalidBackupException(message: String) : Exception(message)

/**
 * Định dạng tệp .vault: "SKV1" | memKb | iterations | parallelism | salt(16) | nonce(12) | bản mã+thẻ.
 * Tiêu đề được dùng làm dữ liệu xác thực kèm (AAD) nên không thể bị sửa mà không bị phát hiện.
 */
object BackupFile {
    private val MAGIC = byteArrayOf(0x53, 0x4B, 0x56, 0x31)
    private const val SALT_BYTES = 16
    private const val HEADER_BYTES = 4 + 12 + SALT_BYTES
    private const val MIN_SIZE = HEADER_BYTES + Crypto.NONCE_BYTES + Crypto.TAG_BYTES

    fun newKey(passphrase: CharArray, kdf: KdfParams = Crypto.DEFAULT_KDF): BackupKey {
        val salt = Crypto.randomBytes(SALT_BYTES)
        return BackupKey(salt, kdf, Crypto.deriveKey(passphrase, salt, kdf))
    }

    fun seal(plain: ByteArray, backupKey: BackupKey): ByteArray {
        val header = ByteBuffer.allocate(HEADER_BYTES)
            .put(MAGIC)
            .putInt(backupKey.kdf.memKb)
            .putInt(backupKey.kdf.iterations)
            .putInt(backupKey.kdf.parallelism)
            .put(backupKey.salt)
            .array()
        return header + Crypto.encrypt(backupKey.key, plain, header)
    }

    /** Mở bằng mật khẩu (dùng khi khôi phục). */
    fun open(file: ByteArray, passphrase: CharArray): OpenedBackup {
        val (kdf, salt) = parseHeader(file)
        val backupKey = BackupKey(salt, kdf, Crypto.deriveKey(passphrase, salt, kdf))
        return OpenedBackup(decryptBody(file, backupKey.key), backupKey)
    }

    /** Mở bằng khóa đã suy ra sẵn (dùng khi kiểm tra bản sao lưu tự động). */
    fun openWithKey(file: ByteArray, backupKey: BackupKey): ByteArray {
        parseHeader(file)
        return decryptBody(file, backupKey.key)
    }

    private fun parseHeader(file: ByteArray): Pair<KdfParams, ByteArray> {
        if (file.size < MIN_SIZE || !file.copyOfRange(0, 4).contentEquals(MAGIC)) {
            throw InvalidBackupException("Not a vault backup")
        }
        val buffer = ByteBuffer.wrap(file, 4, 12)
        val memKb = buffer.getInt()
        val iterations = buffer.getInt()
        val parallelism = buffer.getInt()
        if (memKb !in 64..262144 || iterations !in 1..10 || parallelism !in 1..8) {
            throw InvalidBackupException("Unsupported parameters")
        }
        return KdfParams(memKb, iterations, parallelism) to file.copyOfRange(16, 16 + SALT_BYTES)
    }

    private fun decryptBody(file: ByteArray, key: ByteArray): ByteArray = try {
        Crypto.decrypt(key, file.copyOfRange(HEADER_BYTES, file.size), file.copyOfRange(0, HEADER_BYTES))
    } catch (e: GeneralSecurityException) {
        throw WrongPassphraseException()
    }
}
