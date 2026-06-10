package com.maligai.app

import android.app.Activity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

// Warm/earthy green palette suited to a grocery shop.
private val Green700 = Color(0xFF1B5E20)
private val Green500 = Color(0xFF2E7D32)
private val Green100 = Color(0xFFC8E6C9)
private val Amber500 = Color(0xFFF9A825)
private val Sand = Color(0xFFFBF6EC)
private val Ink = Color(0xFF1B1B17)

val DraftTabColor = Amber500
val LoanColor = Color(0xFFC62828)

private val LightColors = lightColorScheme(
    primary = Green700,
    onPrimary = Color.White,
    primaryContainer = Green100,
    onPrimaryContainer = Green700,
    secondary = Amber500,
    onSecondary = Color.White,
    background = Sand,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Color(0xFFEFE9DC),
    onSurfaceVariant = Color(0xFF4A4A40),
    outline = Color(0xFF7A7568)
)

private val DarkColors = darkColorScheme(
    primary = Green500,
    onPrimary = Color.White,
    primaryContainer = Green700,
    onPrimaryContainer = Green100,
    secondary = Amber500,
    onSecondary = Color.Black,
    background = Color(0xFF14140F),
    onBackground = Color(0xFFF1ECE0),
    surface = Color(0xFF1F1F18),
    onSurface = Color(0xFFF1ECE0),
    surfaceVariant = Color(0xFF2A2A22),
    onSurfaceVariant = Color(0xFFD8D2C4),
    outline = Color(0xFF8A8578)
)

// Default FontFamily; Android renders Indic glyphs via system Noto fallbacks.
val MaligaiTypography = Typography(
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 17.sp),
    bodyLarge = TextStyle(fontSize = 17.sp),
    bodyMedium = TextStyle(fontSize = 15.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
)

@Composable
fun resolveDarkTheme(themeMode: String?): Boolean {
    val systemDark = isSystemInDarkTheme()
    return when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        else -> systemDark
    }
}

/** Normalizes stored theme values so saved Light/Dark is always honored. */
fun normalizeThemeMode(themeMode: String?): String = when (themeMode) {
    ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM -> themeMode
    else -> ThemeMode.SYSTEM
}

/** Primary body/heading text color for the active app theme. */
@Composable
fun appTextColor(): Color = MaterialTheme.colorScheme.onBackground

/** Secondary/subtitle text color for the active app theme. */
@Composable
fun appMutedTextColor(): Color = MaterialTheme.colorScheme.onSurfaceVariant

/** Themed [Text] — avoids inheriting black platform defaults before Scaffold mounts. */
@Composable
fun AppText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = appTextColor()
) {
    Text(text, modifier = modifier, style = style, color = color)
}

@Composable
fun visibleOutlinedTextFieldColors(): TextFieldColors {
    val text = MaterialTheme.colorScheme.onSurface
    val surface = MaterialTheme.colorScheme.surface
    return OutlinedTextFieldDefaults.colors(
        focusedTextColor = text,
        unfocusedTextColor = text,
        disabledTextColor = text.copy(alpha = 0.38f),
        errorTextColor = text,
        focusedContainerColor = surface,
        unfocusedContainerColor = surface,
        disabledContainerColor = surface.copy(alpha = 0.6f),
        cursorColor = MaterialTheme.colorScheme.primary,
        focusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
        focusedLabelColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** Outlined field with high-contrast typed text (PIN, security answers, etc.). */
@Composable
fun VisibleOutlinedTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: @Composable () -> Unit,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    isError: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        singleLine = singleLine,
        enabled = enabled,
        isError = isError,
        keyboardOptions = keyboardOptions,
        visualTransformation = VisualTransformation.None,
        colors = visibleOutlinedTextFieldColors()
    )
}

@Composable
private fun SyncSystemBars(darkTheme: Boolean, background: Color) {
    val view = LocalView.current
    val context = LocalContext.current
    SideEffect {
        val window = (context as? Activity)?.window ?: return@SideEffect
        window.statusBarColor = background.toArgb()
        window.navigationBarColor = background.toArgb()
        window.decorView.setBackgroundColor(background.toArgb())
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }
}

@Composable
fun MaligaiTheme(
    themeMode: String = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val normalized = normalizeThemeMode(themeMode)
    val darkTheme = resolveDarkTheme(normalized)
    val colors = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = MaligaiTypography,
        content = {
            // Activity XML theme can supply black text in dark mode; override for Compose.
            CompositionLocalProvider(
                LocalContentColor provides colors.onBackground,
                LocalTextStyle provides TextStyle(color = colors.onBackground)
            ) {
                SyncSystemBars(darkTheme, colors.background)
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = colors.background,
                    contentColor = colors.onBackground
                ) {
                    content()
                }
            }
        }
    )
}
