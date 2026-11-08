"""
Unified Python media extraction and download facade for Emre Gallery.
Delegates responsibilities to cohesive sub-modules:
- gallery_dl_support: Engine status, site catalog, cookiefile preparation, and extractor matching
- gallery_dl_extract_gdl: gallery-dl support inspection and gallery metadata extraction
- gallery_dl_extract_ytdlp: yt-dlp stream extraction and unified extract_media router
- gallery_dl_downloader: Resumable yt-dlp and gallery-dl file downloading
"""

from gallery_dl_support import (
    DEFAULT_STEALTH_UA,
    get_engine_status,
    is_ytdlp_available,
    is_gallery_dl_available,
    get_supported_sites,
    _prepare_cookiefile,
    _get_ytdlp_extractors,
    find_ytdlp_extractor,
    is_ytdlp_supported,
    find_gallery_dl_extractor,
    is_gallery_dl_supported,
)
from gallery_dl_extract_gdl import (
    check_support,
    check_gallery_dl_support,
    extract_gallery_dl,
)
from gallery_dl_extract_ytdlp import (
    extract_yt_dlp,
    extract_media,
)
from gallery_dl_downloader import (
    _download_with_ytdlp,
    _download_with_gallery_dl,
    download_media_file,
)
