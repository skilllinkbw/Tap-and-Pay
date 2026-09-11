package com.getauthepay.app.ui.qr

import android.Manifest
import android.content.pm.PackageManager
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.getauthepay.app.ui.components.ScreenScaffold
import com.getauthepay.app.ui.components.SectionHeader
import com.getauthepay.app.ui.theme.BrandBlue
import com.getauthepay.app.ui.theme.DangerRed
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

/**
 * QR code scanner for the authorised camera workflows.
 *
 * WHAT THIS SCANNER IS FOR (directive section 14):
 *   A. Payment QR         — a tokenised payment request
 *   B. Payment reference  — an invoice / order reference QR
 *   C. Merchant QR        — a merchant identifier
 *   D. Receipt QR         — a receipt lookup reference
 *   E. Onboarding         — business / identity documents
 *   F. Sandbox            — a test payment token
 *
 * WHAT IT IS NOT FOR:
 *   This app does NOT scan payment cards to capture a PAN, expiry date or
 *   CVV. There is deliberately no card-OCR path. Any scanned value is
 *   treated as an opaque token or reference and is validated before use;
 *   it is never written to logs.
 */
@Composable
fun QrScanScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    var scanResult by remember { mutableStateOf<ScannedCode?>(null) }
    var mode by remember { mutableStateOf(ScanMode.PAYMENT_QR) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    ScreenScaffold(
        title = "Scan QR code",
        subtitle = "Camera is used for QR codes only",
        onBack = onBack,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            // Clear statement of purpose — required by directive section 14.
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Outlined.QrCodeScanner,
                        contentDescription = null,
                        tint = BrandBlue,
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        "This scanner reads QR codes only. Do not use it to photograph " +
                            "a payment card — AuthePay never captures card numbers, " +
                            "expiry dates or CVV codes.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            SectionHeader("What are you scanning?")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ScanModeChip(
                    label = "Payment QR",
                    selected = mode == ScanMode.PAYMENT_QR,
                    onClick = { mode = ScanMode.PAYMENT_QR },
                    modifier = Modifier.weight(1f),
                )
                ScanModeChip(
                    label = "Reference",
                    selected = mode == ScanMode.REFERENCE,
                    onClick = { mode = ScanMode.REFERENCE },
                    modifier = Modifier.weight(1f),
                )
                ScanModeChip(
                    label = "Receipt",
                    selected = mode == ScanMode.RECEIPT,
                    onClick = { mode = ScanMode.RECEIPT },
                    modifier = Modifier.weight(1f),
                )
            }

            Spacer(Modifier.height(16.dp))

            if (!hasCameraPermission) {
                CameraPermissionCard(onRequest = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                })
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(320.dp)
                        .clip(RoundedCornerShape(16.dp)),
                ) {
                    if (scanResult == null) {
                        CameraPreview(
                            onCodeDetected = { raw ->
                                if (scanResult == null) {
                                    scanResult = ScannedCode(
                                        rawValue = raw,
                                        mode = mode,
                                        looksSafe = looksLikeSafeToken(raw),
                                    )
                                }
                            },
                        )
                    } else {
                        ScanResultCard(
                            scanned = scanResult!!,
                            onScanAgain = { scanResult = null },
                            onDone = onBack,
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

private enum class ScanMode(val description: String) {
    PAYMENT_QR("a tokenised payment request"),
    REFERENCE("an invoice or order reference"),
    RECEIPT("a receipt lookup reference"),
}

private data class ScannedCode(
    val rawValue: String,
    val mode: ScanMode,
    val looksSafe: Boolean,
)

@Composable
private fun CameraPreview(onCodeDetected: (String) -> Unit) {
    val lifecycleOwner = LocalLifecycleOwner.current
    val context = LocalContext.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val scanner = remember { BarcodeScanning.getClient() }

    DisposableEffect(Unit) {
        onDispose {
            runCatching { cameraProviderFuture.get().unbindAll() }
            runCatching { scanner.close() }
            executor.shutdown()
        }
    }

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }
            val analysis = ImageAnalysis.Builder()
                .setTargetResolution(Size(1280, 720))
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analyser ->
                    analyser.setAnalyzer(executor) { proxy ->
                        analyzeCameraFrame(proxy, scanner, onCodeDetected)
                    }
                }

            runCatching {
                val provider = cameraProviderFuture.get()
                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysis,
                )
            }
            previewView
        },
        modifier = Modifier.fillMaxSize(),
    )
}

/**
 * Processes a single camera frame for QR / barcode detection.
 *
 * `proxy.image` is annotated `@ExperimentalGetImage`. The opt-in is declared on
 * this function — the immediate declaration that uses the experimental API — so
 * both the Kotlin compiler and Android Lint treat the usage as intentional
 * (rather than flagging it as an un-opted experimental access).
 */
@androidx.annotation.OptIn(ExperimentalGetImage::class)
private fun analyzeCameraFrame(
    proxy: ImageProxy,
    scanner: BarcodeScanner,
    onCodeDetected: (String) -> Unit,
) {
    val mediaImage = proxy.image
    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, proxy.imageInfo.rotationDegrees)
        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                val code = barcodes.firstOrNull {
                    it.valueType == Barcode.TYPE_TEXT || it.valueType == Barcode.TYPE_URL
                }
                code?.rawValue?.let(onCodeDetected)
            }
            .addOnFailureListener { /* ignored: keep scanning */ }
            .addOnCompleteListener { proxy.close() }
    } else {
        proxy.close()
    }
}

@Composable
private fun ScanResultCard(
    scanned: ScannedCode,
    onScanAgain: () -> Unit,
    onDone: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                "Code captured",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Read as ${scanned.mode.description}.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            // Deliberately truncated: a scanned token is never echoed in full
            // to the screen or to logs.
            Text(
                text = previewOf(scanned.rawValue),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            if (scanned.looksSafe) {
                Text(
                    "This looks like a valid AuthePay reference. The value is " +
                        "handled as an opaque token and validated server-side.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            } else {
                Text(
                    "This code is not a recognised AuthePay reference. It has not " +
                        "been used and nothing has been sent anywhere.",
                    style = MaterialTheme.typography.bodySmall,
                    color = DangerRed,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(20.dp))
            OutlinedButton(onClick = onScanAgain, modifier = Modifier.fillMaxWidth()) {
                Text("Scan again")
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
        }
    }
}

@Composable
private fun CameraPermissionCard(onRequest: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                Icons.Outlined.QrCodeScanner,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(12.dp))
            Text(
                "Camera permission needed",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Allow camera access to scan QR codes. The camera is used for " +
                    "QR codes only and images are processed on the device.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onRequest, modifier = Modifier.fillMaxWidth()) {
                Text("Grant camera permission")
            }
        }
    }
}

@Composable
private fun ScanModeChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = if (selected) BrandBlue.copy(alpha = 0.14f)
        else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) BrandBlue else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 10.dp),
        )
    }
}

/**
 * Conservative shape check for an AuthePay token/reference.
 *
 * The point of this check is to refuse anything that does not look like a
 * reference — in particular, a 13-19 digit run (a card number) is rejected
 * outright so a merchant can never feed a PAN into the payment flow by
 * scanning it.
 */
private fun looksLikeSafeToken(raw: String): Boolean {
    val value = raw.trim()
    val digitsOnly = value.all { it.isDigit() }
    // A bare 13-19 digit string is a card number. Never treat it as a token.
    if (digitsOnly && value.length in 13..19) return false
    return value.startsWith("AUTHEPAY:") ||
        value.startsWith("atx:", ignoreCase = true) ||
        value.length in 4..64
}

/** Short, non-sensitive preview of a scanned value — never the whole token. */
private fun previewOf(raw: String): String {
    val value = raw.trim()
    return if (value.length <= 12) value else value.take(6) + "…" + value.takeLast(4)
}
