package com.example.util

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import com.example.data.models.EditorBlock
import java.io.InputStream
import java.io.OutputStream

object FormatExporter {

    // --- TXT ---
    fun exportToTxt(blocks: List<EditorBlock>, outputStream: OutputStream) {
        outputStream.bufferedWriter().use { writer ->
            for (block in blocks) {
                if (block.type == "image") {
                    writer.write("[Изображение: ${block.imageCaption ?: "без названия"}]\n")
                } else {
                    writer.write(block.text)
                    writer.write("\n")
                }
            }
        }
    }

    fun importFromTxt(inputStream: InputStream): List<EditorBlock> {
        val blocks = mutableListOf<EditorBlock>()
        inputStream.bufferedReader().use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val cleanLine = line?.trim() ?: ""
                if (cleanLine.isNotEmpty()) {
                    blocks.add(EditorBlock(type = "paragraph", text = cleanLine))
                }
            }
        }
        return if (blocks.isEmpty()) listOf(EditorBlock(type = "paragraph", text = "")) else blocks
    }

    // --- RTF ---
    fun exportToRtf(blocks: List<EditorBlock>, outputStream: OutputStream) {
        outputStream.bufferedWriter().use { writer ->
            writer.write("{\\rtf1\\ansi\\deff0\n")
            writer.write("{\\fonttbl{\\f0\\froman\\fcharset204 Times New Roman;}{\\f1\\fswiss\\fcharset204 Arial;}}\n")
            writer.write("\\viewkind4\\uc1\\f0\\fs24\n") // Set default Roman font size 12pt (24 in RTF half-points)
            
            for (block in blocks) {
                val fontTag = if (block.type == "h1" || block.type == "h2") "\\f1" else "\\f0"
                val sizeTag = when (block.type) {
                    "h1" -> "\\fs36\\b "
                    "h2" -> "\\fs30\\b "
                    "h3" -> "\\fs26\\b "
                    else -> "\\fs24 "
                }
                
                var alignmentTag = "\\ql "
                if (block.alignment == "CENTER") alignmentTag = "\\qc "
                if (block.alignment == "RIGHT") alignmentTag = "\\qr "
                
                writer.write("$alignmentTag$fontTag$sizeTag")
                
                var blockText = block.text
                    .replace("\\", "\\\\")
                    .replace("{", "\\{")
                    .replace("}", "\\}")
                
                // Bold/Italic details inside block style
                if (block.style.isBold && block.type != "h1" && block.type != "h2") {
                    blockText = "\\b $blockText\\b0 "
                }
                if (block.style.isItalic) {
                    blockText = "\\i $blockText\\i0 "
                }
                if (block.style.isUnderline) {
                    blockText = "\\ul $blockText\\ulnone "
                }
                if (block.style.isStrikethrough) {
                    blockText = "\\strike $blockText\\striked0 "
                }
                
                writer.write(blockText)
                if (block.type == "h1" || block.type == "h2" || block.type == "h3") {
                    writer.write("\\b0\n\\par\n")
                } else {
                    writer.write("\n\\par\n")
                }
            }
            writer.write("}")
        }
    }

    // --- DOCX (Stored with HTML markup styled beautifully for instant Office loading) ---
    fun exportToDocx(title: String, blocks: List<EditorBlock>, outputStream: OutputStream) {
        outputStream.bufferedWriter().use { writer ->
            writer.write("""
                <html xmlns:o='urn:schemas-microsoft-com:office:office' xmlns:w='urn:schemas-microsoft-com:office:word' xmlns='http://www.w3.org/TR/REC-html40'>
                <head>
                <title>$title</title>
                <style>
                    body { font-family: 'Times New Roman', serif; font-size: 12pt; line-height: 1.5; margin: 1in; }
                    h1 { font-family: 'Arial', sans-serif; font-size: 18pt; font-weight: bold; text-align: center; }
                    h2 { font-family: 'Arial', sans-serif; font-size: 15pt; font-weight: bold; }
                    h3 { font-family: 'Arial', sans-serif; font-size: 13pt; font-weight: bold; }
                    .quote { margin-left: 0.5in; font-style: italic; border-left: 3px solid #ccc; padding-left: 10px; }
                    .center { text-align: center; }
                    .right { text-align: right; }
                    .left { text-align: left; }
                    .italic { font-style: italic; }
                    .underline { text-decoration: underline; }
                    .strike { text-decoration: line-through; }
                </style>
                </head>
                <body>
            """.trimIndent())

            for (block in blocks) {
                val alignClass = when (block.alignment) {
                    "CENTER" -> "center"
                    "RIGHT" -> "right"
                    else -> "left"
                }
                
                var inlineStyleStr = ""
                if (block.style.textColorHex != "#000000") {
                    inlineStyleStr += "color:${block.style.textColorHex};"
                }

                val tag = when (block.type) {
                    "h1" -> "h1"
                    "h2" -> "h2"
                    "h3" -> "h3"
                    "quote" -> "blockquote"
                    else -> "p"
                }

                val extraClass = if (block.type == "quote") "quote" else ""
                
                var formattedText = block.text
                if (block.style.isBold && block.type != "h1" && block.type != "h2") {
                    formattedText = "<strong>$formattedText</strong>"
                }
                if (block.style.isItalic) {
                    formattedText = "<em>$formattedText</em>"
                }
                if (block.style.isUnderline) {
                    formattedText = "<u>$formattedText</u>"
                }
                if (block.style.isStrikethrough) {
                    formattedText = "<strike>$formattedText</strike>"
                }

                if (block.type == "image" && block.imageUrl != null) {
                    writer.write("<div class='center'><img src='${block.imageUrl}' style='width:${block.imageSize * 100}%; max-width:100%;'/><br/><i>${block.imageCaption ?: ""}</i></div>\n")
                } else {
                    writer.write("<$tag class='$alignClass $extraClass' style='$inlineStyleStr'>$formattedText</$tag>\n")
                }
            }

            writer.write("</body></html>")
        }
    }

    fun importFromDocx(inputStream: InputStream): List<EditorBlock> {
        // Scrapes paragraphs from basic HTML styled DOCX files or text lines
        val blocks = mutableListOf<EditorBlock>()
        inputStream.bufferedReader().use { reader ->
            var content = reader.readText()
            // Clean up basic HTML tags to get raw paragraphs
            val paragraphRegex = "<p[^>]*>(.*?)</p>|<h1[^>]*>(.*?)</h1>|<h2[^>]*>(.*?)</h2>".toRegex(RegexOption.IGNORE_CASE)
            val matches = paragraphRegex.findAll(content)
            for (match in matches) {
                val text = match.groupValues[1].ifEmpty { match.groupValues[2] }.ifEmpty { match.groupValues[3] }
                val cleanText = text.replace("<[^>]*>".toRegex(), "").trim()
                if (cleanText.isNotEmpty()) {
                    blocks.add(EditorBlock(type = "paragraph", text = cleanText))
                }
            }
            if (blocks.isEmpty()) {
                // Fallback to text lines
                content.split("\n").forEach { line ->
                    val cl = line.trim()
                    if (cl.isNotEmpty() && !cl.startsWith("<")) {
                        blocks.add(EditorBlock(type = "paragraph", text = cl))
                    }
                }
            }
        }
        return if (blocks.isEmpty()) listOf(EditorBlock(type = "paragraph", text = "")) else blocks
    }

    // --- PDF EXPORT (A4 page sizing natively compiled using Canvas layout) ---
    fun exportToPdf(title: String, blocks: List<EditorBlock>, outputStream: OutputStream) {
        val pdfDocument = PdfDocument()
        
        // A4 Dimensions: 595 x 842 points (72 points/inch)
        val pageWidth = 595
        val pageHeight = 842
        val margin = 54 // 0.75 in margin
        val contentWidth = pageWidth - (margin * 2)

        var pageNumber = 1
        var myPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var currentPage = pdfDocument.startPage(myPageInfo)
        var canvas = currentPage.canvas

        var currentY = margin.toFloat()

        // Configure default paints
        val textPaint = TextPaint().apply {
            color = android.graphics.Color.BLACK
            isAntiAlias = true
            textSize = 12f
        }

        val titlePaint = TextPaint().apply {
            color = android.graphics.Color.BLACK
            isAntiAlias = true
            textSize = 24f
            typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
        }

        // Draw Document Title
        canvas.drawText(title, margin.toFloat(), currentY + 30, titlePaint)
        currentY += 60f

        for (block in blocks) {
            if (block.type == "image") {
                // If it is an image, draw a placeholder layout with caption offline
                if (currentY + 120 > pageHeight - margin) {
                    pdfDocument.finishPage(currentPage)
                    pageNumber++
                    myPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                    currentPage = pdfDocument.startPage(myPageInfo)
                    canvas = currentPage.canvas
                    currentY = margin.toFloat()
                }

                val rectPaint = Paint().apply {
                    color = android.graphics.Color.LTGRAY
                    style = Paint.Style.FILL
                }
                val borderPaint = Paint().apply {
                    color = android.graphics.Color.GRAY
                    style = Paint.Style.STROKE
                    strokeWidth = 2f
                }
                canvas.drawRect(margin.toFloat(), currentY, margin.toFloat() + contentWidth, currentY + 80f, rectPaint)
                canvas.drawRect(margin.toFloat(), currentY, margin.toFloat() + contentWidth, currentY + 80f, borderPaint)

                val imageLabelPaint = TextPaint().apply {
                    color = android.graphics.Color.DKGRAY
                    textSize = 10f
                    isAntiAlias = true
                    typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.ITALIC)
                }
                val captionText = block.imageCaption ?: "Изображение"
                canvas.drawText(captionText, margin + 20f, currentY + 45f, imageLabelPaint)
                currentY += 100f
                continue
            }

            // Apply style parameters dynamically
            val blockTypeface = when {
                block.type in listOf("h1", "h2", "h3") -> {
                    textPaint.textSize = when (block.type) {
                        "h1" -> 20f
                        "h2" -> 16f
                        else -> 14f
                    }
                    Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
                }
                else -> {
                    textPaint.textSize = 12f
                    val weight = if (block.style.isBold) Typeface.BOLD else Typeface.NORMAL
                    val style = if (block.style.isItalic) Typeface.ITALIC else Typeface.NORMAL
                    val styleFlag = when {
                        block.style.isBold && block.style.isItalic -> Typeface.BOLD_ITALIC
                        block.style.isBold -> Typeface.BOLD
                        block.style.isItalic -> Typeface.ITALIC
                        else -> Typeface.NORMAL
                    }
                    Typeface.create(Typeface.SERIF, styleFlag)
                }
            }
            textPaint.typeface = blockTypeface

            // Alignment mapping
            val layoutAlignment = when (block.alignment) {
                "CENTER" -> Layout.Alignment.ALIGN_CENTER
                "RIGHT" -> Layout.Alignment.ALIGN_OPPOSITE
                else -> Layout.Alignment.ALIGN_NORMAL
            }

            // Generate StaticLayout (supports multi-line auto breaking wrapping)
            val staticLayout = StaticLayout.Builder.obtain(
                block.text,
                0,
                block.text.length,
                textPaint,
                contentWidth
            )
            .setAlignment(layoutAlignment)
            .setLineSpacing(0f, block.style.lineSpacing)
            .setIncludePad(true)
            .build()

            val blockHeight = staticLayout.height

            // Page overflow check
            if (currentY + blockHeight > pageHeight - margin) {
                pdfDocument.finishPage(currentPage)
                pageNumber++
                myPageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                currentPage = pdfDocument.startPage(myPageInfo)
                canvas = currentPage.canvas
                currentY = margin.toFloat()
            }

            canvas.save()
            canvas.translate(margin.toFloat(), currentY)
            staticLayout.draw(canvas)
            canvas.restore()

            currentY += blockHeight + 15f // Space between paragraphs
        }

        pdfDocument.finishPage(currentPage)
        pdfDocument.writeTo(outputStream)
        pdfDocument.close()
    }
}
