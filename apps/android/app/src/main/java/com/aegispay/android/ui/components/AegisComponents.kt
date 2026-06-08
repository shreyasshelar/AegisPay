package com.aegispay.android.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.aegispay.android.network.Transaction
import com.aegispay.android.network.TransactionStatus
import com.aegispay.android.ui.theme.*
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// ── AegisCard ─────────────────────────────────────────────────────────────────

@Composable
fun AegisCard(
    modifier: Modifier = Modifier,
    padding:  PaddingValues = PaddingValues(16.dp),
    content:  @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(containerColor = AegisColor.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(padding), content = content)
    }
}

// ── AegisBadge ────────────────────────────────────────────────────────────────

@Composable
fun AegisBadge(status: TransactionStatus) {
    val bg   = statusLightColor(status)
    val tint = statusColor(status)
    Row(
        modifier          = Modifier
            .clip(RoundedCornerShape(50))
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(tint)
        )
        Text(
            text  = status.displayLabel,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
        )
    }
}

// ── AegisStatusTimeline ───────────────────────────────────────────────────────

private val STEPS = listOf(
    "Initiated"      to TransactionStatus.INITIATED,
    "Funds Reserved" to TransactionStatus.RESERVED,
    "Risk Cleared"   to TransactionStatus.RISK_CLEARED,
    "Processing"     to TransactionStatus.PROCESSING,
    "Completed"      to TransactionStatus.COMPLETED,
)

private val STATUS_ORDER = buildMap {
    STEPS.forEachIndexed { i, (_, s) -> put(s, i) }
    put(TransactionStatus.FAILED, -1)
    put(TransactionStatus.ROLLED_BACK, -1)
}

@Composable
fun AegisStatusTimeline(status: TransactionStatus) {
    val currentIndex = STATUS_ORDER[status] ?: 0
    val isFailed     = status == TransactionStatus.FAILED || status == TransactionStatus.ROLLED_BACK

    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
        STEPS.forEachIndexed { index, (label, _) ->
            Row(
                verticalAlignment  = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Circle
                val circleColor = when {
                    isFailed && index < currentIndex -> AegisColor.Primary
                    isFailed                          -> AegisColor.Border
                    index < currentIndex              -> AegisColor.Success
                    index == currentIndex             -> AegisColor.Primary
                    else                              -> AegisColor.Border
                }
                Box(
                    modifier          = Modifier.size(28.dp).clip(CircleShape).background(circleColor),
                    contentAlignment  = Alignment.Center,
                ) {
                    when {
                        index < currentIndex && !isFailed ->
                            Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(14.dp))
                        index == currentIndex && !status.isTerminal ->
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                        else ->
                            Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.7f)))
                    }
                }
                Text(
                    text  = label,
                    style = if (index == currentIndex && !isFailed)
                        MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Default, color = AegisColor.Text)
                    else
                        MaterialTheme.typography.bodyMedium.copy(color = AegisColor.TextSubtle),
                )
            }
            // Connector line
            if (index < STEPS.lastIndex) {
                Box(
                    modifier = Modifier
                        .padding(start = 13.dp)
                        .width(2.dp)
                        .height(20.dp)
                        .background(if (index < currentIndex && !isFailed) AegisColor.Primary else AegisColor.Border)
                )
            }
        }

        // Failure step
        if (isFailed) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Box(
                    modifier         = Modifier.size(28.dp).clip(CircleShape).background(AegisColor.Danger),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.Close, null, tint = Color.White, modifier = Modifier.size(14.dp))
                }
                Text(
                    text  = if (status == TransactionStatus.FAILED) "Failed" else "Rolled Back",
                    style = MaterialTheme.typography.bodyMedium.copy(color = AegisColor.Danger),
                )
            }
        }
    }
}

// ── AegisTransactionRow ───────────────────────────────────────────────────────

@Composable
fun AegisTransactionRow(
    transaction:   Transaction,
    currentUserId: String,
    onClick:       () -> Unit,
) {
    val isSent = transaction.payerId == currentUserId
    val amountColor = if (isSent) AegisColor.Danger else AegisColor.Success
    val prefix      = if (isSent) "−" else "+"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment     = Alignment.CenterVertically,
    ) {
        // Direction icon
        Icon(
            imageVector = if (isSent) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
            contentDescription = null,
            tint     = amountColor,
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(amountColor.copy(alpha = 0.12f))
                .padding(8.dp),
        )

        // Labels
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text  = if (isSent) "To ${transaction.payeeId.take(8)}…"
                        else "From ${transaction.payerId.take(8)}…",
                style = MaterialTheme.typography.bodyMedium,
                color = AegisColor.Text,
            )
            Text(
                text  = formatRelative(transaction.initiatedAt),
                style = MaterialTheme.typography.bodySmall,
                color = AegisColor.TextMuted,
            )
        }

        // Amount + badge
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text  = "$prefix${formatAmount(transaction.amount, transaction.currency)}",
                style = MaterialTheme.typography.titleSmall,
                color = amountColor,
            )
            Spacer(Modifier.height(4.dp))
            AegisBadge(transaction.status)
        }
    }
}

// ── Skeleton shimmer ──────────────────────────────────────────────────────────

@Composable
fun ShimmerBox(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val x by transition.animateFloat(
        initialValue   = -1f,
        targetValue    = 2f,
        animationSpec  = infiniteRepeatable(
            animation  = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "shimmer_x",
    )

    val brush = Brush.linearGradient(
        colors  = listOf(AegisColor.Border, Color.White.copy(alpha = 0.7f), AegisColor.Border),
        start   = Offset(x * 400, 0f),
        end     = Offset((x + 0.5f) * 400, 0f),
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(brush),
    )
}

@Composable
fun TransactionRowSkeleton() {
    Row(
        modifier  = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ShimmerBox(Modifier.size(40.dp).clip(CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ShimmerBox(Modifier.width(130.dp).height(12.dp))
            ShimmerBox(Modifier.width(80.dp).height(10.dp))
        }
        ShimmerBox(Modifier.width(70.dp).height(14.dp))
    }
}

// ── AegisTextField ────────────────────────────────────────────────────────────

@Composable
fun AegisTextField(
    value:         String,
    onValueChange: (String) -> Unit,
    label:         String,
    placeholder:   String = "",
    error:         String? = null,
    modifier:      Modifier = Modifier,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine:    Boolean = true,
) {
    Column(modifier = modifier) {
        OutlinedTextField(
            value         = value,
            onValueChange = onValueChange,
            label         = { Text(label) },
            placeholder   = { Text(placeholder, color = AegisColor.TextSubtle) },
            isError       = error != null,
            singleLine    = singleLine,
            keyboardOptions = keyboardOptions,
            shape         = RoundedCornerShape(12.dp),
            colors        = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = AegisColor.Primary,
                unfocusedBorderColor = AegisColor.Border,
                errorBorderColor     = AegisColor.Danger,
            ),
            modifier = Modifier.fillMaxWidth(),
        )
        if (error != null) {
            Text(
                text     = error,
                style    = MaterialTheme.typography.bodySmall,
                color    = AegisColor.Danger,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }
    }
}

// ── Formatters ────────────────────────────────────────────────────────────────

fun formatAmount(amount: java.math.BigDecimal, currency: String): String =
    com.aegispay.android.ui.wallet.formatCurrency(amount, currency)

fun formatRelative(date: Date): String {
    val diffMs = System.currentTimeMillis() - date.time
    return when {
        diffMs < 60_000     -> "just now"
        diffMs < 3_600_000  -> "${diffMs / 60_000}m ago"
        diffMs < 86_400_000 -> "${diffMs / 3_600_000}h ago"
        else                -> SimpleDateFormat("d MMM", Locale.getDefault()).format(date)
    }
}
