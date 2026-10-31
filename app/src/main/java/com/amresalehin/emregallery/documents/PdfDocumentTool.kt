package com.amresalehin.emregallery.documents

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.ui.graphics.vector.ImageVector

enum class PdfDocumentTool(
    val title: String,
    val description: String,
    val icon: ImageVector,
    val operations: List<PdfOperation>
) {
    SCAN(
        "Document scan",
        "Capture pages and create a PDF",
        Icons.Default.DocumentScanner,
        listOf(PdfOperation.IMAGES_TO_PDF)
    ),
    CONVERT(
        "PDF / image conversion",
        "Export PDF pages or create a PDF from images",
        Icons.Default.Image,
        listOf(PdfOperation.PDF_TO_IMAGES, PdfOperation.IMAGES_TO_PDF)
    ),
    PREVIEW_PRINT(
        "Preview and print",
        "Open pages, navigate and send the PDF to print",
        Icons.Default.Visibility,
        emptyList()
    ),
    MERGE_SPLIT(
        "Merge / split PDFs",
        "Combine documents or split into one-page PDFs",
        Icons.Default.CallSplit,
        listOf(PdfOperation.MERGE_PDFS, PdfOperation.SPLIT_PDF)
    ),
    ROTATE_REORDER(
        "Rotate / rearrange pages",
        "Rotate all pages or reverse page order",
        Icons.Default.RotateRight,
        listOf(PdfOperation.ROTATE_PDF, PdfOperation.REARRANGE_PDF)
    ),
    PAGE_NUMBERS(
        "Page numbering",
        "Add page numbers to every page",
        Icons.Default.FormatListNumbered,
        listOf(PdfOperation.PAGE_NUMBERS)
    ),
    WATERMARK_SIGNATURE(
        "Watermark / signature",
        "Stamp text across pages or add a signature line",
        Icons.Default.Draw,
        listOf(PdfOperation.WATERMARK, PdfOperation.SIGNATURE)
    ),
    COMPRESS_GRAYSCALE(
        "Compress / grayscale",
        "Reduce file size or create a grayscale copy",
        Icons.Default.Compress,
        listOf(PdfOperation.COMPRESS, PdfOperation.GRAYSCALE)
    ),
    REPAIR_SANITIZE(
        "Repair / sanitize",
        "Re-save readable PDFs or remove active content and metadata",
        Icons.Default.CleaningServices,
        listOf(PdfOperation.REPAIR, PdfOperation.SANITIZE)
    ),
    PROTECT_UNLOCK(
        "Protect / unlock",
        "Add password encryption or save a password-protected PDF unencrypted",
        Icons.Default.Lock,
        listOf(PdfOperation.PROTECT, PdfOperation.UNLOCK)
    ),
    METADATA(
        "PDF metadata",
        "Edit title, author and subject fields",
        Icons.Default.Info,
        listOf(PdfOperation.EDIT_METADATA)
    ),
    REMOVE_PAGES(
        "Remove pages",
        "Delete selected page numbers or ranges",
        Icons.Default.Delete,
        listOf(PdfOperation.REMOVE_PAGES)
    ),
    CROP_RESIZE(
        "Crop / resize pages",
        "Trim page margins or standardise page size",
        Icons.Default.Crop,
        listOf(PdfOperation.CROP_PAGES, PdfOperation.RESIZE_PAGES)
    ),
    FLATTEN(
        "Flatten document",
        "Render document layers into static page content",
        Icons.Default.Layers,
        listOf(PdfOperation.FLATTEN)
    ),
    EXTRACT_IMAGES(
        "Extract embedded images",
        "Collect images found inside a PDF into a ZIP",
        Icons.Default.ImageSearch,
        listOf(PdfOperation.EXTRACT_IMAGES)
    ),
    ZIP_PDFS(
        "ZIP PDF documents",
        "Pack selected PDFs into one ZIP archive",
        Icons.Default.FolderZip,
        listOf(PdfOperation.ZIP_PDFS)
    ),
    OCR(
        "PDF OCR",
        "Recognise text with Emre's on-device ML Kit OCR",
        Icons.Default.TextFields,
        listOf(PdfOperation.OCR_PDF)
    ),
    REMOVE_ANNOTATIONS(
        "Remove annotations",
        "Remove comments, markups and annotation objects",
        Icons.Default.Comment,
        listOf(PdfOperation.REMOVE_ANNOTATIONS)
    ),
    CONTACT_SHEET(
        "PDF contact sheet",
        "Create a page-thumbnail overview PDF",
        Icons.Default.GridView,
        listOf(PdfOperation.CONTACT_SHEET)
    ),
    COMPARE(
        "Compare PDFs",
        "Compare matching pages and export a difference report",
        Icons.Default.CompareArrows,
        listOf(PdfOperation.COMPARE_PDFS)
    );

    val initialOperation: PdfOperation
        get() = operations.firstOrNull() ?: PdfOperation.PDF_TO_IMAGES
}
