package com.example

import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.ui.CacheCleanerApp
import com.example.ui.viewmodel.CacheCleanerViewModel
import com.example.ui.theme.MyApplicationTheme

class MainActivity : FragmentActivity() {

    private val viewModel: CacheCleanerViewModel by viewModels()

    companion object {
        const val PERMISSION_REQ_CODE = 1001
    }

    fun requestSystemPermissions(permissions: Array<String>) {
        ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQ_CODE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme(
                themeOption = viewModel.appThemeSetting,
                darkTheme = viewModel.isAppDarkTheme
            ) {
                CacheCleanerApp(viewModel)
            }
        }

        // Trigger biometric authorization request on startup
        showBiometricPrompt()
    }

    private fun showBiometricPrompt() {
        val biometricManager = BiometricManager.from(this)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or 
                BiometricManager.Authenticators.DEVICE_CREDENTIAL

        if (biometricManager.canAuthenticate(authenticators) == BiometricManager.BIOMETRIC_SUCCESS) {
            val executor = ContextCompat.getMainExecutor(this)
            val biometricPrompt = BiometricPrompt(this, executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        // Trigger secure PIN screen fallback on UI
                        Toast.makeText(this@MainActivity, "Biometrics unavailable. Enter secure PIN bypass.", Toast.LENGTH_LONG).show()
                    }

                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        viewModel.isUserAuthenticated = true
                        Toast.makeText(this@MainActivity, "Welcome Back", Toast.LENGTH_SHORT).show()
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                    }
                })

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle("Secure System Access")
                .setSubtitle("Use Biometrics or screen credentials to unlock Cache Cleaner.")
                .setAllowedAuthenticators(authenticators)
                .build()

            biometricPrompt.authenticate(promptInfo)
        } else {
            // Emulators or devices without hardware enrollments
            Toast.makeText(this, "Security Shield Enabled: Authenticate with fallback PIN.", Toast.LENGTH_LONG).show()
        }
    }
}

