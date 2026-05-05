package com.aegispay.android.ui.transactions

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.ExperimentalMaterialApi
import androidx.compose.material.pullrefresh.PullRefreshIndicator
import androidx.compose.material.pullrefresh.pullRefresh
import androidx.compose.material.pullrefresh.rememberPullRefreshState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.aegispay.android.network.TransactionStatus
import com.aegispay.android.ui.components.AegisCard
import com.aegispay.android.ui.components.AegisTransactionRow
import com.aegispay.android.ui.components.TransactionRowSkeleton
import com.aegispay.android.ui.theme.AegisColor

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterialApi::class)
@Composable
fun TransactionListScreen(
    viewModel:         TransactionListViewModel,
    onNavigateToDetail: (String) -> Unit,
    onNavigateUp:      () -> Unit,
) {
    val uiState   by viewModel.uiState.collectAsState()
    val userId    = viewModel.currentUserId ?: ""
    val listState = rememberLazyListState()

    // Infinite scroll — load next page when near bottom
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total       = listState.layoutInfo.totalItemsCount
            lastVisible >= total - 3 && total > 0
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) viewModel.loadNextPage()
    }

    // Pull-to-refresh
    val pullRefreshState = rememberPullRefreshState(
        refreshing = uiState.isRefreshing,
        onRefresh  = viewModel::refresh,
    )

    var showFilterSheet by remember { mutableStateOf(false) }

    if (showFilterSheet) {
        FilterBottomSheet(
            currentStatus = uiState.statusFilter,
            onStatusSelected = { viewModel.setStatusFilter(it) },
            onDismiss    = { showFilterSheet = false },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transactions", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showFilterSheet = true }) {
                        Icon(
                            if (viewModel.hasActiveFilters) Icons.Default.FilterAlt
                            else Icons.Default.FilterAltOff,
                            contentDescription = "Filters",
                            tint = if (viewModel.hasActiveFilters) AegisColor.Primary
                                   else AegisColor.TextMuted,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AegisColor.Background),
            )
        },
        containerColor = AegisColor.Background,
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pullRefresh(pullRefreshState),
        ) {
            LazyColumn(
                state          = listState,
                modifier       = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                // Active filter chip
                if (viewModel.hasActiveFilters) {
                    item {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment     = Alignment.CenterVertically,
                        ) {
                            uiState.statusFilter?.let { status ->
                                FilterChip(
                                    selected = true,
                                    onClick  = { viewModel.setStatusFilter(null) },
                                    label    = { Text(status.name, style = MaterialTheme.typography.labelSmall) },
                                    trailingIcon = {
                                        Icon(Icons.Default.Close, null, modifier = Modifier.size(14.dp))
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = AegisColor.Primary.copy(alpha = 0.1f),
                                        selectedLabelColor     = AegisColor.Primary,
                                    ),
                                )
                            }
                            TextButton(onClick = viewModel::clearFilters) {
                                Text("Clear", style = MaterialTheme.typography.labelSmall, color = AegisColor.TextMuted)
                            }
                        }
                    }
                }

                if (uiState.isLoading && uiState.transactions.isEmpty()) {
                    items(6) { TransactionRowSkeleton() }
                } else {
                    items(uiState.transactions, key = { it.transactionId }) { tx ->
                        AegisCard(padding = PaddingValues(0.dp)) {
                            AegisTransactionRow(
                                transaction   = tx,
                                currentUserId = userId,
                                onClick       = { onNavigateToDetail(tx.transactionId) },
                            )
                        }
                    }

                    if (uiState.isLoading) {
                        items(2) { TransactionRowSkeleton() }
                    }
                }

                uiState.error?.let { err ->
                    item {
                        Box(
                            modifier         = Modifier.fillMaxWidth().padding(32.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(err, color = AegisColor.Danger, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            // Pull-to-refresh indicator (always on top)
            PullRefreshIndicator(
                refreshing = uiState.isRefreshing,
                state      = pullRefreshState,
                modifier   = Modifier.align(Alignment.TopCenter),
                contentColor = AegisColor.Primary,
            )
        }
    }
}

// ── Filter bottom sheet ───────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterBottomSheet(
    currentStatus:   TransactionStatus?,
    onStatusSelected: (TransactionStatus?) -> Unit,
    onDismiss:       () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Filter by status",
                style    = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color    = AegisColor.Text,
                modifier = Modifier.padding(bottom = 4.dp),
            )

            // "Any" option
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(
                    selected = currentStatus == null,
                    onClick  = { onStatusSelected(null); onDismiss() },
                    colors   = RadioButtonDefaults.colors(selectedColor = AegisColor.Primary),
                )
                Text("Any", style = MaterialTheme.typography.bodyMedium, color = AegisColor.Text)
            }

            TransactionStatus.values().forEach { status ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = currentStatus == status,
                        onClick  = { onStatusSelected(status); onDismiss() },
                        colors   = RadioButtonDefaults.colors(selectedColor = AegisColor.Primary),
                    )
                    Text(
                        status.displayLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = AegisColor.Text,
                    )
                }
            }
        }
    }
}
