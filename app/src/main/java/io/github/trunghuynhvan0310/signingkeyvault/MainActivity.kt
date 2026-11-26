package io.github.trunghuynhvan0310.signingkeyvault

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import io.github.trunghuynhvan0310.signingkeyvault.ui.AppRoot
import io.github.trunghuynhvan0310.signingkeyvault.ui.SkvTheme

class MainActivity : FragmentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Chặn chụp màn hình và ẩn nội dung trong danh sách ứng dụng gần đây.
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        setContent { SkvTheme { AppRoot(vm, requireAuth = ::authenticate) } }
    }

    override fun onStart() {
        super.onStart()
        vm.onActivityStarted()
    }

    override fun onStop() {
        super.onStop()
        vm.onActivityStopped()
    }

    /** Hiện hộp thoại sinh trắc học / khóa màn hình; chạy [onSuccess] khi xác thực thành công. */
    private fun authenticate(onSuccess: () -> Unit) {
        if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) return
        val authenticators = BIOMETRIC_STRONG or DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            vm.showMessage(R.string.msg_no_lock_screen)
            return
        }
        val callback = object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                onSuccess()
            }

            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                val silent = errorCode == BiometricPrompt.ERROR_USER_CANCELED ||
                    errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON ||
                    errorCode == BiometricPrompt.ERROR_CANCELED
                if (!silent) vm.showMessage(R.string.msg_auth_error, errString.toString())
            }
        }
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(getString(R.string.auth_title))
            .setSubtitle(getString(R.string.auth_subtitle))
            .setAllowedAuthenticators(authenticators)
            .build()
        BiometricPrompt(this, ContextCompat.getMainExecutor(this), callback).authenticate(info)
    }
}
