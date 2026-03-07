package app.gamenative.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.alorma.compose.settings.ui.base.internal.SettingsTileColors
import com.alorma.compose.settings.ui.base.internal.SettingsTileDefaults

// GameNative design system — Steam blue accent, dark surfaces. Do not use purple or Material defaults.

// Background tier
val gnBgDeepest = Color(0xFF09090F)
val gnBgSurface = Color(0xFF12121C)
val gnBgElevated = Color(0xFF1A1A28)
val gnBgOverlay = Color(0xFF22223A)

// Accent (Steam blue)
val gnAccentPrimary = Color(0xFF1E9FFF)
val gnAccentGlow = Color(0xFF3DB8FF)
val gnAccentDim = Color(0xFF1066A8)
val gnAccentSubtle = Color(0x261E9FFF)

// Neon pops — badges and status only
val gnNeonTeal = Color(0xFF00E5C3)
val gnNeonPurple = Color(0xFF7C6EFA)
val gnNeonOrange = Color(0xFFFF7B54)

// Typography
val gnTextPrimary = Color(0xFFEEEEF5)
val gnTextSecondary = Color(0xFF8A8AA8)
val gnTextTertiary = Color(0xFF505068)
val gnTextOnAccent = Color(0xFFFFFFFF)

// Status
val gnStatusInstalled = Color(0xFF00E5C3)
val gnStatusDownloading = Color(0xFF1E9FFF)
val gnStatusError = Color(0xFFFF4D6A)

// Structure
val gnDivider = Color(0x0FFFFFFF)
val gnBorderCard = Color(0x18FFFFFF)
val gnScrimArt = Color(0xA0000000)

// Compose theme mapping (replaces old purple/cyan palette)
val customBackground = gnBgDeepest
val customForeground = gnTextPrimary
val customCard = gnBgSurface
val customCardForeground = gnTextPrimary
val customPrimary = gnAccentPrimary
val customPrimaryForeground = gnTextOnAccent
val customSecondary = gnBgElevated
val customSecondaryForeground = gnTextPrimary
val customMuted = gnBgOverlay
val customMutedForeground = gnTextSecondary
val customAccent = gnAccentGlow
val customAccentForeground = gnTextOnAccent
val customDestructive = gnStatusError

val pluviaSeedColor = gnAccentPrimary

/**
 * Alorma compose settings tile colors
 */
@Composable
fun settingsTileColors(): SettingsTileColors = SettingsTileDefaults.colors(
    titleColor = customForeground,
    subtitleColor = customMutedForeground,
    actionColor = customAccent,
)

@Composable
fun settingsTileColorsAlt(): SettingsTileColors = SettingsTileDefaults.colors(
    titleColor = customForeground,
    subtitleColor = customMutedForeground,
)

@Composable
fun settingsTileColorsDebug(): SettingsTileColors = SettingsTileDefaults.colors(
    titleColor = customDestructive,
    subtitleColor = customMutedForeground,
    actionColor = customAccent,
)
