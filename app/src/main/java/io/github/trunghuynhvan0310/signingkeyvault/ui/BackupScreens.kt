package io.github.trunghuynhvan0310.signingkeyvault.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import io.github.trunghuynhvan0310.signingkeyvault.AppViewModel
import io.github.trunghuynhvan0310.signingkeyvault.R
import io.github.trunghuynhvan0310.signingkeyvault.Screen
import io.github.trunghuynhvan0310.signingkeyvault.UiState

/** Thẻ tóm tắt tình trạng sao lưu: một dòng, màu xanh khi ổn, màu đỏ khi cần xử lý. */
@Composable
fun BackupHealthCard(state: UiState, onOpen: (() -> Unit)?) {
    val status = state.backup
    val hasKey = state.data.backup != null
    val ok: Boolean
    val text: String
    when {
        !hasKey || status.folderName == null -> { ok = false; text = stringResource(R.string.health_not_set) }
        status.error != null -> { ok = false; text = stringResource(R.string.health_failed, status.error) }
        status.lastBackupAt == 0L -> { ok = false; text = stringResource(R.string.health_never) }
        status.passphraseVerifiedAt == 0L -> { ok = true; text = stringResource(R.string.health_unverified, formatDateTime(status.lastBackupAt)) }
        else -> { ok = true; text = stringResource(R.string.health_ok, formatDateTime(status.lastBackupAt)) }
    }
    val colors = CardDefaults.cardColors(
        containerColor = if (ok) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.errorContainer,
    )
    val content: @Composable ColumnScope.() -> Unit = {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Warning, contentDescription = null)
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
    if (onOpen != null) {
        Card(onClick = onOpen, modifier = Modifier.fillMaxWidth(), colors = colors, content = content)
    } else {
        Card(modifier = Modifier.fillMaxWidth(), colors = colors, content = content)
    }
}

@Composable
fun BackupScreen(vm: AppViewModel, state: UiState) {
    var setDialog by remember { mutableStateOf(false) }
    var verifyDialog by remember { mutableStateOf(false) }
    var advanced by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<Uri?>(null) }
    val hasKey = state.data.backup != null
    val hasFolder = state.backup.folderName != null

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri: Uri? ->
        if (uri != null) {
            vm.setBackupFolder(uri)
            if (!hasKey) setDialog = true
        }
    }
    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null) vm.exportCopy(uri)
    }
    val restorePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? -> restoreUri = uri }

    ScreenFrame(title = stringResource(R.string.title_backup), onBack = { vm.back() }) {
        BackupHealthCard(state, onOpen = null)
        if (!hasKey || !hasFolder) {
            Text(stringResource(R.string.backup_setup_info), style = MaterialTheme.typography.bodyMedium)
            Button(
                onClick = {
                    if (!hasFolder) { vm.markExternal(); folderPicker.launch(null) } else setDialog = true
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_setup_backup)) }
        } else {
            state.backup.folderName?.let { Text(stringResource(R.string.backup_folder_current, it), style = MaterialTheme.typography.bodyMedium) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.backupNow() }) { Text(stringResource(R.string.action_backup_now)) }
                OutlinedButton(onClick = { vm.verifyFolder() }) { Text(stringResource(R.string.action_verify_folder)) }
            }
            if (state.backup.passphraseVerifiedAt == 0L) {
                OutlinedButton(onClick = { verifyDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_verify_passphrase))
                }
            }
        }

        TextButton(onClick = { advanced = !advanced }) { Text(stringResource(R.string.action_advanced)) }
        if (advanced) {
            OutlinedButton(onClick = { vm.markExternal(); folderPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_choose_folder))
            }
            OutlinedButton(onClick = { setDialog = true }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(if (hasKey) R.string.action_change_passphrase else R.string.action_set_passphrase))
            }
            if (hasKey) {
                OutlinedButton(onClick = { verifyDialog = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.action_verify_passphrase))
                }
            }
            OutlinedButton(
                onClick = { vm.markExternal(); exportPicker.launch("signing-key-vault-copy.vault") },
                enabled = hasKey,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_export_copy)) }
            OutlinedButton(
                onClick = { vm.markExternal(); restorePicker.launch(arrayOf("*/*")) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_restore_file)) }
            Text(stringResource(R.string.backup_crypto_note), style = MaterialTheme.typography.bodySmall)
        }
    }

    if (setDialog) {
        PassphraseDialog(
            title = stringResource(R.string.dlg_set_passphrase_title),
            hint = stringResource(R.string.dlg_set_passphrase_hint),
            confirm = true,
            onDone = { setDialog = false; vm.setBackupPassphrase(it) },
            onDismiss = { setDialog = false },
        )
    }
    if (verifyDialog) {
        PassphraseDialog(
            title = stringResource(R.string.dlg_verify_title),
            hint = null,
            confirm = false,
            onDone = { verifyDialog = false; vm.verifyPassphrase(it) },
            onDismiss = { verifyDialog = false },
        )
    }
    restoreUri?.let { uri ->
        PassphraseDialog(
            title = stringResource(R.string.dlg_restore_title),
            hint = stringResource(R.string.dlg_restore_merge_hint),
            confirm = false,
            onDone = { restoreUri = null; vm.restoreMerge(uri, it) },
            onDismiss = { restoreUri = null },
        )
    }
}

private val LOCK_OPTIONS = listOf(0 to R.string.lock_immediately, 60 to R.string.lock_1min, 300 to R.string.lock_5min)

@Composable
fun SettingsScreen(vm: AppViewModel, state: UiState) {
    val context = LocalContext.current
    var confirmWipe by remember { mutableStateOf(false) }
    val version = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }.getOrNull().orEmpty()
    }

    ScreenFrame(title = stringResource(R.string.title_settings), onBack = { vm.back() }) {
        SectionTitle(stringResource(R.string.settings_lock))
        Column(Modifier.selectableGroup()) {
            LOCK_OPTIONS.forEach { (seconds, label) ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = state.lockTimeoutSec == seconds, onClick = { vm.setLockTimeout(seconds) }, role = Role.RadioButton)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = state.lockTimeoutSec == seconds, onClick = null)
                    Text(stringResource(label), modifier = Modifier.padding(start = 12.dp))
                }
            }
        }

        SectionTitle(stringResource(R.string.settings_guide))
        InfoCard(stringResource(R.string.guide_text))

        SectionTitle(stringResource(R.string.settings_security))
        Text(stringResource(R.string.security_text), style = MaterialTheme.typography.bodyMedium)

        SectionTitle(stringResource(R.string.settings_about))
        Text(stringResource(R.string.about_text, version), style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(
            onClick = {
                vm.markExternal()
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/trunghuynhvan0310/signing-key-vault")))
                }
            },
        ) { Text(stringResource(R.string.action_open_repo)) }

        TextButton(onClick = { confirmWipe = true }) {
            Text(stringResource(R.string.action_wipe_all), color = MaterialTheme.colorScheme.error)
        }
    }

    if (confirmWipe) {
        ConfirmDialog(
            title = stringResource(R.string.dlg_wipe_title),
            text = stringResource(R.string.dlg_wipe_all_text),
            confirmLabel = stringResource(R.string.action_wipe),
            onConfirm = { confirmWipe = false; vm.wipeAll() },
            onDismiss = { confirmWipe = false },
        )
    }
}
