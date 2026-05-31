package com.aegispay.android.ui.profile

import android.Manifest
import android.app.Activity
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import com.aegispay.android.auth.BiometricAuthManager
import com.aegispay.android.network.KycDocumentType
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

                        if (cfg.canUpload) {
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

            // ── Upload success notice ─────────────────────────────────────────
            if (uiState.uploadSuccess) {
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
                        ProfileInfoRow("Phone",    profile.phone ?: "Not set")
                        ProfileInfoRow("Role",     profile.role)
                    }
                }
            }

            // ── Phone number (OTP verification) ──────────────────────────────
            PhoneCard(uiState = uiState, viewModel = viewModel)

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

// ── Sub-components ────────────────────────────────────────────────────────────

@Composable
private fun ProfileInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = AegisColor.TextMuted)
        Text(value, style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium), color = AegisColor.Text)
    }
}

/**
 * Phone number card with Firebase OTP verification flow.
 *
 * Flow: [IDLE] → tap Add/Update → [ENTER_PHONE] → tap "Send OTP" → [SENDING]
 *   → SMS arrives → [ENTER_OTP] → tap "Verify & Save" → [SAVING] → [SAVED]
 *
 * Firebase verifies phone ownership; AegisPay backend is updated only after
 * Firebase confirms the credential.  The Firebase session is signed out immediately
 * after, keeping it completely separate from the Keycloak session.
 */
@Composable
private fun PhoneCard(uiState: ProfileUiState, viewModel: ProfileViewModel) {
    val context  = LocalContext.current
    val activity = context as? Activity

    AegisCard {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

            // ── Header row ────────────────────────────────────────────────────
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
                    Icon(Icons.Default.Phone, null, tint = AegisColor.Primary, modifier = Modifier.size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text("Phone Number",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = AegisColor.Text)
                    Text("Required for SMS notifications",
                        style = MaterialTheme.typography.bodySmall,
                        color = AegisColor.TextMuted)
                }
                if (uiState.phoneStep == PhoneStep.IDLE) {
                    TextButton(onClick = { viewModel.openPhoneSheet() }) {
                        Text(
                            text  = if (uiState.profile?.phone != null) "Update" else "Add",
                            color = AegisColor.Primary,
                        )
                    }
                }
            }

            // Current (masked) phone — only shown when idle and a number is on file
            if (uiState.phoneStep == PhoneStep.IDLE && uiState.profile?.phone != null) {
                Text(
                    uiState.profile.phone,
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                    color = AegisColor.Text,
                )
            }

            // ── Saved success banner ──────────────────────────────────────────
            if (uiState.phoneStep == PhoneStep.SAVED) {
                Surface(
                    color  = AegisColor.SuccessLight,
                    shape  = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, AegisColor.Success.copy(alpha = 0.3f)),
                ) {
                    Row(
                        verticalAlignment     = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier              = Modifier.fillMaxWidth().padding(10.dp),
                    ) {
                        Icon(Icons.Default.CheckCircle, null, tint = AegisColor.Success, modifier = Modifier.size(20.dp))
                        Text("Phone number saved",
                            style = MaterialTheme.typography.bodySmall, color = AegisColor.Success,
                            modifier = Modifier.weight(1f))
                        TextButton(
                            onClick      = { viewModel.openPhoneSheet() },
                            contentPadding = PaddingValues(horizontal = 4.dp),
                        ) { Text("Update", color = AegisColor.Primary, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }

            // ── Phone entry step ──────────────────────────────────────────────
            if (uiState.phoneStep == PhoneStep.ENTER_PHONE || uiState.phoneStep == PhoneStep.SENDING) {
                OutlinedTextField(
                    value         = uiState.phoneInput,
                    onValueChange = { viewModel.onPhoneChange(it) },
                    label         = { Text("Phone number") },
                    placeholder   = { Text("+919876543210") },
                    leadingIcon   = { Icon(Icons.Default.Phone, null) },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction    = ImeAction.Send,
                    ),
                    keyboardActions = KeyboardActions(onSend = {
                        activity?.let { viewModel.sendOtp(it) }
                    }),
                    isError  = uiState.phoneError != null,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = AegisColor.Primary,
                        unfocusedBorderColor = AegisColor.Border,
                    ),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { viewModel.cancelPhone() }) { Text("Cancel", color = AegisColor.TextMuted) }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick  = { activity?.let { viewModel.sendOtp(it) } },
                        enabled  = uiState.phoneStep == PhoneStep.ENTER_PHONE && uiState.phoneInput.isNotBlank(),
                        colors   = ButtonDefaults.buttonColors(containerColor = AegisColor.Primary),
                        shape    = RoundedCornerShape(8.dp),
                    ) {
                        if (uiState.phoneStep == PhoneStep.SENDING) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("Send OTP", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ── OTP entry step ────────────────────────────────────────────────
            if (uiState.phoneStep == PhoneStep.ENTER_OTP || uiState.phoneStep == PhoneStep.SAVING) {
                Text(
                    "Enter the 6-digit code sent to ${uiState.phoneInput}",
                    style = MaterialTheme.typography.bodySmall,
                    color = AegisColor.TextMuted,
                )
                OutlinedTextField(
                    value         = uiState.otpInput,
                    onValueChange = { if (it.length <= 6) viewModel.onOtpChange(it) },
                    label         = { Text("6-digit OTP") },
                    singleLine    = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.NumberPassword,
                        imeAction    = ImeAction.Done,
                    ),
                    keyboardActions = KeyboardActions(onDone = { viewModel.verifyOtp() }),
                    isError  = uiState.phoneError != null,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor   = AegisColor.Primary,
                        unfocusedBorderColor = AegisColor.Border,
                    ),
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { viewModel.cancelPhone() }) { Text("Cancel", color = AegisColor.TextMuted) }
                    Spacer(Modifier.weight(1f))
                    Button(
                        onClick  = { viewModel.verifyOtp() },
                        enabled  = uiState.phoneStep == PhoneStep.ENTER_OTP && uiState.otpInput.length == 6,
                        colors   = ButtonDefaults.buttonColors(containerColor = AegisColor.Primary),
                        shape    = RoundedCornerShape(8.dp),
                    ) {
                        if (uiState.phoneStep == PhoneStep.SAVING) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                            Spacer(Modifier.width(6.dp))
                        }
                        Text("Verify & Save", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // ── Error ─────────────────────────────────────────────────────────
            uiState.phoneError?.let {
                Text(it, color = AegisColor.Danger, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
