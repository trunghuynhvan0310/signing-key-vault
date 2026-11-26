package io.github.trunghuynhvan0310.signingkeyvault

import io.github.trunghuynhvan0310.signingkeyvault.crypto.BackupFile
import io.github.trunghuynhvan0310.signingkeyvault.crypto.InvalidBackupException
import io.github.trunghuynhvan0310.signingkeyvault.crypto.KdfParams
import io.github.trunghuynhvan0310.signingkeyvault.crypto.WrongPassphraseException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class BackupFileTest {
    // Tham số nhỏ để test chạy nhanh.
    private val fast = KdfParams(memKb = 64, iterations = 1, parallelism = 1)
    private val data = "{\"hello\":\"xin chào\"}".toByteArray()

    @Test
    fun roundTripWithPassphrase() {
        val key = BackupFile.newKey("mật khẩu thử 123".toCharArray(), fast)
        val file = BackupFile.seal(data, key)
        val opened = BackupFile.open(file, "mật khẩu thử 123".toCharArray())
        assertArrayEquals(data, opened.plain)
        assertArrayEquals(key.key, opened.key.key)
    }

    @Test
    fun roundTripWithDerivedKey() {
        val key = BackupFile.newKey("abcdefghij".toCharArray(), fast)
        assertArrayEquals(data, BackupFile.openWithKey(BackupFile.seal(data, key), key))
    }

    @Test
    fun wrongPassphraseIsRejected() {
        val file = BackupFile.seal(data, BackupFile.newKey("abcdefghij".toCharArray(), fast))
        assertThrows(WrongPassphraseException::class.java) {
            BackupFile.open(file, "abcdefghik".toCharArray())
        }
    }

    @Test
    fun tamperedBodyIsRejected() {
        val key = BackupFile.newKey("abcdefghij".toCharArray(), fast)
        val file = BackupFile.seal(data, key)
        file[file.size - 1] = (file[file.size - 1].toInt() xor 1).toByte()
        assertThrows(WrongPassphraseException::class.java) { BackupFile.openWithKey(file, key) }
    }

    @Test
    fun tamperedHeaderIsRejected() {
        val key = BackupFile.newKey("abcdefghij".toCharArray(), fast)
        val file = BackupFile.seal(data, key)
        file[19] = (file[19].toInt() xor 1).toByte() // một byte của muối
        assertThrows(WrongPassphraseException::class.java) { BackupFile.openWithKey(file, key) }
    }

    @Test
    fun garbageIsNotABackup() {
        assertThrows(InvalidBackupException::class.java) {
            BackupFile.open(ByteArray(100), "abcdefghij".toCharArray())
        }
    }

    @Test
    fun unicodeNormalizationGivesSameKey() {
        val composed = "éclair-123".toCharArray()
        val decomposed = "éclair-123".toCharArray()
        val file = BackupFile.seal(data, BackupFile.newKey(composed, fast))
        assertArrayEquals(data, BackupFile.open(file, decomposed).plain)
    }
}
