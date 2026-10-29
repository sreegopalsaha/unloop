package unloop.launcher.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import unloop.launcher.subscription.model.PricingHelper
import unloop.launcher.subscription.model.SubscriptionPlan

@Composable
fun OnboardingSubscriptionScreen(
    plans: List<SubscriptionPlan>,
    isProcessing: Boolean,
    errorMessage: String?,
    onSubscribeMonthly: () -> Unit,
    onPurchaseLifetime: () -> Unit,
    onClearError: () -> Unit,
    onRetrySync: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedPlanId by remember { mutableStateOf(PricingHelper.PLAN_MONTHLY) }

    val monthlyPlan = plans.find { it.id == PricingHelper.PLAN_MONTHLY } ?: PricingHelper.DEFAULT_MONTHLY_PLAN
    val lifetimePlan = plans.find { it.id == PricingHelper.PLAN_LIFETIME } ?: PricingHelper.DEFAULT_LIFETIME_PLAN

    val scrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top Header
        Spacer(modifier = Modifier.height(8.dp))

        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
            shape = RoundedCornerShape(20.dp),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Text(
                text = "UNLOOP PREMIUM",
                style = MaterialTheme.typography.labelSmall.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.5.sp
                ),
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Reclaim your focus.",
            style = MaterialTheme.typography.headlineMedium.copy(
                fontWeight = FontWeight.Light,
                fontSize = 28.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = "Choose your plan to start using Unloop as your daily launcher.",
            style = MaterialTheme.typography.bodyMedium.copy(
                fontSize = 14.sp,
                lineHeight = 20.sp
            ),
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Plan 1: Monthly UPI AutoPay Card
        PlanCard(
            title = monthlyPlan.name,
            price = monthlyPlan.displayPrice,
            period = monthlyPlan.displayPeriod,
            tagline = "UPI AutoPay Recurring Mandate",
            description = "Subsequent monthly renewals are billed automatically. Cancel anytime.",
            badge = "Flexible",
            isSelected = selectedPlanId == PricingHelper.PLAN_MONTHLY,
            onClick = {
                if (!isProcessing) {
                    selectedPlanId = PricingHelper.PLAN_MONTHLY
                    onClearError()
                }
            }
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Plan 2: Lifetime One-Time Card
        PlanCard(
            title = lifetimePlan.name,
            price = lifetimePlan.displayPrice,
            period = lifetimePlan.displayPeriod,
            tagline = "Single One-Time Payment",
            description = "No recurring charges or mandates. Permanent entitlement for this account.",
            badge = "Best Value",
            isSelected = selectedPlanId == PricingHelper.PLAN_LIFETIME,
            onClick = {
                if (!isProcessing) {
                    selectedPlanId = PricingHelper.PLAN_LIFETIME
                    onClearError()
                }
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Feature Highlights
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                FeatureRow(text = "Minimalist distraction-free home screen")
                Spacer(modifier = Modifier.height(8.dp))
                FeatureRow(text = "Short-video reels & shorts blocker")
                Spacer(modifier = Modifier.height(8.dp))
                FeatureRow(text = "Mindful focus pause on opening tempting apps")
                Spacer(modifier = Modifier.height(8.dp))
                FeatureRow(text = "Quiet notification batching & manager")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // AutoPay Recurring Notice when Monthly is selected
        AnimatedVisibility(visible = selectedPlanId == PricingHelper.PLAN_MONTHLY) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "Recurring mandate info",
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .size(18.dp)
                            .padding(top = 2.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Monthly plan authorizes an automated UPI AutoPay recurring mandate of ₹99/month. You can cancel anytime from Settings or directly in your UPI app.",
                        style = MaterialTheme.typography.bodySmall.copy(
                            fontSize = 12.sp,
                            lineHeight = 16.sp
                        ),
                        color = MaterialTheme.colorScheme.secondary
                    )
                }
            }
        }

        // Error message banner
        if (!errorMessage.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.error.copy(alpha = 0.2f),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Payment Notice",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onBackground
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = errorMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
                        )
                    }
                    Button(
                        onClick = onRetrySync,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.height(36.dp)
                    ) {
                        Text(text = "Retry", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Main CTA Button
        Button(
            onClick = {
                if (isProcessing) return@Button
                if (selectedPlanId == PricingHelper.PLAN_MONTHLY) {
                    onSubscribeMonthly()
                } else {
                    onPurchaseLifetime()
                }
            },
            enabled = !isProcessing,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary
            ),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
        ) {
            if (isProcessing) {
                CircularProgressIndicator(
                    modifier = Modifier.size(22.dp),
                    strokeWidth = 2.5.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = "Verifying with server...",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            } else {
                val buttonText = if (selectedPlanId == PricingHelper.PLAN_MONTHLY) {
                    "Authorize Monthly AutoPay • ₹99/mo"
                } else {
                    "Get Lifetime Premium • ₹699"
                }
                Text(
                    text = buttonText,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Secure payments processed via Razorpay. Encrypted verification.",
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun PlanCard(
    title: String,
    price: String,
    period: String,
    tagline: String,
    description: String,
    badge: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val borderWidth by animateDpAsState(
        targetValue = if (isSelected) 2.dp else 1.dp,
        label = "PlanCardBorderWidth"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        label = "PlanCardBorderColor"
    )
    val cardBg by animateColorAsState(
        targetValue = if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f),
        label = "PlanCardBg"
    )

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(borderWidth, borderColor),
        color = cardBg,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Radio Dot
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .border(
                                width = if (isSelected) 6.dp else 2.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = badge,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = price,
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = period,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = tagline,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall.copy(lineHeight = 16.sp),
                color = MaterialTheme.colorScheme.secondary
            )
        }
    }
}

@Composable
private fun FeatureRow(text: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(12.dp)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
        )
    }
}
