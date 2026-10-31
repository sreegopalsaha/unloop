package unloop.launcher.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import unloop.launcher.ui.components.HeaderActionType
import unloop.launcher.ui.components.ScreenHeader
import unloop.launcher.util.AppVersionUtils

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    versionOverride: String? = null,
    onGetHelpClick: (() -> Unit)? = null,
    onSendFeedbackClick: (() -> Unit)? = null,
    onPrivacyPolicyClick: (() -> Unit)? = null,
    onTermsOfServiceClick: (() -> Unit)? = null,
    onRefundPolicyClick: (() -> Unit)? = null
) {
    BackHandler(onBack = onBack)

    val context = LocalContext.current
    val appVersion = remember(context, versionOverride) {
        versionOverride ?: AppVersionUtils.getAppVersion(context)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .safeDrawingPadding()
            .padding(horizontal = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp, bottom = 24.dp)
        ) {
            ScreenHeader(
                title = "About & Support",
                onActionClick = onBack,
                actionType = HeaderActionType.BACK
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Unloop",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Your Phone, Your Control. • v$appVersion",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.secondary
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )

            AboutSectionHeader(title = "HELP & SUPPORT")

            AboutActionItem(
                title = "Get Help & FAQs",
                subtitle = "Troubleshooting guide and common questions",
                onClick = {
                    if (onGetHelpClick != null) {
                        onGetHelpClick()
                    } else {
                        openUrlSafely(
                            context = context,
                            url = "https://unloop-landing-page.vercel.app/help",
                            fallbackEmailSubject = "Unloop Help & Support"
                        )
                    }
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            AboutActionItem(
                title = "Send Feedback",
                subtitle = "Report issues, suggest features, or say hi",
                onClick = {
                    if (onSendFeedbackClick != null) {
                        onSendFeedbackClick()
                    } else {
                        sendFeedbackEmail(context, appVersion)
                    }
                }
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )

            AboutSectionHeader(title = "LEGAL & PRIVACY")

            AboutActionItem(
                title = "Privacy Policy",
                subtitle = "Learn how your data stays strictly on-device",
                onClick = {
                    if (onPrivacyPolicyClick != null) {
                        onPrivacyPolicyClick()
                    } else {
                        openUrlSafely(
                            context = context,
                            url = "https://unloop-landing-page.vercel.app/privacy-policy/",
                            fallbackEmailSubject = "Unloop Privacy Inquiry"
                        )
                    }
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            AboutActionItem(
                title = "Terms and Conditions",
                subtitle = "Review our terms of service and license grant",
                onClick = {
                    if (onTermsOfServiceClick != null) {
                        onTermsOfServiceClick()
                    } else {
                        openUrlSafely(
                            context = context,
                            url = "https://unloop-landing-page.vercel.app/terms-and-conditions/",
                            fallbackEmailSubject = "Unloop Terms & Conditions"
                        )
                    }
                }
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)

            AboutActionItem(
                title = "Refund & Cancellation Policy",
                subtitle = "Subscription plans, duplicate charges, and refund requests",
                onClick = {
                    if (onRefundPolicyClick != null) {
                        onRefundPolicyClick()
                    } else {
                        openUrlSafely(
                            context = context,
                            url = "https://unloop-landing-page.vercel.app/refund-policy/",
                            fallbackEmailSubject = "Unloop Refund Inquiry"
                        )
                    }
                }
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )

            Text(
                text = "Break the Scroll Cycle",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Break free from endless scrolling and build a healthier relationship with your phone. Unloop is a simple, distraction-free Android launcher designed to help you take control of your attention. With a clean home screen and built-in tools to manage distractions, Unloop helps you spend less time mindlessly scrolling and more time doing what matters.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "With a clean, text-based home screen and no distracting app icons, Unloop helps you focus on what you actually need. Open what matters, ignore what doesn't, and take back control of your attention.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )

            Text(
                text = "Features",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(12.dp))

            FeatureItem(
                title = "Clean Home Screen",
                description = "A simple, text-based interface without distracting app icons."
            )
            FeatureItem(
                title = "Focus Mode",
                description = "Reduce distractions when studying, working, or concentrating on what matters."
            )
            FeatureItem(
                title = "Short-Video Blocking",
                description = "Help break the habit of endlessly scrolling through short videos on supported apps and interfaces."
            )
            FeatureItem(
                title = "Notification Management",
                description = "Keep distracting notifications out of your way and review filtered alerts inside Unloop."
            )
            FeatureItem(
                title = "Hidden Apps",
                description = "Hide selected apps from your regular home screen and manage them when needed."
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )

            Text(
                text = "Your Privacy, Our Promise",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "Your privacy matters. Unloop is designed to keep your personal activity on your device.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Your notification content, app usage, and other locally managed activity stay on your device and are not transmitted to our servers. We only store the account information necessary to manage your account and subscription.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Your digital habits are personal. They should stay that way.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            HorizontalDivider(
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                InfoRow(label = "Developer", value = "Sree Gopal Saha")
                InfoRow(label = "Developer Email", value = "sreegopal0101@gmail.com")
                InfoRow(label = "App Version", value = appVersion)
            }
        }
    }
}

@Composable
private fun AboutSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelMedium.copy(
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp
        ),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
private fun AboutActionItem(
    title: String,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (subtitle != null) {
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
            modifier = Modifier.size(20.dp)
        )
    }
}

@Composable
private fun FeatureItem(
    title: String,
    description: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
        Spacer(modifier = Modifier.height(3.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

private fun openUrlSafely(
    context: Context,
    url: String,
    fallbackEmailSubject: String? = null
) {
    try {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    } catch (_: Exception) {
        if (fallbackEmailSubject != null) {
            sendEmailFallback(context, fallbackEmailSubject)
        }
    }
}

private fun sendFeedbackEmail(context: Context, appVersion: String) {
    try {
        val deviceInfo = "Device: ${Build.MANUFACTURER} ${Build.MODEL}\nAndroid: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\nApp Version: $appVersion"
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:sreegopal0101@gmail.com")
            putExtra(Intent.EXTRA_SUBJECT, "Unloop Feedback (v$appVersion)")
            putExtra(Intent.EXTRA_TEXT, "\n\n---\n$deviceInfo")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, "Send Feedback"))
    } catch (_: Exception) {
    }
}

private fun sendEmailFallback(context: Context, subject: String) {
    try {
        val intent = Intent(Intent.ACTION_SENDTO).apply {
            data = Uri.parse("mailto:sreegopal0101@gmail.com")
            putExtra(Intent.EXTRA_SUBJECT, subject)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(Intent.createChooser(intent, subject))
    } catch (_: Exception) {
    }
}
