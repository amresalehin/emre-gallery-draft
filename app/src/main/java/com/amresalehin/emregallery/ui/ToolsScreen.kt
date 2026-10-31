package com.amresalehin.emregallery.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class ToolGroup(
    val title: String,
    val icon: ImageVector,
    val tools: List<String>
)

private val toolGroups = listOf(
    ToolGroup(
        "Image editing",
        Icons.Default.Edit,
        listOf(
            "Single-image editing",
            "Batch image processing",
            "Crop and free-rotation crop",
            "Perspective correction",
            "Aspect-ratio crop and custom presets",
            "Shape-based crop and masks",
            "Resize by width and height",
            "Adaptive and aspect-ratio-preserving resize",
            "Maximum-dimension resize",
            "Center crop",
            "Background blur while resizing",
            "Background colour customization",
            "Rotate and flip",
            "Image stitching",
            "Image stacking",
            "Image splitting",
            "Batch image cutting",
            "Raster image tracing to SVG",
            "Before/after preview",
            "Undo and redo history"
        )
    ),
    ToolGroup(
        "Filters and effects",
        Icons.Default.Tune,
        listOf(
            "500+ filters and effects",
            "Brightness, contrast and exposure",
            "Saturation, vibrance and hue",
            "RGB adjustment and white balance",
            "Gamma, highlights and shadows",
            "Sharpness and unsharp mask",
            "Monochrome, black and white, sepia and inversion",
            "Gaussian, box, stack, median and motion blur",
            "Zoom, bilateral and fast blur",
            "Oil painting, watercolour and sketch",
            "Cartoon, emboss and posterization",
            "Halftone, crosshatch and glitch",
            "Pixelation, mosaic and dithering",
            "Neon, vintage, film, Polaroid and Cyberpunk",
            "Swirl, bulge, pinch and sphere refraction",
            "Kaleidoscope, mirror and glass distortion",
            "Vignette and geometric effects",
            "Convolution filters",
            "Edge detection, Sobel and Laplacian",
            "CLAHE and histogram equalization",
            "Colour matrices and replacement",
            "LUT processing and import/export",
            "Tone mapping and noise reduction",
            "Filter chains and custom filter recipes"
        )
    ),
    ToolGroup(
        "AI tools",
        Icons.Default.AutoAwesome,
        listOf(
            "AI upscaling",
            "Background removal",
            "JPEG artifact reduction (DeJPEG)",
            "AI denoising",
            "Photo enhancement and restoration",
            "Old-photo colorization",
            "Anime enhancement",
            "Style transfer",
            "AI image detection",
            "Depth estimation",
            "Depth maps, masks and cutouts",
            "Portrait focus and background blur",
            "Lens blur and cinematic depth grading",
            "Relighting and fog effects",
            "Normal maps and stereo effects",
            "Depth-edge effects",
            "Background models: U2Net, U2NetP, RMBG, InSPyReNet",
            "Background models: BiRefNet, ISNet, YOLO and MODNet",
            "MobileSAM point-based subject selection",
            "ML Kit segmentation",
            "Model downloads, verification and management"
        )
    ),
    ToolGroup(
        "Drawing and annotation",
        Icons.Default.Brush,
        listOf(
            "Freehand pen and brush",
            "Flood fill and spray brush",
            "Neon brush and highlighter",
            "Text and sticker insertion",
            "Image brush and filter brush",
            "Warp, move, grow, shrink, swirl and mix brushes",
            "Privacy blur and pixelation painting",
            "Spot healing",
            "AI-assisted generative inpainting",
            "Automatic face detection and masking",
            "Arrows, lines and double arrows",
            "Rectangles, circles, triangles and polygons",
            "Stars and lasso selection",
            "Dashed, dotted, zigzag and stamped lines",
            "Markup layers for text, images and shapes"
        )
    ),
    ToolGroup(
        "Watermark and privacy",
        Icons.Default.Security,
        listOf(
            "Text watermark",
            "Repeating text watermark",
            "Image and logo watermark",
            "Timestamp and stamp watermark",
            "Digital watermarking through steganography",
            "Face masking",
            "Privacy blur",
            "Pixelation-based censorship",
            "File encryption and decryption"
        )
    ),
    ToolGroup(
        "OCR and text extraction",
        Icons.Default.TextFields,
        listOf(
            "Text extraction from images",
            "Tesseract OCR",
            "PaddleOCR v5/v6",
            "Fast, Standard and Best recognition modes",
            "Recognition in 120+ languages",
            "Multiple languages simultaneously",
            "Custom OCR engine settings",
            "Page segmentation configuration",
            "Batch OCR processing",
            "Export extracted text",
            "Write OCR results into EXIF",
            "Searchable PDFs with an OCR text layer"
        )
    ),
    ToolGroup(
        "PDF and documents",
        Icons.Default.PictureAsPdf,
        listOf(
            "Document scanning",
            "PDF to images and images to PDF",
            "PDF preview and printing",
            "Merge and split PDFs",
            "Rotate and rearrange pages",
            "Page numbering",
            "Watermarks and signatures",
            "PDF compression and grayscale",
            "Repair and sanitize PDFs",
            "Password protection and unlocking",
            "PDF metadata management",
            "Remove selected pages",
            "Crop and resize pages",
            "Flatten document layers",
            "Extract embedded images",
            "ZIP PDF documents",
            "PDF OCR",
            "Remove PDF annotations",
            "PDF contact sheets",
            "Compare PDF documents"
        )
    ),
    ToolGroup(
        "Format conversion",
        Icons.Default.Image,
        listOf(
            "JPEG and JPG",
            "PNG: lossless and lossy",
            "AVIF, HEIC and HEIF",
            "WebP and animated WebP",
            "JPEG XL (JXL)",
            "TIFF and QOI",
            "PSD and RAW rasterization",
            "SVG rasterization",
            "JPEG 2000 (JP2/J2K)",
            "ICO generation with multiple resolutions",
            "GIF and animated formats",
            "OxiPNG and ImageQuant",
            "MozJPEG and Jpegli",
            "Telegram sticker PNG conversion",
            "GIF to images and images to GIF",
            "GIF to WebP and WebP to GIF",
            "APNG and JXL conversion",
            "Animated format conversion",
            "Combine multiple animations",
            "Compression quality and output-size controls"
        )
    ),
    ToolGroup(
        "Collage and creation",
        Icons.Default.GridView,
        listOf(
            "Collages from 1–20 images",
            "310+ collage layouts",
            "Photomosaic generation",
            "Image stitching and panorama",
            "Image stacking",
            "Texture generation",
            "Procedural pattern generation",
            "3D geometric pattern generation",
            "Wallpaper creation",
            "ASCII art generation"
        )
    ),
    ToolGroup(
        "Barcode and QR",
        Icons.Default.QrCode,
        listOf(
            "Scan QR codes",
            "Scan Aztec, Codabar, Code 39 and Code 93",
            "Scan Code 128 and Data Matrix",
            "Scan EAN-8, EAN-13, ITF, PDF417 and UPC",
            "Generate QR codes and barcodes",
            "Plain text and URL payloads",
            "Wi-Fi credentials",
            "Email, phone and SMS",
            "Geolocation",
            "Contacts (vCard)",
            "Calendar events",
            "Export as raster image or SVG"
        )
    ),
    ToolGroup(
        "Colour and analysis",
        Icons.Default.Palette,
        listOf(
            "Colour picker",
            "Palette generation and extraction",
            "33,000+ colour library",
            "Material You colour schemes",
            "Gradient and mesh-gradient generation",
            "Gradient overlay",
            "Colour mixing and conversion",
            "Colour harmonies and shading",
            "Palette import/export in 41 formats",
            "Tone, RGB, CMYK and LAB curves",
            "Hue versus saturation, hue and luminance",
            "Colour wheels",
            "RGB and brightness histograms",
            "Camera-style RGB histogram",
            "Waveform scopes",
            "Vectorscope"
        )
    ),
    ToolGroup(
        "Image comparison",
        Icons.Default.CompareArrows,
        listOf(
            "Slide comparison",
            "Side-by-side comparison",
            "Transparency comparison",
            "Tap-to-toggle comparison",
            "Before/after GIF export",
            "Horizontal and vertical comparison animations",
            "Optional comparison labels",
            "Pixel-by-pixel comparison",
            "SSIM, AE, MAE, NCC, PSNR and RMSE metrics"
        )
    ),
    ToolGroup(
        "Photo discovery",
        Icons.Default.Search,
        listOf(
            "Embedded and system photo picker",
            "Emre gallery selection and file picker",
            "Camera input",
            "Select all images in a folder",
            "Filename search",
            "Multilingual semantic image search",
            "Find visually similar images from a reference",
            "Automatic image categorization",
            "Custom categories from descriptions or reference images",
            "Adjustable similarity thresholds",
            "Live category previews",
            "Automatic AI indexing",
            "Offline semantic search with downloaded models",
            "People groups and naming",
            "Face covers and matching thresholds",
            "Merge people groups",
            "Exclude selected photographs"
        )
    ),
    ToolGroup(
        "Code preview",
        Icons.Default.Code,
        listOf(
            "Code-to-image cards",
            "Syntax highlighting for 190+ languages",
            "256 syntax themes",
            "30 gradient backgrounds",
            "Custom colours and transparency",
            "Canvas spacing and rounded corners",
            "Rotation and window controls",
            "Line numbers and card shadows"
        )
    ),
    ToolGroup(
        "Shader Studio",
        Icons.Default.DeveloperMode,
        listOf(
            "Custom fragment shaders",
            "GLSL editor and syntax highlighting",
            "Shader validation",
            "Live preview",
            "Configurable shader parameters",
            "Import and export shader presets",
            "Share and manage saved shaders"
        )
    ),
    ToolGroup(
        "Screenshot and social export",
        Icons.Default.CropLandscape,
        listOf(
            "Solid and gradient backgrounds",
            "Custom padding and aspect ratios",
            "Rounded corners and shadows",
            "Instagram posts, Stories, Reels and profile images",
            "Facebook posts, Stories and covers",
            "YouTube thumbnails, banners and channel images",
            "TikTok portrait, landscape and square",
            "X posts, headers and profile images",
            "LinkedIn posts, covers and article images",
            "Pinterest Pins",
            "Reddit banners and community icons",
            "Snapchat Stories and advertisements",
            "Behance project images",
            "GitHub social previews",
            "Telegram stickers and custom emoji",
            "Discord banners and avatars",
            "Twitch banners and thumbnails"
        )
    ),
    ToolGroup(
        "Files and archives",
        Icons.Default.FolderZip,
        listOf(
            "Create and extract ZIP, 7Z and TAR",
            "CPIO, AR and ISO 9660",
            "GZIP, BZIP2, XZ and ZSTD",
            "LZ4, LZIP, LZMA and Brotli",
            "Password-protected ZIP and 7Z",
            "Encrypted archive extraction",
            "RAR and CBR extraction",
            "Streaming archive creation",
            "Files to ZIP",
            "Pattern-based batch renaming",
            "Date-based and sequential filenames",
            "Extension handling and random filenames",
            "Checksum-based filenames",
            "Exact duplicate detection",
            "Visually similar photo detection",
            "Duplicate retention recommendations"
        )
    ),
    ToolGroup(
        "Other utilities",
        Icons.Default.MoreHoriz,
        listOf(
            "64 checksum and hashing algorithms",
            "Base64 encoding and decoding",
            "File encryption and decryption",
            "Audio album-cover extraction",
            "Wallpaper export",
            "ASCII art",
            "User-initiated internet image import",
            "Searchable Help and Tips",
            "On-device tool assistant",
            "Contextual tool navigation",
            "Android Quick Settings tiles",
            "Screenshot actions",
            "Privacy-conscious usage statistics",
            "Processing logs",
            "EXIF editing and deletion"
        )
    )
)

@Composable
fun ToolsScreen(onBack: () -> Unit) {
    var query by remember { mutableStateOf("") }
    var expandedGroups by remember { mutableStateOf(setOf("Image editing")) }

    val visibleGroups = remember(query) {
        toolGroups.mapNotNull { group ->
            val matches = if (query.isBlank()) {
                group.tools
            } else {
                group.tools.filter { it.contains(query, ignoreCase = true) }
            }
            when {
                query.isBlank() -> group
                group.title.contains(query, ignoreCase = true) -> group
                matches.isNotEmpty() -> group.copy(tools = matches)
                else -> null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Back to settings")
            }
            Text(
                "Tools",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(48.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            border = BorderStroke(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (query.isEmpty()) {
                        Text(
                            "Search tools",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(
                        onClick = { query = "" },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 16.dp,
                bottom = 28.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (visibleGroups.isEmpty()) {
                item {
                    Text(
                        "No tools found",
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(visibleGroups, key = { it.title }) { group ->
                val expanded = query.isNotBlank() || group.title in expandedGroups
                ElevatedCard(
                    onClick = {
                        if (query.isBlank()) {
                            expandedGroups = if (group.title in expandedGroups) {
                                expandedGroups - group.title
                            } else {
                                expandedGroups + group.title
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    )
                ) {
                    Column(Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 15.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(11.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        group.icon,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.width(13.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    group.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${group.tools.size} tools",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(
                                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = if (expanded) "Collapse" else "Expand",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        if (expanded) {
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 10.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                group.tools.forEach { tool ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = 34.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Surface(
                                            shape = CircleShape,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f),
                                            modifier = Modifier.size(4.dp)
                                        ) {}
                                        Spacer(Modifier.width(11.dp))
                                        Text(
                                            tool,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
