package com.aegispay.android.ui.onboarding

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aegispay.android.ui.theme.AegisColor

/**
 * First-time registration screen.
 * Shown when the user has a valid Keycloak JWT but no AegisPay account yet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OnboardingScreen(
    viewModel:    OnboardingViewModel,
    prefillEmail: String?,
    onSignOut:    () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    // Pre-fill email from Keycloak JWT once
    LaunchedEffect(prefillEmail) {
        if (prefillEmail != null && uiState.email.isEmpty()) {
            viewModel.onEmailChange(prefillEmail)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Welcome", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AegisColor.Bg),
            )
        },
        containerColor = AegisColor.Bg,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {

            // ── Hero ──────────────────────────────────────────────────────────
            Column(
                modifier            = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = MaterialTheme.shapes.extraLarge,
                    color = AegisColor.Primary.copy(alpha = 0.1f),
                    modifier = Modifier.size(80.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector        = Icons.Default.PersonAdd,
                            contentDescription = null,
                            tint               = AegisColor.Primary,
                            modifier           = Modifier.size(40.dp),
                        )
                    }
                }
                Text(
                    text  = "Create your profile",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text  = "Just a few details to finish setting up your AegisPay account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }

            // ── Form card ─────────────────────────────────────────────────────
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
            ) {
                Column(
                    modifier            = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    OutlinedTextField(
                        value         = uiState.firstName,
                        onValueChange = viewModel::onFirstNameChange,
                        label         = { Text("First name") },
                        placeholder   = { Text("e.g. Aarav") },
                        isError       = viewModel.firstNameError != null,
                        supportingText = viewModel.firstNameError?.let { { Text(it) } },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction      = ImeAction.Next,
                        ),
                    )
                    OutlinedTextField(
                        value         = uiState.lastName,
                        onValueChange = viewModel::onLastNameChange,
                        label         = { Text("Last name") },
                        placeholder   = { Text("e.g. Sharma") },
                        isError       = viewModel.lastNameError != null,
                        supportingText = viewModel.lastNameError?.let { { Text(it) } },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction      = ImeAction.Next,
                        ),
                    )
                    OutlinedTextField(
                        value         = uiState.email,
                        onValueChange = viewModel::onEmailChange,
                        label         = { Text("Email") },
                        placeholder   = { Text("your@email.com") },
                        isError       = viewModel.emailError != null,
                        supportingText = viewModel.emailError?.let { { Text(it) } },
                        singleLine    = true,
                        modifier      = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction    = ImeAction.Done,
                        ),
                    )
                }
            }

            // ── Error banner ──────────────────────────────────────────────────
            uiState.errorMessage?.let { err ->
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier            = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment   = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector        = Icons.Default.Warning,
                            contentDescription = null,
                            tint               = MaterialTheme.colorScheme.onErrorContainer,
                            modifier           = Modifier.size(20.dp),
                        )
                        Text(
                            text  = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }

            // ── CTA ───────────────────────────────────────────────────────────
            Button(
                onClick  = viewModel::register,
                enabled  = viewModel.isValid && !uiState.isSubmitting,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                colors = ButtonDefaults.buttonColors(containerColor = AegisColor.Primary),
            ) {
                if (uiState.isSubmitting) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color    = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("Creating account…")
                } else {
                    Text("Get started", fontWeight = FontWeight.SemiBold)
                }
            }

            // ── Sign-out link ─────────────────────────────────────────────────
            TextButton(
                onClick  = onSignOut,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            ) {
                Text(
                    text  = "Sign in with a different account",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
