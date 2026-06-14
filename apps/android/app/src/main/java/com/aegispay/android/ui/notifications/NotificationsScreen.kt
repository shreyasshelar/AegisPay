package com.aegispay.android.ui.notifications

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aegispay.android.network.PushNotification
import com.aegispay.android.ui.components.ShimmerBox
import com.aegispay.android.ui.theme.AegisColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    viewModel:   NotificationsViewModel,
    onNavigateUp: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", fontWeight = FontWeight.Bold) },
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
        if (uiState.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                repeat(6) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ShimmerBox(Modifier.size(40.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            ShimmerBox(Modifier.fillMaxWidth(0.7f).height(14.dp))
                            ShimmerBox(Modifier.fillMaxWidth(0.9f).height(12.dp))
                        }
                    }
                }
            }
        } else if (uiState.notifications.isEmpty()) {
            Box(
                modifier         = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.NotificationsNone,
                        contentDescription = null,
                        tint     = AegisColor.TextMuted,
                        modifier = Modifier.size(56.dp),
                    )
                    Text(
                        "No notifications",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AegisColor.TextMuted,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier       = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(uiState.notifications, key = { it.id }) { notif ->
                    NotificationCard(notif)
                }
            }
        }
    }
}

@Composable
private fun NotificationCard(notif: PushNotification) {
    val (icon, tint) = notifIconAndTint(notif.type)
    Card(
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(containerColor = AegisColor.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text  = notif.title,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = AegisColor.Text,
                )
                Text(
                    text  = notif.body,
                    style = MaterialTheme.typography.bodySmall,
                    color = AegisColor.TextMuted,
                )
            }
        }
    }
}

private fun notifIconAndTint(type: String): Pair<ImageVector, Color> = when (type) {
    "TRANSACTION_COMPLETED"   -> Icons.Default.CheckCircle to AegisColor.Success
    "TRANSACTION_FAILED"      -> Icons.Default.Cancel to AegisColor.Danger
    "TRANSACTION_ROLLED_BACK" -> Icons.Default.Undo to AegisColor.Warning
    "KYC_STATUS_CHANGED",
    "KYC_APPROVED"            -> Icons.Default.Badge to AegisColor.Success
    "KYC_REJECTED"            -> Icons.Default.Badge to AegisColor.Danger
    "USER_REGISTERED"         -> Icons.Default.PersonAdd to AegisColor.Primary
    else                      -> Icons.Default.Info to AegisColor.Primary
}
