package com.aegispay.android.ui.transactions

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aegispay.android.network.TransactionStatus
import com.aegispay.android.ui.components.*
import com.aegispay.android.ui.theme.AegisColor
import java.text.SimpleDateFormat
import java.util.*

private val FAILED_STATUSES = setOf(TransactionStatus.FAILED, TransactionStatus.ROLLED_BACK)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    transactionId:     String,
    viewModel:         TransactionDetailViewModel,
    onNavigateUp:      () -> Unit,
    /** Non-null when the signed-in user is ADMIN — opens Triage with pre-filled context. */
    onNavigateToTriage: ((txId: String, service: String) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(transactionId) {
        viewModel.load(transactionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transaction", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AegisColor.Background),
            )
        },
        containerColor = AegisColor.Background,
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier         = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = AegisColor.Primary) }
            }
            uiState.error != null -> {
                Box(
                    modifier         = Modifier.fillMaxSize().padding(padding),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(uiState.error!!, color = AegisColor.Danger)
                }
            }
            uiState.transaction != null -> {
                val tx = uiState.transaction!!
                val isFailed = tx.status in FAILED_STATUSES

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // ── Amount + status ──────────────────────────────────────
                    AegisCard {
                        Column(
                            modifier            = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text  = formatAmount(tx.amount, tx.currency),
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                                color = AegisColor.Text,
                            )
                            AegisBadge(tx.status)
                        }
                    }

                    // ── Timeline ─────────────────────────────────────────────
                    AegisCard {
                        Text(
                            "Status Timeline",
                            style    = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color    = AegisColor.Text,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        AegisStatusTimeline(tx.status)

                        if (!tx.status.isTerminal) {
                            Spacer(Modifier.height(12.dp))
                            Row(
                                modifier              = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment     = Alignment.CenterVertically,
                            ) {
                                CircularProgressIndicator(
                                    modifier    = Modifier.size(12.dp),
                                    color       = AegisColor.Primary,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(
                                    "Live updates active",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = AegisColor.Primary,
                                )
                            }
                        }
                    }

                    // ── AI error resolution ──────────────────────────────────
                    if (isFailed) {
                        Card(
                            shape  = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = AegisColor.Warning.copy(alpha = 0.08f)
                            ),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp, AegisColor.Warning.copy(alpha = 0.3f)
                            ),
                        ) {
                            Column(
                                modifier            = Modifier.fillMaxWidth().padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(
                                    modifier              = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment     = Alignment.CenterVertically,
                                ) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment     = Alignment.CenterVertically,
                                    ) {
                                        Icon(
                                            Icons.Default.Lightbulb,
                                            contentDescription = null,
                                            tint     = AegisColor.Warning,
                                            modifier = Modifier.size(16.dp),
                                        )
                                        Text(
                                            "AI Error Explanation",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                            color = AegisColor.Text,
                                        )
                                    }
                                    if (!uiState.isResolvingError) {
                                        IconButton(
                                            onClick  = { tx.failureReason?.let { viewModel.resolveError(it) } },
                                            modifier = Modifier.size(28.dp),
                                        ) {
                                            Icon(
                                                Icons.Default.Refresh,
                                                contentDescription = "Retry",
                                                tint     = AegisColor.TextSubtle,
                                                modifier = Modifier.size(16.dp),
                                            )
                                        }
                                    }
                                }

                                HorizontalDivider(color = AegisColor.Warning.copy(alpha = 0.2f))

                                when {
                                    uiState.isResolvingError -> {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment     = Alignment.CenterVertically,
                                        ) {
                                            CircularProgressIndicator(
                                                modifier    = Modifier.size(14.dp),
                                                color       = AegisColor.Warning,
                                                strokeWidth = 2.dp,
                                            )
                                            Text(
                                                "Analysing failure…",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = AegisColor.TextMuted,
                                            )
                                        }
                                    }
                                    uiState.errorResolution != null -> {
                                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Text(
                                                uiState.errorResolution!!.resolution,
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = AegisColor.Text,
                                            )
                                            Text(
                                                "Code: ${uiState.errorResolution!!.errorCode}",
                                                style = MaterialTheme.typography.bodySmall,
                                                color = AegisColor.TextMuted,
                                            )
                                        }
                                    }
                                    tx.failureReason != null -> {
                                        Text(
                                            tx.failureReason,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = AegisColor.Danger,
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ── ADMIN triage shortcut ────────────────────────────────
                    if (isFailed && onNavigateToTriage != null) {
                        OutlinedButton(
                            onClick = {
                                onNavigateToTriage(tx.transactionId, "payment-orchestrator")
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape    = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
                            border   = androidx.compose.foundation.BorderStroke(
                                1.dp, AegisColor.Primary.copy(alpha = 0.4f)
                            ),
                        ) {
                            Icon(
                                Icons.Default.Biotech,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                                tint = AegisColor.Primary,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                "Triage Incident",
                                style = MaterialTheme.typography.labelLarge,
                                color = AegisColor.Primary,
                            )
                        }
                    }

                    // ── Details ──────────────────────────────────────────────
                    AegisCard {
                        Text(
                            "Details",
                            style    = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color    = AegisColor.Text,
                            modifier = Modifier.padding(bottom = 12.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            DetailRow("Transaction ID", tx.transactionId, copyable = true, context = context)
                            DetailRow("Payer",    tx.payerId)
                            DetailRow("Payee",    tx.payeeId)
                            DetailRow("Currency", tx.currency)
                            DetailRow(
                                "Date",
                                SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault())
                                    .format(tx.initiatedAt),
                            )
                            tx.completedAt?.let {
                                DetailRow(
                                    "Completed",
                                    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.getDefault()).format(it)
                                )
                            }
                            tx.note?.let { DetailRow("Note", it) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    label:    String,
    value:    String,
    copyable: Boolean = false,
    context:  Context? = null,
) {
    Row(
        modifier              = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.bodySmall,
            color    = AegisColor.TextMuted,
            modifier = Modifier.weight(1f),
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text  = value,
                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                color = AegisColor.Text,
            )
            if (copyable && context != null) {
                IconButton(
                    onClick  = {
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText(label, value))
                    },
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "Copy",
                        modifier = Modifier.size(14.dp),
                        tint     = AegisColor.TextSubtle,
                    )
                }
            }
        }
    }
}
