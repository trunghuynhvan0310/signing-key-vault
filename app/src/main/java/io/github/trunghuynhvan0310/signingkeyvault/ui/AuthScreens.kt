package io.github.trunghuynhvan0310.signingkeyvault.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.trunghuynhvan0310.signingkeyvault.AppViewModel
import io.github.trunghuynhvan0310.signingkeyvault.R
import kotlinx.coroutines.delay

/** [requireAuth] hiện BiometricPrompt rồi chạy khối lệnh nếu xác thực thành công. */
typealias RequireAuth = (() -> Unit) -> Unit

@Composable
fun OnboardingScreen(vm: AppViewModel, requireAuth: RequireAuth) {
    val points = listOf(
        Icons.Default.Lock to R.string.onb_point1,
        Icons.Default.Warning to R.string.onb_point2,
        Icons.Default.CheckCircle to R.string.onb_point3,
    )
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text(stringResource(R.string.onb_title), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(24.dp))
            points.forEach { (icon, text) ->
                Row(Modifier.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Text(stringResource(text), style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { requireAuth { vm.createVault() } }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.action_create_vault))
            }
            RestoreNewButton(vm, requireAuth)
        }
    }
}

@Composable
fun LockedScreen(vm: AppViewModel, requireAuth: RequireAuth) {
    LaunchedEffect(Unit) {
        delay(300)
        requireAuth { vm.unlock() }
    }
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(Icons.Default.Lock, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.locked_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(24.dp))
        Button(onClick = { requireAuth { vm.unlock() } }) { Text(stringResource(R.string.action_unlock)) }
    }
}

/** Khóa thiết bị bị mất (ví dụ gỡ khóa màn hình) hoặc vault hỏng: chỉ còn cách khôi phục từ bản sao lưu. */
@Composable
fun KeyLostScreen(vm: AppViewModel, requireAuth: RequireAuth) {
    var confirmWipe by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier.fillMaxSize().safeDrawingPadding().padding(24.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(stringResource(R.string.keylost_title), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.keylost_text), style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(24.dp))
        RestoreNewButton(vm, requireAuth)
        TextButton(onClick = { confirmWipe = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.action_wipe_start_over))
        }
    }
    if (confirmWipe) {
        ConfirmDialog(
            title = stringResource(R.string.dlg_wipe_title),
            text = stringResource(R.string.dlg_wipe_text),
            confirmLabel = stringResource(R.string.action_wipe),
            onConfirm = { confirmWipe = false; vm.wipeAll() },
            onDismiss = { confirmWipe = false },
        )
    }
}

/** Chọn tệp .vault, nhập mật khẩu sao lưu, xác thực thiết bị rồi tạo kho mới từ bản sao lưu. */
@Composable
fun RestoreNewButton(vm: AppViewModel, requireAuth: RequireAuth) {
    var picked by remember { mutableStateOf<Uri?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { picked = it }
    OutlinedButton(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.action_restore_file))
    }
    picked?.let { uri ->
        PassphraseDialog(
            title = stringResource(R.string.dlg_restore_title),
            hint = stringResource(R.string.dlg_restore_hint),
            confirm = false,
            onDone = { passphrase ->
                picked = null
                requireAuth { vm.restoreNew(uri, passphrase) }
            },
            onDismiss = { picked = null },
        )
    }
}
