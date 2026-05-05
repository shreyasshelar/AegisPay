package com.aegispay.android.ui.profile

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.aegispay.android.auth.BiometricAuthManager
import com.aegispay.android.network.KycDocumentType
import com.aegispay.android.network.KycExtractedData
import com.aegispay.android.network.KycProcessingResult
import com.aegispay.android.network.KycStatus
import com.aegispay.android.ui.components.AegisCard
import com.aegispay.android.ui.components.ShimmerBox
import com.aegispay.android.ui.theme.AegisColor
import java.io.File

// ── KYC display config ────────────────────────────────────────────────────────

private data class KycConfig(val label: String, val color: Color, val canUpload: Boolean)
private fun kycConfig(s: KycStatus) = when (s) {
    KycStatus.PENDING            -> KycConfig("Pending — upload a document to get started", AegisColor.Warning, true)
    KycStatus.DOCUMENT_SUBMITTED -> KycConfig("Submitted — awaiting AI processing",         AegisColor.Primary, false)
    KycStatus.AI_PROCESSING      -> KycConfig("AI verification in progress…",               AegisColor.Primary, false)
    KycStatus.APPROVED           -> KycConfig("Identity Verified ✓",                         AegisColor.Success, false)
    KycStatus.REJECTED           -> KycConfig("Rejected — please resubmit",                  AegisColor.Danger,  true)
    KycStatus.MANUAL_REVIEW      -> KycConfig("Under manual review",                         AegisColor.Warning, false)
}

// ── Screen ────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    viewModel:             ProfileViewModel,
    onNavigateUp:          () -> Unit,
    onSignOut:             () -> Unit,
    biometricAuthManager:  BiometricAuthManager? = null,
) {
    val uiState   = viewModel.uiState.collectAsState().value
    val context   = LocalContext.current
    var showSignOutDialog    by remember { mutableStateOf(false) }
    var showSourceDialog     by remember { mutableStateOf(false) }
    var docTypeExpanded      by remember { mutableStateOf(false) }

    // Biometric pref state — drives the toggle without recomposing the whole screen
    var biometricEnabled by remember {
        mutableStateOf(biometricAuthManager?.isEnabled ?: false)
    }
    val biometricAvailable = remember { biometricAuthManager?.isAvailable ?: false }

    // Temp URI for camera capture
    val cameraUri = remember {
        val file = File.createTempFile("kyc_", ".jpg", context.cacheDir)
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }

    // Camera launcher (TakePicture returns a Boolean success)
    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { success ->
        if (success) {
            val bm = android.provider.MediaStore.Images.Media
                .getBitmap(context.contentResolver, cameraUri)
            viewModel.processDocumentBitmap(bm)
        }
    }

    // Camera permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) cameraLauncher.launch(cameraUri)
    }

    // Gallery launcher
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.processDocumentUri(it, context.contentResolver) }
    }

    fun launchCamera() {
        val hasPerm = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
        if (hasPerm == PackageManager.PERMISSION_GRANTED) cameraLauncher.launch(cameraUri)
        else permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Sign-out dialog
    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title            = { Text("Sign out") },
            text             = { Text("Are you sure you want to sign out?") },
            confirmButton    = {
                TextButton(onClick = { showSignOutDialog = false; onSignOut() }) {
                    Text("Sign out", color = AegisColor.Danger)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text("Cancel") }
            },
        )
    }

    // Source selection dialog
    if (showSourceDialog) {
        AlertDialog(
            onDismissRequest = { showSourceDialog = false },
            title            = { Text("Add Document") },
            text             = { Text("Choose how to add your identity document.") },
            confirmButton    = {
                TextButton(onClick = { showSourceDialog = false; launchCamera() }) {
                    Icon(Icons.Default.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Camera")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSourceDialog = false; galleryLauncher.launch("image/*") }) {
                    Icon(Icons.Default.Photo, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Gallery")
                }
            },
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Profile", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateUp) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showSignOutDialog = true }) {
                        Icon(Icons.Default.Logout, contentDescription = "Sign out", tint = AegisColor.Danger)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AegisColor.Bg),
            )
        },
        containerColor = AegisColor.Bg,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            // ── Avatar + name ─────────────────────────────────────────────────
            AegisCard {
                Row(
                    modifier              = Modifier.fillMaxWidth(),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        modifier         = Modifier.size(64.dp).clip(CircleShape)
                            .background(AegisColor.Primary.copy(alpha = 0.15f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text  = (viewModel.currentUserName?.firstOrNull() ?: 'U').toString().uppercase(),
                            style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                            color = AegisColor.Primary,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (uiState.isLoading) {
                            ShimmerBox(Modifier.width(120.dp).height(16.dp))
                            ShimmerBox(Modifier.width(180.dp).height(12.dp))
                        } else {
                            Text(viewModel.currentUserName ?: "User",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = AegisColor.Text)
                            Text(viewModel.currentUserEmail ?: "",
                                style = MaterialTheme.typography.bodySmall, color = AegisColor.TextMuted)
                            uiState.profile?.role?.let { role ->
                                Surface(color = AegisColor.PrimaryLight, shape = MaterialTheme.shapes.extraLarge) {
                                    Text(role, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                        color = AegisColor.Primary, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
                                }
                            }
                        }
                    }
                }
            }

            // ── KYC status card ───────────────────────────────────────────────
            uiState.profile?.let { profile ->
                val cfg = kycConfig(profile.kycStatus)
                AegisCard {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("KYC Verification",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = AegisColor.Text)

                        // Status banner
                        Surface(
                            color  = cfg.color.copy(alpha = 0.10f),
                            shape  = MaterialTheme.shapes.small,
                            border = BorderStroke(1.dp, cfg.color.copy(alpha = 0.3f)),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                            ) {
                                Box(
                                    modifier = Modifier.size(10.dp).clip(CircleShape).background(cfg.color)
                                )
                                Text(cfg.label, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = cfg.color)
                            }
                        }

                        if (cfg.canUpload && uiState.kycResult == null) {
                            // Document type picker
                            ExposedDropdownMenuBox(
                                expanded         = docTypeExpanded,
                                onExpandedChange = { docTypeExpanded = !docTypeExpanded },
                            ) {
                                OutlinedTextField(
                                    value         = uiState.selectedDocType.label,
                                    onValueChange = {},
                                    readOnly      = true,
                                    label         = { Text("Document Type") },
                                    trailingIcon  = { ExposedDropdownMenuDefaults.TrailingIcon(docTypeExpanded) },
                                    modifier      = Modifier.menuAnchor().fillMaxWidth(),
                                    colors        = OutlinedTextFieldDefaults.colors(
                                        focusedBorderColor   = AegisColor.Primary,
                                        unfocusedBorderColor = AegisColor.Border,
                                    ),
                                )
                                ExposedDropdownMenu(
                                    expanded         = docTypeExpanded,
                                    onDismissRequest = { docTypeExpanded = false },
                                ) {
                                    KycDocumentType.entries.forEach { dt ->
                                        DropdownMenuItem(
                                            text    = { Text(dt.label) },
                                            onClick = { viewModel.onDocTypeChange(dt); docTypeExpanded = false },
                                        )
                                    }
                                }
                            }

                            // Upload button
                            Button(
                                onClick  = { showSourceDialog = true },
                                enabled  = !uiState.isProcessing,
                                modifier = Modifier.fillMaxWidth().height(52.dp),
                                colors   = ButtonDefaults.buttonColors(containerColor = AegisColor.Primary),
                                shape    = RoundedCornerShape(12.dp),
                            ) {
                                if (uiState.isProcessing) {
                                    CircularProgressIndicator(
                                        modifier    = Modifier.size(20.dp),
                                        color       = Color.White,
                                        strokeWidth = 2.dp,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text("Analysing document…", fontWeight = FontWeight.SemiBold)
                                } else {
                                    Icon(Icons.Default.Upload, null, modifier = Modifier.size(20.dp))
                                    Spacer(Modifier.width(8.dp))
                                    Text("Upload Document", fontWeight = FontWeight.SemiBold)
                                }
                            }

                            uiState.processError?.let {
                                Text(it, color = AegisColor.Danger, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }

            // ── KYC result card ───────────────────────────────────────────────
            AnimatedVisibility(
                visible = uiState.kycResult != null,
                enter   = fadeIn(),
                exit    = fadeOut(),
            ) {
                uiState.kycResult?.let { result ->
                    KycResultCard(
                        result     = result,
                        uiState    = uiState,
                        onRetake   = { viewModel.resetResult() },
                        onConfirm  = { viewModel.confirmKyc() },
                        canConfirm = viewModel.canConfirm,
                    )
                }
            }

            // ── Confirm success notice ────────────────────────────────────────
            if (uiState.confirmSuccess) {
                Surface(
                    color  = AegisColor.SuccessLight,
                    shape  = MaterialTheme.shapes.medium,
                    border = BorderStroke(1.dp, AegisColor.Success.copy(alpha = 0.3f)),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = AegisColor.Success)
                        Text("Document submitted for AI verification",
                            style = MaterialTheme.typography.bodySmall, color = AegisColor.Success)
                    }
                }
            }

            // ── Account info ──────────────────────────────────────────────────
            uiState.profile?.let { profile ->
                AegisCard {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Account", style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = AegisColor.Text)
                        ProfileInfoRow("User ID",  viewModel.currentUserId ?: "—")
                        ProfileInfoRow("Email",    viewModel.currentUserEmail ?: "—")
                        ProfileInfoRow("Role",     profile.role)
                    }
                }
            }

            // ── Security (biometric toggle) ───────────────────────────────────
            if (biometricAvailable) {
                AegisCard {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Row(
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Box(
                                modifier         = Modifier
                                    .size(36.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(AegisColor.Primary.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Fingerprint,
                                    contentDescription = null,
                                    tint     = AegisColor.Primary,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Text(
                                "Security",
                                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = AegisColor.Text,
                            )
                        }

                        HorizontalDivider(color = AegisColor.Border)

                        Row(
                            modifier              = Modifier
                                .fillMaxWidth()
                                .semantics { contentDescription = "Biometric unlock toggle" },
                            verticalAlignment     = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    "Biometric Unlock",
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = AegisColor.Text,
                                )
                                Text(
                                    "Require fingerprint when returning to the app",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = AegisColor.TextMuted,
                                )
                            }
                            Switch(
                                checked         = biometricEnabled,
                                onCheckedChange = { enabled ->
                                    biometricEnabled = enabled
                                    biometricAuthManager?.isEnabled = enabled
                                },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor       = Color.White,
                                    checkedTrackColor       = AegisColor.Primary,
                                    uncheckedThumbColor     = AegisColor.TextSubtle,
                                    uncheckedTrackColor     = AegisColor.Border,
                                ),
                            )
                        }
                    }
                }
            }

            uiState.error?.let { Text(it, color = AegisColor.Danger, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

// ── KYC result card ───────────────────────────────────────────────────────────

@Composable
private fun KycResultCard(
    result:     KycProcessingResult,
    uiState:    ProfileUiState,
    onRetake:   () -> Unit,
    onConfirm:  () -> Unit,
    canConfirm: Boolean,
) {
    AegisCard {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // ── Quality ───────────────────────────────────────────────────────
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(Icons.Default.Star, null, tint = AegisColor.Warning, modifier = Modifier.size(18.dp))
                    Text("Image Quality", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = AegisColor.Text)
                    Spacer(Modifier.weight(1f))
                    val qColor = if (result.quality.acceptable) AegisColor.Success else AegisColor.Danger
                    Surface(color = qColor.copy(alpha = 0.12f), shape = MaterialTheme.shapes.extraLarge) {
                        Text(
                            if (result.quality.acceptable) "Acceptable" else "Low Quality",
                            style    = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color    = qColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }
                QualityBar("Sharpness",  result.quality.sharpness)
                QualityBar("Brightness", result.quality.brightness)
                QualityBar("Overall",    result.quality.overallScore)
            }

            // ── Tampering ─────────────────────────────────────────────────────
            if (result.tampering?.tampered == true) {
                Surface(
                    color  = AegisColor.DangerLight,
                    shape  = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, AegisColor.Danger.copy(alpha = 0.3f)),
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                    ) {
                        Icon(Icons.Default.Warning, null, tint = AegisColor.Danger, modifier = Modifier.size(18.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Tampering Detected", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = AegisColor.Danger)
                            if (result.tampering!!.indicators.isNotEmpty()) {
                                Text(result.tampering.indicators.joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = AegisColor.Danger.copy(alpha = 0.8f))
                            }
                        }
                    }
                }
            }

            // ── Extracted data ────────────────────────────────────────────────
            result.extractedData?.let { data ->
                HorizontalDivider(color = AegisColor.Border)
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.FindInPage, null, tint = AegisColor.Primary, modifier = Modifier.size(18.dp))
                        Text("Extracted Information", style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), color = AegisColor.Text)
                    }
                    Surface(color = AegisColor.Surface, shape = MaterialTheme.shapes.small, border = BorderStroke(1.dp, AegisColor.Border)) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            ExtractedRow("Full Name",       data.fullName)
                            ExtractedRow("Date of Birth",   data.dateOfBirth)
                            ExtractedRow("Document Number", data.documentNumber)
                            ExtractedRow("Document Type",   data.documentType)
                            ExtractedRow("Expiry Date",     data.expiryDate)
                            ExtractedRow("Address",         data.address)
                        }
                    }
                    Text("Please verify this information before confirming.",
                        style = MaterialTheme.typography.bodySmall, color = AegisColor.TextSubtle)
                }
            }

            // ── Confirm error ──────────────────────────────────────────────────
            uiState.confirmError?.let {
                Text(it, color = AegisColor.Danger, style = MaterialTheme.typography.bodySmall)
            }

            // ── Actions ───────────────────────────────────────────────────────
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick  = onRetake,
                    enabled  = !uiState.isConfirming,
                    modifier = Modifier.weight(1f).height(52.dp),
                    border   = BorderStroke(1.dp, AegisColor.Border),
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Retake")
                }
                Button(
                    onClick  = onConfirm,
                    enabled  = canConfirm && !uiState.isConfirming,
                    modifier = Modifier.weight(1f).height(52.dp),
                    colors   = ButtonDefaults.buttonColors(containerColor = AegisColor.Primary),
                    shape    = RoundedCornerShape(12.dp),
                ) {
                    if (uiState.isConfirming) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Default.CheckCircle, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Confirm & Submit", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

// ── Sub-components ────────────────────────────────────────────────────────────

@Composable
private fun QualityBar(label: String, score: Double) {
    val color = when {
        score >= 70 -> AegisColor.Success
        score >= 40 -> AegisColor.Warning
        else        -> AegisColor.Danger
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodySmall, color = AegisColor.TextMuted)
            Text("${score.toInt()}%", style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold), color = color)
        }
        LinearProgressIndicator(
            progress = { (score / 100.0).toFloat() },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(4.dp)),
            color    = color,
            trackColor = AegisColor.Border,
        )
    }
}

@Composable
private fun ExtractedRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = AegisColor.TextMuted,
            modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium),
            color = AegisColor.Text, modifier = Modifier.weight(0.6f))
    }
}

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = AegisColor.TextMuted)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = AegisColor.Text)
    }
}
