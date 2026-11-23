package site.kentok.lists.ui.editor

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import site.kentok.lists.data.model.BoardTile
import site.kentok.lists.data.model.Category
import site.kentok.lists.data.model.Document
import site.kentok.lists.data.model.WorkPanel
import site.kentok.lists.ui.board.BoardScreen
import site.kentok.lists.ui.components.SiteSaveBtn
import site.kentok.lists.ui.components.SiteToolBtn
import site.kentok.lists.ui.docs.CatPickerOverlay
import site.kentok.lists.ui.docs.DocsPickerOverlay
import site.kentok.lists.ui.theme.SiteBg
import site.kentok.lists.ui.theme.SiteBlue
import site.kentok.lists.ui.theme.SiteBlueSoft
import site.kentok.lists.ui.theme.SiteBorder
import site.kentok.lists.ui.theme.SiteBorderLight
import site.kentok.lists.ui.theme.SiteBtnFace
import site.kentok.lists.ui.theme.SiteInk
import site.kentok.lists.ui.theme.SiteMuted
import site.kentok.lists.ui.theme.SiteMuted2

@Composable
fun EditorShell(
    nick: String,
    panel: WorkPanel,
    current: Document?,
    openDocs: List<Document>,
    docs: List<Document>,
    cats: List<Category>,
    foldShut: Map<String, Boolean>,
    tiles: List<BoardTile>,
    loading: Boolean,
    showMenu: Boolean,
    showDocPicker: Boolean,
    showCatPicker: Boolean,
    pinOn: Boolean,
    autoSaveOn: Boolean,
    autoPinOutOn: Boolean,
    imageBaseUrl: String,
    onMenu: () -> Unit,
    onCloseMenu: () -> Unit,
    onSave: () -> Unit,
    onNew: () -> Unit,
    onOpenPicker: () -> Unit,
    onClosePicker: () -> Unit,
    onGuide: () -> Unit,
    onBoard: () -> Unit,
    onCloseBoard: () -> Unit,
    onAutoSave: () -> Unit,
    onAutoPinOut: () -> Unit,
    onTxt: () -> Unit,
    onCopy: () -> Unit,
    onWord: () -> Unit,
    onPinToggle: () -> Unit,
    onPinChange: (String) -> Unit,
    onOpenDoc: (String) -> Unit,
    onCloseDocTab: (String) -> Unit,
    onChange: (Document) -> Unit,
    onPickPhoto: (Int, Uri) -> Unit,
    onUpdateTiles: (List<BoardTile>) -> Unit,
    onSaveBoard: () -> Unit,
    onLogout: () -> Unit,
    onToggleFolder: (String) -> Unit,
    onAddFolder: (String) -> Unit,
    onRenameFolder: (String, String) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onSetDocCat: (String, String) -> Unit,
    onDeleteDoc: (String) -> Unit,
    onOpenCatPicker: () -> Unit,
    onCloseCatPicker: () -> Unit,
    onPickCat: (String) -> Unit,
    onNewCatFromDoc: (String) -> Unit
) {
    val context = LocalContext.current
    var pinDialog by remember { mutableStateOf(false) }
    var newPin by remember { mutableStateOf("") }

    Box(Modifier.fillMaxSize().background(SiteBg)) {
        Column(Modifier.fillMaxSize()) {
            // —— верхняя панель как на сайте (#top) ——
            Column(Modifier.fillMaxWidth().background(Color.White)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    SiteToolBtn(onClick = onMenu) { Text("☰", fontSize = 16.sp) }
                    SiteSaveBtn(
                        dirty = current?.dirty == true,
                        loading = loading,
                        onClick = onSave
                    )
                    SiteToolBtn(onClick = {}, enabled = false) { Text("↶", fontSize = 14.sp) }
                    SiteToolBtn(onClick = {}, enabled = false) { Text("↷", fontSize = 14.sp) }
                    Box(Modifier.width(1.dp).height(20.dp).background(SiteBorderLight))
                    openDocs.take(6).forEach { d ->
                        val on = d.id == current?.id
                        Box(
                            Modifier
                                .height(30.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (on) SiteBlueSoft else SiteBtnFace)
                                .border(1.dp, if (on) SiteBlue else SiteBorder, RoundedCornerShape(8.dp))
                                .clickable { onOpenDoc(d.id) }
                                .padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                (if (d.dirty) "● " else "") + d.title.ifBlank { "Без названия" },
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = SiteInk,
                                modifier = Modifier.width(100.dp)
                            )
                        }
                    }
                    Text(
                        when (panel) {
                            WorkPanel.BOARD -> "Вид"
                            WorkPanel.GUIDE -> "Инструкция"
                            else -> if (current == null) "нет документа" else if (current.dirty) "не сохранено" else "сохранено"
                        },
                        color = SiteMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 6.dp)
                    )
                }
                if (panel == WorkPanel.EDITOR) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        // Форматирование обрабатывается внутри EditorScreen через колбэки панели —
                        // здесь дублируем визуальный ряд; сами действия — в редакторе через меню/жесты.
                        Text("Формат · свайп номера = уровень · абз/№", color = SiteMuted, fontSize = 12.sp)
                    }
                }
                Box(Modifier.fillMaxWidth().height(1.dp).background(SiteBorderLight))
            }

            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (panel) {
                    WorkPanel.BOARD -> BoardScreen(
                        tiles = tiles,
                        docs = docs,
                        onChange = onUpdateTiles,
                        onSave = onSaveBoard,
                        onOpenDoc = onOpenDoc,
                        onBack = onCloseBoard
                    )
                    WorkPanel.GUIDE -> GuidePanel(onBack = onCloseBoard)
                    WorkPanel.EDITOR -> {
                        if (current == null) {
                            EmptyEditorHint(onMenu = onMenu)
                        } else {
                            EditorScreen(
                                doc = current,
                                loading = loading,
                                imageBaseUrl = imageBaseUrl,
                                embedded = true,
                                folderOn = current.cat.isNotBlank() && cats.any { it.id == current.cat },
                                folderTitle = cats.firstOrNull { it.id == current.cat }?.name
                                    ?: "Папка для сохранения",
                                onFolder = onOpenCatPicker,
                                onChange = onChange,
                                onSave = onSave,
                                onPickPhoto = onPickPhoto,
                                onExportWord = onWord,
                                onBack = { onCloseDocTab(current.id) }
                            )
                        }
                    }
                }
            }

            // —— подвал как #foot ——
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(Color.White)
                    .border(width = 0.dp, color = Color.Transparent)
            ) {
                Box(Modifier.fillMaxWidth().height(1.dp).background(SiteBorderLight))
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SiteToolBtn(onClick = onLogout, height = 32.dp) { Text("⎋", fontSize = 14.sp) }
                    Text(
                        nick,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 6.dp).weight(1f),
                        color = SiteInk
                    )
                    SiteToolBtn(onClick = onGuide, height = 32.dp) { Text("?", fontWeight = FontWeight.Bold) }
                }
                Text(
                    "Enter — новая строка · свайп номера — уровень · ☰ — меню сайта",
                    color = SiteMuted2,
                    fontSize = 11.sp,
                    maxLines = 2,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                )
            }
        }

        if (showMenu) {
            SiteMenuOverlay(
                state = MenuState(
                    dirty = current?.dirty == true,
                    hasDoc = current != null,
                    pinOn = pinOn,
                    autoSaveOn = autoSaveOn,
                    autoPinOutOn = autoPinOutOn,
                    boardOn = panel == WorkPanel.BOARD,
                    guideOn = panel == WorkPanel.GUIDE,
                    openDocs = openDocs,
                    currentId = current?.id
                ),
                onDismiss = onCloseMenu,
                onNew = onNew,
                onOpen = onOpenPicker,
                onGuide = onGuide,
                onBoard = onBoard,
                onSave = onSave,
                onAutoSave = onAutoSave,
                onAutoPinOut = onAutoPinOut,
                onTxt = onTxt,
                onCopy = onCopy,
                onWord = onWord,
                onPinToggle = onPinToggle,
                onPinChange = { pinDialog = true },
                onOpenDoc = onOpenDoc,
                onCloseDoc = onCloseDocTab
            )
        }

        if (showDocPicker) {
            DocsPickerOverlay(
                docs = docs,
                cats = cats,
                foldShut = foldShut,
                openIds = openDocs.map { it.id }.toSet(),
                currentId = current?.id,
                onDismiss = onClosePicker,
                onOpen = onOpenDoc,
                onToggleFolder = onToggleFolder,
                onAddFolder = onAddFolder,
                onRenameFolder = onRenameFolder,
                onDeleteFolder = onDeleteFolder,
                onSetCat = onSetDocCat,
                onDeleteDoc = onDeleteDoc
            )
        }

        if (showCatPicker && current != null) {
            CatPickerOverlay(
                cats = cats,
                currentCat = current.cat,
                onDismiss = onCloseCatPicker,
                onPick = onPickCat,
                onNew = onNewCatFromDoc
            )
        }

        if (pinDialog) {
            AlertDialog(
                onDismissRequest = { pinDialog = false },
                title = { Text("Сменить ПИН-код") },
                text = {
                    OutlinedTextField(
                        value = newPin,
                        onValueChange = { if (it.length <= 8 && it.all { c -> c.isDigit() }) newPin = it },
                        label = { Text("Новый ПИН (3–8 цифр)") },
                        singleLine = true
                    )
                },
                confirmButton = {
                    TextButton(onClick = {
                        onPinChange(newPin)
                        newPin = ""
                        pinDialog = false
                    }) { Text("Сохранить") }
                },
                dismissButton = {
                    TextButton(onClick = { pinDialog = false }) { Text("Отмена") }
                }
            )
        }
    }
}

@Composable
private fun EmptyEditorHint(onMenu: () -> Unit) {
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Нет открытых документов.",
                color = SiteMuted,
                fontSize = 16.sp
            )
            Text(
                "Откройте меню (☰): создайте новую страницу\nили откройте сохранённый документ.",
                color = SiteMuted,
                fontSize = 15.sp,
                modifier = Modifier.padding(top = 8.dp),
                lineHeight = 22.sp
            )
            Spacer(Modifier.height(16.dp))
            SiteToolBtn(onClick = onMenu, primary = true, height = 36.dp) {
                Text("Открыть меню", fontSize = 14.sp)
            }
        }
    }
}

@Composable
private fun GuidePanel(onBack: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color.White)
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            SiteToolBtn(onClick = onBack) { Text("←", fontSize = 16.sp) }
            Text("Инструкция", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(start = 8.dp))
        }
        Text(
            "Меню ☰ — создание и открытие документов, Вид, Word, ПИН.\n\n" +
                "Свайп по номеру строки влево/вправо — уровень.\n\n" +
                "Кнопка «абз/№» — свободный текст без нумерации.\n\n" +
                "Панель формата — жирный, курсив, цвет, фото.\n\n" +
                "Сохранение — синяя кнопка «сохранено/не сохранено», как на сайте.",
            color = SiteInk,
            fontSize = 15.sp,
            lineHeight = 22.sp,
            modifier = Modifier.padding(top = 14.dp)
        )
    }
}

