package io.github.trunghuynhvan0310.signingkeyvault.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.trunghuynhvan0310.signingkeyvault.AppViewModel
import io.github.trunghuynhvan0310.signingkeyvault.R
import io.github.trunghuynhvan0310.signingkeyvault.UiState
import io.github.trunghuynhvan0310.signingkeyvault.crypto.SecretGenerator
import java.util.Base64

private const val MAX_FILE_BYTES = 256 * 1024

@Composable
fun NewSecretScreen(vm: AppViewModel, projectId: String) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var value by remember { mutableStateOf("") }
    var asFile by remember { mutableStateOf(false) }
    var fileName by remember { mutableStateOf("") }
    var fileError by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            if (bytes == null || bytes.size > MAX_FILE_BYTES) {
                fileError = true
            } else {
                fileError = false
                value = Base64.getEncoder().encodeToString(bytes)
                fileName = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':').orEmpty().ifBlank { "file" }
            }
        }
    }

    ScreenFrame(title = stringResource(R.string.title_new_secret), onBack = { vm.back() }) {
        Field(
            label = stringResource(R.string.field_secret_name),
            value = name,
            onChange = { name = it.trim() },
            supporting = stringResource(R.string.hint_secret_name),
            monospace = true,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = !asFile, onClick = { asFile = false; value = ""; fileName = "" }, label = { Text(stringResource(R.string.kind_text)) })
            FilterChip(selected = asFile, onClick = { asFile = true; value = ""; fileName = "" }, label = { Text(stringResource(R.string.kind_file)) })
        }
        if (asFile) {
            OutlinedButton(onClick = { vm.markExternal(); picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                Text(if (fileName.isEmpty()) stringResource(R.string.action_choose_file) else fileName)
            }
            if (fileError) Text(stringResource(R.string.error_file_256), color = MaterialTheme.colorScheme.error)
        } else {
            Field(stringResource(R.string.field_secret_value), value, { value = it }, singleLine = false, monospace = true)
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { value = SecretGenerator.generate(SecretGenerator.Kind.Hex32) }) { Text(stringResource(R.string.gen_hex)) }
                TextButton(onClick = { value = SecretGenerator.generate(SecretGenerator.Kind.Base64_32) }) { Text(stringResource(R.string.gen_base64)) }
                TextButton(onClick = { value = SecretGenerator.generate(SecretGenerator.Kind.Password) }) { Text(stringResource(R.string.gen_password)) }
            }
        }
        Button(
            onClick = { vm.addSecret(projectId, name, value, asFile, fileName) },
            enabled = name.isNotEmpty() && value.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.action_save)) }
    }
}

@Composable
fun SecretDetailScreen(vm: AppViewModel, state: UiState, id: String) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var reveal by remember { mutableStateOf(false) }
    val secret = state.data.secrets.firstOrNull { it.id == id }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri: Uri? ->
        if (uri != null && secret != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(Base64.getDecoder().decode(secret.value)) }
            }.isSuccess
            vm.showMessage(if (ok) R.string.msg_exported else R.string.msg_export_failed)
        }
    }
    if (secret == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }

    ScreenFrame(title = secret.name, onBack = { vm.back() }) {
        if (secret.isFile) {
            Text(stringResource(R.string.secret_file_info, secret.fileName, secret.value.length), style = MaterialTheme.typography.bodyMedium)
            CopyRow(secret.name, secret.value, secret.value.take(24) + "…") { vm.showMessage(R.string.msg_copied) }
            OutlinedButton(
                onClick = { vm.markExternal(); exportLauncher.launch(secret.fileName.ifBlank { "file" }) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_export_file)) }
        } else {
            CopyRow(secret.name, secret.value, if (reveal) secret.value.take(200) else masked(secret.value)) {
                vm.showMessage(R.string.msg_copied)
            }
            TextButton(onClick = { reveal = !reveal }) {
                Text(stringResource(if (reveal) R.string.action_hide else R.string.action_show))
            }
        }
        TextButton(onClick = { confirmDelete = true }) {
            Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.dlg_delete_secret_title),
            text = stringResource(R.string.dlg_delete_secret_text),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { confirmDelete = false; vm.deleteSecret(id) },
            onDismiss = { confirmDelete = false },
        )
    }
}
