package site.kentok.lists.ui.docs

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.kentok.lists.data.model.Category
import site.kentok.lists.data.model.Document
import site.kentok.lists.ui.theme.SiteBlue
import site.kentok.lists.ui.theme.SiteBlueDark
import site.kentok.lists.ui.theme.SiteBlueSoft
import site.kentok.lists.ui.theme.SiteBorder
import site.kentok.lists.ui.theme.SiteError
import site.kentok.lists.ui.theme.SiteInk
import site.kentok.lists.ui.theme.SiteMuted
import site.kentok.lists.ui.theme.SiteMuted2
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Окно «Сохранённые документы» как #docs на мобильном сайте: папки, select, удаление. */
@Composable
fun DocsPickerOverlay(
    docs: List<Document>,
    cats: List<Category>,
    foldShut: Map<String, Boolean>,
    openIds: Set<String>,
    currentId: String?,
    onDismiss: () -> Unit,
    onOpen: (String) -> Unit,
    onToggleFolder: (String) -> Unit,
    onAddFolder: (String) -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onSetCat: (String, String) -> Unit,
    onDeleteDoc: (String) -> Unit
) {
    var addFolder by remember { mutableStateOf(false) }
    var folderName by remember { mutableStateOf("") }
    var renameId by remember { mutableStateOf<String?>(null) }
    var renameName by remember { mutableStateOf("") }

    val groups = remember(docs, cats) {
        val known = cats.map { it.id }.toSet()
        val uncategorized = docs.filter { it.cat.isBlank() || it.cat !in known }
        listOf(Category("", "Без категории") to uncategorized) +
            cats.map { c -> c to docs.filter { it.cat == c.id } }
    }

    Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickable(onClick = onDismiss)) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .heightIn(max = 640.dp)
                .clip(RoundedCornerShape(14.dp, 14.dp, 0.dp, 0.dp))
                .background(Color.White)
                .clickable(enabled = false) {}
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Сохранённые документы", fontWeight = FontWeight.Bold, fontSize = 17.sp, color = SiteInk)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(Color(0xFF16A34A))
                            .clickable {
                                folderName = ""
                                addFolder = true
                            }
                            .padding(horizontal = 10.dp, vertical = 7.dp)
                    ) {
                        Text("＋ Добавить папку", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                    Text("✕", modifier = Modifier.clickable(onClick = onDismiss).padding(6.dp), color = SiteMuted, fontSize = 18.sp)
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp)
            ) {
                if (docs.isEmpty()) {
                    Text("Документов пока нет", color = SiteMuted, modifier = Modifier.padding(12.dp))
                } else {
                    groups.forEach { (gr, items) ->
                        if (items.isEmpty() && gr.id.isEmpty()) return@forEach
                        val key = gr.id.ifBlank { "_" }
                        val shut = foldShut[key] ?: true
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onToggleFolder(gr.id) }
                                .padding(vertical = 7.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                if (shut) "▶" else "▼",
                                color = SiteMuted2,
                                fontSize = 12.sp,
                                modifier = Modifier.width(16.dp)
                            )
                            Text(
                                "${gr.name} (${items.size})",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Color(0xFF334155),
                                modifier = Modifier.weight(1f)
                            )
                            if (gr.id.isNotBlank()) {
                                Text(
                                    "✎",
                                    modifier = Modifier
                                        .clickable {
                                            renameId = gr.id
                                            renameName = gr.name
                                        }
                                        .padding(6.dp),
                                    fontSize = 14.sp
                                )
                                Text(
                                    "🗑",
                                    modifier = Modifier
                                        .clickable { onDeleteFolder(gr.id) }
                                        .padding(6.dp),
                                    fontSize = 14.sp
                                )
                            }
                        }
                        if (!shut) {
                            items.forEach { d ->
                                DocRow(
                                    doc = d,
                                    cats = cats,
                                    marked = d.id in openIds || d.id == currentId,
                                    onOpen = { onOpen(d.id) },
                                    onSetCat = { onSetCat(d.id, it) },
                                    onDelete = { onDeleteDoc(d.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (addFolder) {
        AlertDialog(
            onDismissRequest = { addFolder = false },
            title = { Text("Новая папка") },
            text = {
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    singleLine = true,
                    label = { Text("Название") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onAddFolder(folderName)
                    addFolder = false
                }) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { addFolder = false }) { Text("Отмена") } }
        )
    }
    renameId?.let { id ->
        AlertDialog(
            onDismissRequest = { renameId = null },
            title = { Text("Переименовать папку") },
            text = {
                OutlinedTextField(
                    value = renameName,
                    onValueChange = { renameName = it },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRenameFolder(id, renameName)
                    renameId = null
                }) { Text("Сохранить") }
            },
            dismissButton = { TextButton(onClick = { renameId = null }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun DocRow(
    doc: Document,
    cats: List<Category>,
    marked: Boolean,
    onOpen: () -> Unit,
    onSetCat: (String) -> Unit,
    onDelete: () -> Unit
) {
    var showMove by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru")) }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Column(
            Modifier
                .weight(1f)
                .heightIn(min = 46.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, if (marked) SiteBlue else SiteBorder, RoundedCornerShape(8.dp))
                .background(if (marked) SiteBlueSoft else Color(0xFFFAFAFA))
                .clickable(onClick = onOpen)
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Text(
                (if (marked) "● " else "") + doc.title.ifBlank { "Без названия" },
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (marked) SiteBlueDark else SiteInk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                if (doc.updated > 0) fmt.format(Date(doc.updated * 1000)) else doc.id,
                color = SiteMuted,
                fontSize = 12.sp
            )
        }
        Box(
            Modifier
                .height(36.dp)
                .clip(RoundedCornerShape(8.dp))
                .border(1.dp, SiteBorder, RoundedCornerShape(8.dp))
                .background(Color.White)
                .clickable { showMove = true }
                .padding(horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            val name = cats.firstOrNull { it.id == doc.cat }?.name ?: "Без категории"
            Text(name, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, color = SiteInk)
        }
        Box(
            Modifier
                .width(34.dp)
                .height(34.dp)
                .clickable(onClick = onDelete),
            contentAlignment = Alignment.Center
        ) {
            Text("🗑", color = SiteError, fontSize = 14.sp)
        }
    }
    if (showMove) {
        AlertDialog(
            onDismissRequest = { showMove = false },
            title = { Text("Папка документа") },
            text = {
                Column {
                    TextButton(onClick = { onSetCat(""); showMove = false }) {
                        Text(if (doc.cat.isBlank()) "● Без категории" else "Без категории")
                    }
                    cats.forEach { c ->
                        TextButton(onClick = { onSetCat(c.id); showMove = false }) {
                            Text(if (doc.cat == c.id) "● ${c.name}" else c.name)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showMove = false }) { Text("Закрыть") } }
        )
    }
}

@Composable
fun CatPickerOverlay(
    cats: List<Category>,
    currentCat: String,
    onDismiss: () -> Unit,
    onPick: (String) -> Unit,
    onNew: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var askNew by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickable(onClick = onDismiss)) {
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp, 14.dp, 0.dp, 0.dp))
                .background(Color.White)
                .clickable(enabled = false) {}
                .padding(14.dp)
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Папка документа", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                Text("✕", modifier = Modifier.clickable(onClick = onDismiss), color = SiteMuted)
            }
            MiPick("Без папки", currentCat.isBlank()) { onPick("") }
            cats.forEach { c ->
                MiPick(c.name, currentCat == c.id) { onPick(c.id) }
            }
            MiPick("＋ Новая папка", false) { askNew = true }
        }
    }
    if (askNew) {
        AlertDialog(
            onDismissRequest = { askNew = false },
            title = { Text("Новая папка") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    onNew(name)
                    askNew = false
                }) { Text("Создать") }
            },
            dismissButton = { TextButton(onClick = { askNew = false }) { Text("Отмена") } }
        )
    }
}

@Composable
private fun MiPick(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (on) SiteBlueSoft else Color(0xFFFAFAFA))
            .border(1.dp, if (on) SiteBlue else SiteBorder, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(text, fontSize = 15.sp, color = if (on) SiteBlueDark else SiteInk)
    }
}
