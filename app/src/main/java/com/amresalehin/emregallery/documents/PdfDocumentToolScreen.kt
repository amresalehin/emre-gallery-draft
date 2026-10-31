package com.amresalehin.emregallery.documents

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.google.mlkit.vision.documentscanner.GmsDocumentScanning
import com.google.mlkit.vision.documentscanner.GmsDocumentScannerOptions
import com.google.mlkit.vision.documentscanner.GmsDocumentScanningResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PdfDocumentToolScreen(
    tool: PdfDocumentTool,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var operation by remember(tool) { mutableStateOf(tool.initialOperation) }
    var pageSelection by remember(tool) { mutableStateOf("") }
    var cropMarginPercent by remember(tool) { mutableStateOf("3.5") }
    var password by remember(tool) { mutableStateOf("") }
    var ownerPassword by remember(tool) { mutableStateOf("") }
    var watermarkText by remember(tool) { mutableStateOf("Emre Gallery") }
    var signatureText by remember(tool) { mutableStateOf("") }
    var metadataTitle by remember(tool) { mutableStateOf("") }
    var metadataAuthor by remember(tool) { mutableStateOf("") }
    var metadataSubject by remember(tool) { mutableStateOf("") }
    var status by remember { mutableStateOf<String?>(null) }
    var isProcessing by remember { mutableStateOf(false) }
    var outputFile by remember { mutableStateOf<File?>(null) }
    var previewUri by remember { mutableStateOf<Uri?>(null) }
    var previewPage by remember { mutableIntStateOf(0) }
    var previewPageCount by remember { mutableIntStateOf(0) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isPreviewLoading by remember { mutableStateOf(false) }

    val pdfScanner = remember {
        GmsDocumentScanning.getClient(
            GmsDocumentScannerOptions.Builder()
                .setGalleryImportAllowed(true)
                .setPageLimit(100)
                .setResultFormats(
                    GmsDocumentScannerOptions.RESULT_FORMAT_PDF,
                    GmsDocumentScannerOptions.RESULT_FORMAT_JPEG
                )
                .setScannerMode(GmsDocumentScannerOptions.SCANNER_MODE_FULL)
                .build()
        )
    }

    fun showError(error: Throwable) {
        status = error.message?.takeIf(String::isNotBlank) ?: "The operation could not be completed."
    }

    fun startProcessing(uris: List<Uri>) {
        if (uris.isEmpty()) return
        if (operation == PdfOperation.COMPARE_PDFS && uris.size != 2) {
            status = "Select exactly two PDF files to compare."
            return
        }
        if (operation == PdfOperation.MERGE_PDFS && uris.size < 2) {
            status = "Select at least two PDFs to merge."
            return
        }
        if (operation == PdfOperation.ZIP_PDFS && uris.isEmpty()) {
            status = "Select at least one PDF."
            return
        }
        val options = PdfOperationOptions(
            pages = pageSelection,
            password = password,
            ownerPassword = ownerPassword,
            watermarkText = watermarkText,
            signatureText = signatureText,
            title = metadataTitle,
            author = metadataAuthor,
            subject = metadataSubject,
            cropMarginPercent = cropMarginPercent.toFloatOrNull() ?: 3.5f
        )
        scope.launch {
            isProcessing = true
            status = "Processing…"
            try {
                val result = withContext(Dispatchers.IO) {
                    PdfDocumentEngine.process(context.applicationContext, operation, uris, options)
                }
                outputFile = result
                status = "Ready to save: ${result.name}"
            } catch (error: Throwable) {
                showError(error)
            } finally {
                isProcessing = false
            }
        }
    }

    fun saveOutputToUri(uri: Uri?) {
        val file = outputFile ?: return
        if (uri == null) {
            status = "Save cancelled."
            outputFile = null
            file.delete()
            return
        }
        scope.launch {
            isProcessing = true
            status = "Saving…"
            try {
                withContext(Dispatchers.IO) {
                    PdfDocumentEngine.copyResultToUri(context.applicationContext, file, uri)
                }
                status = "Saved successfully."
                file.delete()
            } catch (error: Throwable) {
                showError(error)
            } finally {
                outputFile = null
                isProcessing = false
            }
        }
    }

    val pdfSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
        ::saveOutputToUri
    )
    val zipSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/zip"),
        ::saveOutputToUri
    )
    val textSaveLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
        ::saveOutputToUri
    )

    LaunchedEffect(outputFile) {
        val file = outputFile ?: return@LaunchedEffect
        when (file.extension.lowercase()) {
            "zip" -> zipSaveLauncher.launch(file.name)
            "txt" -> textSaveLauncher.launch(file.name)
            else -> pdfSaveLauncher.launch(file.name)
        }
    }

    val singlePdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            if (tool == PdfDocumentTool.PREVIEW_PRINT) {
                previewUri = uri
                previewPage = 0
            } else {
                startProcessing(listOf(uri))
            }
        }
    }
    val multiPdfPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) startProcessing(uris) }
    val multiImagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris -> if (uris.isNotEmpty()) startProcessing(uris) }

    val scannerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val scan = GmsDocumentScanningResult.fromActivityResultIntent(result.data)
            val scannedPdf = scan?.pdf?.uri
            if (scannedPdf != null) {
                scope.launch {
                    isProcessing = true
                    status = "Preparing scanned PDF…"
                    try {
                        val file = withContext(Dispatchers.IO) {
                            val output = File.createTempFile("emre_scan_", ".pdf", File(context.cacheDir, "pdf-tools").apply { mkdirs() })
                            context.contentResolver.openInputStream(scannedPdf)?.use { input ->
                                FileOutputStream(output).use(input::copyTo)
                            } ?: error("The scanner did not return a readable PDF.")
                            output
                        }
                        outputFile = file
                    } catch (error: Throwable) {
                        showError(error)
                    } finally {
                        isProcessing = false
                    }
                }
            } else {
                val scannedImages = scan?.pages?.mapNotNull { it.imageUri }.orEmpty()
                if (scannedImages.isNotEmpty()) {
                    operation = PdfOperation.IMAGES_TO_PDF
                    startProcessing(scannedImages)
                } else {
                    status = "The scanner returned no pages."
                }
            }
        } else {
            status = "Document scan cancelled."
        }
    }

    fun startScanner() {
        val activity = context.findActivity()
        if (activity == null) {
            status = "Document scanner could not access the current Activity."
            return
        }
        status = "Opening document scanner…"
        pdfScanner.getStartScanIntent(activity)
            .addOnSuccessListener { sender ->
                scannerLauncher.launch(IntentSenderRequest.Builder(sender).build())
            }
            .addOnFailureListener { error ->
                status = "Google Play services document scanner unavailable: ${error.localizedMessage ?: "scanner could not start"}. You can use Images to PDF instead."
            }
    }

    fun chooseInput() {
        when {
            tool == PdfDocumentTool.SCAN -> startScanner()
            operation == PdfOperation.IMAGES_TO_PDF -> multiImagePicker.launch(arrayOf("image/*"))
            operation == PdfOperation.MERGE_PDFS || operation == PdfOperation.ZIP_PDFS ||
                operation == PdfOperation.COMPARE_PDFS -> multiPdfPicker.launch(arrayOf("application/pdf"))
            else -> singlePdfPicker.launch(arrayOf("application/pdf"))
        }
    }

    LaunchedEffect(previewUri, previewPage) {
        val uri = previewUri ?: return@LaunchedEffect
        isPreviewLoading = true
        val previous = previewBitmap
        previewBitmap = null
        previous?.recycle()
        try {
            val result = withContext(Dispatchers.IO) {
                PdfDocumentEngine.renderPage(context.applicationContext, uri, previewPage)
            }
            previewBitmap = result.first
            previewPageCount = result.second
        } catch (error: Throwable) {
            status = error.message ?: "Cannot render this PDF page."
            previewUri = null
        } finally {
            isPreviewLoading = false
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            previewBitmap?.recycle()
            outputFile?.delete()
        }
    }

    if (previewUri != null) {
        PdfPreviewContent(
            uri = previewUri!!,
            pageIndex = previewPage,
            pageCount = previewPageCount,
            bitmap = previewBitmap,
            isLoading = isPreviewLoading,
            onBack = { previewUri = null },
            onPrevious = { previewPage = (previewPage - 1).coerceAtLeast(0) },
            onNext = { previewPage = (previewPage + 1).coerceAtMost((previewPageCount - 1).coerceAtLeast(0)) },
            onPrint = {
                val manager = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
                manager.print(
                    "Emre Gallery PDF",
                    PdfUriPrintAdapter(context.contentResolver, previewUri!!),
                    PrintAttributes.Builder().build()
                )
            }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to tools")
            }
            Column(Modifier.weight(1f)) {
                Text(tool.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(tool.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        if (tool.operations.size > 1) {
            Text("Choose operation", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                tool.operations.forEach { choice ->
                    FilterChip(
                        selected = operation == choice,
                        onClick = { operation = choice; status = null },
                        label = { Text(choice.displayName()) }
                    )
                }
            }
        }

        when (operation) {
            PdfOperation.REMOVE_PAGES -> OutlinedTextField(
                value = pageSelection,
                onValueChange = { pageSelection = it },
                label = { Text("Pages to remove") },
                supportingText = { Text("Example: 2,4-6. Page numbers start at 1.") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            PdfOperation.REARRANGE_PDF -> OutlinedTextField(
                value = pageSelection,
                onValueChange = { pageSelection = it },
                label = { Text("New page order") },
                supportingText = { Text("Enter every page number once, in the desired order. Example for a 3-page PDF: 3,1,2.") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            PdfOperation.CROP_PAGES -> OutlinedTextField(
                value = cropMarginPercent,
                onValueChange = { cropMarginPercent = it },
                label = { Text("Crop each edge (%)") },
                supportingText = { Text("Enter a value from 0 to 20. The same margin is cropped from all four edges.") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            PdfOperation.PROTECT -> {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("User password") },
                    supportingText = { Text("At least four characters.") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = ownerPassword,
                    onValueChange = { ownerPassword = it },
                    label = { Text("Owner password (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
            PdfOperation.UNLOCK -> OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Current PDF password") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )
            PdfOperation.WATERMARK -> OutlinedTextField(
                value = watermarkText,
                onValueChange = { watermarkText = it },
                label = { Text("Watermark text") },
                modifier = Modifier.fillMaxWidth()
            )
            PdfOperation.SIGNATURE -> OutlinedTextField(
                value = signatureText,
                onValueChange = { signatureText = it },
                label = { Text("Signature text") },
                supportingText = { Text("Adds a text signature line. This is not a cryptographic digital signature.") },
                modifier = Modifier.fillMaxWidth()
            )
            PdfOperation.EDIT_METADATA -> {
                OutlinedTextField(value = metadataTitle, onValueChange = { metadataTitle = it }, label = { Text("Title") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = metadataAuthor, onValueChange = { metadataAuthor = it }, label = { Text("Author") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = metadataSubject, onValueChange = { metadataSubject = it }, label = { Text("Subject") }, modifier = Modifier.fillMaxWidth())
            }
            else -> Unit
        }

        if (operation == PdfOperation.OCR_PDF) {
            Text(
                "OCR is performed on-device with Emre ML Kit. The current searchable PDF text layer uses a basic Latin font; non-Latin recognition may not be embedded correctly in the searchable layer.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (tool == PdfDocumentTool.SCAN) {
            Text(
                "Uses Google Play services' on-device document scanner. If it is unavailable on this device, use PDF / image conversion to create a PDF from captured images.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (operation == PdfOperation.COMPRESS || operation == PdfOperation.GRAYSCALE ||
            operation == PdfOperation.RESIZE_PAGES || operation == PdfOperation.FLATTEN) {
            Text(
                if (operation == PdfOperation.RESIZE_PAGES) "This fits rasterised pages onto A4 paper. It can remove vector quality, selectable text and form behaviour. Keep the original if you need those features."
                else "This operation rasterises pages. It can reduce editability, vector quality, selectable text or form behaviour. Keep the original if you need those features.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Button(
            onClick = { chooseInput() },
            enabled = !isProcessing,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (isProcessing) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
            }
            Text(
                when {
                    tool == PdfDocumentTool.SCAN -> "Start document scan"
                    operation == PdfOperation.IMAGES_TO_PDF -> "Choose images"
                    operation == PdfOperation.MERGE_PDFS || operation == PdfOperation.ZIP_PDFS -> "Choose PDF files"
                    operation == PdfOperation.COMPARE_PDFS -> "Choose two PDFs"
                    tool == PdfDocumentTool.PREVIEW_PRINT -> "Choose PDF to preview"
                    else -> "Choose PDF"
                }
            )
        }

        if (status != null) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh
            ) {
                Text(
                    status!!,
                    modifier = Modifier.padding(14.dp),
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Text(
            "Files are processed on this device. Results are saved only after you choose a destination.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PdfPreviewContent(
    uri: Uri,
    pageIndex: Int,
    pageCount: Int,
    bitmap: Bitmap?,
    isLoading: Boolean,
    onBack: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onPrint: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Default.Close, contentDescription = "Close PDF preview") }
            Text("PDF preview", modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("${if (pageCount > 0) pageIndex + 1 else 0} / $pageCount", style = MaterialTheme.typography.labelMedium)
            IconButton(onClick = onPrint) { Icon(Icons.Default.Print, contentDescription = "Print PDF") }
        }
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> CircularProgressIndicator()
                bitmap != null -> Image(
                    bitmap.asImageBitmap(),
                    contentDescription = "PDF page ${pageIndex + 1}",
                    modifier = Modifier.fillMaxSize()
                )
                else -> Text("No page preview available", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(onClick = onPrevious, enabled = pageIndex > 0) {
                Icon(Icons.Default.ArrowBack, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Previous")
            }
            Button(onClick = onNext, enabled = pageCount > 0 && pageIndex < pageCount - 1) {
                Text("Next")
                Spacer(Modifier.width(4.dp))
                Icon(Icons.Default.ArrowForward, contentDescription = null)
            }
        }
    }
}

private class PdfUriPrintAdapter(
    private val resolver: ContentResolver,
    private val uri: Uri
) : PrintDocumentAdapter() {
    private var pageCount: Int = 1

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal,
        callback: LayoutResultCallback,
        extras: Bundle?
    ) {
        if (cancellationSignal.isCanceled) {
            callback.onLayoutCancelled()
            return
        }
        try {
            val descriptor = resolver.openFileDescriptor(uri, "r") ?: error("Cannot open PDF for printing.")
            descriptor.use { PdfRenderer(it).use { renderer -> pageCount = renderer.pageCount } }
            val info = PrintDocumentInfo.Builder("Emre-Gallery.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(pageCount)
                .build()
            callback.onLayoutFinished(info, true)
        } catch (error: Throwable) {
            callback.onLayoutFailed(error.message ?: "Could not prepare PDF for printing.")
        }
    }

    override fun onWrite(
        pages: Array<out PageRange>,
        destination: ParcelFileDescriptor,
        cancellationSignal: CancellationSignal,
        callback: WriteResultCallback
    ) {
        try {
            if (cancellationSignal.isCanceled) {
                callback.onWriteCancelled()
                return
            }
            val input = resolver.openInputStream(uri) ?: error("Cannot read PDF for printing.")
            input.use { source ->
                val output = FileOutputStream(destination.fileDescriptor)
                source.copyTo(output)
                output.flush()
            }
            callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (error: Throwable) {
            callback.onWriteFailed(error.message ?: "Could not send PDF to the print service.")
        }
    }
}

private fun PdfOperation.displayName(): String = when (this) {
    PdfOperation.PDF_TO_IMAGES -> "PDF to images"
    PdfOperation.IMAGES_TO_PDF -> "Images to PDF"
    PdfOperation.MERGE_PDFS -> "Merge PDFs"
    PdfOperation.SPLIT_PDF -> "Split into single-page PDFs"
    PdfOperation.ROTATE_PDF -> "Rotate all pages 90°"
    PdfOperation.REARRANGE_PDF -> "Set custom page order"
    PdfOperation.PAGE_NUMBERS -> "Add page numbers"
    PdfOperation.WATERMARK -> "Add watermark"
    PdfOperation.SIGNATURE -> "Add text signature"
    PdfOperation.COMPRESS -> "Compress PDF"
    PdfOperation.GRAYSCALE -> "Convert to grayscale"
    PdfOperation.REPAIR -> "Repair / re-save PDF"
    PdfOperation.SANITIZE -> "Sanitize PDF"
    PdfOperation.PROTECT -> "Password protect"
    PdfOperation.UNLOCK -> "Unlock with password"
    PdfOperation.EDIT_METADATA -> "Edit metadata"
    PdfOperation.REMOVE_PAGES -> "Remove selected pages"
    PdfOperation.CROP_PAGES -> "Crop page margins"
    PdfOperation.RESIZE_PAGES -> "Resize pages to standard page size"
    PdfOperation.FLATTEN -> "Flatten pages"
    PdfOperation.EXTRACT_IMAGES -> "Extract embedded images"
    PdfOperation.ZIP_PDFS -> "ZIP selected PDFs"
    PdfOperation.OCR_PDF -> "Create searchable OCR PDF"
    PdfOperation.REMOVE_ANNOTATIONS -> "Remove annotations"
    PdfOperation.CONTACT_SHEET -> "Create contact sheet"
    PdfOperation.COMPARE_PDFS -> "Compare two PDFs"
}

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
