package com.aegispay.android.ui

import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.*
import androidx.navigation.compose.*
import com.aegispay.android.auth.AuthState
import com.aegispay.android.auth.BiometricAuthManager
import com.aegispay.android.ui.auth.AuthViewModel
import com.aegispay.android.ui.auth.LoginScreen
import com.aegispay.android.ui.backoffice.BackOfficeScreen
import com.aegispay.android.ui.triage.TriageScreen
import com.aegispay.android.ui.dashboard.DashboardScreen
import com.aegispay.android.ui.notifications.NotificationsScreen
import com.aegispay.android.ui.onboarding.OnboardingScreen
import com.aegispay.android.ui.profile.ProfileScreen
import com.aegispay.android.ui.sendmoney.SendMoneyScreen
import com.aegispay.android.ui.transactions.TransactionDetailScreen
import com.aegispay.android.ui.transactions.TransactionListScreen
import com.aegispay.android.ui.wallet.WalletScreen

// ── Route constants ───────────────────────────────────────────────────────────

object Route {
    const val LOGIN              = "login"
    const val ONBOARDING         = "onboarding"
    const val DASHBOARD          = "dashboard"
    const val TRANSACTIONS       = "transactions"
    const val TRANSACTION_DETAIL = "transactions/{transactionId}"
    const val SEND_MONEY         = "send"
    const val NOTIFICATIONS      = "notifications"
    const val PROFILE            = "profile"
    const val BACK_OFFICE        = "backoffice"
    const val WALLET             = "wallet"
    const val TRIAGE             = "triage?txId={txId}&service={service}"

    fun transactionDetail(id: String) = "transactions/$id"
    fun triage(txId: String? = null, service: String? = null) =
        "triage?txId=${txId ?: ""}&service=${service ?: ""}"
}

private val BACK_OFFICE_ROLES = setOf("BACK_OFFICE", "ADMIN")
private val ADMIN_ROLES        = setOf("ADMIN")

// ── Nav host ──────────────────────────────────────────────────────────────────

@Composable
fun AegisNavHost(
    authViewModel:        AuthViewModel,
    onStartAuthFlow:      () -> Unit,
    modifier:             Modifier = Modifier,
    biometricAuthManager: BiometricAuthManager? = null,
) {
    val navController = rememberNavController()
    val authState by authViewModel.authState.collectAsState()

    // Derive role from auth state (safe — doesn't hit the token store on every recomposition)
    val userRole = (authState as? AuthState.Authenticated)?.user?.role ?: ""
    val isBackOfficeUser = userRole in BACK_OFFICE_ROLES
    val isAdminUser      = userRole in ADMIN_ROLES

    // React to auth state changes
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Authenticated -> {
                // Staff roles land on their work surfaces, not the customer dashboard —
                // mirrors the web ROLE_LANDING map in role-routing.ts.
                val landingRoute = when {
                    isAdminUser      -> Route.TRIAGE
                    isBackOfficeUser -> Route.BACK_OFFICE
                    else             -> Route.DASHBOARD
                }
                navController.navigate(landingRoute) {
                    popUpTo(0) { inclusive = true }
                }
            }
            is AuthState.NeedsRegistration -> {
                navController.navigate(Route.ONBOARDING) {
                    popUpTo(0) { inclusive = true }
                }
            }
            is AuthState.Unauthenticated -> {
                navController.navigate(Route.LOGIN) {
                    popUpTo(0) { inclusive = true }
                }
            }
            else -> Unit
        }
    }

    val startDest = when (authState) {
        is AuthState.Authenticated      -> Route.DASHBOARD
        is AuthState.NeedsRegistration  -> Route.ONBOARDING
        else                            -> Route.LOGIN
    }

    NavHost(
        navController    = navController,
        startDestination = startDest,
        modifier         = modifier,
    ) {

        composable(Route.LOGIN) {
            LoginScreen(
                viewModel       = authViewModel,
                onStartAuthFlow = onStartAuthFlow,
            )
        }

        composable(Route.ONBOARDING) {
            val email = (authState as? AuthState.NeedsRegistration)?.email
            OnboardingScreen(
                viewModel    = hiltViewModel(),
                prefillEmail = email,
                onSignOut    = { authViewModel.signOut() },
            )
        }

        composable(Route.DASHBOARD) {
            DashboardScreen(
                viewModel                 = hiltViewModel(),
                onNavigateToTransactions  = { navController.navigate(Route.TRANSACTIONS) },
                onNavigateToDetail        = { id -> navController.navigate(Route.transactionDetail(id)) },
                onNavigateToSend          = { navController.navigate(Route.SEND_MONEY) },
                onNavigateToNotifications = { navController.navigate(Route.NOTIFICATIONS) },
                onNavigateToProfile       = { navController.navigate(Route.PROFILE) },
                onNavigateToBackOffice    = { navController.navigate(Route.BACK_OFFICE) },
                onNavigateToWallet        = { navController.navigate(Route.WALLET) },
            )
        }

        composable(Route.WALLET) {
            // PaymentSheet is created via rememberPaymentSheet inside WalletScreen.
            // No Activity-level wiring needed — the Compose API handles it.
            WalletScreen(
                viewModel    = hiltViewModel(),
                onNavigateUp = { navController.navigateUp() },
            )
        }

        composable(Route.TRANSACTIONS) {
            TransactionListScreen(
                viewModel        = hiltViewModel(),
                onNavigateToDetail = { id -> navController.navigate(Route.transactionDetail(id)) },
                onNavigateUp     = { navController.navigateUp() },
            )
        }

        composable(
            route     = Route.TRANSACTION_DETAIL,
            arguments = listOf(navArgument("transactionId") { type = NavType.StringType }),
            deepLinks = listOf(
                // Custom scheme:  aegispay://app/transactions/{id}
                navDeepLink { uriPattern = "aegispay://app/transactions/{transactionId}" },
                // App Link (HTTPS): https://api.aegispay.shreyasshelar.uk/transactions/{id}
                navDeepLink { uriPattern = "https://api.aegispay.shreyasshelar.uk/transactions/{transactionId}" },
            ),
        ) { back ->
            val txId = back.arguments?.getString("transactionId")
            if (txId == null) {
                // Deep link arrived without transactionId — navigate back rather than crash
                navController.navigateUp()
                return@composable
            }
            TransactionDetailScreen(
                transactionId      = txId,
                viewModel          = hiltViewModel(),
                onNavigateUp       = { navController.navigateUp() },
                onNavigateToTriage = if (isAdminUser) {
                    { id, svc -> navController.navigate(Route.triage(id, svc)) }
                } else null,
            )
        }

        composable(
            route     = Route.SEND_MONEY,
            deepLinks = listOf(
                navDeepLink { uriPattern = "aegispay://app/send" },
                navDeepLink { uriPattern = "https://api.aegispay.shreyasshelar.uk/send" },
            ),
        ) { _ ->
            SendMoneyScreen(
                viewModel           = hiltViewModel(),
                onNavigateUp        = { navController.navigateUp() },
                onNavigateToDetail  = { id ->
                    navController.navigate(Route.transactionDetail(id)) {
                        popUpTo(Route.DASHBOARD)
                    }
                },
                onNavigateToProfile = { navController.navigate(Route.PROFILE) },
            )
        }

        composable(Route.NOTIFICATIONS) {
            NotificationsScreen(
                viewModel    = hiltViewModel(),
                onNavigateUp = { navController.navigateUp() },
            )
        }

        composable(Route.PROFILE) {
            ProfileScreen(
                viewModel            = hiltViewModel(),
                onNavigateUp         = { navController.navigateUp() },
                onSignOut            = { authViewModel.signOut() },
                biometricAuthManager = biometricAuthManager,
            )
        }

        // ── Back-office (role-gated) ──────────────────────────────────────────
        if (isBackOfficeUser) {
            composable(Route.BACK_OFFICE) {
                BackOfficeScreen(
                    viewModel    = hiltViewModel(),
                    onNavigateUp = { navController.navigateUp() },
                    onNavigateToTriage = if (isAdminUser) {
                        { txId, svc -> navController.navigate(Route.triage(txId, svc)) }
                    } else null,
                )
            }
        }

        // ── AI Triage Agent (ADMIN only) ─────────────────────────────────────
        if (isAdminUser) {
            composable(
                route     = Route.TRIAGE,
                arguments = listOf(
                    navArgument("txId")    { type = NavType.StringType; defaultValue = "" },
                    navArgument("service") { type = NavType.StringType; defaultValue = "" },
                ),
            ) { back ->
                val txId    = back.arguments?.getString("txId")?.ifBlank { null }
                val service = back.arguments?.getString("service")?.ifBlank { null }
                TriageScreen(
                    prefillTransactionId = txId,
                    prefillService       = service,
                    onBack               = { navController.navigateUp() },
                    viewModel            = hiltViewModel(),
                )
            }
        }
    }
}
