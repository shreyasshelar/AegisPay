package com.aegispay.android.ui.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegispay.android.auth.AuthState
import com.aegispay.android.ui.theme.AegisColor

@Composable
fun LoginScreen(
    viewModel:        AuthViewModel,
    onStartAuthFlow:  () -> Unit,
    onNavigateToDocs: () -> Unit = {},
) {
    val authState  by viewModel.authState.collectAsState()
    val loginError by viewModel.loginError.collectAsState()
    val isLoading = authState is AuthState.Loading

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(AegisColor.Primary, AegisColor.PrimaryDark),
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier            = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            // Logo placeholder
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(20.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = "A",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineLarge.copy(fontWeight = FontWeight.Bold),
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text  = "AegisPay",
                    color = Color.White,
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    text      = "Secure payments, powered by intelligence",
                    color     = Color.White.copy(alpha = 0.8f),
                    style     = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                )
            }

            Spacer(Modifier.height(16.dp))

            // Error banner — shown when OAuth callback returns without a valid session
            if (loginError != null) {
                Card(
                    shape  = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text     = loginError!!,
                        color    = Color.White,
                        style    = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }

            Button(
                onClick  = onStartAuthFlow,
                enabled  = !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape  = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor   = AegisColor.Primary,
                ),
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier    = Modifier.size(20.dp),
                        color       = AegisColor.Primary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Text(
                        text       = "Sign in with your organisation",
                        fontWeight = FontWeight.SemiBold,
                        fontSize   = 15.sp,
                    )
                }
            }

            Text(
                text      = "By signing in you agree to our Terms of Service\nand Privacy Policy",
                color     = Color.White.copy(alpha = 0.6f),
                style     = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )

            TextButton(
                onClick = onNavigateToDocs,
                colors  = ButtonDefaults.textButtonColors(contentColor = Color.White.copy(alpha = 0.8f)),
            ) {
                Text(
                    text  = "📄  Architecture & Developer Docs",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
