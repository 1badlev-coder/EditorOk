package site.kentok.lists.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Палитра мобильной версии editor-ok.kent-ok.site */
val SiteBlue = Color(0xFF2563EB)
val SiteBlueDark = Color(0xFF1D4ED8)
val SiteBlueSoft = Color(0xFFDBE5FF)
val SiteBlueFocus = Color(0xFF93C5FD)
val SiteInk = Color(0xFF222222)
val SiteBg = Color(0xFFF4F5F7)
val SiteMuted = Color(0xFF64748B)
val SiteMuted2 = Color(0xFF475569)
val SiteHint = Color(0xFF94A3B8)
val SiteBorder = Color(0xFFBBBBBB)
val SiteBorderLight = Color(0xFFDDDDDD)
val SiteBorderSoft = Color(0xFFCBD5E1)
val SiteError = Color(0xFFB91C1C)
val SiteOk = Color(0xFF15803D)
val SiteOkBright = Color(0xFF16A34A)
val SiteOkBg = Color(0xFFDCFCE7)
val SiteBtnFace = Color(0xFFFAFAFA)
val SiteSelRow = Color(0xFFEEF4FF)
val SiteEdFocus = Color(0xFFF8FBFF)
val SiteAmber = Color(0xFFF59E0B)

private val Light = lightColorScheme(
    primary = SiteBlue,
    onPrimary = Color.White,
    primaryContainer = SiteBlueSoft,
    onPrimaryContainer = SiteBlueDark,
    secondary = SiteBlueDark,
    background = SiteBg,
    surface = Color.White,
    onBackground = SiteInk,
    onSurface = SiteInk,
    onSurfaceVariant = SiteMuted,
    error = SiteError,
    outline = SiteBorder,
    surfaceVariant = Color(0xFFF8FAFC)
)

private val SiteTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 20.sp,
        color = SiteInk
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        color = SiteInk
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        color = SiteInk
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 22.sp,
        color = SiteInk
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        color = SiteInk
    ),
    bodySmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        color = SiteMuted
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        color = SiteInk
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        color = SiteBlue
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 11.sp,
        color = SiteMuted
    )
)

@Composable
fun EditorOkTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = Light, typography = SiteTypography, content = content)
}
