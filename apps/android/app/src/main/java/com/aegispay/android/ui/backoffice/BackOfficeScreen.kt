package com.aegispay.android.ui.backoffice

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aegispay.android.network.FraudExplainResponse
import com.aegispay.android.network.RiskCase
import com.aegispay.android.network.RiskDecision
import com.aegispay.android.ui.components.MarkdownText
import com.aegispay.android.ui.theme.AegisColor

// ── Root screen ───────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackOfficeScreen(
    viewModel:          BackOfficeViewModel,
    onNavigateUp:       () -> Unit,
    /** Non-null only when the calling user is ADMIN — shows "Open AI Triage" button in the incident tab. */
    onNavigateToTriage: ((txId: String?, service: String?) -> Unit)? = null,
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Risk Cases", "Incident Triage")

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Back Office", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (selectedTab == 0) {
                        IconButton(onClick = viewModel::loadRiskCases) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AegisColor.Bg),
            )
        },
        containerColor = AegisColor.Bg,
    ) { padding ->
        Column(modifier = Modifier.padding(padding)) {
            // ── Tab row ───────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor   = AegisColor.Surface,
                contentColor     = AegisColor.Primary,
                indicator        = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                        color    = AegisColor.Primary,
                    )
                },
            ) {
                tabs.forEachIndexed { i, title ->
                    Tab(
                        selected = selectedTab == i,
                        onClick  = { selectedTab = i },
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedTab == i) FontWeight.SemiBold
                                             else FontWeight.Normal,
                            )
                        },
                    )
                }
            }

            // ── Tab content ───────────────────────────────────────────────────
            AnimatedContent(
                targetState   = selectedTab,
                transitionSpec = {
                    if (targetState > initialState)
                        (slideInHorizontally { it } + fadeIn()) togetherWith
                        (slideOutHorizontally { -it } + fadeOut())
                    else
                        (slideInHorizontally { -it } + fadeIn()) togetherWith
                        (slideOutHorizontally { it } + fadeOut())
                },
                label = "backOfficeTab",
            ) { tab ->
                when (tab) {
                    0 -> RiskCasesTab(uiState = uiState, viewModel = viewModel)
                    else -> IncidentTriageTab(
                        uiState = uiState,
                        viewModel = viewModel,
                        onNavigateToFullTriage = onNavigateToTriage,
                    )
                }
            }
        }
    }
}

// ── Risk cases tab ────────────────────────────────────────────────────────────

@Composable
private fun RiskCasesTab(
    uiState:   BackOfficeUiState,
    viewModel: BackOfficeViewModel,
) {
    when {
        uiState.isLoadingCases -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            CircularProgressIndicator(color = AegisColor.Primary)
        }
        uiState.casesError != null -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.ErrorOutline, null, tint = AegisColor.Danger, modifier = Modifier.size(40.dp))
                Text(uiState.casesError, color = AegisColor.Danger, style = MaterialTheme.typography.bodySmall)
            }
        }
        uiState.cases.isEmpty() -> Box(Modifier.fillMaxSize(), Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.CheckCircle, null, tint = AegisColor.Success, modifier = Modifier.size(40.dp))
                Text("No risk cases — all clear", color = AegisColor.TextMuted, style = MaterialTheme.typography.bodyMedium)
            }
        }
        else -> LazyColumn(
            contentPadding      = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(uiState.cases, key = { it.id }) { rc ->
                RiskCaseCard(
                    riskCase  = rc,
                    isSelected = uiState.selectedCase?.id == rc.id,
                    onClick   = { viewModel.selectCase(rc) },
                )
            }
            // Detail panel below list on mobile
            if (uiState.selectedCase != null) {
                item {
                    RiskCaseDetailPanel(
                        uiState   = uiState,
                        viewModel = viewModel,
                    )
                }
            }
        }
    }
}

@Composable
private fun RiskCaseCard(
    riskCase:   RiskCase,
    isSelected: Boolean,
    onClick:    () -> Unit,
) {
    val decisionColor = riskCase.decision.accentColor
    Card(
        modifier  = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape     = RoundedCornerShape(12.dp),
        colors    = CardDefaults.cardColors(
            containerColor = if (isSelected) AegisColor.PrimaryLight else AegisColor.Surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 0.dp else 1.dp),
    ) {
        Row(
            modifier             = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment    = Alignment.CenterVertically,
        ) {
            // Score circle
            Box(
                modifier         = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(decisionColor.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text  = riskCase.riskScore.toString(),
                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.ExtraBold),
                    color = decisionColor,
                )
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text  = "Tx ${riskCase.transactionId.take(8)}…",
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = AegisColor.Text,
                )
                if (riskCase.ruleFlagKeys.isNotEmpty()) {
                    Text(
                        text  = riskCase.ruleFlagKeys.take(3).joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = AegisColor.TextMuted,
                    )
                }
            }

            DecisionChip(decision = riskCase.decision)
        }
    }
}

@Composable
private fun RiskCaseDetailPanel(
    uiState:   BackOfficeUiState,
    viewModel: BackOfficeViewModel,
) {
    val rc = uiState.selectedCase ?: return
    Card(
        modifier  = Modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(16.dp),
        colors    = CardDefaults.cardColors(containerColor = AegisColor.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(
            modifier            = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier             = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment    = Alignment.CenterVertically,
            ) {
                Text("Case Detail", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                DecisionChip(decision = rc.decision)
            }

            // Risk score bar
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Risk Score", style = MaterialTheme.typography.bodySmall, color = AegisColor.TextMuted)
                    Text("${rc.riskScore} / 100", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Bold))
                }
                LinearProgressIndicator(
                    progress       = { rc.riskScore / 100f },
                    modifier       = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color          = rc.decision.accentColor,
                    trackColor     = AegisColor.Border,
                )
            }

            // Rule flags
            if (rc.ruleFlagKeys.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Triggered Rules", style = MaterialTheme.typography.bodySmall, color = AegisColor.TextMuted)
                    rc.ruleFlagKeys.forEach { rule ->
                        Text(
                            text  = rule,
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                            color = AegisColor.Danger,
                            modifier = Modifier
                                .background(AegisColor.DangerLight, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
            }

            // AI explain button
            if (rc.ruleFlagKeys.isNotEmpty()) {
                Button(
                    onClick  = viewModel::explainFraud,
                    enabled  = !uiState.isExplaining,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = AegisColor.Primary),
                ) {
                    if (uiState.isExplaining) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color    = Color.White,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Asking AI…")
                    } else {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("AI Fraud Explanation")
                    }
                }
            }

            // Explanation result
            uiState.explanation?.let { res ->
                Column(
                    modifier = Modifier
                        .background(AegisColor.PrimaryLight, RoundedCornerShape(10.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        "AI Explanation",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = AegisColor.TextMuted,
                    )
                    Text(res.explanation, style = MaterialTheme.typography.bodySmall, color = AegisColor.Text)
                }
            }

            uiState.explainError?.let { err ->
                Text(err, style = MaterialTheme.typography.labelSmall, color = AegisColor.Danger)
            }
        }
    }
}

// ── Incident triage tab ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IncidentTriageTab(
    uiState:              BackOfficeUiState,
    viewModel:            BackOfficeViewModel,
    onNavigateToFullTriage: ((txId: String?, service: String?) -> Unit)? = null,
) {
    Column(
        modifier            = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── Input form ────────────────────────────────────────────────────────
        Card(
            shape  = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = AegisColor.Surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        ) {
            Column(
                modifier            = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    verticalAlignment    = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Default.Terminal, null, tint = AegisColor.TextMuted, modifier = Modifier.size(18.dp))
                    Text("New Incident", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                }

                OutlinedTextField(
                    value         = uiState.serviceName,
                    onValueChange = viewModel::onServiceNameChange,
                    label         = { Text("Affected Service") },
                    placeholder   = { Text("e.g. transaction-service") },
                    singleLine    = true,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp),
                )

                OutlinedTextField(
                    value         = uiState.incidentDesc,
                    onValueChange = viewModel::onIncidentDescChange,
                    label         = { Text("Incident Description") },
                    placeholder   = { Text("Describe symptoms, errors, or timeline…") },
                    minLines      = 4,
                    maxLines      = 8,
                    modifier      = Modifier.fillMaxWidth(),
                    shape         = RoundedCornerShape(10.dp),
                )

                Button(
                    onClick  = viewModel::runTriage,
                    enabled  = viewModel.canTriage && !uiState.isTriaging,
                    modifier = Modifier.fillMaxWidth(),
                    shape    = RoundedCornerShape(10.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = AegisColor.Primary),
                ) {
                    if (uiState.isTriaging) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color    = Color.White,
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("Triaging…")
                    } else {
                        Icon(Icons.Default.AutoAwesome, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Run AI Triage", fontWeight = FontWeight.SemiBold)
                    }
                }

                // ADMIN shortcut — opens the dedicated full-featured triage screen
                if (onNavigateToFullTriage != null) {
                    OutlinedButton(
                        onClick  = { onNavigateToFullTriage(null, uiState.serviceName.ifBlank { null }) },
                        modifier = Modifier.fillMaxWidth(),
                        shape    = RoundedCornerShape(10.dp),
                    ) {
                        Icon(Icons.Default.OpenInNew, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Open Full Triage Agent")
                    }
                }
            }
        }

        // ── Triage report ─────────────────────────────────────────────────────
        uiState.triageReport?.let { report ->
            Card(
                shape  = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.CheckCircle, null, tint = AegisColor.Success, modifier = Modifier.size(18.dp))
                            Text(
                                "Triage Report",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = AegisColor.Text,
                            )
                        }
                        TextButton(onClick = viewModel::resetTriage) {
                            Text("Clear", color = AegisColor.TextMuted, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                    MarkdownText(
                        markdown  = report,
                        textColor = Color(0xFF1E293B),
                    )
                }
            }
        }

        uiState.triageError?.let { err ->
            Row(
                modifier             = Modifier
                    .background(AegisColor.DangerLight, RoundedCornerShape(10.dp))
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment    = Alignment.CenterVertically,
            ) {
                Icon(Icons.Default.ErrorOutline, null, tint = AegisColor.Danger, modifier = Modifier.size(16.dp))
                Text(err, style = MaterialTheme.typography.bodySmall, color = AegisColor.Danger)
            }
        }
    }
}

// ── Shared sub-components ─────────────────────────────────────────────────────

@Composable
private fun DecisionChip(decision: RiskDecision) {
    val (bg, fg, label) = when (decision) {
        RiskDecision.APPROVED -> Triple(AegisColor.SuccessLight, AegisColor.Success, "APPROVED")
        RiskDecision.REVIEW   -> Triple(AegisColor.WarningLight, AegisColor.Warning, "REVIEW")
        RiskDecision.REJECTED -> Triple(AegisColor.DangerLight, AegisColor.Danger, "REJECTED")
    }
    Text(
        text  = label,
        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold, fontSize = 10.sp),
        color = fg,
        modifier = Modifier
            .background(bg, RoundedCornerShape(100.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}

private val RiskDecision.accentColor: Color
    get() = when (this) {
        RiskDecision.APPROVED -> AegisColor.Success
        RiskDecision.REVIEW   -> AegisColor.Warning
        RiskDecision.REJECTED -> AegisColor.Danger
    }
