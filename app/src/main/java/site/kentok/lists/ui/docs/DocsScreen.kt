package site.kentok.lists.ui.docs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.kentok.lists.data.model.Document
import site.kentok.lists.ui.components.SiteStatus
import site.kentok.lists.ui.components.SiteToolBtn
import site.kentok.lists.ui.components.SiteTopBar
import site.kentok.lists.ui.theme.SiteBg
import site.kentok.lists.ui.theme.SiteBlue
import site.kentok.lists.ui.theme.SiteBlueSoft
import site.kentok.lists.ui.theme.SiteBorderSoft
import site.kentok.lists.ui.theme.SiteHint
import site.kentok.lists.ui.theme.SiteInk
import site.kentok.lists.ui.theme.SiteMuted
import site.kentok.lists.ui.theme.SiteMuted2
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DocsScreen(
    nick: String,
    docs: List<Document>,
    syncing: Boolean,
    onOpen: (String) -> Unit,
    onNew: () -> Unit,
    onSync: () -> Unit,
    onBoard: () -> Unit,
    onLogout: () -> Unit
) {
    Column(Modifier.fillMaxSize().background(SiteBg)) {
        SiteTopBar {
            SiteToolBtn(onClick = onNew, primary = true) {
                Text("+ Стр.", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            SiteToolBtn(onClick = onBoard) {
                Text("Вид", fontSize = 14.sp)
            }
            SiteToolBtn(onClick = onSync, enabled = !syncing) {
                Text(if (syncing) "…" else "↻", fontSize = 14.sp)
            }
            SiteStatus(if (syncing) "синхронизация…" else "документы")
            Spacer(Modifier.weight(1f))
            Text(
                text = nick,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = SiteInk,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(end = 4.dp)
            )
            SiteToolBtn(onClick = onLogout) {
                Text("Выход", fontSize = 13.sp)
            }
        }

        if (docs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    "Нет документов. Создайте первый\nили дождитесь синхронизации.",
                    color = SiteMuted,
                    fontSize = 15.sp,
                    lineHeight = 22.sp
                )
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(12.dp, 14.dp, 12.dp, 24.dp),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color.White)
                            .padding(vertical = 4.dp)
                    ) {
                        docs.forEachIndexed { i, doc ->
                            DocRow(doc = doc, onOpen = { onOpen(doc.id) })
                            if (i < docs.lastIndex) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp)
                                        .height(1.dp)
                                        .background(Color(0xFFE2E8F0))
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DocRow(doc: Document, onOpen: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onOpen)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (doc.dirty) {
                    Text("● ", color = SiteBlue, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                Text(
                    text = doc.title.ifBlank { "Без названия" },
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 15.sp,
                    color = SiteInk,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            val meta = buildString {
                if (doc.cat.isNotBlank()) append(doc.cat).append(" · ")
                append(formatTs(doc.updated))
            }
            if (meta.isNotBlank()) {
                Text(meta, color = SiteMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 2.dp))
            }
        }
        Box(
            Modifier
                .width(28.dp)
                .height(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, SiteBorderSoft, RoundedCornerShape(8.dp))
                .background(SiteBlueSoft.copy(alpha = 0.35f)),
            contentAlignment = Alignment.Center
        ) {
            Text("›", color = SiteMuted2, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatTs(sec: Long): String {
    if (sec <= 0L) return ""
    return SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault()).format(Date(sec * 1000))
}
