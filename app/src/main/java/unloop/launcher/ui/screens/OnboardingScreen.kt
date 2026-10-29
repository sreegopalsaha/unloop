package unloop.launcher.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import unloop.launcher.R
import unloop.launcher.auth.model.AuthState
import unloop.launcher.subscription.model.SubscriptionPlan

@Composable
fun OnboardingScreen(
    isDefaultLauncher: Boolean,
    initialPage: Int = 0,
    authState: AuthState = AuthState.Idle,
    isEntitled: Boolean = false,
    plans: List<SubscriptionPlan> = emptyList(),
    isSubscriptionProcessing: Boolean = false,
    subscriptionErrorMessage: String? = null,
    onContinueWithGoogle: () -> Unit,
    onDismissAuthError: () -> Unit = {},
    onSubscribeMonthly: () -> Unit = {},
    onPurchaseLifetime: () -> Unit = {},
    onClearSubscriptionError: () -> Unit = {},
    onRetrySubscriptionSync: () -> Unit = {},
    onSetDefaultLauncher: () -> Unit,
    onCompleteOnboarding: () -> Unit,
    onIntroCompleted: () -> Unit
) {
    val pageCount = 4
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, pageCount - 1)
    ) { pageCount }
    val coroutineScope = rememberCoroutineScope()
    val isAuthenticated = authState is AuthState.Authenticated

    // Auto-advance to Page 1 (Welcome to Unloop) as soon as user successfully signs in on Page 0
    LaunchedEffect(isAuthenticated) {
        if (isAuthenticated && pagerState.currentPage == 0) {
            pagerState.animateScrollToPage(1)
        }
    }

    // Auto-advance to Page 3 (Default Launcher) as soon as user becomes entitled on Page 2
    LaunchedEffect(isEntitled, pagerState.currentPage) {
        if (isEntitled && pagerState.currentPage == 2) {
            pagerState.animateScrollToPage(3)
        }
    }

    // Mark intro completed as soon as user navigates to the default launcher setup screen (page 3) with valid auth and entitlement
    LaunchedEffect(pagerState.currentPage, isAuthenticated, isEntitled) {
        if (pagerState.currentPage >= 3 && isAuthenticated && isEntitled) {
            onIntroCompleted()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Main Pager with 4 screens:
        // Page 0: "Continue with Google"
        // Page 1: "Welcome to Unloop" (Marketing / App Explanation)
        // Page 2: NEW "Subscription & Payment" Onboarding Screen
        // Page 3: "Set as Default Launcher"
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = when (pagerState.currentPage) {
                0 -> false
                2 -> isEntitled // Gate: Cannot swipe past subscription until entitled
                else -> true
            },
            modifier = Modifier.fillMaxSize()
        ) { page ->
            when (page) {
                0 -> OnboardingSignInScreen(
                    authState = authState,
                    onContinueWithGoogle = onContinueWithGoogle
                )

                1 -> {
                    if (isAuthenticated) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp)
                                .padding(top = 80.dp, bottom = 100.dp)
                        ) {
                            OnboardingExplainAppScreen()
                        }
                    } else {
                        OnboardingSignInScreen(
                            authState = authState,
                            onContinueWithGoogle = onContinueWithGoogle
                        )
                    }
                }

                2 -> {
                    if (isAuthenticated) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp)
                                .padding(top = 70.dp, bottom = 24.dp)
                        ) {
                            OnboardingSubscriptionScreen(
                                plans = plans,
                                isProcessing = isSubscriptionProcessing,
                                errorMessage = subscriptionErrorMessage,
                                onSubscribeMonthly = onSubscribeMonthly,
                                onPurchaseLifetime = onPurchaseLifetime,
                                onClearError = onClearSubscriptionError,
                                onRetrySync = onRetrySubscriptionSync
                            )
                        }
                    } else {
                        OnboardingSignInScreen(
                            authState = authState,
                            onContinueWithGoogle = onContinueWithGoogle
                        )
                    }
                }

                3 -> {
                    if (isAuthenticated && isEntitled) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp)
                                .padding(top = 80.dp, bottom = 120.dp)
                        ) {
                            OnboardingDefaultLauncherScreen(
                                isDefaultLauncher = isDefaultLauncher,
                                onSetDefaultLauncher = onSetDefaultLauncher
                            )
                        }
                    } else if (isAuthenticated) {
                        // Redirect to subscription gate
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 24.dp)
                                .padding(top = 70.dp, bottom = 24.dp)
                        ) {
                            OnboardingSubscriptionScreen(
                                plans = plans,
                                isProcessing = isSubscriptionProcessing,
                                errorMessage = subscriptionErrorMessage,
                                onSubscribeMonthly = onSubscribeMonthly,
                                onPurchaseLifetime = onPurchaseLifetime,
                                onClearError = onClearSubscriptionError,
                                onRetrySync = onRetrySubscriptionSync
                            )
                        }
                    } else {
                        OnboardingSignInScreen(
                            authState = authState,
                            onContinueWithGoogle = onContinueWithGoogle
                        )
                    }
                }
            }
        }

        // Top Minimalist Progress Indicators
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(vertical = 16.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            repeat(pageCount) { index ->
                val isSelected = pagerState.currentPage == index
                val width by animateDpAsState(
                    targetValue = if (isSelected) 32.dp else 8.dp,
                    label = "IndicatorWidth"
                )
                val color by animateColorAsState(
                    targetValue = if (isSelected) {
                        if (pagerState.currentPage == 0) Color.White else MaterialTheme.colorScheme.primary
                    } else {
                        if (pagerState.currentPage == 0) Color.White.copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant
                    },
                    label = "IndicatorColor"
                )

                Box(
                    modifier = Modifier
                        .padding(horizontal = 4.dp)
                        .height(4.dp)
                        .width(width)
                        .clip(CircleShape)
                        .background(color)
                )
            }
        }

        // Bottom Actions & Navigation (for Page 1 and Page 3)
        if (pagerState.currentPage == 1 || pagerState.currentPage == 3) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 24.dp)
            ) {
                when (pagerState.currentPage) {
                    1 -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(2)
                                    }
                                }
                            ) {
                                Text(
                                    text = "Skip",
                                    color = MaterialTheme.colorScheme.secondary,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(2)
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text(
                                    text = "Next",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Medium)
                                )
                            }
                        }
                    }

                    3 -> {
                        if (isAuthenticated && isEntitled) {
                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (isDefaultLauncher) {
                                    Button(
                                        onClick = onCompleteOnboarding,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                    ) {
                                        Text(
                                            text = "Get Started",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                    }
                                } else {
                                    Button(
                                        onClick = onSetDefaultLauncher,
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(52.dp)
                                    ) {
                                        Text(
                                            text = "Set as Default Launcher",
                                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(10.dp))

                                    TextButton(
                                        onClick = onCompleteOnboarding,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text(
                                            text = "Continue without setting default",
                                            color = MaterialTheme.colorScheme.secondary,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Screen 1 (Position 1): Redesigned "Continue with Google" onboarding screen.
 */
@Composable
private fun OnboardingSignInScreen(
    authState: AuthState,
    onContinueWithGoogle: () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Image(
            painter = painterResource(id = R.drawable.onboarding_bg),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Black.copy(alpha = 0.45f),
                            Color.Black.copy(alpha = 0.2f),
                            Color.Black.copy(alpha = 0.85f)
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(top = 40.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.weight(1f))

            Text(
                text = "Unloop",
                style = MaterialTheme.typography.displayMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 40.sp,
                    letterSpacing = 2.sp
                ),
                color = Color.White,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.weight(1f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Your phone, your control.",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.Medium,
                        fontSize = 32.sp,
                        lineHeight = 38.sp
                    ),
                    color = Color.White,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "A distraction-free launcher for more intentional living.",
                    style = MaterialTheme.typography.bodyLarge.copy(
                        fontSize = 16.sp,
                        lineHeight = 22.sp
                    ),
                    color = Color.White.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(28.dp))

                if (authState is AuthState.Error) {
                    Surface(
                        color = MaterialTheme.colorScheme.error.copy(alpha = 0.25f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Text(
                                text = "Sign-in Failed",
                                style = MaterialTheme.typography.titleSmall,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = authState.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.9f)
                            )
                        }
                    }
                }

                val isLoading = authState is AuthState.Loading || authState is AuthState.Authenticated

                Surface(
                    onClick = {
                        if (!isLoading) onContinueWithGoogle()
                    },
                    enabled = !isLoading,
                    shape = RoundedCornerShape(14.dp),
                    color = Color.White,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.5.dp,
                                color = Color(0xFF4285F4)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Signing in...",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1F1F1F)
                                )
                            )
                        } else {
                            Image(
                                painter = painterResource(id = R.drawable.ic_google_logo),
                                contentDescription = "Google logo",
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Continue with Google",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF1F1F1F)
                                )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "By continuing, you agree to our Terms and Conditions and Privacy Policy.",
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    ),
                    color = Color.White.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    }
}

/**
 * Screen 2 (Position 2): Existing first onboarding screen ("Welcome to Unloop").
 */
@Composable
private fun OnboardingExplainAppScreen() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(20.dp)
                )
                .background(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(horizontal = 28.dp, vertical = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    text = "10:42",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Light,
                        fontSize = 32.sp
                    ),
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Phone",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Messages",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Notes",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        Spacer(modifier = Modifier.height(36.dp))

        Text(
            text = "Welcome to Unloop",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Light,
                fontSize = 30.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "A distraction-free, text-based launcher designed to help you stop mindless scrolling and reclaim your focus.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "No colorful icons, no algorithmic feeds, no attention traps. Just the essentials, right when you need them.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.8f),
            textAlign = TextAlign.Center,
            lineHeight = 22.sp
        )
    }
}

/**
 * Screen 4 (Position 4): Default launcher setup guide.
 */
@Composable
private fun OnboardingDefaultLauncherScreen(
    isDefaultLauncher: Boolean,
    onSetDefaultLauncher: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(
                1.dp,
                if (isDefaultLauncher) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = if (isDefaultLauncher) "STATUS: ACTIVE" else "FINAL STEP",
                    style = MaterialTheme.typography.labelSmall.copy(
                        letterSpacing = 2.sp,
                        fontWeight = FontWeight.SemiBold
                    ),
                    color = if (isDefaultLauncher) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.secondary
                    }
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = if (isDefaultLauncher) {
                        "Unloop is your default launcher"
                    } else {
                        "Set Unloop as Default"
                    },
                    style = MaterialTheme.typography.titleLarge.copy(
                        fontWeight = FontWeight.Medium
                    ),
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = if (isDefaultLauncher) {
                        "Setup complete. Tap 'Get Started' to enter your new focused home screen."
                    } else {
                        "Tap the button below to open system settings and select Unloop as your default home app."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Text(
            text = "Make Unloop Your Home",
            style = MaterialTheme.typography.headlineLarge.copy(
                fontWeight = FontWeight.Light,
                fontSize = 28.sp
            ),
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "Setting Unloop as your default launcher ensures it opens automatically whenever you press Home or swipe up.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.secondary,
            textAlign = TextAlign.Center,
            lineHeight = 24.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InstructionStep(
                number = "1",
                text = "Tap 'Set as Default Launcher' below"
            )
            InstructionStep(
                number = "2",
                text = "Choose 'Unloop' when prompted by Android"
            )
        }
    }
}

@Composable
private fun InstructionStep(
    number: String,
    text: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(24.dp)
                .height(24.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onBackground
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}
