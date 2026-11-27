package io.github.trunghuynhvan0310.signingkeyvault.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.trunghuynhvan0310.signingkeyvault.AppViewModel
import io.github.trunghuynhvan0310.signingkeyvault.R
import io.github.trunghuynhvan0310.signingkeyvault.Screen
import io.github.trunghuynhvan0310.signingkeyvault.UiState

internal val REPO_REGEX = Regex("^[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+$")

@Composable
fun HomeScreen(vm: AppViewModel, state: UiState) {
    ScreenFrame(
        title = stringResource(R.string.app_name),
        onBack = null,
        actions = {
            IconButton(onClick = { vm.lock() }) { Icon(Icons.Default.Lock, contentDescription = stringResource(R.string.action_lock)) }
            IconButton(onClick = { vm.navigate(Screen.Settings) }) {
                Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.title_settings))
            }
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { vm.navigate(Screen.QuickKeystore) },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.action_quick_keystore)) },
            )
        },
    ) {
        BackupHealthCard(state, onOpen = { vm.navigate(Screen.Backup) })
        if (state.data.projects.isEmpty()) {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 40.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.primary)
                Text(stringResource(R.string.home_empty), style = MaterialTheme.typography.titleMedium)
            }
        }
        state.data.projects.forEach { project ->
            val keys = state.data.keys.count { it.projectId == project.id }
            val secrets = state.data.secrets.count { it.projectId == project.id }
            Card(onClick = { vm.navigate(Screen.ProjectDetail(project.id)) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(project.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        pluralStringResource(R.plurals.count_keystores, keys, keys) + " · " +
                            pluralStringResource(R.plurals.count_secrets, secrets, secrets),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        TextButton(onClick = { vm.navigate(Screen.NewProject) }) { Text(stringResource(R.string.action_new_project)) }
        Spacer(Modifier.height(72.dp))
    }
}

@Composable
fun NewProjectScreen(vm: AppViewModel) {
    var name by remember { mutableStateOf("") }
    var repo by remember { mutableStateOf("") }
    val repoValid = repo.isEmpty() || REPO_REGEX.matches(repo.trim())
    ScreenFrame(title = stringResource(R.string.title_new_project), onBack = { vm.back() }) {
        Field(stringResource(R.string.field_project_name), name, { name = it })
        Field(
            label = stringResource(R.string.field_repo),
            value = repo,
            onChange = { repo = it },
            supporting = stringResource(if (repoValid) R.string.hint_repo else R.string.error_repo),
        )
        Button(
            onClick = { vm.addProject(name, repo) },
            enabled = name.isNotBlank() && repoValid,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.action_create)) }
    }
}

@Composable
fun ProjectDetailScreen(vm: AppViewModel, state: UiState, id: String) {
    var editing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val project = state.data.projects.firstOrNull { it.id == id }
    if (project == null) {
        LaunchedEffect(Unit) { vm.back() }
        return
    }
    val keys = state.data.keys.filter { it.projectId == id }
    val secrets = state.data.secrets.filter { it.projectId == id }

    ScreenFrame(
        title = project.name,
        onBack = { vm.back() },
        actions = {
            IconButton(onClick = { editing = true }) {
                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.action_edit))
            }
        },
    ) {
        if (project.repo.isNotEmpty()) Text(project.repo, style = MaterialTheme.typography.bodyMedium)

        SectionTitle(stringResource(R.string.section_keystores))
        if (keys.isEmpty()) Text(stringResource(R.string.project_no_keys), style = MaterialTheme.typography.bodyMedium)
        keys.forEach { key ->
            Card(onClick = { vm.navigate(Screen.KeyDetail(key.id)) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(key.label.ifBlank { key.alias }, style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.key_alias_valid, key.alias, formatDate(key.validUntil)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { vm.navigate(Screen.NewKeystore(id)) }) { Text(stringResource(R.string.action_new_keystore)) }
            OutlinedButton(onClick = { vm.navigate(Screen.ImportKeystore(id)) }) { Text(stringResource(R.string.action_import_keystore)) }
        }

        SectionTitle(stringResource(R.string.section_secrets))
        if (secrets.isEmpty()) Text(stringResource(R.string.project_no_secrets), style = MaterialTheme.typography.bodyMedium)
        secrets.forEach { secret ->
            Card(onClick = { vm.navigate(Screen.SecretDetail(secret.id)) }, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp)) {
                    Text(secret.name, style = MaterialTheme.typography.titleSmall)
                    if (secret.isFile) Text(secret.fileName, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        OutlinedButton(onClick = { vm.navigate(Screen.NewSecret(id)) }) { Text(stringResource(R.string.action_new_secret)) }

        Spacer(Modifier.height(16.dp))
        TextButton(onClick = { confirmDelete = true }) {
            Text(stringResource(R.string.action_delete_project), color = MaterialTheme.colorScheme.error)
        }
    }

    if (editing) {
        var name by remember { mutableStateOf(project.name) }
        var repo by remember { mutableStateOf(project.repo) }
        val repoValid = repo.isEmpty() || REPO_REGEX.matches(repo.trim())
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text(stringResource(R.string.action_edit)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Field(stringResource(R.string.field_project_name), name, { name = it })
                    Field(
                        label = stringResource(R.string.field_repo),
                        value = repo,
                        onChange = { repo = it },
                        supporting = stringResource(if (repoValid) R.string.hint_repo else R.string.error_repo),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = { vm.updateProject(id, name, repo); editing = false },
                    enabled = name.isNotBlank() && repoValid,
                ) { Text(stringResource(R.string.action_save)) }
            },
            dismissButton = { TextButton(onClick = { editing = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = stringResource(R.string.dlg_delete_project_title),
            text = stringResource(R.string.dlg_delete_project_text, keys.size, secrets.size),
            confirmLabel = stringResource(R.string.action_delete),
            onConfirm = { confirmDelete = false; vm.deleteProject(id) },
            onDismiss = { confirmDelete = false },
        )
    }
}
