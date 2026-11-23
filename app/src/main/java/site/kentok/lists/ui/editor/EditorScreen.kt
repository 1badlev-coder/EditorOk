package site.kentok.lists.ui.editor

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import site.kentok.lists.data.model.DocItem
import site.kentok.lists.data.model.Document
import site.kentok.lists.ui.components.SiteSaveBtn
import site.kentok.lists.ui.components.SiteStatus
import site.kentok.lists.ui.components.SiteToolBtn
import site.kentok.lists.ui.components.SiteTopBar
import site.kentok.lists.ui.theme.SiteBg
import site.kentok.lists.ui.theme.SiteBlue
import site.kentok.lists.ui.theme.SiteBlueFocus
import site.kentok.lists.ui.theme.SiteBlueSoft
import site.kentok.lists.ui.theme.SiteBorderLight
import site.kentok.lists.ui.theme.SiteEdFocus
import site.kentok.lists.ui.theme.SiteHint
import site.kentok.lists.ui.theme.SiteInk
import site.kentok.lists.ui.theme.SiteMuted
import site.kentok.lists.ui.theme.SiteSelRow
// folder button uses SiteBlue / SiteBlueSoft

@Composable
fun EditorScreen(
    doc: Document,
    loading: Boolean,
    imageBaseUrl: String,
    embedded: Boolean = false,
    folderOn: Boolean = false,
    folderTitle: String = "Папка для сохранения",
    onFolder: (() -> Unit)? = null,
    onChange: (Document) -> Unit,
    onSave: () -> Unit,
    onPickPhoto: (Int, Uri) -> Unit,
    onExportWord: () -> Unit,
    onBack: () -> Unit
) {
    var focused by remember { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) onPickPhoto(focused, uri)
    }
    val cur = doc.items.getOrNull(focused) ?: DocItem()

    Column(Modifier.fillMaxSize().background(if (embedded) Color.Transparent else SiteBg)) {
        if (!embedded) {
            SiteTopBar {
                SiteToolBtn(onClick = onBack) { Text("←", fontSize = 16.sp) }
                SiteToolBtn(onClick = { onToggle(doc, focused, "b", onChange) }, active = hasTag(cur.t, "b")) {
                    Text("B", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                SiteToolBtn(onClick = { onToggle(doc, focused, "i", onChange) }, active = hasTag(cur.t, "i")) {
                    Text("I", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 14.sp)
                }
                SiteToolBtn(onClick = { onToggle(doc, focused, "u", onChange) }, active = hasTag(cur.t, "u")) {
                    Text("U", fontSize = 14.sp)
                }
                SiteToolBtn(onClick = { onToggle(doc, focused, "s", onChange) }, active = hasTag(cur.t, "s")) {
                    Text("S", fontSize = 14.sp)
                }
                Box(Modifier.width(1.dp).height(20.dp).background(SiteBorderLight))
                SiteToolBtn(onClick = { cycleFont(doc, focused, onChange) }, active = cur.f.isNotBlank()) {
                    Text("Шр", fontSize = 13.sp)
                }
                SiteToolBtn(onClick = { cycleSize(doc, focused, onChange) }, active = cur.s > 0) {
                    Text("Аа", fontSize = 13.sp)
                }
                SiteToolBtn(onClick = { cycleColor(doc, focused, onChange) }, active = cur.col.isNotBlank()) {
                    Text("А", fontSize = 13.sp, color = parseCol(cur.col).takeIf { it != Color.Unspecified } ?: SiteInk)
                }
                SiteToolBtn(onClick = { picker.launch("image/*") }) { Text("🖼", fontSize = 13.sp) }
                SiteToolBtn(onClick = onExportWord) { Text("Word", fontSize = 13.sp) }
                Spacer(Modifier.weight(1f))
                SiteStatus(if (doc.dirty) "не сохранено" else "сохранено")
                SiteSaveBtn(dirty = doc.dirty, loading = loading, onClick = onSave)
            }
        } else {
            // вторая полоса формата как на сайте
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                SiteToolBtn(onClick = { onToggle(doc, focused, "b", onChange) }, active = hasTag(cur.t, "b")) {
                    Text("B", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
                SiteToolBtn(onClick = { onToggle(doc, focused, "i", onChange) }, active = hasTag(cur.t, "i")) {
                    Text("I", fontStyle = androidx.compose.ui.text.font.FontStyle.Italic, fontSize = 14.sp)
                }
                SiteToolBtn(onClick = { onToggle(doc, focused, "u", onChange) }, active = hasTag(cur.t, "u")) {
                    Text("U", fontSize = 14.sp)
                }
                SiteToolBtn(onClick = { onToggle(doc, focused, "s", onChange) }, active = hasTag(cur.t, "s")) {
                    Text("S", fontSize = 14.sp)
                }
                Box(Modifier.width(1.dp).height(20.dp).background(SiteBorderLight))
                SiteToolBtn(onClick = { cycleFont(doc, focused, onChange) }, active = cur.f.isNotBlank()) {
                    Text("Шр", fontSize = 13.sp)
                }
                SiteToolBtn(onClick = { cycleSize(doc, focused, onChange) }, active = cur.s > 0) {
                    Text("Аа", fontSize = 13.sp)
                }
                SiteToolBtn(onClick = { cycleColor(doc, focused, onChange) }, active = cur.col.isNotBlank()) {
                    Text("А", fontSize = 13.sp)
                }
                SiteToolBtn(onClick = { picker.launch("image/*") }) { Text("🖼", fontSize = 13.sp) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(SiteBorderLight))
        }

        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 12.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color.White)
                .padding(horizontal = 14.dp, vertical = 14.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                BasicTextField(
                    value = doc.title,
                    onValueChange = { onChange(doc.copy(title = it)) },
                    singleLine = true,
                    textStyle = TextStyle(
                        fontSize = 20.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = SiteInk,
                        fontFamily = FontFamily.SansSerif
                    ),
                    cursorBrush = SolidColor(SiteBlue),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        Column {
                            Box {
                                if (doc.title.isEmpty()) {
                                    Text("Название страницы", color = SiteHint, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                                }
                                inner()
                            }
                            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFAAAAAA).copy(alpha = 0.5f)))
                        }
                    }
                )
                if (onFolder != null) {
                    Box(
                        Modifier
                            .width(36.dp)
                            .height(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(
                                1.dp,
                                if (folderOn) SiteBlue else Color(0xFFCBD5E1),
                                RoundedCornerShape(8.dp)
                            )
                            .background(if (folderOn) SiteBlueSoft else Color(0xFFF8FAFC))
                            .clickable(onClick = onFolder),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("📁", fontSize = 16.sp)
                    }
                }
            }

            LazyColumn(
                Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                itemsIndexed(doc.items) { index, item ->
                    EditorRow(
                        index = index,
                        item = item,
                        focused = focused == index,
                        label = numberLabel(doc.items, index),
                        imageUrl = imageUrlOf(item, imageBaseUrl),
                        onFocus = { focused = index },
                        onText = { t ->
                            val items = doc.items.toMutableList()
                            items[index] = item.copy(t = t)
                            onChange(doc.copy(items = items))
                            focused = index
                        },
                        onLevel = { delta ->
                            val items = doc.items.toMutableList()
                            items[index] = item.copy(l = (item.l + delta).coerceIn(0, 8))
                            onChange(doc.copy(items = items))
                        },
                        onToggleFree = {
                            val items = doc.items.toMutableList()
                            items[index] = item.copy(n = !item.n)
                            onChange(doc.copy(items = items))
                        }
                    )
                }
            }

            SiteToolBtn(
                onClick = { onChange(doc.copy(items = doc.items + DocItem())) },
                modifier = Modifier.padding(top = 10.dp)
            ) { Text("+ Строка", fontSize = 14.sp) }
        }
    }
}

@Composable
private fun EditorRow(
    index: Int,
    item: DocItem,
    focused: Boolean,
    label: String,
    imageUrl: String?,
    onFocus: () -> Unit,
    onText: (String) -> Unit,
    onLevel: (Int) -> Unit,
    onToggleFree: () -> Unit
) {
    var drag by remember { mutableFloatStateOf(0f) }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(if (focused) SiteSelRow else Color.Transparent)
            .padding(vertical = 2.dp)
    ) {
        if (imageUrl != null && item.ip == "above") {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .padding(start = (item.l * 16).dp)
            )
        }
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = if (item.n) "—" else label,
                modifier = Modifier
                    .padding(start = (item.l * 16).dp, end = 4.dp, top = 3.dp)
                    .pointerInput(index, item.l) {
                        detectHorizontalDragGestures(
                            onDragEnd = {
                                when {
                                    drag > 40f -> onLevel(1)
                                    drag < -40f -> onLevel(-1)
                                }
                                drag = 0f
                            },
                            onHorizontalDrag = { _, dx -> drag += dx }
                        )
                    },
                color = if (item.n) SiteHint else SiteBlue,
                fontWeight = FontWeight.SemiBold,
                fontSize = 14.sp
            )
            BasicTextField(
                value = stripForEdit(item.t),
                onValueChange = { onText(it); onFocus() },
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { if (it.isFocused) onFocus() }
                    .clip(RoundedCornerShape(6.dp))
                    .border(
                        1.dp,
                        if (focused) SiteBlueFocus else Color.Transparent,
                        RoundedCornerShape(6.dp)
                    )
                    .background(if (focused) SiteEdFocus else Color.Transparent)
                    .padding(horizontal = 5.dp, vertical = 3.dp),
                textStyle = TextStyle(
                    fontFamily = when (item.f) {
                        "serif" -> FontFamily.Serif
                        "mono" -> FontFamily.Monospace
                        else -> FontFamily.SansSerif
                    },
                    fontSize = if (item.s > 0) item.s.sp else 16.sp,
                    color = parseCol(item.col).takeIf { it != Color.Unspecified } ?: SiteInk,
                    lineHeight = 22.sp
                ),
                cursorBrush = SolidColor(SiteBlue)
            )
            SiteToolBtn(
                onClick = onToggleFree,
                active = item.n,
                height = 28.dp,
                modifier = Modifier.padding(start = 2.dp)
            ) {
                Text(if (item.n) "№" else "абз", fontSize = 11.sp)
            }
        }
        if (imageUrl != null && item.ip != "above") {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .padding(start = (item.l * 16).dp, top = 4.dp)
            )
        }
    }
}

private fun imageUrlOf(item: DocItem, base: String): String? {
    if (item.img.isBlank()) return null
    return if (item.img.startsWith("http") || item.img.startsWith("file") || item.img.startsWith("content") || item.img.startsWith("/"))
        item.img
    else base.trimEnd('/') + "/uploads/" + item.img
}

private fun onToggle(doc: Document, focused: Int, tag: String, onChange: (Document) -> Unit) {
    val items = doc.items.toMutableList()
    val cur = items.getOrNull(focused) ?: return
    items[focused] = cur.copy(t = toggleHtml(cur.t, tag))
    onChange(doc.copy(items = items))
}

private fun cycleFont(doc: Document, focused: Int, onChange: (Document) -> Unit) {
    val items = doc.items.toMutableList()
    val cur = items.getOrNull(focused) ?: return
    items[focused] = cur.copy(
        f = when (cur.f) {
            "" -> "serif"
            "serif" -> "mono"
            else -> ""
        }
    )
    onChange(doc.copy(items = items))
}

private fun cycleSize(doc: Document, focused: Int, onChange: (Document) -> Unit) {
    val items = doc.items.toMutableList()
    val cur = items.getOrNull(focused) ?: return
    items[focused] = cur.copy(
        s = when {
            cur.s <= 0 -> 18
            cur.s < 24 -> 24
            cur.s < 32 -> 32
            else -> 0
        }
    )
    onChange(doc.copy(items = items))
}

private fun cycleColor(doc: Document, focused: Int, onChange: (Document) -> Unit) {
    val items = doc.items.toMutableList()
    val cur = items.getOrNull(focused) ?: return
    items[focused] = cur.copy(
        col = when (cur.col.lowercase()) {
            "" -> "#16A34A"
            "#16a34a" -> "#B91C1C"
            "#b91c1c" -> "#1D4ED8"
            else -> ""
        }
    )
    onChange(doc.copy(items = items))
}

private fun parseCol(c: String): Color = try {
    if (c.matches(Regex("^#[0-9a-fA-F]{6}$"))) Color(android.graphics.Color.parseColor(c))
    else Color.Unspecified
} catch (_: Exception) {
    Color.Unspecified
}

private fun stripForEdit(html: String): String =
    html.replace(Regex("</?(b|i|u|s|strong|em)>", RegexOption.IGNORE_CASE), "")

private fun hasTag(html: String, tag: String) =
    html.contains("<$tag>", true) || html.contains("<$tag ", true)

private fun toggleHtml(text: String, tag: String): String {
    val open = "<$tag>"
    val close = "</$tag>"
    return if (text.contains(open, true)) {
        text.replace(open, "", true).replace(close, "", true)
    } else {
        "$open$text$close"
    }
}

private fun numberLabel(items: List<DocItem>, index: Int): String {
    val counters = IntArray(16)
    var label = ""
    for (i in 0..index) {
        val it = items[i]
        if (it.n) continue
        val lvl = it.l.coerceIn(0, 15)
        counters[lvl]++
        for (j in lvl + 1 until counters.size) counters[j] = 0
        if (i == index) label = (0..lvl).joinToString(".") { counters[it].toString() }
    }
    return label
}
