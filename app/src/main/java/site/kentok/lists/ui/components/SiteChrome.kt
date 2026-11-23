package site.kentok.lists.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import site.kentok.lists.ui.theme.SiteBlue
import site.kentok.lists.ui.theme.SiteBlueDark
import site.kentok.lists.ui.theme.SiteBlueSoft
import site.kentok.lists.ui.theme.SiteBorder
import site.kentok.lists.ui.theme.SiteBorderLight
import site.kentok.lists.ui.theme.SiteBtnFace
import site.kentok.lists.ui.theme.SiteInk
import site.kentok.lists.ui.theme.SiteMuted
import site.kentok.lists.ui.theme.SiteMuted2
import site.kentok.lists.ui.theme.SiteOkBright

@Composable
private fun rememberElapsedSec(startedAtMs: Long): Int {
    var sec by remember(startedAtMs) { mutableIntStateOf(0) }
    LaunchedEffect(startedAtMs) {
        if (startedAtMs <= 0L) {
            sec = 0
            return@LaunchedEffect
        }
        while (true) {
            sec = ((System.currentTimeMillis() - startedAtMs) / 1000L).toInt().coerceAtLeast(0)
            delay(500)
        }
    }
    return sec
}

/** Тонкая строка загрузки при синхронизации с сервером. */
@Composable
fun SyncLoadingLine(
    visible: Boolean,
    percent: Int? = null,
    label: String? = null,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    val text = buildString {
        append(label ?: "Синхронизация с сервером…")
        if (percent != null) append(" · ").append(percent).append('%')
    }
    Column(modifier.fillMaxWidth()) {
        if (percent != null) {
            LinearProgressIndicator(
                progress = { (percent.coerceIn(0, 100) / 100f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = SiteBlue,
                trackColor = Color(0xFFE2E8F0)
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
                color = SiteBlue,
                trackColor = Color(0xFFE2E8F0)
            )
        }
        Text(
            text = text,
            color = SiteMuted,
            fontSize = 11.sp,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8FAFC))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        )
    }
}

/**
 * Компактный процент синка документов в правом верхнем углу — редактор остаётся доступен.
 */
@Composable
fun SyncCornerBadge(
    percent: Int?,
    label: String? = null,
    startedAtMs: Long = 0L,
    modifier: Modifier = Modifier
) {
    if (percent == null) return
    val pct = percent.coerceIn(0, 100)
    val elapsedSec = rememberElapsedSec(startedAtMs)
    Row(
        modifier
            .padding(10.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xF0FFFFFF))
            .border(1.dp, Color(0xFFDBE5FF), RoundedCornerShape(12.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CircularProgressIndicator(
            progress = { pct / 100f },
            modifier = Modifier.size(22.dp),
            color = SiteBlue,
            trackColor = Color(0xFFDBE5FF),
            strokeWidth = 3.dp,
            strokeCap = StrokeCap.Round
        )
        Column {
            Text(
                "$pct%",
                color = SiteBlueDark,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = (label ?: "Синхронизация…").let { if (it.length > 28) it.take(27) + "…" else it },
                color = SiteMuted2,
                fontSize = 11.sp,
                maxLines = 1
            )
            if (startedAtMs > 0L) {
                Text(
                    text = if (elapsedSec < 60) "Время: $elapsedSec с" else "Время: ${elapsedSec / 60} м ${elapsedSec % 60} с",
                    color = SiteMuted,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Полноэкранный оверлей как #sync на сайте: спиннер + процент + подпись + время + полоска.
 */
@Composable
fun SyncOverlay(
    visible: Boolean,
    percent: Int? = null,
    label: String? = null,
    startedAtMs: Long = 0L,
    modifier: Modifier = Modifier
) {
    if (!visible) return
    val pct = percent?.coerceIn(0, 100)
    val elapsedSec = rememberElapsedSec(startedAtMs)
    Box(
        modifier
            .fillMaxSize()
            .background(Color(0xF7FFFFFF)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .widthIn(max = 340.dp)
                .fillMaxWidth(0.86f)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(54.dp),
                color = SiteBlue,
                trackColor = Color(0xFFDBE5FF),
                strokeWidth = 6.dp,
                strokeCap = StrokeCap.Round
            )
            Text(
                text = if (pct != null) "$pct%" else "…",
                color = SiteBlueDark,
                fontSize = 34.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 14.dp)
            )
            Text(
                text = label ?: "Синхронизация…",
                color = SiteMuted2,
                fontSize = 15.sp,
                textAlign = TextAlign.Center,
                minLines = 2,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
                    .height(44.dp)
            )
            if (startedAtMs > 0L) {
                Text(
                    text = if (elapsedSec < 60) "Время: $elapsedSec с" else "Время: ${elapsedSec / 60} м ${elapsedSec % 60} с",
                    color = SiteMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Color(0xFFE2E8F0))
            ) {
                Box(
                    Modifier
                        .fillMaxWidth(fraction = ((pct ?: 15).coerceIn(0, 100)) / 100f)
                        .height(8.dp)
                        .background(SiteBlue)
                )
            }
        }
    }
}

@Composable
fun SiteTopBar(
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit
) {
    Column(modifier.fillMaxWidth().background(Color.White)) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            content = content
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(SiteBorderLight))
    }
}

@Composable
fun SiteToolBtn(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    primary: Boolean = false,
    saved: Boolean = false,
    active: Boolean = false,
    height: Dp = 30.dp,
    content: @Composable () -> Unit
) {
    val bg = when {
        !enabled -> SiteBtnFace.copy(alpha = 0.5f)
        saved -> SiteOkBright
        primary -> SiteBlue
        active -> SiteBlueSoft
        else -> SiteBtnFace
    }
    val border = when {
        saved -> Color(0xFF15803D)
        primary -> SiteBlueDark
        active -> SiteBlue
        else -> SiteBorder
    }
    val fg = when {
        primary || saved -> Color.White
        active -> SiteBlueDark
        else -> SiteInk
    }
    Box(
        modifier
            .height(height)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) { content() }
    }
}

@Composable
fun SiteSaveBtn(
    dirty: Boolean,
    loading: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bg = if (!dirty && !loading) SiteOkBright else SiteBlue
    val border = if (!dirty && !loading) Color(0xFF15803D) else SiteBlueDark
    Box(
        modifier
            .width(62.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(bg)
            .border(1.dp, border, RoundedCornerShape(8.dp))
            .clickable(enabled = !loading, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("💾", fontSize = 12.sp, color = Color.White)
            Text(
                text = when {
                    loading -> "…"
                    dirty -> "не сохранено"
                    else -> "сохранено"
                },
                fontSize = 8.sp,
                color = Color.White,
                maxLines = 1,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun SitePanel(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 14.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .padding(18.dp)
    ) { content() }
}

@Composable
fun SiteStatus(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        color = SiteMuted,
        fontSize = 12.sp,
        modifier = modifier.padding(start = 8.dp)
    )
}
