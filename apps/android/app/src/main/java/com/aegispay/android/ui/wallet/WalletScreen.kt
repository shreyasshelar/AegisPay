package com.aegispay.android.ui.wallet

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aegispay.android.network.Account
import com.aegispay.android.network.FxRates
import com.stripe.android.paymentsheet.PaymentSheet
import com.stripe.android.paymentsheet.PaymentSheetResult
import com.stripe.android.paymentsheet.rememberPaymentSheet
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

// ── Currency locale helpers ───────────────────────────────────────────────────

/**
 * Returns the Locale that produces correct digit-grouping for [currency].
 *   INR → en_IN   (lakh system: ₹1,00,000)
 *   USD → en_US   ($1,000.00)
 *   GBP → en_GB   (£1,000.00)
 *   EUR → en_IE   (€1,000.00 — English EU locale)
 */
fun currencyLocale(currency: String): Locale = when (currency.uppercase()) {
    "INR" -> Locale("en", "IN")
    "USD" -> Locale.US
    "GBP" -> Locale("en", "GB")
    "EUR" -> Locale("en", "IE")
    else  -> Locale.US
}

fun formatCurrency(amount: BigDecimal, currency: String): String {
    val fmt = NumberFormat.getCurrencyInstance(currencyLocale(currency))
    fmt.currency = java.util.Currency.getInstance(currency)
    return fmt.format(amount)
}

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
    val fxRates      by viewModel.fxRates.collectAsState()
    val fxLoading    by viewModel.fxLoading.collectAsState()

    // ── Stripe PaymentSheet ───────────────────────────────────────────────────
    val paymentSheet = rememberPaymentSheet { result: PaymentSheetResult ->
        when (result) {
            is PaymentSheetResult.Completed -> {
                val piId = viewModel.pendingPaymentIntentId
                if (piId != null) viewModel.confirmTopUp(piId)
            }
            is PaymentSheetResult.Canceled  -> viewModel.clearTopUpResult()
            is PaymentSheetResult.Failed    -> viewModel.onPaymentSheetFailed(result.error.message)
        }
    }

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
                    if (fxLoading) {
                        CircularProgressIndicator(
                            modifier    = Modifier.size(20.dp).padding(end = 8.dp),
                            strokeWidth = 2.dp,
                        )
                    }
                    IconButton(onClick = viewModel::loadAccounts) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
            )
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier.fillMaxSize().padding(innerPadding),
        ) {
            when (val state = uiState) {
                is WalletUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }

                is WalletUiState.Error -> {
                    Column(
                        modifier            = Modifier.align(Alignment.Center).padding(24.dp),
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
                        fxRates         = fxRates,
                        inrRoom         = viewModel.inrRoom,
                        wouldExceedLimit = { viewModel.wouldExceedLimit(it) },
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
    accounts:         List<Account>,
    topUpResult:      TopUpResult?,
    isLoading:        Boolean,
    fxRates:          FxRates,
    inrRoom:          BigDecimal,
    wouldExceedLimit: (BigDecimal) -> Boolean,
    onTopUp:          (BigDecimal) -> Unit,
    onDismissResult:  () -> Unit,
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
        Text(text = "Balances", style = MaterialTheme.typography.titleMedium)

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
            accounts.forEach { account ->
                BalanceCard(account = account, fxRates = fxRates)
            }
        }

        // ── Wallet limit info ──────────────────────────────────────────────────
        val inrAccount = accounts.firstOrNull { it.currency == "INR" }
        if (inrAccount != null) {
            val limitFormatted = formatCurrency(BALANCE_LIMIT_INR, "INR")
            val roomFormatted  = formatCurrency(inrRoom, "INR")
            val gbpEquiv       = fxRates.forCurrency("GBP")?.let { rate ->
                if (rate > 0) BALANCE_LIMIT_INR.multiply(BigDecimal(rate)).setScale(0, RoundingMode.HALF_UP) else null
            }

            Surface(
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Icon(
                        Icons.Default.Info,
                        contentDescription = null,
                        tint     = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(16.dp).padding(top = 1.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        val limitLine = if (gbpEquiv != null)
                            "Wallet limit: $limitFormatted (~${formatCurrency(gbpEquiv, "GBP")} live)"
                        else "Wallet limit: $limitFormatted"
                        Text(limitLine, style = MaterialTheme.typography.bodySmall,
                             color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            text  = "Room: $roomFormatted",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (inrRoom <= BigDecimal.ZERO)
                                MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        HorizontalDivider()

        // ── Top-up form ────────────────────────────────────────────────────────
        Text(text = "Add Money", style = MaterialTheme.typography.titleMedium)

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

        // Quick-amount chips — disable presets that exceed the remaining room
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf(500, 1_000, 2_000, 5_000).forEach { preset ->
                val presetBd = BigDecimal(preset)
                FilterChip(
                    selected = amountText == preset.toString(),
                    onClick  = { amountText = preset.toString(); amountError = null },
                    label    = { Text("₹$preset") },
                    enabled  = presetBd <= inrRoom,
                )
            }
        }

        Button(
            onClick = {
                val parsed = amountText.toBigDecimalOrNull()
                when {
                    parsed == null || parsed <= BigDecimal.ZERO ->
                        amountError = "Enter a valid amount"
                    parsed < BigDecimal.ONE ->
                        amountError = "Minimum top-up is ₹1"
                    wouldExceedLimit(parsed) ->
                        amountError = "Exceeds wallet limit of ${formatCurrency(BALANCE_LIMIT_INR, "INR")}. Room: ${formatCurrency(inrRoom, "INR")}"
                    else -> onTopUp(parsed)
                }
            },
            enabled  = !isLoading && inrRoom > BigDecimal.ZERO,
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
            modifier         = Modifier.fillMaxSize(),
            contentAlignment = Alignment.BottomCenter,
        ) {
            Snackbar(
                modifier       = Modifier.padding(16.dp),
                containerColor = if (result == TopUpResult.SUCCESS)
                    MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.errorContainer,
            ) {
                Text(message)
            }
        }
    }
}

// ── Balance Card ──────────────────────────────────────────────────────────────

@Composable
private fun BalanceCard(account: Account, fxRates: FxRates) {
    val formatted = formatCurrency(account.availableBalance, account.currency)
    val reservedFormatted = formatCurrency(account.reservedBalance, account.currency)

    // For non-INR accounts show approx INR equivalent
    val inrEquivText: String? = if (account.currency != "INR") {
        fxRates.toInr(account.availableBalance, account.currency)
            ?.setScale(0, RoundingMode.HALF_UP)
            ?.let { "≈ ${formatCurrency(it, "INR")} at live rate" }
    } else null

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors   = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text  = account.currency,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text  = formatted,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            if (account.reservedBalance > BigDecimal.ZERO) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text  = "Reserved: $reservedFormatted",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                )
            }
            if (inrEquivText != null) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text  = inrEquivText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.55f),
                )
            }
        }
    }
}
