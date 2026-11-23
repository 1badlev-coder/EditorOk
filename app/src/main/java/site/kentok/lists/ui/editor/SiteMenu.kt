package site.kentok.lists.ui.editor

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import site.kentok.lists.ui.theme.SiteBlue
import site.kentok.lists.ui.theme.SiteBlueDark
import site.kentok.lists.ui.theme.SiteBlueSoft
import site.kentok.lists.ui.theme.SiteBorderSoft
import site.kentok.lists.ui.theme.SiteError
import site.kentok.lists.ui.theme.SiteInk
import site.kentok.lists.ui.theme.SiteMuted
import site.kentok.lists.ui.theme.SiteOk

data class MenuState(
    val dirty: Boolean,
    val hasDoc: Boolean,
    val pinOn: Boolean,
    val autoSaveOn: Boolean,
    val autoPinOutOn: Boolean,
    val boardOn: Boolean,
    val guideOn: Boolean,
    val openDocs: List<Document>,
    val currentId: String?
)

@Composable
fun SiteMenuOverlay(
    state: MenuState,
    onDismiss: () -> Unit,
    onNew: () -> Unit,
    onOpen: () -> Unit,
    onGuide: () -> Unit,
    onBoard: () -> Unit,
    onSave: () -> Unit,
    onAutoSave: () -> Unit,
    onAutoPinOut: () -> Unit,
    onTxt: () -> Unit,
    onCopy: () -> Unit,
    onWord: () -> Unit,
    onPinToggle: () -> Unit,
    onPinChange: () -> Unit,
    onOpenDoc: (String) -> Unit,
    onCloseDoc: (String) -> Unit
) {
    Box(Modifier.fillMaxSize().background(Color(0x66000000)).clickable(onClick = onDismiss)) {
        Column(
            Modifier
                .align(Alignment.TopStart)
                .fillMaxHeight()
                .width(340.dp)
                .padding(top = 56.dp)
                .clip(RoundedCornerShape(0.dp, 0.dp, 14.dp, 0.dp))
                .background(Color.White)
                .clickable(enabled = false) {}
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp, bottom = 16.dp, start = 6.dp, end = 6.dp)
        ) {
            Mi("＋  Новая страница", onClick = { onDismiss(); onNew() })
            Mi("📁  Открыть документ", onClick = { onDismiss(); onOpen() })
            Mi(
                "📖  Инструкция",
                onClick = { onDismiss(); onGuide() },
                active = state.guideOn
            )
            Mi(
                "▦  Вид",
                onClick = { onDismiss(); onBoard() },
                active = state.boardOn
            )
            Mi(
                "💾  Сохранить",
                onClick = { onDismiss(); onSave() },
                enabled = state.hasDoc,
                color = if (state.dirty) SiteBlueDark else SiteOk,
                bold = true
            )
            Mi(
                if (state.autoSaveOn) "⏱  Автосохранение: вкл" else "⏱  Автосохранение: выкл",
                onClick = onAutoSave,
                active = state.autoSaveOn,
                color = if (state.autoSaveOn) SiteOk else SiteInk
            )
            if (state.pinOn) {
                Mi(
                    if (state.autoPinOutOn) "🔒  Автовыход на ПИН-код: вкл" else "🔒  Автовыход на ПИН-код: выкл",
                    onClick = onAutoPinOut,
                    active = state.autoPinOutOn
                )
            }
            Mi("⬇  Скачать (.txt)", onClick = { onDismiss(); onTxt() }, enabled = state.hasDoc)
            Mi("⧉  Копировать текст", onClick = { onDismiss(); onCopy() }, enabled = state.hasDoc)
            Mi("W  Сохранить в Word (.docx)", onClick = { onDismiss(); onWord() }, enabled = state.hasDoc)
            Mi(
                if (state.pinOn) "🔒  Вход по ПИН-коду: вкл" else "🔒  Вход по ПИН-коду: выкл",
                onClick = onPinToggle,
                active = state.pinOn
            )
            if (state.pinOn) {
                Mi("🔑  Сменить ПИН-код", onClick = { onDismiss(); onPinChange() })
            }
            Text(
                "ОТКРЫТЫЕ ДОКУМЕНТЫ",
                color = SiteMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 4.dp)
            )
            if (state.openDocs.isEmpty()) {
                Text(
                    "Нет открытых документов",
                    color = Color(0xFF94A3B8),
                    fontSize = 14.sp,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            } else {
                state.openDocs.forEach { doc ->
                    val cur = doc.id == state.currentId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (cur) SiteBlueSoft else Color.Transparent)
                                .border(
                                    1.dp,
                                    if (cur) SiteBlue else SiteBorderSoft,
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable { onDismiss(); onOpenDoc(doc.id) }
                                .padding(horizontal = 10.dp),
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Text(
                                text = (if (doc.dirty) "● " else "") + doc.title.ifBlank { "Без названия" },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 14.sp,
                                color = SiteInk
                            )
                        }
                        Box(
                            Modifier
                                .padding(start = 6.dp)
                                .width(34.dp)
                                .height(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onCloseDoc(doc.id) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("×", color = SiteError, fontSize = 20.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Mi(
    text: String,
    onClick: () -> Unit,
    enabled: Boolean = true,
    active: Boolean = false,
    bold: Boolean = false,
    color: Color = SiteInk
) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp, horizontal = 4.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) SiteBlueSoft else Color.Transparent)
            .border(
                1.dp,
                if (active) SiteBlue else Color.Transparent,
                RoundedCornerShape(8.dp)
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            fontSize = 15.sp,
            fontWeight = if (bold || active) FontWeight.SemiBold else FontWeight.Normal,
            color = if (enabled) color else color.copy(alpha = 0.35f)
        )
    }
}
