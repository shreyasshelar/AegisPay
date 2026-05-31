package com.aegispay.android.ui.wallet

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aegispay.android.network.Account
import com.stripe.android.PaymentConfiguration
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import com.stripe.android.paymentsheet.rememberPaymentSheet
import java.text.NumberFormat
import java.util.Locale

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WalletScreen(
    viewModel:    WalletViewModel,
    onNavigateUp: () -> Unit,
) {
    val uiState      by viewModel.uiState.collectAsState()
    val clientSecret by viewModel.clientSecret.collectAsState()
    val isLoading    by viewModel.isTopUpLoading.collectAsState()

    // ── Stripe PaymentSheet ───────────────────────────────────────────────────
    // rememberPaymentSheet registers an ActivityResultLauncher and delivers
    // the payment result via the callback.
    val paymentSheet = rememberPaymentSheet { result: PaymentSheetResult ->
        when (result) {
            is PaymentSheetResult.Completed -> {
                // Payment confirmed by Stripe SDK; notify backend to credit balance.
                // The paymentIntentId is embedded in the clientSecret (pi_xxx_secret_yyy → "pi_xxx").
                val piId = viewModel.pendingPaymentIntentId
                if (piId != null) viewModel.confirmTopUp(piId)
            }
            is PaymentSheetResult.Canceled  -> viewModel.clearTopUpResult()
            is PaymentSheetResult.Failed    -> viewModel.onPaymentSheetFailed(result.error.message)
        }
    }

    // Present PaymentSheet whenever a new clientSecret arrives from the backend.
    LaunchedEffect(clientSecret) {
        val secret = clientSecret ?: return@LaunchedEffect
        val config = PaymentSheet.Configuration(
            merchantDisplayName = "AegisPay",
            allowsDelayedPaymentMethods = false,
        )
        paymentSheet.presentWithPaymentIntent(secret, config)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Wallet") },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::loadAccounts) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            when (val state = uiState) {
                is WalletUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is WalletUiState.Error -> {
                    Column(
                        modifier            = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text      = state.message,
                            color     = MaterialTheme.colorScheme.error,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = viewModel::loadAccounts) { Text("Retry") }
                    }
                }

                is WalletUiState.Success -> {
                    WalletContent(
                        accounts        = state.accounts,
                        topUpResult     = state.topUpResult,
                        isLoading       = isLoading,
                        onTopUp         = { amount -> viewModel.createTopUpIntent(amount) },
                        onDismissResult = viewModel::clearTopUpResult,
                    )
                }
            }
        }
    }
}

// ── Content ───────────────────────────────────────────────────────────────────

@Composable
private fun WalletContent(
    accounts:        List<Account>,
    topUpResult:     TopUpResult?,
    isLoading:       Boolean,
    onTopUp:         (java.math.BigDecimal) -> Unit,
    onDismissResult: () -> Unit,
) {
    var amountText  by remember { mutableStateOf("") }
    var amountError by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {

        // ── Balance cards ──────────────────────────────────────────────────────
        Text(
            text  = "Balances",
            style = MaterialTheme.typography.titleMedium,
        )

        if (accounts.isEmpty()) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text      = "No accounts found. Your wallet will be created automatically after registration.",
                    modifier  = Modifier.padding(16.dp),
                    textAlign = TextAlign.Center,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            accounts.forEach { account -> BalanceCard(account) }
        }

        HorizontalDivider()

        // ── Top-up form ────────────────────────────────────────────────────────
        Text(
            text  = "Add Money",
            style = MaterialTheme.typography.titleMedium,
        )

        OutlinedTextField(
            value         = amountText,
            onValueChange = {
                amountText  = it
                amountError = null
            },
            label   = { Text("Amount (INR)") },
            prefix  = { Text("₹ ") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = amountError != null,
            supportingText = amountError?.let { msg ->
                { Text(msg, color = MaterialTheme.colorScheme.error) }
            },
            modifier = Modifier.fillMaxWidth(),
        )

        // Quick-amount chips
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(500, 1_000, 2_000, 5_000).forEach { preset ->
                FilterChip(
                    selected = amountText == preset.toString(),
                    onClick  = { amountText = preset.toString() },
                    label    = { Text("₹$preset") },
                )
            }
        }

        Button(
            onClick = {
                val parsed = amountText.toBigDecimalOrNull()
                when {
                    parsed == null || parsed <= java.math.BigDecimal.ZERO ->
                        amountError = "Enter a valid amount"
                    parsed < java.math.BigDecimal.ONE ->
                        amountError = "Minimum top-up is ₹1"
                    else -> onTopUp(parsed)
                }
            },
            enabled  = !isLoading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color       = MaterialTheme.colorScheme.onPrimary,
                )
                Spacer(Modifier.width(8.dp))
            }
            Text(if (isLoading) "Preparing payment…" else "Add Money via Card")
        }
    }

    // ── Result banner ──────────────────────────────────────────────────────────
    topUpResult?.let { result ->
        val message = when (result) {
            TopUpResult.SUCCESS -> "Top-up successful! Your balance has been updated."
            TopUpResult.FAILED  -> "Top-up failed. Please try again."
        }
        LaunchedEffect(result) {
            kotlinx.coroutines.delay(3_500)
            onDismissResult()
        }
        Box(
            modifier          = Modifier.fillMaxSize(),
            contentAlignment  = Alignment.BottomCenter,
        ) {
            Snackbar(
                modifier       = Modifier.padding(16.dp),
                containerColor = if (result == TopUpResult.SUCCESS)
                    MaterialTheme.colorScheme.primaryContainer
                else
                    MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(message)
            }
        }
    }
}

// ── Balance Card ──────────────────────────────────────────────────────────────

@Composable
private fun BalanceCard(account: Account) {
    val fmt = NumberFormat.getCurrencyInstance(
        when (account.currency) {
            "INR" -> Locale("en", "IN")
            "USD" -> Locale.US
            "EUR" -> Locale.GERMANY
            else  -> Locale.getDefault()
        }
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text  = account.currency,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = fmt.format(account.availableBalance),
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (account.reservedBalance > java.math.BigDecimal.ZERO) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "Reserved: ${fmt.format(account.reservedBalance)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
        }
    }
}
