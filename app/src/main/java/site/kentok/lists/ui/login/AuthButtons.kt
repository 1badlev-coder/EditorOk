package site.kentok.lists.ui.login

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.kentok.lists.ui.theme.SiteBlue
import site.kentok.lists.ui.theme.SiteBlueDark

@Composable
fun Pill3d(
    text: String,
    brush: Brush,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = true
) {
    val shape = RoundedCornerShape(999.dp)
    Row(
        modifier = modifier
            .shadow(4.dp, shape, clip = false)
            .clip(shape)
            .background(brush)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
            .then(
                if (compact) Modifier.height(30.dp).padding(start = 11.dp, end = 5.dp)
                else Modifier.fillMaxWidth().height(48.dp).padding(start = 16.dp, end = 8.dp)
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = if (compact) Arrangement.Start else Arrangement.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = if (compact) 11.sp else 16.sp,
            letterSpacing = 0.3.sp
        )
        Spacer(Modifier.width(7.dp))
        Box(
            Modifier
                .size(if (compact) 20.dp else 26.dp)
                .background(Color.White.copy(alpha = 0.22f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(if (compact) 11.dp else 14.dp)
            )
        }
    }
}

@Composable
fun RegPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Pill3d(
        text = "Регистрация",
        brush = Brush.verticalGradient(listOf(Color(0xFF9A9A9A), Color(0xFF4A4A4A))),
        icon = Icons.Default.PersonAdd,
        onClick = onClick,
        modifier = modifier,
        compact = true
    )
}

@Composable
fun RecoverPill(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Pill3d(
        text = "Восстановление пароля",
        brush = Brush.verticalGradient(listOf(Color(0xFF5AA7FF), SiteBlueDark)),
        icon = Icons.Default.Lock,
        onClick = onClick,
        modifier = modifier,
        compact = true
    )
}

@Composable
fun LoginPill(onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Pill3d(
        text = "Вход",
        brush = Brush.verticalGradient(
            if (enabled) listOf(Color(0xFF3F3F3F), Color(0xFF111111))
            else listOf(Color(0xFFAAAAAA), Color(0xFF777777))
        ),
        icon = Icons.Default.Login,
        onClick = { if (enabled) onClick() },
        modifier = modifier,
        compact = false
    )
}

@Composable
fun PrimarySiteButton(text: String, onClick: () -> Unit, enabled: Boolean, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (enabled) SiteBlue else Color(0xFF94A3B8))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color.White, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
fun SecondarySiteButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFFFAFAFA))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color(0xFF222222))
    }
}
