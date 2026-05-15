package com.aegispay.android.ui.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CardDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aegispay.android.network.KycStatus
import com.aegispay.android.ui.components.*
import com.aegispay.android.ui.theme.AegisColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    viewModel:                 DashboardViewModel,
    onNavigateToTransactions:  () -> Unit,
    onNavigateToDetail:        (String) -> Unit,
    onNavigateToSend:          () -> Unit,
    onNavigateToNotifications: () -> Unit,
    onNavigateToProfile:       () -> Unit,
    onNavigateToBackOffice:    () -> Unit = {},
) {
    val uiState       by viewModel.uiState.collectAsState()
    val badgeCount    by viewModel.badgeCount.collectAsState()
    val userId        = viewModel.currentUserId ?: ""
    val isBackOffice  = viewModel.isBackOfficeUser

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("AegisPay", fontWeight = FontWeight.Bold) },
                actions = {
                    BadgedBox(
                        badge = {
                            if (badgeCount > 0) {
                                Badge {
                                    Text(
                                        text  = if (badgeCount > 99) "99+" else badgeCount.toString(),
                                        style = MaterialTheme.typography.labelSmall,
                                    )
                                }
                            }
                        },
                    ) {
                        IconButton(onClick = onNavigateToNotifications) {
                            Icon(Icons.Default.Notifications, contentDescription = "Notifications")
                        }
                    }
                    IconButton(onClick = onNavigateToProfile) {
                        Icon(Icons.Default.Person, contentDescription = "Profile")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AegisColor.Background),
            )
        },
        containerColor = AegisColor.Background,
    ) { padding ->
        LazyColumn(
            modifier            = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── KYC nudge banner ──────────────────────────────────────────────
            val kyc = uiState.kycStatus
            if (kyc != null && kyc != KycStatus.APPROVED) {
                item {
                    KycNudgeBanner(
                        kycStatus           = kyc,
                        onNavigateToProfile = onNavigateToProfile,
                    )
                }
            }

            // ── Balance card ──────────────────────────────────────────────────
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(AegisColor.Primary, AegisColor.PrimaryDark)
                            )
                        )
                        .padding(24.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text  = "Available Balance",
                            color = Color.White.copy(alpha = 0.8f),
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        if (uiState.isLoadingAccount) {
                            ShimmerBox(
                                Modifier
                                    .width(160.dp)
                                    .height(36.dp)
                            )
                        } else {
                            Text(
                                text  = formatAmount(
                                    uiState.account?.availableBalance ?: 0.0,
                                    uiState.account?.currency ?: "INR",
                                ),
                                color = Color.White,
                                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                            )
                        }
                        uiState.account?.let {
                            Text(
                                text  = "Reserved: ${formatAmount(it.reservedBalance, it.currency)}",
                                color = Color.White.copy(alpha = 0.7f),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            // ── Quick actions ─────────────────────────────────────────────────
            item {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    QuickAction(
                        icon    = Icons.Default.Send,
                        label   = "Send",
                        onClick = onNavigateToSend,
                        modifier = Modifier.weight(1f),
                    )
                    QuickAction(
                        icon    = Icons.Default.History,
                        label   = "History",
                        onClick = onNavigateToTransactions,
                        modifier = Modifier.weight(1f),
                    )
                    QuickAction(
                        icon    = Icons.Default.Analytics,
                        label   = "Analytics",
                        onClick = { /* Phase F2 */ },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            // ── Back-office quick-access (role-gated) ─────────────────────────
            if (isBackOffice) {
                item {
                    Card(
                        onClick = onNavigateToBackOffice,
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(14.dp),
                        colors   = CardDefaults.cardColors(
                            containerColor = AegisColor.PrimaryLight,
                        ),
                    ) {
                        Row(
                            modifier             = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment    = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier         = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(AegisColor.Primary.copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.AdminPanelSettings,
                                    contentDescription = null,
                                    tint     = AegisColor.Primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Back Office",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                    color = AegisColor.Primary,
                                )
                                Text(
                                    "Risk cases · Incident triage",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AegisColor.TextMuted,
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint     = AegisColor.TextSubtle,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }

            // ── Recent transactions header ─────────────────────────────────────
            item {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment     = Alignment.CenterVertically,
                ) {
                    Text(
                        text  = "Recent Transactions",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = AegisColor.Text,
                    )
                    TextButton(onClick = onNavigateToTransactions) {
                        Text("See all", color = AegisColor.Primary)
                    }
                }
            }

            // ── Transaction rows ──────────────────────────────────────────────
            if (uiState.isLoadingTransactions) {
                items(4) { TransactionRowSkeleton() }
            } else if (uiState.recentTransactions.isEmpty()) {
                item {
                    Box(
                        modifier          = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment  = Alignment.Center,
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Icon(
                                Icons.Default.ReceiptLong,
                                contentDescription = null,
                                tint     = AegisColor.TextMuted,
                                modifier = Modifier.size(48.dp),
                            )
                            Text(
                                "No transactions yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = AegisColor.TextMuted,
                            )
                        }
                    }
                }
            } else {
                items(
                    items = uiState.recentTransactions,
                    key   = { it.transactionId },
                ) { tx ->
                    AegisCard(padding = PaddingValues(0.dp)) {
                        AegisTransactionRow(
                            transaction   = tx,
                            currentUserId = userId,
                            onClick       = { onNavigateToDetail(tx.transactionId) },
                        )
                    }
                }
            }

            // Error banner
            uiState.error?.let { err ->
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AegisColor.Danger.copy(alpha = 0.1f)),
                        shape  = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.ErrorOutline, null, tint = AegisColor.Danger, modifier = Modifier.size(18.dp))
                            Text(err, style = MaterialTheme.typography.bodySmall, color = AegisColor.Danger)
                        }
                    }
                }
            }
        }
    }
}

// ── Quick action tile ─────────────────────────────────────────────────────────

// ── KYC nudge banner ──────────────────────────────────────────────────────────

@Composable
private fun KycNudgeBanner(
    kycStatus:           KycStatus,
    onNavigateToProfile: () -> Unit,
) {
    val (icon, message) = when (kycStatus) {
        KycStatus.PENDING ->
            Icons.Default.Shield to "Complete identity verification to unlock all features."
        KycStatus.DOCUMENT_SUBMITTED, KycStatus.AI_PROCESSING ->
            Icons.Default.AccessTime to "Your identity is being verified. We'll notify you when it's done."
        KycStatus.REJECTED ->
            Icons.Default.Warning to "Verification failed. Please re-submit your documents."
        KycStatus.MANUAL_REVIEW ->
            Icons.Default.HourglassBottom to "Your documents are under manual review."
        KycStatus.APPROVED -> return   // never shown
    }

    val isRejected = kycStatus == KycStatus.REJECTED
    val bgColor    = if (isRejected) MaterialTheme.colorScheme.errorContainer
                     else MaterialTheme.colorScheme.tertiaryContainer
    val onColor    = if (isRejected) MaterialTheme.colorScheme.onErrorContainer
                     else MaterialTheme.colorScheme.onTertiaryContainer

    Surface(
        onClick = onNavigateToProfile,
        shape  = MaterialTheme.shapes.large,
        color  = bgColor,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier            = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment   = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector        = icon,
                contentDescription = null,
                tint               = onColor,
                modifier           = Modifier.size(24.dp),
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text       = "Identity verification",
                    style      = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color      = onColor,
                )
                Text(
                    text      = message,
                    style     = MaterialTheme.typography.bodySmall,
                    color     = onColor.copy(alpha = 0.8f),
                    maxLines  = 2,
                )
            }
            Icon(
                imageVector        = Icons.Default.ChevronRight,
                contentDescription = null,
                tint               = onColor.copy(alpha = 0.6f),
                modifier           = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun QuickAction(
    icon:     ImageVector,
    label:    String,
    onClick:  () -> Unit,
    modifier: Modifier = Modifier,
) {
    AegisCard(modifier = modifier, padding = PaddingValues(16.dp)) {
        Column(
            modifier            = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier         = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AegisColor.Primary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                IconButton(onClick = onClick, modifier = Modifier.size(44.dp)) {
                    Icon(icon, contentDescription = label, tint = AegisColor.Primary)
                }
            }
            Text(
                text  = label,
                style = MaterialTheme.typography.labelMedium,
                color = AegisColor.Text,
            )
        }
    }
}
