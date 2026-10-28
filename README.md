# Unloop

> Your Phone, Your Control.

## Introduction

Unloop is a minimal, distraction-free Android launcher designed to help users reduce distractions, spend less time mindlessly scrolling, and use their phones more intentionally. By replacing colorful icon grids with a clean, text-based interface and built-in digital wellbeing tools, Unloop empowers you to take back control of your attention.

## Why Unloop?

Modern smartphones and applications are engineered to capture and hold attention through algorithmic feeds, notification spikes, and endless short-form video loops. Unloop breaks this cycle by providing:

- **Friction Against Mindless Habits**: A calm, intentional interface that encourages deliberate phone usage.
- **Attention Recovery**: Tools designed to interrupt doom-scrolling patterns and filter out noisy notifications.
- **Clutter-Free Minimalism**: A distraction-free environment that helps you focus on what matters most.

## Features

- **Clean Home Screen**: A simple, text-based interface with customizable favorites and zero distracting app icons.
- **Focus Mode**: Select apps to restrict during study or work sessions, featuring mindful pause dialogs before restricted apps can be launched.
- **Short-Video Blocking**: Detects and exits infinite short-video loops (Reels, Shorts, Spotlight) on supported platforms using an on-device accessibility service.
- **Notification Management**: Privately intercepts and filters noisy notifications from distracting apps, holding them locally for mindful review within Unloop.
- **Hidden Apps**: Hide apps from the main drawer and search results, accessible in a dedicated management screen.
- **Adaptive Appearance**: Fast switching between Light, Dark, and System monochrome themes.

## Privacy

Privacy is a core pillar of Unloop:

- **100% On-Device Processing**: All notification filtering, app usage monitoring, and hidden app settings remain strictly on your device.
- **No Remote Tracking**: Unloop does not transmit personal activity, keystrokes, messages, or habits to external servers.
- **Minimal Footprint**: The application does not require internet permissions for its launcher or wellbeing features.

## Tech Stack

- **Language**: Kotlin 2.2.10 (Java 11 compatibility)
- **UI Framework**: Jetpack Compose with Material 3 (Compose BOM 2026.02.01)
- **Architecture**: MVVM (Model-View-ViewModel) with AndroidViewModel, Kotlin Coroutines, and StateFlow
- **Build System**: Android Gradle Plugin 9.4.1 with Gradle Kotlin DSL
- **SDK Targets**: Min SDK 26 (Android 8.0 Oreo), Target & Compile SDK 37
- **Core Libraries**: AndroidX Core KTX, Lifecycle ViewModel Compose, Material Icons Extended
- **System Services**:
  - `AccessibilityService` (`ShortVideoBlockerAccessibilityService`) for short-video detection
  - `NotificationListenerService` (`NotificationManagerService`) for distraction filtering
- **Testing**: JUnit 4, AndroidX Test JUnit, Espresso Core, Compose UI Testing

## Getting Started

### Prerequisites

- Android Studio (Ladybug or newer) or IntelliJ IDEA with Android support
- Java Development Kit (JDK) 11 or higher
- Android SDK configured with Platform 37

### Setup and Build

Clone the repository and open the project directory:

```bash
# Clone repository
git clone <repository-url>
cd unloop

# Build debug APK
./gradlew assembleDebug

# Windows PowerShell:
.\gradlew.bat assembleDebug
```

### Running Tests

Execute the unit test suite:

```bash
./gradlew test

# Windows PowerShell:
.\gradlew.bat test
```

### Installing on Device / Emulator

Ensure an Android device or emulator with developer options enabled is connected:

```bash
./gradlew installDebug

# Windows PowerShell:
.\gradlew.bat installDebug
```

## Project Structure

```text
app/src/main/
├── AndroidManifest.xml          # Permissions, launcher intent filters, and service declarations
└── java/unloop/launcher/
    ├── MainActivity.kt          # Single-activity host with Compose crossfade screen transitions
    ├── data/
    │   ├── AppModel.kt          # Domain models for launcher apps and categorization
    │   ├── AppsRepository.kt    # Package manager resolution, querying, and app lifecycle
    │   └── PreferencesManager.kt# SharedPreferences management for settings, focus, and notifications
    ├── receiver/
    │   └── PackageChangeReceiver.kt # Broadcast receiver reacting to app install/uninstall events
    ├── service/
    │   ├── FilteredNotificationItem.kt # Model & JSON serialization for filtered alerts
    │   ├── NotificationManagerService.kt # NotificationListenerService for intercepting alerts
    │   ├── ShortVideoBlockerAccessibilityService.kt # Accessibility service monitoring active windows
    │   └── ShortVideoDetector.kt# Detection rules for short-form video UI hierarchies
    ├── ui/
    │   ├── LauncherViewModel.kt # StateFlow-driven view model handling UI state and navigation
    │   ├── components/          # Reusable UI widgets (search bar, permission dialogs, gestures)
    │   ├── screens/             # Jetpack Compose screens
    │   │   ├── HomeScreen.kt    # Clean text-based primary launcher screen
    │   │   ├── AllAppsScreen.kt # Complete app drawer with instant search
    │   │   ├── SettingsScreen.kt# Settings overview and launcher configurations
    │   │   ├── AboutScreen.kt   # Minimal About Unloop screen with philosophy and details
    │   │   ├── FocusSettingsScreen.kt # App selection and configuration for Focus Mode
    │   │   ├── HiddenAppsScreen.kt    # Hidden application management
    │   │   ├── NotificationsScreen.kt # Filtered notification inbox and review
    │   │   ├── OnboardingScreen.kt    # Welcome walk-through and default launcher setup
    │   │   ├── AppMenuDialog.kt       # App options context modal (favorite, hide, pause)
    │   │   └── MindfulPromptDialog.kt # Mindful pause overlay before opening restricted apps
    │   └── theme/               # Material 3 theme palette, colors, and typography definitions
    └── util/
        ├── AppSearchEngine.kt   # App query filtering and ranking engine
        ├── AppVersionUtils.kt   # Dynamic package version resolver
        └── PermissionUtils.kt   # System intent launchers and permission status helpers
```

## Developer

- **Developer**: Sree Gopal Saha
- **Developer Email**: sreegopal0101@gmail.com
