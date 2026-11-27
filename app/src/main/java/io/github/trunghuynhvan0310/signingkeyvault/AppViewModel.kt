package io.github.trunghuynhvan0310.signingkeyvault

import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.security.keystore.KeyPermanentlyInvalidatedException
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.trunghuynhvan0310.signingkeyvault.crypto.BackupFile
import io.github.trunghuynhvan0310.signingkeyvault.crypto.Crypto
import io.github.trunghuynhvan0310.signingkeyvault.crypto.InvalidBackupException
import io.github.trunghuynhvan0310.signingkeyvault.crypto.KeystoreGenerator
import io.github.trunghuynhvan0310.signingkeyvault.crypto.KeystoreRequest
import io.github.trunghuynhvan0310.signingkeyvault.crypto.WrongPassphraseException
import io.github.trunghuynhvan0310.signingkeyvault.data.BackupManager
import io.github.trunghuynhvan0310.signingkeyvault.data.BackupSecret
import io.github.trunghuynhvan0310.signingkeyvault.data.KeyItem
import io.github.trunghuynhvan0310.signingkeyvault.data.Prefs
import io.github.trunghuynhvan0310.signingkeyvault.data.Project
import io.github.trunghuynhvan0310.signingkeyvault.data.SecretItem
import io.github.trunghuynhvan0310.signingkeyvault.data.VaultData
import io.github.trunghuynhvan0310.signingkeyvault.data.VaultJson
import io.github.trunghuynhvan0310.signingkeyvault.data.VaultStore
import io.github.trunghuynhvan0310.signingkeyvault.data.newId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.Base64
import javax.crypto.AEADBadTagException

enum class Phase { Onboarding, Locked, Unlocked, KeyLost }

sealed interface Screen {
    data object Home : Screen
    data object NewProject : Screen
    data object QuickKeystore : Screen
    data class ProjectDetail(val id: String) : Screen
    data class NewKeystore(val projectId: String) : Screen
    data class ImportKeystore(val projectId: String) : Screen
    data class KeyDetail(val id: String) : Screen
    data class NewSecret(val projectId: String) : Screen
    data class SecretDetail(val id: String) : Screen
    data object Backup : Screen
    data object Settings : Screen
}

data class BackupStatus(
    val folderName: String? = null,
    val lastBackupAt: Long = 0L,
    val error: String? = null,
    val passphraseVerifiedAt: Long = 0L,
)

data class UiMessage(@StringRes val res: Int, val arg: String? = null)

data class UiState(
    val phase: Phase = Phase.Locked,
    val stack: List<Screen> = listOf(Screen.Home),
    val data: VaultData = VaultData(),
    val backup: BackupStatus = BackupStatus(),
    val busy: Boolean = false,
    val message: UiMessage? = null,
    val lockTimeoutSec: Int = 60,
)

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext
    private val store = VaultStore(context.filesDir)
    private val prefs = Prefs(context)
    private val backups = BackupManager(context)
    private val saveMutex = Mutex()

    // DEK chỉ nằm trong bộ nhớ khi đang mở khóa.
    private var dek: ByteArray? = null
    private var stoppedAt = 0L
    private var externalAction = false

    private val _state = MutableStateFlow(
        UiState(
            phase = if (store.exists()) Phase.Locked else Phase.Onboarding,
            backup = readBackupStatus(),
            lockTimeoutSec = prefs.lockTimeoutSec,
        ),
    )
    val state: StateFlow<UiState> = _state.asStateFlow()

    private fun readBackupStatus() = BackupStatus(
        folderName = prefs.backupFolderName,
        lastBackupAt = prefs.lastBackupAt,
        error = prefs.lastBackupError,
        passphraseVerifiedAt = prefs.passphraseVerifiedAt,
    )

    private fun refreshBackupStatus() = _state.update { it.copy(backup = readBackupStatus()) }

    private fun post(res: Int, arg: String? = null) = _state.update { it.copy(message = UiMessage(res, arg)) }

    fun showMessage(res: Int, arg: String? = null) = post(res, arg)

    fun consumeMessage() = _state.update { it.copy(message = null) }

    // ---- Điều hướng ----

    fun navigate(screen: Screen) = _state.update { it.copy(stack = it.stack + screen) }

    fun replaceTop(screen: Screen) = _state.update { it.copy(stack = it.stack.dropLast(1) + screen) }

    fun back() = _state.update { if (it.stack.size > 1) it.copy(stack = it.stack.dropLast(1)) else it }

    // ---- Khóa / mở khóa ----

    /** Gọi ngay trước khi mở bộ chọn tệp hoặc trình duyệt để không bị khóa khi quay lại. */
    fun markExternal() {
        externalAction = true
    }

    fun onActivityStopped() {
        stoppedAt = SystemClock.elapsedRealtime()
    }

    fun onActivityStarted() {
        val current = _state.value
        if (current.phase == Phase.Unlocked && stoppedAt != 0L) {
            val limitSec = if (externalAction) maxOf(current.lockTimeoutSec, EXTERNAL_GRACE_SEC) else current.lockTimeoutSec
            if (SystemClock.elapsedRealtime() - stoppedAt >= limitSec * 1000L) lock()
        }
        externalAction = false
        stoppedAt = 0L
    }

    fun lock() {
        dek = null
        _state.update {
            if (it.phase == Phase.Unlocked) UiState(phase = Phase.Locked, backup = it.backup, lockTimeoutSec = it.lockTimeoutSec) else it
        }
    }

    fun setLockTimeout(seconds: Int) {
        prefs.lockTimeoutSec = seconds
        _state.update { it.copy(lockTimeoutSec = seconds) }
    }

    /** Gọi sau khi người dùng vừa xác thực thành công. */
    fun unlock() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val key = store.openDek()
                    key to store.load(key)
                }
            }
            result.onSuccess { (key, data) ->
                dek = key
                _state.update {
                    it.copy(phase = Phase.Unlocked, data = data, stack = listOf(Screen.Home), busy = false, backup = readBackupStatus())
                }
            }.onFailure { error ->
                val lost = error is KeyPermanentlyInvalidatedException || error is AEADBadTagException
                _state.update { it.copy(busy = false, phase = if (lost) Phase.KeyLost else it.phase) }
                if (!lost) post(R.string.msg_unlock_failed)
            }
        }
    }

    fun createVault() {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val result = withContext(Dispatchers.IO) { runCatching { store.create(VaultData()) } }
            result.onSuccess { key ->
                dek = key
                _state.update {
                    it.copy(
                        phase = Phase.Unlocked, data = VaultData(), busy = false, backup = readBackupStatus(),
                        stack = listOf(Screen.Home, Screen.Backup),
                    )
                }
            }.onFailure {
                _state.update { it.copy(busy = false) }
                post(R.string.msg_create_failed)
            }
        }
    }

    /** Khôi phục vào máy mới (hoặc khi khóa thiết bị bị mất): người dùng đã xác thực trước. */
    fun restoreNew(uri: Uri, passphrase: CharArray) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val opened = BackupFile.open(backups.read(uri), passphrase)
                    val data = VaultJson.decode(opened.plain).copy(backup = BackupSecret.from(opened.key))
                    data to store.create(data)
                }
            }
            passphrase.fill('\u0000')
            result.onSuccess { (data, key) ->
                dek = key
                prefs.passphraseVerifiedAt = System.currentTimeMillis()
                _state.update {
                    it.copy(
                        phase = Phase.Unlocked, data = data, busy = false, backup = readBackupStatus(),
                        stack = listOf(Screen.Home, Screen.Backup),
                    )
                }
                post(R.string.msg_restore_ok)
            }.onFailure {
                _state.update { it.copy(busy = false) }
                post(restoreErrorMessage(it))
            }
        }
    }

    fun wipeAll() {
        dek = null
        store.wipe()
        prefs.clear()
        _state.value = UiState(phase = Phase.Onboarding)
    }

    // ---- Lưu và sao lưu ----

    private fun mutate(change: (VaultData) -> VaultData) {
        if (dek == null) return
        val next = change(_state.value.data)
        _state.update { it.copy(data = next) }
        persist(next)
    }

    private fun persist(data: VaultData) {
        val key = dek ?: return
        viewModelScope.launch(Dispatchers.IO) {
            saveMutex.withLock {
                try {
                    store.save(key, data)
                } catch (e: Exception) {
                    post(R.string.msg_save_failed)
                    return@withLock
                }
                runBackup(data, userInitiated = false)
            }
        }
    }

    /** Chạy trên luồng IO, trong saveMutex. */
    private fun runBackup(data: VaultData, userInitiated: Boolean) {
        val folder = prefs.backupFolder?.let(Uri::parse)
        val secret = data.backup
        if (folder == null || secret == null) {
            if (userInitiated) post(if (secret == null) R.string.msg_set_passphrase_first else R.string.msg_pick_folder_first)
            return
        }
        try {
            val key = secret.toKey()
            val plain = VaultJson.encode(data, includeBackup = false)
            val name = try {
                backups.backupToTree(folder, BackupFile.seal(plain, key), key)
            } finally {
                plain.fill(0)
            }
            prefs.lastBackupAt = System.currentTimeMillis()
            prefs.lastBackupError = null
            if (userInitiated) post(R.string.msg_backup_ok, name)
        } catch (e: Exception) {
            prefs.lastBackupError = e.message?.take(80) ?: e.javaClass.simpleName
            if (userInitiated) post(R.string.msg_backup_failed, prefs.lastBackupError)
        }
        refreshBackupStatus()
    }

    fun backupNow() {
        val data = _state.value.data
        if (dek == null) return
        viewModelScope.launch(Dispatchers.IO) { saveMutex.withLock { runBackup(data, userInitiated = true) } }
    }

    fun setBackupFolder(uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        prefs.backupFolder = uri.toString()
        prefs.backupFolderName = runCatching { backups.folderName(uri) }.getOrNull()
        prefs.lastBackupError = null
        refreshBackupStatus()
        if (_state.value.data.backup != null) backupNow()
    }

    fun setBackupPassphrase(passphrase: CharArray) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val key = withContext(Dispatchers.Default) { runCatching { BackupFile.newKey(passphrase) } }
            passphrase.fill('\u0000')
            _state.update { it.copy(busy = false) }
            key.onSuccess {
                prefs.passphraseVerifiedAt = 0L
                mutate { data -> data.copy(backup = BackupSecret.from(it)) }
                refreshBackupStatus()
                post(R.string.msg_passphrase_set)
            }.onFailure { post(R.string.msg_save_failed) }
        }
    }

    /** Kiểm tra người dùng còn nhớ mật khẩu sao lưu (so khóa suy ra với khóa đang lưu). */
    fun verifyPassphrase(passphrase: CharArray) {
        val secret = _state.value.data.backup ?: return
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val ok = withContext(Dispatchers.Default) {
                runCatching {
                    val key = secret.toKey()
                    MessageDigest.isEqual(Crypto.deriveKey(passphrase, key.salt, key.kdf), key.key)
                }.getOrDefault(false)
            }
            passphrase.fill('\u0000')
            _state.update { it.copy(busy = false) }
            if (ok) {
                prefs.passphraseVerifiedAt = System.currentTimeMillis()
                refreshBackupStatus()
                post(R.string.msg_passphrase_ok)
            } else {
                post(R.string.msg_wrong_passphrase)
            }
        }
    }

    fun verifyFolder() {
        val folder = prefs.backupFolder?.let(Uri::parse)
        val secret = _state.value.data.backup
        if (folder == null) return post(R.string.msg_pick_folder_first)
        if (secret == null) return post(R.string.msg_set_passphrase_first)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { runCatching { backups.verify(folder, secret.toKey()) } }
            result.onSuccess { (readable, found) ->
                post(if (found == 0) R.string.msg_verify_none else R.string.msg_verify_result, "$readable/$found")
            }.onFailure { post(R.string.msg_backup_failed, it.message?.take(80)) }
        }
    }

    fun exportCopy(target: Uri) {
        val data = _state.value.data
        val secret = data.backup ?: return post(R.string.msg_set_passphrase_first)
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val key = secret.toKey()
                    val plain = VaultJson.encode(data, includeBackup = false)
                    try {
                        backups.write(target, BackupFile.seal(plain, key))
                    } finally {
                        plain.fill(0)
                    }
                }
            }
            result.onSuccess { post(R.string.msg_exported) }.onFailure { post(R.string.msg_backup_failed, it.message?.take(80)) }
        }
    }

    /** Khôi phục trong khi app đã mở: gộp vào dữ liệu hiện có, không ghi đè. */
    fun restoreMerge(uri: Uri, passphrase: CharArray) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val opened = BackupFile.open(backups.read(uri), passphrase)
                    VaultJson.decode(opened.plain) to BackupSecret.from(opened.key)
                }
            }
            passphrase.fill('\u0000')
            _state.update { it.copy(busy = false) }
            result.onSuccess { (incoming, backupSecret) ->
                mutate { it.mergedWith(incoming).copy(backup = it.backup ?: backupSecret) }
                post(R.string.msg_restore_ok)
            }.onFailure { post(restoreErrorMessage(it)) }
        }
    }

    private fun restoreErrorMessage(error: Throwable): Int = when (error) {
        is WrongPassphraseException -> R.string.msg_wrong_passphrase
        is InvalidBackupException -> R.string.msg_invalid_backup
        else -> R.string.msg_restore_failed
    }

    // ---- Dữ liệu ----

    fun addProject(name: String, repo: String) {
        val project = Project(newId(), name.trim(), repo.trim(), System.currentTimeMillis())
        mutate { it.copy(projects = it.projects + project) }
        replaceTop(Screen.ProjectDetail(project.id))
    }

    fun updateProject(id: String, name: String, repo: String) =
        mutate { data -> data.copy(projects = data.projects.map { if (it.id == id) it.copy(name = name.trim(), repo = repo.trim()) else it }) }

    fun deleteProject(id: String) {
        mutate { data ->
            data.copy(
                projects = data.projects.filterNot { it.id == id },
                keys = data.keys.filterNot { it.projectId == id },
                secrets = data.secrets.filterNot { it.projectId == id },
            )
        }
        back()
    }

    /** Tạo nhanh: một dự án và một keystore với giá trị mặc định (alias release, RSA 2048, 30 năm). */
    fun quickKeystore(name: String, repo: String) {
        val project = Project(newId(), name.trim(), repo.trim(), System.currentTimeMillis())
        mutate { it.copy(projects = it.projects + project) }
        generateKeystore(project.id, project.name + " release", KeystoreRequest("release", project.name, "", "", 30, 2048))
    }

    fun generateKeystore(projectId: String, label: String, request: KeystoreRequest) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val result = withContext(Dispatchers.Default) {
                runCatching { KeystoreGenerator.generate(request, KeystoreGenerator.generatePassword()) }
            }
            _state.update { it.copy(busy = false) }
            result.onSuccess { generated ->
                val item = KeyItem(
                    id = newId(), projectId = projectId, label = label.trim(), alias = generated.info.alias,
                    storePassword = generated.password, keyPassword = generated.password,
                    keystoreB64 = Base64.getEncoder().encodeToString(generated.bytes),
                    sha1 = generated.info.sha1, sha256 = generated.info.sha256,
                    validUntil = generated.info.validUntil, createdAt = System.currentTimeMillis(),
                )
                mutate { it.copy(keys = it.keys + item) }
                replaceTop(Screen.KeyDetail(item.id))
                post(R.string.msg_keystore_created)
            }.onFailure { post(R.string.msg_keystore_failed) }
        }
    }

    fun importKeystore(projectId: String, label: String, bytes: ByteArray, storePassword: String, keyPassword: String, alias: String) {
        viewModelScope.launch {
            _state.update { it.copy(busy = true) }
            val keyPw = keyPassword.ifEmpty { storePassword }
            val result = withContext(Dispatchers.Default) {
                runCatching { KeystoreGenerator.inspect(bytes, storePassword, keyPw, alias) }
            }
            _state.update { it.copy(busy = false) }
            result.onSuccess { info ->
                val item = KeyItem(
                    id = newId(), projectId = projectId, label = label.trim(), alias = info.alias,
                    storePassword = storePassword, keyPassword = keyPw,
                    keystoreB64 = Base64.getEncoder().encodeToString(bytes),
                    sha1 = info.sha1, sha256 = info.sha256, validUntil = info.validUntil, createdAt = System.currentTimeMillis(),
                )
                mutate { it.copy(keys = it.keys + item) }
                replaceTop(Screen.KeyDetail(item.id))
            }.onFailure { post(R.string.msg_import_failed) }
        }
    }

    fun deleteKey(id: String) {
        mutate { data -> data.copy(keys = data.keys.filterNot { it.id == id }) }
        back()
    }

    fun addSecret(projectId: String, name: String, value: String, isFile: Boolean, fileName: String) {
        val item = SecretItem(newId(), projectId, name.trim(), value, isFile, fileName, System.currentTimeMillis())
        mutate { it.copy(secrets = it.secrets + item) }
        replaceTop(Screen.SecretDetail(item.id))
    }

    fun deleteSecret(id: String) {
        mutate { data -> data.copy(secrets = data.secrets.filterNot { it.id == id }) }
        back()
    }

    private companion object {
        const val EXTERNAL_GRACE_SEC = 180
    }
}
