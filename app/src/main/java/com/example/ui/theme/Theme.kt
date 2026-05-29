package com.example.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

enum class AppThemeOption(val displayName: String) {
    SLATE("Midnight Slate"),
    CYBERPUNK("Neon Cyberpunk"),
    OCEANIC("Oceanic Gale"),
    MINT("Nature Mint"),
    SUNSET("Vibrant Sunset")
}

// 1. Midnight Slate Color Palette
private val SlateDark = darkColorScheme(
    primary = Color(0xFF6366F1), // Indigo
    secondary = Color(0xFF94A3B8), // slate
    tertiary = Color(0xFF38BDF8), // sky cyan
    background = Color(0xFF0F172A), // off black slate
    surface = Color(0xFF1E293B),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFFF8FAFC),
    onSurface = Color(0xFFF8FAFC)
)

private val SlateLight = lightColorScheme(
    primary = Color(0xFF4F46E5),
    secondary = Color(0xFF64748B),
    tertiary = Color(0xFF0284C7),
    background = Color(0xFFF8FAFC),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF0F172A),
    onSurface = Color(0xFF0F172A)
)

// 2. Cyberpunk Palette
private val CyberpunkDark = darkColorScheme(
    primary = Color(0xFFEC4899), // Fluor Rose/Pink
    secondary = Color(0xFFF43F5E),
    tertiary = Color(0xFF06B6D4), // Cyan
    background = Color(0xFF090514), // very deep purple-black
    surface = Color(0xFF170D2B),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color(0xFFF4F4F6),
    onSurface = Color(0xFFF4F4F6)
)

private val CyberpunkLight = lightColorScheme(
    primary = Color(0xFFDB2777),
    secondary = Color(0xFFE11D48),
    tertiary = Color(0xFF0891B2),
    background = Color(0xFFFAFAFE),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF090514),
    onSurface = Color(0xFF090514)
)

// 3. Oceanic Breeze Palette
private val OceanicDark = darkColorScheme(
    primary = Color(0xFF0EA5E9), // ocean light blue
    secondary = Color(0xFF06B6D4), // Cyan
    tertiary = Color(0xFF10B981), // Emerald
    background = Color(0xFF030712), // pitch black blue
    surface = Color(0xFF111827),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFFF3F4F6),
    onSurface = Color(0xFFF3F4F6)
)

private val OceanicLight = lightColorScheme(
    primary = Color(0xFF0284C7),
    secondary = Color(0xFF0891B2),
    tertiary = Color(0xFF059669),
    background = Color(0xFFF0F9FF),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF030712),
    onSurface = Color(0xFF030712)
)

// 4. Emerald Forest Palette
private val MintDark = darkColorScheme(
    primary = Color(0xFF10B981), // Mint emerald
    secondary = Color(0xFF34D399),
    tertiary = Color(0xFFF59E0B), // amber
    background = Color(0xFF022C22), // deep forest green dark
    surface = Color(0xFF064E3B),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color(0xFFECFDF5),
    onSurface = Color(0xFFECFDF5)
)

private val MintLight = lightColorScheme(
    primary = Color(0xFF059669),
    secondary = Color(0xFF10B981),
    tertiary = Color(0xFFD97706),
    background = Color(0xFFF0FDF4),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF022C22),
    onSurface = Color(0xFF022C22)
)

// 5. Vibrant Sunset Palette
private val SunsetDark = darkColorScheme(
    primary = Color(0xFFF97316), // sunset orange
    secondary = Color(0xFFEF4444), // crimson red
    tertiary = Color(0xFFFBBF24), // Gold
    background = Color(0xFF1C0A00), // deep sunset charcoal
    surface = Color(0xFF2D1600),
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.Black,
    onBackground = Color(0xFFFFF7ED),
    onSurface = Color(0xFFFFF7ED)
)

private val SunsetLight = lightColorScheme(
    primary = Color(0xFFEA580C),
    secondary = Color(0xFFDC2626),
    tertiary = Color(0xFFD97706),
    background = Color(0xFFFFF7ED),
    surface = Color.White,
    onPrimary = Color.White,
    onSecondary = Color.White,
    onTertiary = Color.White,
    onBackground = Color(0xFF1C0A00),
    onSurface = Color(0xFF1C0A00)
)

@Composable
fun MyApplicationTheme(
    themeOption: AppThemeOption = AppThemeOption.SLATE,
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false, // Set to false to allow our gorgeous custom themes to pop!
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        else -> {
            if (darkTheme) {
                when (themeOption) {
                    AppThemeOption.SLATE -> SlateDark
                    AppThemeOption.CYBERPUNK -> CyberpunkDark
                    AppThemeOption.OCEANIC -> OceanicDark
                    AppThemeOption.MINT -> MintDark
                    AppThemeOption.SUNSET -> SunsetDark
                }
            } else {
                when (themeOption) {
                    AppThemeOption.SLATE -> SlateLight
                    AppThemeOption.CYBERPUNK -> CyberpunkLight
                    AppThemeOption.OCEANIC -> OceanicLight
                    AppThemeOption.MINT -> MintLight
                    AppThemeOption.SUNSET -> SunsetLight
                }
            }
        }
    }

    MaterialTheme(colorScheme = colorScheme, typography = Typography, content = content)
}
