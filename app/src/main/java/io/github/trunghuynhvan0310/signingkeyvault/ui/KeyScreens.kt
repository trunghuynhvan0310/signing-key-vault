package io.github.trunghuynhvan0310.signingkeyvault.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.trunghuynhvan0310.signingkeyvault.AppViewModel
import io.github.trunghuynhvan0310.signingkeyvault.R
import io.github.trunghuynhvan0310.signingkeyvault.UiState
import io.github.trunghuynhvan0310.signingkeyvault.crypto.KeystoreRequest
import java.util.Base64

private const val MAX_IMPORT_BYTES = 1_000_000

@Composable
fun QuickKeystoreScreen(vm: AppViewModel) {
    var name by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("") }
    val repoValid = repo.isEmpty() || REPO_REGEX.matches(repo.trim())
    ScreenFrame(title = stringResource(R.string.title_quick_keystore), onBack = { vm.back() }) {
        Field(stringResource(R.string.field_app_name), name, { name = it })
        Field(
            label = stringResource(R.string.field_repo),
            value = repo,
            onChange = { repo = it },
            supporting = stringResource(if (repoValid) R.string.hint_repo else R.string.error_repo),
        )
        Button(
            onClick = { vm.quickKeystore(name, repo) },
            enabled = name.isNotBlank() && repoValid,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.action_generate)) }
        Text(stringResource(R.string.quick_info), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun NewKeystoreScreen(vm: AppViewModel, state: UiState, projectId: String) {
    val project = state.data.projects.firstOrNull { it.id == projectId }
    var label by remember { mutableStateOf((project?.name ?: "") + " release") }
    var advanced by remember { mutableStateOf(false) }
    var alias by remember { mutableStateOf("release") }
    var commonName by remember { mutableStateOf(project?.name ?: "") }
    var organization by remember { mutableStateOf("") }
    var country by remember { mutableStateOf("") }
    var years by remember { mutableStateOf("30") }
    var bits by remember { mutableStateOf(2048) }
    val yearsValue = years.toIntOrNull()
    val valid = label.isNotBlank() && alias.isNotBlank() && yearsValue != null && yearsValue in 1..100 &&
        (country.isEmpty() || country.length == 2)

    ScreenFrame(title = stringResource(R.string.title_new_keystore), onBack = { vm.back() }) {
        Field(stringResource(R.string.field_label), label, { label = it })
        Text(stringResource(R.string.quick_info), style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { advanced = !advanced }) { Text(stringResource(R.string.action_advanced)) }
        if (advanced) {
            Field(stringResource(R.string.field_alias), alias, { alias = it.trim() })
            Field(stringResource(R.string.field_common_name), commonName, { commonName = it })
            Field(stringResource(R.string.field_organization), organization, { organization = it })
            Field(
                label = stringResource(R.string.field_country),
                value = country,
                onChange = { country = it.take(2).uppercase() },
            )
            Field(
                label = stringResource(R.string.field_validity_years),
                value = years,
                onChange = { years = it.filter(Char::isDigit).take(3) },
                keyboardType = KeyboardType.Number,
                supporting = stringResource(R.string.hint_validity),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(2048, 4096).forEach { size ->
                    FilterChip(selected = bits == size, onClick = { bits = size }, label = { Text("RSA $size") })
                }
            }
            if (bits == 4096) Text(stringResource(R.string.hint_slow_4096), style = MaterialTheme.typography.bodySmall)
        }
        Button(
            onClick = {
                vm.generateKeystore(
                    projectId,
                    label,
                    KeystoreRequest(alias, commonName, organization, country, yearsValue ?: 30, bits),
                )
            },
            enabled = valid,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.action_generate)) }
    }
}

@Composable
fun ImportKeystoreScreen(vm: AppViewModel, projectId: String) {
    val context = LocalContext.current
    var bytes by remember { mutableStateOf<ByteArray?>(null) }
    var fileError by remember { mutableStateOf(false) }
    var label by remember { mutableStateOf("") }
    var alias by remember { mutableStateOf("") }
    var storePassword by remember { mutableStateOf("") }
    var keyPassword by remember { mutableStateOf("") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            val data = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            fileError = data == null || data.size > MAX_IMPORT_BYTES
            bytes = if (fileError) null else data
        }
    }

    ScreenFrame(title = stringResource(R.string.title_import_keystore), onBack = { vm.back() }) {
        InfoCard(stringResource(R.string.keystore_import_info))
        OutlinedButton(onClick = { vm.markExternal(); picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(if (bytes == null) R.string.action_choose_file else R.string.file_chosen))
        }
        if (fileError) Text(stringResource(R.string.error_file), color = MaterialTheme.colorScheme.error)
        Field(stringResource(R.string.field_label), label, { label = it })
        Field(stringResource(R.string.field_store_password), storePassword, { storePassword = it }, password = true)
        Field(
            label = stringResource(R.string.field_key_password),
            value = keyPassword,
            onChange = { keyPassword = it },
            password = true,
            supporting = stringResource(R.string.hint_key_password_same),
        )
        Field(
            label = stringResource(R.string.field_alias_optional),
            value = alias,
            onChange = { alias = it.trim() },
        )
        Button(
            onClick = { bytes?.let { vm.importKeystore(projectId, label, it, storePassword, keyPassword, alias) } },
            enabled = bytes != null && label.isNotBlank() && storePassword.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.action_import)) }
    }
}

@Composable
fun KeyDetailScreen(vm: AppViewModel, state: UiState, id: String) {
    val context = LocalContext.current
    var confirmDelete by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var doneRaw by rememberSaveable { mutableStateOf("") }
    val done = doneRaw.split(",").filter { it.isNotEmpty() }.toSet()
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-pkcs12")) { uri: Uri? ->
        val item = state.data.keys.firstOrNull { it.id == id }
        if (uri != null && item != null) {
            val ok = runCatching {
                context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(Base64.getDecoder().decode(item.keystoreB64)) }
            }.isSuccess
            vm.showMessage(if (ok) R.string.msg_exported else R.string.msg_export_failed)
        }
    }
    val key = state.data.keys.firstOrNull { it.id == id }
    if (key == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val repo = state.data.projects.firstOrNull { it.id == key.projectId }?.repo.orEmpty()
    val copied = { isValue: Boolean, name: String ->
        if (isValue && name !in done) doneRaw = (done + name).joinToString(",")
        vm.showMessage(R.string.msg_copied)
    }

    ScreenFrame(title = key.label.ifBlank { key.alias }, onBack = { vm.back() }) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(R.string.key_info_valid, formatDate(key.validUntil)), style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = { details = !details }) { Text(stringResource(R.string.action_details)) }
                if (details) {
                    SelectionContainer {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(stringResource(R.string.key_info_alias, key.alias), style = MaterialTheme.typography.bodyMedium)
                            Text("SHA-1", style = MaterialTheme.typography.labelMedium)
                            Text(key.sha1, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                            Text("SHA-256", style = MaterialTheme.typography.labelMedium)
                            Text(key.sha256, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                        }
                    }
                }
            }
        }

        SectionTitle(stringResource(R.string.section_github, done.size.coerceAtMost(4)))
        Text(stringResource(R.string.github_steps), style = MaterialTheme.typography.bodySmall)
        if (repo.isNotEmpty()) {
            Button(
                onClick = {
                    vm.markExternal()
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/$repo/settings/secrets/actions/new"))
                    runCatching { context.startActivity(intent) }
                },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.action_open_github)) }
        } else {
            Text(stringResource(R.string.hint_set_repo), style = MaterialTheme.typography.bodySmall)
        }
        CopyRow("KEYSTORE_BASE64", key.keystoreB64, "${key.keystoreB64.take(24)}… (${key.keystoreB64.length})", "KEYSTORE_BASE64" in done) { copied(it, "KEYSTORE_BASE64") }
        CopyRow("KEYSTORE_PASSWORD", key.storePassword, masked(key.storePassword), "KEYSTORE_PASSWORD" in done) { copied(it, "KEYSTORE_PASSWORD") }
        CopyRow("KEY_ALIAS", key.alias, key.alias, "KEY_ALIAS" in done) { copied(it, "KEY_ALIAS") }
        CopyRow("KEY_PASSWORD", key.keyPassword, masked(key.keyPassword), "KEY_PASSWORD" in done) { copied(it, "KEY_PASSWORD") }

        OutlinedButton(
            onClick = { vm.markExternal(); exportLauncher.launch(key.alias + ".p12") },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.action_export_keystore)) }
        TextButton(onClick = { confirmDelete = true }) {
            Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error)
        }
    }

    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.dlg_delete_key_title),
            text = stringResource(R.string.dlg_delete_key_text),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { confirmDelete = false; vm.deleteKey(id) },
            onDismiss = { confirmDelete = false },
        )
    }
}
