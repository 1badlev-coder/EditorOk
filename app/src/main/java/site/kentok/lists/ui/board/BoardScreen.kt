package site.kentok.lists.ui.board

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.kentok.lists.data.model.BoardTile
import site.kentok.lists.data.model.Document
import site.kentok.lists.ui.components.SiteToolBtn
import site.kentok.lists.ui.components.SiteTopBar
import site.kentok.lists.ui.theme.SiteBg
import site.kentok.lists.ui.theme.SiteBlue
import site.kentok.lists.ui.theme.SiteInk
import site.kentok.lists.ui.theme.SiteMuted

@Composable
fun BoardScreen(
    tiles: List<BoardTile>,
    docs: List<Document>,
    onChange: (List<BoardTile>) -> Unit,
    onSave: () -> Unit,
    onOpenDoc: (String) -> Unit,
    onBack: () -> Unit
) {
    var editIndex by remember { mutableStateOf<Int?>(null) }
    Column(Modifier.fillMaxSize().background(SiteBg)) {
        SiteTopBar {
            SiteToolBtn(onClick = onBack) { Text("←", fontSize = 16.sp) }
            Text("Вид", fontWeight = FontWeight.SemiBold, fontSize = 15.sp, color = SiteInk)
            SiteToolBtn(onClick = { onChange(tiles + BoardTile(saved = false)) }) {
                Text("+", fontSize = 16.sp)
            }
            androidx.compose.foundation.layout.Spacer(Modifier.weight(1f))
            SiteToolBtn(onClick = onSave, primary = true) { Text("Сохранить", fontSize = 13.sp) }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            itemsIndexed(tiles) { index, tile ->
                val title = docs.firstOrNull { it.id == tile.doc }?.title
                    ?: if (tile.doc.isBlank()) "Пусто" else tile.doc
                val bg = parseColor(tile.color)
                Box(
                    Modifier
                        .aspectRatio(1f)
                        .background(bg, RoundedCornerShape(14.dp))
                        .clickable { editIndex = index }
                        .padding(10.dp)
                ) {
                    Column {
                        Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 15.sp)
                        if (tile.note.isNotBlank()) {
                            Text(tile.note, color = Color.White.copy(alpha = 0.9f), maxLines = 4, fontSize = 13.sp)
                        }
                        tile.rem?.let {
                            Text(
                                when (it.type) {
                                    "week" -> "↻ ${it.times.joinToString()}"
                                    else -> "⏰ ${it.at}"
                                },
                                color = Color.White,
                                modifier = Modifier.padding(top = 6.dp),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }

    editIndex?.let { idx ->
        val tile = tiles.getOrNull(idx) ?: return@let
        val fieldColors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = SiteBlue,
            unfocusedBorderColor = Color(0xFFBBBBBB)
        )
        AlertDialog(
            onDismissRequest = { editIndex = null },
            title = { Text("Секция") },
            text = {
                Column {
                    OutlinedTextField(
                        value = tile.doc,
                        onValueChange = { v ->
                            val next = tiles.toMutableList()
                            next[idx] = tile.copy(doc = v.trim())
                            onChange(next)
                        },
                        label = { Text("ID документа") },
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Документы:", color = SiteMuted, modifier = Modifier.padding(top = 8.dp), fontSize = 13.sp)
                    docs.take(8).forEach { d ->
                        Text(
                            d.title,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    val next = tiles.toMutableList()
                                    next[idx] = tile.copy(doc = d.id)
                                    onChange(next)
                                }
                                .padding(vertical = 4.dp),
                            color = SiteBlue
                        )
                    }
                    OutlinedTextField(
                        value = tile.note,
                        onValueChange = { v ->
                            val next = tiles.toMutableList()
                            next[idx] = tile.copy(note = v)
                            onChange(next)
                        },
                        label = { Text("Заметка") },
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    OutlinedTextField(
                        value = tile.color,
                        onValueChange = { v ->
                            val next = tiles.toMutableList()
                            next[idx] = tile.copy(color = v)
                            onChange(next)
                        },
                        label = { Text("Цвет #RRGGBB") },
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
                    )
                    TextButton(onClick = {
                        if (tile.doc.isNotBlank()) onOpenDoc(tile.doc)
                        editIndex = null
                    }) { Text("Открыть документ") }
                    TextButton(onClick = {
                        val colors = listOf("#1F8A32", "#1D4ED8", "#B91C1C", "#CA8A04", "#7C3AED", "")
                        val i = colors.indexOf(tile.color).let { if (it < 0) 0 else (it + 1) % colors.size }
                        val next = tiles.toMutableList()
                        next[idx] = tile.copy(color = colors[i])
                        onChange(next)
                    }) { Text("Сменить цвет") }
                    TextButton(onClick = {
                        onChange(tiles.filterIndexed { i, _ -> i != idx })
                        editIndex = null
                    }) { Text("Удалить секцию", color = Color(0xFFB91C1C)) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val next = tiles.toMutableList()
                    next[idx] = tile.copy(saved = true)
                    onChange(next)
                    editIndex = null
                }) { Text("ОК") }
            }
        )
    }
}

private fun parseColor(hex: String): Color = try {
    if (hex.matches(Regex("^#[0-9a-fA-F]{6}$"))) Color(android.graphics.Color.parseColor(hex))
    else Color(0xFF334155)
} catch (_: Exception) {
    Color(0xFF334155)
}
