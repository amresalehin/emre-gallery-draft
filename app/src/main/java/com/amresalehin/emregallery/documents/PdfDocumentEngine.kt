package com.amresalehin.emregallery.documents

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import androidx.exifinterface.media.ExifInterface
import com.amresalehin.emregallery.ocr.MlKitOcr
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDDocumentInformation
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.encryption.AccessPermission
import com.tom_roush.pdfbox.pdmodel.encryption.StandardProtectionPolicy
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotation
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.pdmodel.graphics.state.RenderingMode
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

enum class PdfOperation {
    PDF_TO_IMAGES,
    IMAGES_TO_PDF,
    MERGE_PDFS,
    SPLIT_PDF,
    ROTATE_PDF,
    REARRANGE_PDF,
    PAGE_NUMBERS,
    WATERMARK,
    SIGNATURE,
    COMPRESS,
    GRAYSCALE,
    REPAIR,
    SANITIZE,
    PROTECT,
    UNLOCK,
    EDIT_METADATA,
    REMOVE_PAGES,
    CROP_PAGES,
    RESIZE_PAGES,
    FLATTEN,
    EXTRACT_IMAGES,
    ZIP_PDFS,
    OCR_PDF,
    REMOVE_ANNOTATIONS,
    CONTACT_SHEET,
    COMPARE_PDFS
}

data class PdfOperationOptions(
    val pages: String = "",
    val password: String = "",
    val ownerPassword: String = "",
    val watermarkText: String = "Emre Gallery",
    val signatureText: String = "",
    val title: String = "",
    val author: String = "",
    val subject: String = "",
    val ocrLanguages: List<String> = listOf("latin", "devanagari")
)

object PdfDocumentEngine {
    private const val MAX_RENDER_DIMENSION = 2600
    private const val MAX_IMAGE_PIXELS = 12_000_000L

    fun initialise(context: Context) {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    fun renderPage(context: Context, uri: Uri, pageIndex: Int, maxDimension: Int = 1600): Pair<Bitmap, Int> {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Cannot open the selected PDF.")
        descriptor.use { parcel ->
            PdfRenderer(parcel).use { renderer ->
                require(pageIndex in 0 until renderer.pageCount) { "Page does not exist." }
                renderer.openPage(pageIndex).use { page ->
                    val scale = min(1f, maxDimension.toFloat() / max(page.width, page.height).coerceAtLeast(1))
                    val width = (page.width * scale).roundToInt().coerceAtLeast(1)
                    val height = (page.height * scale).roundToInt().coerceAtLeast(1)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    bitmap.eraseColor(Color.WHITE)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    return bitmap to renderer.pageCount
                }
            }
        }
    }

    fun pageCount(context: Context, uri: Uri): Int {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r")
            ?: error("Cannot open the selected PDF.")
        descriptor.use { PdfRenderer(it).use { renderer -> renderer.pageCount } }
    }

    fun process(
        context: Context,
        operation: PdfOperation,
        inputUris: List<Uri>,
        options: PdfOperationOptions = PdfOperationOptions()
    ): File {
        initialise(context)
        require(inputUris.isNotEmpty()) { "Select the required source file(s)." }
        return when (operation) {
            PdfOperation.PDF_TO_IMAGES -> pdfToImages(context, inputUris.first())
            PdfOperation.IMAGES_TO_PDF -> imagesToPdf(context, inputUris)
            PdfOperation.MERGE_PDFS -> mergePdfs(context, inputUris)
            PdfOperation.SPLIT_PDF -> splitPdf(context, inputUris.first(), options.password)
            PdfOperation.ROTATE_PDF -> mutatePdf(context, inputUris.first(), options.password, "rotated") { document ->
                document.pages.forEach { page -> page.rotation = (page.rotation + 90) % 360 }
            }
            PdfOperation.REARRANGE_PDF -> rearrangePdf(context, inputUris.first(), options.password)
            PdfOperation.PAGE_NUMBERS -> mutatePdf(context, inputUris.first(), options.password, "numbered") { document ->
                document.pages.forEachIndexed { index, page ->
                    PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                        stream.setNonStrokingColor(30, 30, 30)
                        stream.beginText()
                        stream.setFont(PDType1Font.HELVETICA, 10f)
                        stream.newLineAtOffset(page.mediaBox.width / 2f - 12f, 18f)
                        stream.showText("${index + 1} / ${document.numberOfPages}")
                        stream.endText()
                    }
                }
            }
            PdfOperation.WATERMARK -> mutatePdf(context, inputUris.first(), options.password, "watermarked") { document ->
                val label = cleanPdfText(options.watermarkText).ifBlank { "Emre Gallery" }
                document.pages.forEach { page ->
                    PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                        stream.setNonStrokingColor(150, 150, 150)
                        stream.beginText()
                        stream.setFont(PDType1Font.HELVETICA_BOLD, 28f)
                        stream.newLineAtOffset(40f, page.mediaBox.height / 2f)
                        stream.showText(label.take(90))
                        stream.endText()
                    }
                }
            }
            PdfOperation.SIGNATURE -> mutatePdf(context, inputUris.first(), options.password, "signed") { document ->
                val label = cleanPdfText(options.signatureText).ifBlank { "Signed with Emre Gallery" }
                document.pages.forEach { page ->
                    PDPageContentStream(document, page, PDPageContentStream.AppendMode.APPEND, true, true).use { stream ->
                        stream.setNonStrokingColor(25, 25, 25)
                        stream.beginText()
                        stream.setFont(PDType1Font.HELVETICA_OBLIQUE, 16f)
                        stream.newLineAtOffset(36f, 36f)
                        stream.showText(label.take(100))
                        stream.endText()
                    }
                }
            }
            PdfOperation.COMPRESS -> rasterizePdf(context, inputUris.first(), 105, 0.58f, false, "compressed")
            PdfOperation.GRAYSCALE -> rasterizePdf(context, inputUris.first(), 140, 0.78f, true, "grayscale")
            PdfOperation.REPAIR -> mutatePdf(context, inputUris.first(), options.password, "repaired") { document ->
                document.documentInformation.producer = "Emre Gallery PDF Tools"
            }
            PdfOperation.SANITIZE -> mutatePdf(context, inputUris.first(), options.password, "sanitized") { document ->
                val catalog = document.documentCatalog.cosObject
                catalog.removeItem(COSName.OPEN_ACTION)
                catalog.removeItem(COSName.AA)
                catalog.removeItem(COSName.getPDFName("Names"))
                catalog.removeItem(COSName.getPDFName("Metadata"))
                document.documentInformation = PDDocumentInformation()
                document.pages.forEach { page ->
                    page.annotations = emptyList<PDAnnotation>()
                    page.cosObject.removeItem(COSName.AA)
                    page.cosObject.removeItem(COSName.METADATA)
                }
            }
            PdfOperation.PROTECT -> protectPdf(context, inputUris.first(), options)
            PdfOperation.UNLOCK -> unlockPdf(context, inputUris.first(), options.password)
            PdfOperation.EDIT_METADATA -> mutatePdf(context, inputUris.first(), options.password, "metadata") { document ->
                document.documentInformation = document.documentInformation.apply {
                    if (options.title.isNotBlank()) title = options.title.take(200)
                    if (options.author.isNotBlank()) author = options.author.take(200)
                    if (options.subject.isNotBlank()) subject = options.subject.take(500)
                    producer = "Emre Gallery PDF Tools"
                }
            }
            PdfOperation.REMOVE_PAGES -> removePages(context, inputUris.first(), options)
            PdfOperation.CROP_PAGES -> mutatePdf(context, inputUris.first(), options.password, "cropped") { document ->
                document.pages.forEach { page ->
                    val box = page.cropBox
                    val mx = box.width * 0.035f
                    val my = box.height * 0.035f
                    page.cropBox = PDRectangle(box.lowerLeftX + mx, box.lowerLeftY + my, box.width - 2 * mx, box.height - 2 * my)
                }
            }
            PdfOperation.RESIZE_PAGES -> rasterizePdf(context, inputUris.first(), 130, 0.82f, false, "a4")
            PdfOperation.FLATTEN -> rasterizePdf(context, inputUris.first(), 160, 0.90f, false, "flattened")
            PdfOperation.EXTRACT_IMAGES -> extractImages(context, inputUris.first(), options.password)
            PdfOperation.ZIP_PDFS -> zipPdfs(context, inputUris)
            PdfOperation.OCR_PDF -> searchableOcrPdf(context, inputUris.first(), options)
            PdfOperation.REMOVE_ANNOTATIONS -> mutatePdf(context, inputUris.first(), options.password, "no_annotations") { document ->
                document.pages.forEach { page -> page.annotations = emptyList<PDAnnotation>() }
            }
            PdfOperation.CONTACT_SHEET -> contactSheet(context, inputUris.first())
            PdfOperation.COMPARE_PDFS -> comparePdfs(context, inputUris[0], inputUris[1])
        }
    }

    fun copyResultToUri(context: Context, file: File, uri: Uri) {
        val output = context.contentResolver.openOutputStream(uri, "w")
            ?: error("Cannot write to the selected destination.")
        output.use { destination -> FileInputStream(file).use { it.copyTo(destination) } }
    }

    private fun outputFile(context: Context, prefix: String, extension: String): File {
        val directory = File(context.cacheDir, "pdf-tools").apply {
            if (!exists() && !mkdirs()) error("Cannot create temporary PDF workspace.")
        }
        return File.createTempFile("${prefix}_", ".$extension", directory)
    }

    private fun copyUriToFile(context: Context, uri: Uri): File {
        val source = context.contentResolver.openInputStream(uri) ?: error("Cannot read the selected file.")
        val target = outputFile(context, "source", "pdf")
        source.use { input -> FileOutputStream(target).use { input.copyTo(it) } }
        return target
    }

    private inline fun withPdf(
        context: Context,
        uri: Uri,
        password: String = "",
        block: (PDDocument) -> File
    ): File {
        val source = copyUriToFile(context, uri)
        try {
            PDDocument.load(source, password).use { document -> return block(document) }
        } catch (error: Exception) {
            if (error.message?.contains("password", true) == true || error.javaClass.simpleName.contains("InvalidPassword")) {
                throw IllegalArgumentException("This PDF is password-protected. Choose Unlock PDF and enter its password first.")
            }
            throw error
        } finally {
            source.delete()
        }
    }

    private inline fun mutatePdf(
        context: Context,
        uri: Uri,
        password: String,
        prefix: String,
        crossinline mutation: (PDDocument) -> Unit
    ): File = withPdf(context, uri, password) { document ->
        mutation(document)
        val output = outputFile(context, prefix, "pdf")
        document.save(output)
        output
    }

    private fun mergePdfs(context: Context, uris: List<Uri>): File {
        require(uris.size >= 2) { "Select at least two PDF files to merge." }
        val output = PDDocument()
        val sources = mutableListOf<PDDocument>()
        try {
            uris.forEach { uri ->
                val sourceFile = copyUriToFile(context, uri)
                val source = PDDocument.load(sourceFile)
                sources.add(source)
                for (page in source.pages) output.importPage(page)
                sourceFile.deleteOnExit()
            }
            val target = outputFile(context, "merged", "pdf")
            output.save(target)
            return target
        } finally {
            sources.forEach { runCatching { it.close() } }
            output.close()
        }
    }

    private fun splitPdf(context: Context, uri: Uri, password: String): File = withPdf(context, uri, password) { source ->
        require(source.numberOfPages > 0) { "The PDF contains no pages." }
        val target = outputFile(context, "split_pages", "zip")
        ZipOutputStream(FileOutputStream(target)).use { zip ->
            for (index in 0 until source.numberOfPages) {
                val pageDoc = PDDocument()
                try {
                    pageDoc.importPage(source.getPage(index))
                    zip.putNextEntry(ZipEntry("page_${(index + 1).toString().padStart(3, '0')}.pdf"))
                    pageDoc.save(zip)
                    zip.closeEntry()
                } finally {
                    pageDoc.close()
                }
            }
        }
        target
    }

    private fun rearrangePdf(context: Context, uri: Uri, password: String): File = withPdf(context, uri, password) { source ->
        val output = PDDocument()
        try {
            for (index in source.numberOfPages - 1 downTo 0) output.importPage(source.getPage(index))
            val target = outputFile(context, "reversed", "pdf")
            output.save(target)
            target
        } finally {
            output.close()
        }
    }

    private fun protectPdf(context: Context, uri: Uri, options: PdfOperationOptions): File =
        withPdf(context, uri) { document ->
            require(options.password.length >= 4) { "Use a password containing at least four characters." }
            val owner = options.ownerPassword.ifBlank { options.password + "_owner" }
            val permission = AccessPermission()
            val policy = StandardProtectionPolicy(owner, options.password, permission).apply {
                encryptionKeyLength = 128
            }
            document.protect(policy)
            val target = outputFile(context, "protected", "pdf")
            document.save(target)
            target
        }

    private fun unlockPdf(context: Context, uri: Uri, password: String): File = withPdf(context, uri, password) { document ->
        val target = outputFile(context, "unlocked", "pdf")
        document.save(target)
        target
    }

    private fun removePages(context: Context, uri: Uri, options: PdfOperationOptions): File =
        mutatePdf(context, uri, options.password, "pages_removed") { document ->
            val indices = parsePageSelection(options.pages, document.numberOfPages)
            require(indices.size < document.numberOfPages) { "You cannot remove every page." }
            indices.sortedDescending().forEach(document::removePage)
        }

    private fun parsePageSelection(value: String, pageCount: Int): List<Int> {
        require(value.isNotBlank()) { "Enter page numbers, for example 2,4-6." }
        val pages = mutableSetOf<Int>()
        value.split(',').map(String::trim).filter(String::isNotEmpty).forEach { part ->
            val range = part.split('-').map(String::trim)
            require(range.size in 1..2) { "Invalid page range: $part" }
            val first = range[0].toIntOrNull() ?: error("Invalid page number: $part")
            val last = range.getOrNull(1)?.toIntOrNull() ?: first
            require(first >= 1 && last >= first && last <= pageCount) { "Page range $part is outside 1-$pageCount." }
            for (page in first..last) pages += page - 1
        }
        return pages.toList()
    }

    private fun imagesToPdf(context: Context, uris: List<Uri>): File {
        require(uris.isNotEmpty()) { "Select one or more images." }
        val pdf = PdfDocument()
        try {
            uris.forEachIndexed { index, uri ->
                val bitmap = decodeImage(context, uri)
                try {
                    val pageWidth = bitmap.width.coerceAtMost(MAX_RENDER_DIMENSION)
                    val pageHeight = bitmap.height.coerceAtMost(MAX_RENDER_DIMENSION)
                    val pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, index + 1).create()
                    val page = pdf.startPage(pageInfo)
                    page.canvas.drawColor(Color.WHITE)
                    page.canvas.drawBitmap(bitmap, null, Rect(0, 0, pageWidth, pageHeight), Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG))
                    pdf.finishPage(page)
                } finally {
                    bitmap.recycle()
                }
            }
            val target = outputFile(context, "images_to_pdf", "pdf")
            FileOutputStream(target).use(pdf::writeTo)
            return target
        } finally {
            pdf.close()
        }
    }

    private fun decodeImage(context: Context, uri: Uri): Bitmap {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            ?: error("Cannot open selected image.")
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "The selected image is invalid." }
        val sample = calculateSample(bounds.outWidth, bounds.outHeight, MAX_RENDER_DIMENSION)
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, options) }
            ?: error("Cannot decode selected image.")
        val rotation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { ExifInterface(it).rotationDegrees } ?: 0
        }.getOrDefault(0)
        if (rotation == 0) return decoded
        val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
        val rotated = Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, matrix, true)
        if (rotated !== decoded) decoded.recycle()
        return rotated
    }

    private fun calculateSample(width: Int, height: Int, target: Int): Int {
        var sample = 1
        while (max(width / sample, height / sample) > target) sample *= 2
        return sample
    }

    private fun pdfToImages(context: Context, uri: Uri): File {
        val target = outputFile(context, "pdf_pages", "zip")
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Cannot open PDF.")
        descriptor.use { parcel ->
            PdfRenderer(parcel).use { renderer ->
                require(renderer.pageCount <= 500) { "This export is limited to 500 pages at a time." }
                ZipOutputStream(FileOutputStream(target)).use { zip ->
                    for (index in 0 until renderer.pageCount) {
                        renderer.openPage(index).use { page ->
                            val scale = min(2.5f, MAX_RENDER_DIMENSION.toFloat() / max(page.width, page.height).coerceAtLeast(1))
                            val bitmap = Bitmap.createBitmap(
                                (page.width * scale).roundToInt().coerceAtLeast(1),
                                (page.height * scale).roundToInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888
                            )
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                zip.putNextEntry(ZipEntry("page_${(index + 1).toString().padStart(3, '0')}.png"))
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)
                                zip.closeEntry()
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            }
        }
        return target
    }

    private fun rasterizePdf(
        context: Context,
        uri: Uri,
        dpi: Int,
        jpegQuality: Float,
        grayscale: Boolean,
        prefix: String
    ): File {
        val sourceFile = copyUriToFile(context, uri)
        try {
            PDDocument.load(sourceFile).use { source ->
                val output = PDDocument()
                try {
                    val renderer = com.tom_roush.pdfbox.rendering.PDFRenderer(source)
                    require(source.numberOfPages <= 300) { "This operation is limited to 300 pages at a time." }
                    for (index in 0 until source.numberOfPages) {
                        var bitmap = renderer.renderImageWithDPI(index, dpi.toFloat())
                        if (bitmap.width.toLong() * bitmap.height.toLong() > MAX_IMAGE_PIXELS) {
                            val ratio = kotlin.math.sqrt(MAX_IMAGE_PIXELS.toDouble() / (bitmap.width.toLong() * bitmap.height.toLong())).toFloat()
                            val scaled = Bitmap.createScaledBitmap(bitmap, (bitmap.width * ratio).roundToInt(), (bitmap.height * ratio).roundToInt(), true)
                            bitmap.recycle()
                            bitmap = scaled
                        }
                        if (grayscale) {
                            val gray = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
                            val canvas = Canvas(gray)
                            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                                colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                            }
                            canvas.drawBitmap(bitmap, 0f, 0f, paint)
                            bitmap.recycle()
                            bitmap = gray
                        }
                        try {
                            val pageWidth = bitmap.width * 72f / dpi
                            val pageHeight = bitmap.height * 72f / dpi
                            val page = PDPage(PDRectangle(pageWidth, pageHeight))
                            output.addPage(page)
                            val image: PDImageXObject = JPEGFactory.createFromImage(output, bitmap, jpegQuality.coerceIn(0.1f, 1f))
                            PDPageContentStream(output, page).use { stream ->
                                stream.drawImage(image, 0f, 0f, pageWidth, pageHeight)
                            }
                        } finally {
                            bitmap.recycle()
                        }
                    }
                    val target = outputFile(context, prefix, "pdf")
                    output.save(target)
                    return target
                } finally {
                    output.close()
                }
            }
        } finally {
            sourceFile.delete()
        }
    }

    private fun extractImages(context: Context, uri: Uri, password: String): File =
        withPdf(context, uri, password) { document ->
            val target = outputFile(context, "embedded_images", "zip")
            var imageIndex = 0
            val seen = mutableSetOf<String>()
            ZipOutputStream(FileOutputStream(target)).use { zip ->
                document.pages.forEach { page ->
                    val resources = page.resources ?: return@forEach
                    for (name in resources.xObjectNames) {
                        val xObject = runCatching { resources.getXObject(name) }.getOrNull()
                        if (xObject is PDImageXObject) {
                            val bitmap = xObject.image
                            val hash = "${bitmap.width}x${bitmap.height}:${bitmap.allocationByteCount}"
                            if (seen.add(hash)) {
                                zip.putNextEntry(ZipEntry("image_${(++imageIndex).toString().padStart(3, '0')}.png"))
                                bitmap.compress(Bitmap.CompressFormat.PNG, 100, zip)
                                zip.closeEntry()
                            }
                            bitmap.recycle()
                        }
                    }
                }
            }
            require(imageIndex > 0) { "No embedded images were found in this PDF." }
            target
        }

    private fun zipPdfs(context: Context, uris: List<Uri>): File {
        require(uris.isNotEmpty()) { "Select at least one PDF." }
        val target = outputFile(context, "pdf_documents", "zip")
        ZipOutputStream(FileOutputStream(target)).use { zip ->
            uris.forEachIndexed { index, uri ->
                zip.putNextEntry(ZipEntry("document_${(index + 1).toString().padStart(2, '0')}.pdf"))
                context.contentResolver.openInputStream(uri)?.use { it.copyTo(zip) }
                    ?: error("Cannot read one of the selected PDFs.")
                zip.closeEntry()
            }
        }
        return target
    }

    private fun contactSheet(context: Context, uri: Uri): File {
        val output = PdfDocument()
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Cannot open PDF.")
        try {
            PdfRenderer(descriptor).use { renderer ->
                require(renderer.pageCount <= 500) { "Contact sheets are limited to 500 pages." }
                val pagesPerSheet = 9
                val cellWidth = 190
                val cellHeight = 238
                val margin = 14
                val sheetWidth = margin * 2 + cellWidth * 3
                val sheetHeight = margin * 2 + cellHeight * 3
                for (start in 0 until renderer.pageCount step pagesPerSheet) {
                    val info = PdfDocument.PageInfo.Builder(sheetWidth, sheetHeight, start / pagesPerSheet + 1).create()
                    val page = output.startPage(info)
                    page.canvas.drawColor(Color.WHITE)
                    val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
                    for (slot in 0 until pagesPerSheet) {
                        val pageIndex = start + slot
                        if (pageIndex >= renderer.pageCount) break
                        val row = slot / 3
                        val column = slot % 3
                        val left = margin + column * cellWidth
                        val top = margin + row * cellHeight
                        renderer.openPage(pageIndex).use { pdfPage ->
                            val scale = min(
                                (cellWidth - 12).toFloat() / pdfPage.width.coerceAtLeast(1),
                                (cellHeight - 34).toFloat() / pdfPage.height.coerceAtLeast(1)
                            )
                            val bitmap = Bitmap.createBitmap(
                                (pdfPage.width * scale).roundToInt().coerceAtLeast(1),
                                (pdfPage.height * scale).roundToInt().coerceAtLeast(1),
                                Bitmap.Config.ARGB_8888
                            )
                            try {
                                bitmap.eraseColor(Color.WHITE)
                                pdfPage.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val destination = Rect(left + 6, top + 6, left + cellWidth - 6, top + cellHeight - 30)
                                page.canvas.drawBitmap(bitmap, null, destination, paint)
                                paint.color = Color.DKGRAY
                                paint.textSize = 12f
                                page.canvas.drawText("Page ${pageIndex + 1}", left + 8f, (top + cellHeight - 12).toFloat(), paint)
                                paint.color = Color.BLACK
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                    output.finishPage(page)
                }
            }
            val target = outputFile(context, "pdf_contact_sheet", "pdf")
            FileOutputStream(target).use(output::writeTo)
            return target
        } finally {
            descriptor.close()
            output.close()
        }
    }

    private fun comparePdfs(context: Context, first: Uri, second: Uri): File {
        val report = StringBuilder()
        val fdA = context.contentResolver.openFileDescriptor(first, "r") ?: error("Cannot open the first PDF.")
        val fdB = context.contentResolver.openFileDescriptor(second, "r") ?: error("Cannot open the second PDF.")
        try {
            PdfRenderer(fdA).use { a ->
                PdfRenderer(fdB).use { b ->
                    report.appendLine("PDF comparison")
                    report.appendLine("First document: ${a.pageCount} pages")
                    report.appendLine("Second document: ${b.pageCount} pages")
                    report.appendLine()
                    val count = min(a.pageCount, b.pageCount)
                    for (index in 0 until count) {
                        val left = renderSmallPage(a, index)
                        val right = renderSmallPage(b, index)
                        try {
                            val difference = meanAbsoluteDifference(left, right)
                            report.appendLine("Page ${index + 1}: ${String.format(Locale.US, "%.2f", difference)}% mean pixel difference")
                        } finally {
                            left.recycle()
                            right.recycle()
                        }
                    }
                    if (a.pageCount != b.pageCount) report.appendLine("Page-count mismatch: ${kotlin.math.abs(a.pageCount - b.pageCount)} page(s).")
                }
            }
        } finally {
            fdA.close()
            fdB.close()
        }
        val target = outputFile(context, "pdf_comparison", "txt")
        target.writeText(report.toString(), Charsets.UTF_8)
        return target
    }

    private fun renderSmallPage(renderer: PdfRenderer, index: Int): Bitmap =
        renderer.openPage(index).use { page ->
            val maxDimension = 256f
            val scale = min(maxDimension / page.width.coerceAtLeast(1), maxDimension / page.height.coerceAtLeast(1))
            val bitmap = Bitmap.createBitmap(
                (page.width * scale).roundToInt().coerceAtLeast(1),
                (page.height * scale).roundToInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap
        }

    private fun meanAbsoluteDifference(first: Bitmap, second: Bitmap): Float {
        val width = min(first.width, second.width)
        val height = min(first.height, second.height)
        val a = IntArray(width * height)
        val b = IntArray(width * height)
        first.getPixels(a, 0, width, 0, 0, width, height)
        second.getPixels(b, 0, width, 0, 0, width, height)
        var sum = 0L
        for (index in a.indices) {
            val red = kotlin.math.abs(Color.red(a[index]) - Color.red(b[index]))
            val green = kotlin.math.abs(Color.green(a[index]) - Color.green(b[index]))
            val blue = kotlin.math.abs(Color.blue(a[index]) - Color.blue(b[index]))
            sum += red + green + blue
        }
        return (sum.toDouble() / (a.size.coerceAtLeast(1) * 3.0 * 255.0) * 100.0).toFloat()
    }

    private fun searchableOcrPdf(context: Context, uri: Uri, options: PdfOperationOptions): File {
        val descriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: error("Cannot open PDF.")
        val target = outputFile(context, "searchable_ocr", "pdf")
        val document = PDDocument()
        try {
            PdfRenderer(descriptor).use { renderer ->
                require(renderer.pageCount <= 120) { "OCR is limited to 120 pages per run." }
                val font = PDType1Font.HELVETICA
                for (index in 0 until renderer.pageCount) {
                    val pair = renderSmallPageForOcr(renderer, index)
                    val bitmap = pair.first
                    val pageWidth = pair.second.first
                    val pageHeight = pair.second.second
                    try {
                        val extracted = MlKitOcr.recognize(bitmap, 0, options.ocrLanguages).trim()
                        val page = PDPage(PDRectangle(pageWidth, pageHeight))
                        document.addPage(page)
                        val image = LosslessFactory.createFromImage(document, bitmap)
                        PDPageContentStream(document, page).use { stream ->
                            stream.drawImage(image, 0f, 0f, pageWidth, pageHeight)
                            if (extracted.isNotBlank()) {
                                stream.beginText()
                                stream.setRenderingMode(RenderingMode.NEITHER)
                                stream.setFont(font, 8f)
                                stream.newLineAtOffset(14f, pageHeight - 20f)
                                extracted.split(Regex("\\s+")).filter(String::isNotBlank).take(4000).forEach { token ->
                                    val safe = cleanPdfText(token)
                                    if (safe.isNotBlank()) stream.showText(safe.take(80) + " ")
                                }
                                stream.endText()
                            }
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
            }
            document.save(target)
            return target
        } finally {
            descriptor.close()
            document.close()
        }
    }

    private fun renderSmallPageForOcr(renderer: PdfRenderer, index: Int): Pair<Bitmap, Pair<Float, Float>> =
        renderer.openPage(index).use { page ->
            val maxDimension = 1800f
            val scale = min(1f, maxDimension / max(page.width, page.height).coerceAtLeast(1))
            val bitmap = Bitmap.createBitmap(
                (page.width * scale).roundToInt().coerceAtLeast(1),
                (page.height * scale).roundToInt().coerceAtLeast(1),
                Bitmap.Config.ARGB_8888
            )
            bitmap.eraseColor(Color.WHITE)
            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmap to Pair(page.width.toFloat() * 72f / 72f, page.height.toFloat() * 72f / 72f)
        }

    private fun cleanPdfText(value: String): String = value
        .replace(Regex("[^\\x20-\\x7E]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
