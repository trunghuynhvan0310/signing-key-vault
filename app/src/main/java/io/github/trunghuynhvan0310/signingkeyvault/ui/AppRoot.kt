package io.github.trunghuynhvan0310.signingkeyvault.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.trunghuynhvan0310.signingkeyvault.AppViewModel
import io.github.trunghuynhvan0310.signingkeyvault.Phase
import io.github.trunghuynhvan0310.signingkeyvault.R
import io.github.trunghuynhvan0310.signingkeyvault.Screen

@Composable
fun AppRoot(vm: AppViewModel, requireAuth: RequireAuth) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            val text = context.getString(message.res, message.arg.orEmpty())
            vm.consumeMessage()
            snackbar.showSnackbar(text)
        }
    }
    BackHandler(enabled = state.phase == Phase.Unlocked && state.stack.size > 1) { vm.back() }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            when (state.phase) {
                Phase.Onboarding -> OnboardingScreen(vm, requireAuth)
                Phase.Locked -> LockedScreen(vm, requireAuth)
                Phase.KeyLost -> KeyLostScreen(vm, requireAuth)
                Phase.Unlocked -> when (val screen = state.stack.last()) {
                    Screen.Home -> HomeScreen(vm, state)
                    Screen.NewProject -> NewProjectScreen(vm)
                    is Screen.ProjectDetail -> ProjectDetailScreen(vm, state, screen.id)
                    is Screen.NewKeystore -> NewKeystoreScreen(vm, state, screen.projectId)
                    is Screen.ImportKeystore -> ImportKeystoreScreen(vm, screen.projectId)
                    is Screen.KeyDetail -> KeyDetailScreen(vm, state, screen.id)
                    is Screen.NewSecret -> NewSecretScreen(vm, screen.projectId)
                    is Screen.SecretDetail -> SecretDetailScreen(vm, state, screen.id)
                    Screen.Backup -> BackupScreen(vm, state)
                    Screen.Settings -> SettingsScreen(vm, state)
                }
            }
            SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
            if (state.busy) {
                Box(
                    Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)).clickable(enabled = true, onClick = {}),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        CircularProgressIndicator()
                        Text(stringResource(R.string.busy_working), color = Color.White, modifier = Modifier.padding(8.dp))
                    }
                }
            }
        }
    }
}
