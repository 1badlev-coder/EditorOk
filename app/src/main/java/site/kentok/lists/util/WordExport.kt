package site.kentok.lists.util

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import site.kentok.lists.data.model.DocItem
import site.kentok.lists.data.model.Document
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Минимальный .docx (OOXML) с уровнями и базовым оформлением. */
object WordExport {
    fun share(context: Context, doc: Document) {
        val file = File(context.cacheDir, safeName(doc.title) + ".docx")
        file.writeBytes(buildDocx(doc))
        val uri = FileProvider.getUriForFile(context, context.packageName + ".files", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Сохранить Word"))
    }

    private fun safeName(t: String): String =
        t.replace(Regex("[\\\\/:*?\"<>|]"), "_").take(40).ifBlank { "document" }

    private fun buildDocx(doc: Document): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zip ->
            zip.putNextEntry(ZipEntry("[Content_Types].xml"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
  <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
  <Default Extension="xml" ContentType="application/xml"/>
  <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
</Types>""".toByteArray()
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("_rels/.rels"))
            zip.write(
                """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
  <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
</Relationships>""".toByteArray()
            )
            zip.closeEntry()

            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(documentXml(doc).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        return bos.toByteArray()
    }

    private fun documentXml(doc: Document): String {
        val body = StringBuilder()
        body.append(p(esc(doc.title), bold = true, size = 28))
        val counters = IntArray(16)
        doc.items.forEach { item ->
            val indent = item.l.coerceIn(0, 8)
            val prefix = if (item.n) {
                ""
            } else {
                counters[indent]++
                for (j in indent + 1 until counters.size) counters[j] = 0
                (0..indent).joinToString(".") { counters[it].toString() } + ". "
            }
            val plain = strip(item.t)
            body.append(
                p(
                    prefix + plain,
                    bold = hasTag(item.t, "b"),
                    italic = hasTag(item.t, "i"),
                    underline = hasTag(item.t, "u"),
                    strike = hasTag(item.t, "s"),
                    size = if (item.s > 0) item.s * 2 else 22,
                    color = item.col.removePrefix("#"),
                    indentTwips = indent * 360
                )
            )
        }
        return """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>
<w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
  <w:body>
    $body
    <w:sectPr><w:pgSz w:w="11906" w:h="16838"/></w:sectPr>
  </w:body>
</w:document>"""
    }

    private fun p(
        text: String,
        bold: Boolean = false,
        italic: Boolean = false,
        underline: Boolean = false,
        strike: Boolean = false,
        size: Int = 22,
        color: String = "",
        indentTwips: Int = 0
    ): String {
        val rPr = buildString {
            append("<w:rPr>")
            if (bold) append("<w:b/>")
            if (italic) append("<w:i/>")
            if (underline) append("<w:u w:val=\"single\"/>")
            if (strike) append("<w:strike/>")
            append("<w:sz w:val=\"$size\"/><w:szCs w:val=\"$size\"/>")
            if (color.matches(Regex("^[0-9a-fA-F]{6}$"))) append("<w:color w:val=\"$color\"/>")
            append("</w:rPr>")
        }
        val ind = if (indentTwips > 0) "<w:ind w:left=\"$indentTwips\"/>" else ""
        return """<w:p><w:pPr>$ind</w:pPr><w:r>$rPr<w:t xml:space="preserve">${esc(text)}</w:t></w:r></w:p>"""
    }

    private fun esc(s: String) = s
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")

    private fun strip(html: String) =
        html.replace(Regex("</?(b|i|u|s|strong|em)>", RegexOption.IGNORE_CASE), "")

    private fun hasTag(html: String, tag: String) =
        html.contains("<$tag>", true) || html.contains("<$tag ", true)
}
