package com.getauthepay.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.getauthepay.app.di.ServiceLocator
import com.getauthepay.app.security.ScreenshotProtector
import com.getauthepay.app.ui.LocalLocator
import com.getauthepay.app.ui.about.AboutScreen
import com.getauthepay.app.ui.accept.AcceptPaymentScreen
import com.getauthepay.app.ui.auth.LoginScreen
import com.getauthepay.app.ui.auth.OtpScreen
import com.getauthepay.app.ui.dashboard.DashboardScreen
import com.getauthepay.app.ui.dashboard.DevicesScreen
import com.getauthepay.app.ui.dashboard.TransactionDetailScreen
import com.getauthepay.app.ui.dashboard.TransactionHistoryScreen
import com.getauthepay.app.ui.help.HelpScreen
import com.getauthepay.app.ui.nav.Routes
import com.getauthepay.app.ui.onboarding.OnboardingScreen
import com.getauthepay.app.ui.onboarding.VerificationPendingScreen
import com.getauthepay.app.ui.qr.QrScanScreen
import com.getauthepay.app.ui.refund.RefundScreen
import com.getauthepay.app.ui.security.SecurityAlertsScreen
import com.getauthepay.app.ui.security.SecuritySettingsScreen
import com.getauthepay.app.ui.settings.SettingsScreen
import com.getauthepay.app.ui.settlement.SettlementsScreen
import com.getauthepay.app.ui.splash.SplashScreen
import com.getauthepay.app.ui.team.TeamScreen
import com.getauthepay.app.ui.theme.AuthePayTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val locator = (application as AuthePayApp).locator
        locator.currentActivity = this
        ScreenshotProtector.protect(this)

        setContent {
            AuthePayTheme {
                CompositionLocalProvider(LocalLocator provides locator) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        AppNavHost()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        (application as? AuthePayApp)?.locator?.currentActivity = null
        super.onDestroy()
    }
}

@Composable
private fun AppNavHost() {
    val navController = rememberNavController()
    val locator = LocalLocator.current
    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(onContinue = {
                val destination = if (locator.sessionManager.current().isActive) Routes.DASHBOARD
                else Routes.LOGIN
                navController.navigate(destination) {
                    popUpTo(Routes.SPLASH) { inclusive = true }
                }
            })
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = { phone ->
                    navController.navigate(Routes.otp(phone)) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onRegister = { navController.navigate(Routes.ONBOARDING) },
            )
        }
        composable(
            Routes.OTP,
            arguments = listOf(navArgument("phone") { type = NavType.StringType }),
        ) { entry ->
            val phone = entry.arguments?.getString("phone").orEmpty()
            OtpScreen(
                phone = phone,
                onVerified = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.ONBOARDING) {
            OnboardingScreen(
                onComplete = {
                    navController.navigate(Routes.VERIFICATION_PENDING) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.VERIFICATION_PENDING) {
            VerificationPendingScreen(
                onDone = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.VERIFICATION_PENDING) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onAcceptPayment = { navController.navigate(Routes.ACCEPT_PAYMENT) },
                onTransactions = { navController.navigate(Routes.TRANSACTIONS) },
                onSettlements = { navController.navigate(Routes.SETTLEMENTS) },
                onDevices = { navController.navigate(Routes.DEVICES) },
                onTeam = { navController.navigate(Routes.TEAM) },
                onSecurity = { navController.navigate(Routes.SECURITY_ALERTS) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.DASHBOARD) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.ACCEPT_PAYMENT) {
            AcceptPaymentScreen(
                onCompleted = { navController.popBackStack() },
                onScanQr = { navController.navigate(Routes.QR_SCAN) },
            )
        }
        composable(Routes.TRANSACTIONS) {
            TransactionHistoryScreen(
                onBack = { navController.popBackStack() },
                onTransaction = { id -> navController.navigate(Routes.transactionDetail(id)) },
            )
        }
        composable(
            Routes.TRANSACTION_DETAIL,
            arguments = listOf(navArgument("transactionId") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("transactionId").orEmpty()
            TransactionDetailScreen(
                transactionId = id,
                onBack = { navController.popBackStack() },
                onRefund = { navController.navigate(Routes.refund(id)) },
            )
        }
        composable(
            Routes.REFUND,
            arguments = listOf(navArgument("transactionId") { type = NavType.StringType }),
        ) { entry ->
            val id = entry.arguments?.getString("transactionId").orEmpty()
            RefundScreen(
                transactionId = id,
                onDone = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTLEMENTS) {
            SettlementsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.DEVICES) {
            DevicesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TEAM) {
            TeamScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SECURITY_ALERTS) {
            SecurityAlertsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onSecuritySettings = { navController.navigate(Routes.SECURITY_SETTINGS) },
                onHelp = { navController.navigate(Routes.HELP) },
                onAbout = { navController.navigate(Routes.ABOUT) },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.DASHBOARD) { inclusive = true }
                    }
                },
            )
        }
        composable(Routes.SECURITY_SETTINGS) {
            SecuritySettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.HELP) {
            HelpScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.ABOUT) {
            AboutScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.QR_SCAN) {
            QrScanScreen(onBack = { navController.popBackStack() })
        }
    }
}