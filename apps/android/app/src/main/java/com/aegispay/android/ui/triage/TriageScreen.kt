package com.aegispay.android.ui.triage

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.aegispay.android.ui.theme.AegisColor
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss").withZone(ZoneId.systemDefault())

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TriageScreen(
    prefillTransactionId: String? = null,
    prefillService:       String? = null,
    onBack:               () -> Unit,
    viewModel:            TriageViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    // Pre-fill from navigation args on first composition
    LaunchedEffect(prefillTransactionId, prefillService) {
        viewModel.prefill(prefillTransactionId, prefillService)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("AI Triage Agent", fontWeight = FontWeight.Bold)
                        Text(
                            "ADMIN only",
                            style = MaterialTheme.typography.labelSmall,
                            color = AegisColor.TextMuted,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (uiState.sessions.isNotEmpty()) {
                        IconButton(
                            onClick = viewModel::clearSessions,
                            modifier = Modifier.semantics { contentDescription = "Clear session history" },
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null,
                                tint = AegisColor.Danger)
                        }
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(AegisColor.Background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {

            // ── Input form ───────────────────────────────────────────────────
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(16.dp),
                    colors   = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Terminal, contentDescription = null,
                                tint = AegisColor.TextMuted, modifier = Modifier.size(18.dp))
                            Text("New Triage Request",
                                fontWeight = FontWeight.SemiBold,
                                style = MaterialTheme.typography.titleSmall)
                        }

                        if (prefillTransactionId != null) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFFFFFBEB))
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.Top,
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null,
                                    tint = Color(0xFFB45309), modifier = Modifier.size(16.dp))
                                Text(
                                    "Pre-filled from transaction $prefillTransactionId",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF92400E),
                                )
                            }
                        }

                        OutlinedTextField(
                            value    = uiState.serviceName,
                            onValueChange = viewModel::onServiceNameChange,
                            label    = { Text("Affected Service") },
                            placeholder = { Text("e.g. transaction-service") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                        )

                        OutlinedTextField(
                            value    = uiState.description,
                            onValueChange = viewModel::onDescriptionChange,
                            label    = { Text("Incident Description") },
                            placeholder = { Text("Symptoms, error messages, transaction ID…") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 3,
                            maxLines = 6,
                        )

                        uiState.triageError?.let { err ->
                            Text(err,
                                color = AegisColor.Danger,
                                style = MaterialTheme.typography.bodySmall)
                        }

                        Button(
                            onClick  = viewModel::runTriage,
                            enabled  = viewModel.canTriage && !uiState.isTriaging,
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            shape    = RoundedCornerShape(12.dp),
                            colors   = ButtonDefaults.buttonColors(containerColor = AegisColor.Primary),
                        ) {
                            if (uiState.isTriaging) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color    = Color.White,
                                    strokeWidth = 2.dp,
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("Agent is investigating…", color = Color.White,
                                    fontWeight = FontWeight.SemiBold)
                            } else {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null,
                                    modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(8.dp))
                                Text("Run AI Triage", color = Color.White,
                                    fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            // ── Session history header ────────────────────────────────────────
            if (uiState.sessions.isNotEmpty()) {
                item {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Default.History, contentDescription = null,
                            tint = AegisColor.TextMuted, modifier = Modifier.size(16.dp))
                        Text(
                            "Session History (${uiState.sessions.size})",
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.titleSmall,
                        )
                    }
                }
            }

            // ── Session cards ────────────────────────────────────────────────
            items(uiState.sessions, key = { it.id }) { session ->
                TriageSessionCard(
                    session    = session,
                    expanded   = uiState.expandedId == session.id,
                    onToggle   = { viewModel.toggleExpanded(session.id) },
                )
            }

            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

// ── Session card ──────────────────────────────────────────────────────────────

@Composable
private fun TriageSessionCard(
    session:  TriageSession,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column {
            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.MedicalServices,
                    contentDescription = null,
                    tint = if (session.degraded) Color(0xFFF59E0B) else AegisColor.Primary,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(session.serviceName, fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.bodyMedium)
                    Text(
                        session.description.take(80) + if (session.description.length > 80) "…" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = AegisColor.TextMuted,
                    )
                }
                Column(horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    if (session.degraded) {
                        Text("DEGRADED",
                            style = MaterialTheme.typography.labelSmall,
                            color = Color(0xFFB45309),
                            fontWeight = FontWeight.Bold,
                            fontSize = 9.sp)
                    }
                    Text(TIME_FMT.format(session.timestamp),
                        style = MaterialTheme.typography.labelSmall,
                        color = AegisColor.TextMuted)
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = AegisColor.TextMuted,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            // Analysis (monospace terminal style)
            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0F172A))
                        .padding(16.dp),
                ) {
                    Text(
                        text      = session.analysis,
                        color     = Color(0xFF86EFAC),   // green-300
                        fontFamily = FontFamily.Monospace,
                        style     = MaterialTheme.typography.bodySmall,
                        lineHeight = 20.sp,
                    )
                }
            }
        }
    }
}
