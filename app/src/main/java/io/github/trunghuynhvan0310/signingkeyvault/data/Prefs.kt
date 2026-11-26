package io.github.trunghuynhvan0310.signingkeyvault.data

import android.content.Context

/** Cài đặt không nhạy cảm (khóa sao lưu và mật khẩu nằm trong vault, không nằm ở đây). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("skv_prefs", Context.MODE_PRIVATE)

    var backupFolder: String?
        get() = sp.getString("backupFolder", null)
        set(value) = sp.edit().putString("backupFolder", value).apply()

    var backupFolderName: String?
        get() = sp.getString("backupFolderName", null)
        set(value) = sp.edit().putString("backupFolderName", value).apply()

    var lastBackupAt: Long
        get() = sp.getLong("lastBackupAt", 0L)
        set(value) = sp.edit().putLong("lastBackupAt", value).apply()

    var lastBackupError: String?
        get() = sp.getString("lastBackupError", null)
        set(value) = sp.edit().putString("lastBackupError", value).apply()

    var passphraseVerifiedAt: Long
        get() = sp.getLong("passphraseVerifiedAt", 0L)
        set(value) = sp.edit().putLong("passphraseVerifiedAt", value).apply()

    var lockTimeoutSec: Int
        get() = sp.getInt("lockTimeoutSec", 60)
        set(value) = sp.edit().putInt("lockTimeoutSec", value).apply()

    fun clear() = sp.edit().clear().apply()
}
