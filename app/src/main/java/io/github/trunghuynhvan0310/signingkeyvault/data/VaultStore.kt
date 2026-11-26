package io.github.trunghuynhvan0310.signingkeyvault.data

import io.github.trunghuynhvan0310.signingkeyvault.crypto.Crypto
import java.io.File

/** Vault mã hóa bằng DEK ngẫu nhiên; DEK được bọc bởi khóa trong Android Keystore. */
class VaultStore(private val dir: File) {
    private val dekFile = File(dir, "dek.bin")
    private val vaultFile = File(dir, "vault.bin")
    private val aad = "skv-vault-v1".toByteArray()

    fun exists(): Boolean = dekFile.exists() && vaultFile.exists()

    /** Cần người dùng vừa xác thực (khóa Keystore có hiệu lực theo thời gian). */
    fun create(initial: VaultData): ByteArray {
        wipe()
        try {
            val dek = Crypto.randomBytes(Crypto.KEY_BYTES)
            write(dekFile, DeviceKey.encrypt(dek))
            save(dek, initial)
            return dek
        } catch (e: Exception) {
            wipe()
            throw e
        }
    }

    fun openDek(): ByteArray = DeviceKey.decrypt(dekFile.readBytes())

    fun load(dek: ByteArray): VaultData {
        val plain = Crypto.decrypt(dek, vaultFile.readBytes(), aad)
        try {
            return VaultJson.decode(plain)
        } finally {
            plain.fill(0)
        }
    }

    fun save(dek: ByteArray, data: VaultData) {
        val plain = VaultJson.encode(data, includeBackup = true)
        try {
            write(vaultFile, Crypto.encrypt(dek, plain, aad))
        } finally {
            plain.fill(0)
        }
    }

    fun wipe() {
        dekFile.delete()
        vaultFile.delete()
        File(dir, "dek.bin.tmp").delete()
        File(dir, "vault.bin.tmp").delete()
        DeviceKey.delete()
    }

    private fun write(target: File, bytes: ByteArray) {
        val temp = File(dir, target.name + ".tmp")
        temp.writeBytes(bytes)
        if (!temp.renameTo(target)) {
            target.delete()
            check(temp.renameTo(target)) { "Cannot write " + target.name }
        }
    }
}
