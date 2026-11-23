package site.kentok.lists.ui.pin

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.kentok.lists.R
import site.kentok.lists.ui.components.BrandLogo
import site.kentok.lists.ui.theme.SiteBg
import site.kentok.lists.ui.theme.SiteBlue
import site.kentok.lists.ui.theme.SiteBlueDark
import site.kentok.lists.ui.theme.SiteBorder
import site.kentok.lists.ui.theme.SiteBtnFace
import site.kentok.lists.ui.theme.SiteError
import site.kentok.lists.ui.theme.SiteHint
import site.kentok.lists.ui.theme.SiteInk
import site.kentok.lists.ui.theme.SiteMuted2

@Composable
fun PinScreen(
    nick: String,
    error: String?,
    loading: Boolean = false,
    /** Длина сохранённого ПИН (3–8); 0 = авто-вход при 4 цифрах. */
    expectedPinLength: Int = 0,
    onSubmit: (String) -> Unit,
    onLogout: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    val autoLen = when (expectedPinLength) {
        in 3..8 -> expectedPinLength
        else -> 4
    }
    fun tryAutoSubmit(next: String) {
        if (loading) return
        if (next.length == autoLen && next.length in 3..8) onSubmit(next)
    }
    Column(
        Modifier
            .fillMaxSize()
            .background(SiteBg)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .shadow(6.dp, RoundedCornerShape(16.dp))
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(22.dp)
        ) {
            BrandLogo(
                modifier = Modifier.align(Alignment.CenterHorizontally),
                size = 88.dp
            )
            Text(
                stringResource(R.string.pin_title),
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
                color = SiteInk,
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
            )
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(1.dp, SiteBorder, RoundedCornerShape(12.dp))
                    .background(Color(0xFFF8FAFC)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = if (pin.isEmpty()) stringResource(R.string.pin_hint) else pin.map { '*' }.joinToString(""),
                    fontSize = if (pin.isEmpty()) 16.sp else 32.sp,
                    fontWeight = if (pin.isEmpty()) FontWeight.Medium else FontWeight.Bold,
                    color = if (pin.isEmpty()) SiteHint else SiteInk,
                    letterSpacing = if (pin.isEmpty()) 0.sp else 10.sp
                )
            }
            if (error != null) {
                Text(
                    error,
                    color = SiteError,
                    fontSize = 14.sp,
                    modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                )
            }
            val keys = listOf(
                listOf("1", "2", "3"),
                listOf("4", "5", "6"),
                listOf("7", "8", "9"),
                listOf("C", "0", "⌫")
            )
            Column(Modifier.padding(top = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                keys.forEach { row ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        row.forEach { key ->
                            PinKey(
                                label = key,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    if (loading) return@PinKey
                                    when (key) {
                                        "C" -> pin = ""
                                        "⌫" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                        else -> if (pin.length < 8) {
                                            val next = pin + key
                                            pin = next
                                            tryAutoSubmit(next)
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (pin.length >= 3 && !loading) SiteBlue else SiteBlue.copy(alpha = 0.45f))
                        .border(1.dp, SiteBlueDark, RoundedCornerShape(8.dp))
                        .clickable(enabled = pin.length >= 3 && !loading) { onSubmit(pin) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        if (loading) stringResource(R.string.pin_checking) else stringResource(R.string.pin_enter),
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(
            Modifier
                .fillMaxWidth()
                .shadow(4.dp, RoundedCornerShape(16.dp))
                .background(Color.White, RoundedCornerShape(16.dp))
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(stringResource(R.string.pin_account) + " ", color = SiteMuted2, fontSize = 14.sp)
            Text(nick, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = SiteInk, modifier = Modifier.weight(1f))
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, SiteBorder, RoundedCornerShape(10.dp))
                    .background(SiteBtnFace)
                    .clickable(onClick = onLogout)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Text(stringResource(R.string.pin_logout), fontSize = 14.sp, color = SiteInk)
            }
        }
    }
}

@Composable
private fun PinKey(label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(56.dp)
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, SiteBorder, RoundedCornerShape(12.dp))
            .background(SiteBtnFace)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = SiteInk)
    }
}
