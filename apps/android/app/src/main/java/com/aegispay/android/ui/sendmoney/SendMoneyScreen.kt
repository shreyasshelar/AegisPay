package com.aegispay.android.ui.sendmoney

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.shape.CircleShape
import com.aegispay.android.network.KycStatus
import com.aegispay.android.network.Transaction
import com.aegispay.android.network.TransactionStatus
import com.aegispay.android.ui.components.AegisCard
import com.aegispay.android.ui.components.AegisStatusTimeline
import com.aegispay.android.ui.components.AegisTextField
import com.aegispay.android.ui.theme.AegisColor

// ── Currencies ────────────────────────────────────────────────────────────────

private val CURRENCIES = listOf("INR", "USD", "EUR", "GBP")

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SendMoneyScreen(
    viewModel:           SendMoneyViewModel,
    onNavigateUp:        () -> Unit,
    onNavigateToDetail:  (String) -> Unit,
    onNavigateToProfile: () -> Unit = {},
) {
    val uiState by viewModel.uiState.collectAsState()
    val context  = LocalContext.current

    // Haptic on COMPLETED
    LaunchedEffect(uiState.createdTransaction?.status) {
        if (uiState.createdTransaction?.status == TransactionStatus.COMPLETED) {
            vibrateSuccess(context)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Send Money", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (uiState.step != SendStep.STATUS) {
                        IconButton(onClick = {
                            if (uiState.step == SendStep.PAYEE) onNavigateUp()
                            else viewModel.back()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AegisColor.Bg),
            )
        },
        containerColor = AegisColor.Bg,
    ) { padding ->
        when {
            // ── KYC loading ───────────────────────────────────────────────────
            uiState.kycLoading -> {
                Box(
                    Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }

            // ── KYC blocked ───────────────────────────────────────────────────
            uiState.kycStatus != null && uiState.kycStatus != KycStatus.APPROVED -> {
                KycBlockedCard(
                    modifier             = Modifier.fillMaxSize().padding(padding),
                    onNavigateToProfile  = onNavigateToProfile,
                )
            }

            // ── Wizard ────────────────────────────────────────────────────────
            else -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                ) {
                    if (uiState.step != SendStep.STATUS) {
                        StepIndicator(current = uiState.step)
                    }
                    AnimatedContent(
                        targetState  = uiState.step,
                        transitionSpec = {
                            slideInHorizontally { it } + fadeIn() togetherWith
                            slideOutHorizontally { -it } + fadeOut()
                        },
                        label = "send-step",
                    ) { step ->
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .verticalScroll(rememberScrollState())
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            when (step) {
                                SendStep.PAYEE  -> PayeeStep(viewModel, uiState)
                                SendStep.AMOUNT -> AmountStep(viewModel, uiState)
                                SendStep.REVIEW -> ReviewStep(viewModel, uiState)
                                SendStep.STATUS -> StatusStep(
                                    uiState           = uiState,
                                    viewModel         = viewModel,
                                    onNavigateToDetail = onNavigateToDetail,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ── KYC blocked card ──────────────────────────────────────────────────────────

@Composable
private fun KycBlockedCard(
    modifier:            Modifier = Modifier,
    onNavigateToProfile: () -> Unit,
) {
    Column(
        modifier            = modifier.padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Surface(
            shape  = CircleShape,
            color  = MaterialTheme.colorScheme.secondaryContainer,
            modifier = Modifier.size(80.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector        = Icons.Default.Shield,
                    contentDescription = null,
                    tint               = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier           = Modifier.size(40.dp),
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text       = "Identity verification required",
            style      = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign  = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text      = "You need to complete KYC verification before sending money. This protects you and your recipients from fraud.",
            style     = MaterialTheme.typography.bodyMedium,
            color     = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick  = onNavigateToProfile,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AegisColor.Primary),
        ) {
            Text("Complete KYC now", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

// ── Step indicator ────────────────────────────────────────────────────────────

@Composable
private fun StepIndicator(current: SendStep) {
    val steps = listOf(SendStep.PAYEE, SendStep.AMOUNT, SendStep.REVIEW)
    val labels = listOf("Payee", "Amount", "Review")
    val currentIdx = steps.indexOf(current).coerceAtLeast(0)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        steps.forEachIndexed { i, _ ->
            val done    = i < currentIdx
            val active  = i == currentIdx

            // Circle
            Box(contentAlignment = Alignment.Center) {
                Surface(
                    shape  = CircleShape,
                    color  = if (done || active) AegisColor.Primary else AegisColor.Border,
                    modifier = Modifier.size(28.dp),
                ) {}
                if (done) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint   = Color.White,
                        modifier = Modifier.size(14.dp),
                    )
                } else {
                    Text(
                        text  = "${i + 1}",
                        color = if (active) Color.White else AegisColor.TextMuted,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                    )
                }
            }

            // Label
            Text(
                text  = labels[i],
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                    color      = if (active) AegisColor.Primary else AegisColor.TextMuted,
                ),
                modifier = Modifier.padding(start = 4.dp),
            )

            // Connector
            if (i < steps.size - 1) {
                HorizontalDivider(
                    modifier  = Modifier.weight(1f).padding(horizontal = 8.dp),
                    thickness = 2.dp,
                    color     = if (i < currentIdx) AegisColor.Primary else AegisColor.Border,
                )
            }
        }
    }
}

// ── Step 1: Payee ─────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.PayeeStep(vm: SendMoneyViewModel, s: SendMoneyUiState) {
    AegisCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StepHeader(
                icon     = Icons.Default.Person,
                title    = "Who are you sending to?",
                subtitle = "Enter the recipient's AegisPay user ID",
            )

            AegisTextField(
                value         = s.payeeId,
                onValueChange = vm::onPayeeIdChange,
                label         = "Payee ID (UUID)",
                placeholder   = "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx",
                error         = if (s.payeeId.isNotEmpty()) vm.payeeIdError else null,
            )

            Surface(
                color  = AegisColor.Surface,
                shape  = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text     = "You can find a recipient's user ID on their AegisPay profile page.",
                    style    = MaterialTheme.typography.bodySmall,
                    color    = AegisColor.TextMuted,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }

    Button(
        onClick  = { vm.goTo(SendStep.AMOUNT) },
        enabled  = vm.isPayeeValid,
        modifier = Modifier.fillMaxWidth().height(52.dp),
        colors   = ButtonDefaults.buttonColors(containerColor = AegisColor.Primary),
    ) {
        Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Text("Continue", fontWeight = FontWeight.SemiBold)
    }
}

// ── Step 2: Amount ────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ColumnScope.AmountStep(vm: SendMoneyViewModel, s: SendMoneyUiState) {
    var currencyExpanded by remember { mutableStateOf(false) }

    AegisCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StepHeader(
                icon     = Icons.Default.AttachMoney,
                title    = "How much to send?",
                subtitle = "Enter the amount and an optional note",
            )

            // Amount + currency row
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                AegisTextField(
                    value         = s.amountText,
                    onValueChange = { v ->
                        val filtered = v.filter { it.isDigit() || it == '.' }.let { raw ->
                            val parts = raw.split('.')
                            when {
                                parts.size > 2 -> parts[0] + "." + parts[1]
                                parts.size == 2 -> parts[0] + "." + parts[1].take(2)
                                else -> raw
                            }
                        }
                        vm.onAmountChange(filtered)
                    },
                    label           = "Amount",
                    placeholder     = "0.00",
                    error           = if (s.amountText.isNotEmpty()) vm.amountError else null,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier        = Modifier.weight(1f),
                )

                // Currency dropdown
                ExposedDropdownMenuBox(
                    expanded         = currencyExpanded,
                    onExpandedChange = { currencyExpanded = !currencyExpanded },
                    modifier         = Modifier.width(110.dp),
                ) {
                    OutlinedTextField(
                        value         = s.currency,
                        onValueChange = {},
                        readOnly      = true,
                        label         = { Text("Currency") },
                        trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(currencyExpanded) },
                        modifier      = Modifier.menuAnchor(),
                        colors        = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor   = AegisColor.Primary,
                            unfocusedBorderColor = AegisColor.Border,
                        ),
                    )
                    ExposedDropdownMenu(
                        expanded         = currencyExpanded,
                        onDismissRequest = { currencyExpanded = false },
                    ) {
                        CURRENCIES.forEach { cur ->
                            DropdownMenuItem(
                                text    = { Text(cur) },
                                onClick = { vm.onCurrencyChange(cur); currencyExpanded = false },
                            )
                        }
                    }
                }
            }

            // Note
            AegisTextField(
                value         = s.note,
                onValueChange = vm::onNoteChange,
                label         = "Note (optional)",
                placeholder   = "What's this for?",
                singleLine    = false,
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick  = { vm.back() },
            modifier = Modifier.weight(1f).height(52.dp),
            border   = BorderStroke(1.dp, AegisColor.Border),
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Back")
        }
        Button(
            onClick  = { vm.goTo(SendStep.REVIEW) },
            enabled  = vm.isAmountValid,
            modifier = Modifier.weight(1f).height(52.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = AegisColor.Primary),
        ) {
            Text("Review", fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
        }
    }
}

// ── Step 3: Review ────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.ReviewStep(vm: SendMoneyViewModel, s: SendMoneyUiState) {
    AegisCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            StepHeader(
                icon     = Icons.Default.Shield,
                title    = "Review your transfer",
                subtitle = "Double-check the details before sending",
            )

            // Amount display
            Surface(
                color    = AegisColor.Surface,
                shape    = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 16.dp),
                ) {
                    Text(
                        text  = "${s.currency} ${s.amountText}",
                        style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                        color = AegisColor.Text,
                    )
                    Text(
                        text  = "TOTAL AMOUNT",
                        style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
                        color = AegisColor.TextMuted,
                    )
                }
            }

            HorizontalDivider(color = AegisColor.Border)

            // Detail rows
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ReviewRow("To", s.payeeId, mono = true)
                ReviewRow("Currency", s.currency)
                if (s.note.isNotBlank()) ReviewRow("Note", s.note)
            }
        }
    }

    // Risk notice
    Surface(
        color  = AegisColor.Warning.copy(alpha = 0.1f),
        shape  = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, AegisColor.Warning.copy(alpha = 0.3f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(12.dp),
        ) {
            Icon(
                Icons.Default.Warning,
                contentDescription = null,
                tint     = AegisColor.Warning,
                modifier = Modifier.size(18.dp).padding(top = 2.dp),
            )
            Text(
                text  = "This transfer will undergo real-time risk assessment. Funds are reserved immediately and released only after all checks pass.",
                style = MaterialTheme.typography.bodySmall,
                color = AegisColor.Warning,
            )
        }
    }

    // Submission error
    s.submissionError?.let { err ->
        Surface(
            color  = AegisColor.Danger.copy(alpha = 0.08f),
            shape  = MaterialTheme.shapes.small,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text     = err,
                color    = AegisColor.Danger,
                style    = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(12.dp),
            )
        }
    }

    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(
            onClick  = { vm.back() },
            enabled  = !s.isSubmitting,
            modifier = Modifier.weight(1f).height(52.dp),
            border   = BorderStroke(1.dp, AegisColor.Border),
        ) {
            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Edit")
        }
        Button(
            onClick  = { vm.submit() },
            enabled  = !s.isSubmitting,
            modifier = Modifier.weight(1f).height(52.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = AegisColor.Primary),
        ) {
            if (s.isSubmitting) {
                CircularProgressIndicator(
                    modifier    = Modifier.size(20.dp),
                    color       = Color.White,
                    strokeWidth = 2.dp,
                )
            } else {
                Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Confirm & Send", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Step 4: Status ────────────────────────────────────────────────────────────

@Composable
private fun ColumnScope.StatusStep(
    uiState:            SendMoneyUiState,
    viewModel:          SendMoneyViewModel,
    onNavigateToDetail: (String) -> Unit,
) {
    val tx = uiState.createdTransaction

    if (tx == null) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.fillMaxWidth().height(240.dp),
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(color = AegisColor.Primary)
                Text("Starting transfer…", color = AegisColor.TextMuted, style = MaterialTheme.typography.bodySmall)
            }
        }
        return
    }

    val isTerminal = tx.status.isTerminal
    val isComplete = tx.status == TransactionStatus.COMPLETED
    val isFailed   = tx.status == TransactionStatus.FAILED || tx.status == TransactionStatus.ROLLED_BACK

    // Main status card
    AegisCard {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Icon
            when {
                isComplete -> Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint     = AegisColor.Success,
                    modifier = Modifier.size(64.dp),
                )
                isFailed -> Icon(
                    Icons.Default.Cancel,
                    contentDescription = null,
                    tint     = AegisColor.Danger,
                    modifier = Modifier.size(64.dp),
                )
                else -> Box(contentAlignment = Alignment.Center, modifier = Modifier.size(64.dp)) {
                    Surface(
                        shape  = CircleShape,
                        color  = AegisColor.Primary.copy(alpha = 0.1f),
                        modifier = Modifier.fillMaxSize(),
                    ) {}
                    CircularProgressIndicator(
                        color        = AegisColor.Primary,
                        strokeWidth  = 3.dp,
                        modifier     = Modifier.size(36.dp),
                    )
                }
            }

            // Amount
            Text(
                text  = "${tx.currency} ${tx.amount}",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = AegisColor.Text,
            )

            // Status label
            Text(
                text  = when {
                    isComplete -> "Transfer Complete"
                    isFailed   -> "Transfer Failed"
                    else       -> "Transfer in Progress…"
                },
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = when {
                    isComplete -> AegisColor.Success
                    isFailed   -> AegisColor.Danger
                    else       -> AegisColor.Primary
                },
            )

            // Live badge
            if (!isTerminal) {
                Surface(
                    color  = AegisColor.Primary.copy(alpha = 0.1f),
                    shape  = MaterialTheme.shapes.extraLarge,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    ) {
                        CircularProgressIndicator(
                            color        = AegisColor.Primary,
                            strokeWidth  = 2.dp,
                            modifier     = Modifier.size(14.dp),
                        )
                        Text(
                            "Live updates active",
                            style = MaterialTheme.typography.labelSmall,
                            color = AegisColor.Primary,
                        )
                    }
                }
            }

            // Timeline
            AegisStatusTimeline(status = tx.status)
        }
    }

    // AI error card
    if (isFailed) {
        AiErrorCard(tx, uiState, viewModel)
    }

    // CTA buttons
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(
            onClick  = { onNavigateToDetail(tx.transactionId) },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = AegisColor.Primary),
        ) {
            Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("View Full Details", fontWeight = FontWeight.SemiBold)
        }

        OutlinedButton(
            onClick  = { viewModel.reset() },
            modifier = Modifier.fillMaxWidth().height(52.dp),
            border   = BorderStroke(1.dp, AegisColor.Border),
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (isFailed) "Try Again" else "Send Another")
        }
    }
}

// ── AI error card ─────────────────────────────────────────────────────────────

@Composable
private fun AiErrorCard(
    tx:        Transaction,
    s:         SendMoneyUiState,
    viewModel: SendMoneyViewModel,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = MaterialTheme.shapes.medium,
        border   = BorderStroke(1.dp, AegisColor.Warning.copy(alpha = 0.3f)),
        colors   = CardDefaults.cardColors(containerColor = AegisColor.Warning.copy(alpha = 0.07f)),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Default.Lightbulb, contentDescription = null, tint = AegisColor.Warning, modifier = Modifier.size(18.dp))
                Text("What went wrong?", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = AegisColor.Text)
            }

            when {
                s.isResolvingError -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(color = AegisColor.Warning, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Text("Analysing failure…", style = MaterialTheme.typography.bodySmall, color = AegisColor.TextMuted)
                }

                s.errorResolution != null -> Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(s.errorResolution.resolution, style = MaterialTheme.typography.bodySmall, color = AegisColor.Text)
                    Text(
                        "Code: ${s.errorResolution.errorCode}",
                        style    = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color    = AegisColor.Warning,
                    )
                }

                tx.failureReason != null -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        tx.failureReason,
                        style    = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color    = AegisColor.TextMuted,
                    )
                    TextButton(
                        onClick = { viewModel.resolveError(tx.failureReason) },
                        contentPadding = PaddingValues(0.dp),
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Retry Analysis", style = MaterialTheme.typography.labelSmall, color = AegisColor.Warning)
                    }
                }
            }
        }
    }
}

// ── Shared sub-components ─────────────────────────────────────────────────────

@Composable
private fun StepHeader(
    icon:     androidx.compose.ui.graphics.vector.ImageVector,
    title:    String,
    subtitle: String,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = AegisColor.Primary.copy(alpha = 0.1f),
            modifier = Modifier.size(40.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = AegisColor.Primary, modifier = Modifier.size(20.dp))
            }
        }
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold), color = AegisColor.Text)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = AegisColor.TextMuted)
        }
    }
}

@Composable
private fun ReviewRow(label: String, value: String, mono: Boolean = false) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = AegisColor.TextMuted)
        Text(
            text      = value,
            style     = if (mono)
                MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace)
            else
                MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
            color     = AegisColor.Text,
            textAlign = TextAlign.End,
            modifier  = Modifier.weight(1f, fill = false).padding(start = 16.dp),
        )
    }
}

// ── Vibration helper ──────────────────────────────────────────────────────────

private fun vibrateSuccess(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        manager?.defaultVibrator?.vibrate(
            VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    } else {
        @Suppress("DEPRECATION")
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(200, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(200)
        }
    }
}
