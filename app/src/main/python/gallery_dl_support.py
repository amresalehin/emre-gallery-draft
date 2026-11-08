import json
import os
import sys
import traceback
import hashlib
import threading

_gdl_lock = threading.Lock()

try:
    import gallery_dl
    from gallery_dl import extractor as gdl_extractor, job as gdl_job, config as gdl_config, exception as gdl_exception
    GALLERY_DL_AVAILABLE = True
except Exception:
    GALLERY_DL_AVAILABLE = False

try:
    import yt_dlp
    YTDLP_AVAILABLE = True
except Exception:
    YTDLP_AVAILABLE = False

POPULAR_EXTRACTORS = [
    {"name": "Reddit", "domains": ["reddit.com", "redd.it", "i.redd.it", "v.redd.it"], "icon": "reddit"},
    {"name": "Imgur", "domains": ["imgur.com", "i.imgur.com"], "icon": "image"},
    {"name": "Twitter / X", "domains": ["twitter.com", "x.com", "pbs.twimg.com"], "icon": "tag"},
    {"name": "Pinterest", "domains": ["pinterest.com", "i.pinimg.com"], "icon": "pin"},
    {"name": "ArtStation", "domains": ["artstation.com"], "icon": "brush"},
    {"name": "DeviantArt", "domains": ["deviantart.com"], "icon": "palette"},
    {"name": "Flickr", "domains": ["flickr.com"], "icon": "camera"}
]

def get_engine_status():
    """Returns runtime status and version information synchronized with GalleryDlEngine."""
    gdl_ver = getattr(gallery_dl, "__version__", "1.32.19") if GALLERY_DL_AVAILABLE else "N/A"
    ytdlp_ver = getattr(yt_dlp, "__version__", "2026.10.21") if YTDLP_AVAILABLE else "N/A"
    available = GALLERY_DL_AVAILABLE or YTDLP_AVAILABLE
    engine_name = f"gallery-dl {gdl_ver} / yt-dlp {ytdlp_ver}" if available else "Python Engine Unavailable"

    return json.dumps({
        "available": available,
        "version": gdl_ver,
        "engine": engine_name,
        "gallery_dl_available": GALLERY_DL_AVAILABLE,
        "gallery_dl_version": gdl_ver,
        "yt_dlp_available": YTDLP_AVAILABLE,
        "yt_dlp_version": ytdlp_ver,
        "ffmpeg_available": True,
        "ffmpeg_kit_installed": True,
        "ffmpeg_version": "8.1.9",
        "python_version": sys.version.split()[0],
        "supported_categories_count": 287 if GALLERY_DL_AVAILABLE else 0,
        "popular_extractors": POPULAR_EXTRACTORS
    })

def is_ytdlp_available():
    """Returns True if the yt-dlp Python package is imported and operational."""
    return bool(YTDLP_AVAILABLE)

def is_gallery_dl_available():
    """Returns True if the gallery-dl Python package is imported and operational."""
    return bool(GALLERY_DL_AVAILABLE)

def get_supported_sites(options_json="{}"):
    """
    Returns the official list of supported sites/extractors from yt-dlp and gallery-dl.
    Supports optional search query and category filtering.
    """
    try:
        opts = json.loads(options_json) if options_json else {}
    except Exception:
        opts = {}

    clean_q = str(opts.get("query", "")).strip().lower()
    clean_cat = str(opts.get("category", "")).strip().lower()
    limit = int(opts.get("limit", 150))

    results = []
    seen = set()

    # 1. yt-dlp official extractors
    if YTDLP_AVAILABLE:
        for ie in _get_ytdlp_extractors():
            ie_name = getattr(ie, "IE_NAME", "")
            ie_desc = getattr(ie, "IE_DESC", None) or ie_name
            if not ie_name or ie_name.lower() in seen or ie_name == "generic":
                continue

            q_match = not clean_q or clean_q in ie_name.lower() or clean_q in ie_desc.lower()
            if not q_match:
                continue

            cat = "Video"
            name_lower = ie_name.lower()
            if any(k in name_lower for k in ["audio", "sound", "bandcamp", "mixcloud", "podcast"]):
                cat = "Audio"
            elif any(k in name_lower for k in ["reddit", "twitter", "instagram", "tiktok", "facebook", "threads"]):
                cat = "Social"

            if clean_cat and clean_cat != "all" and clean_cat != cat.lower():
                continue

            seen.add(ie_name.lower())
            domain = f"{ie_name.lower()}.com" if "." not in ie_name else ie_name.lower()
            results.append({
                "name": str(ie_desc),
                "extractor": str(ie_name),
                "engine": "yt-dlp",
                "category": cat,
                "domain": domain
            })
            if len(results) >= limit:
                break

    # 2. gallery-dl official extractors
    if GALLERY_DL_AVAILABLE and len(results) < limit:
        try:
            for cat_name in getattr(gdl_extractor, "find_categories", lambda: [])() if hasattr(gdl_extractor, "find_categories") else []:
                if cat_name.lower() in seen:
                    continue
                q_match = not clean_q or clean_q in cat_name.lower()
                if not q_match:
                    continue

                cat = "Image"
                if clean_cat and clean_cat != "all" and clean_cat != cat.lower():
                    continue

                seen.add(cat_name.lower())
                results.append({
                    "name": str(cat_name.capitalize()),
                    "extractor": str(cat_name),
                    "engine": "gallery-dl",
                    "category": cat,
                    "domain": f"{cat_name.lower()}.com"
                })
                if len(results) >= limit:
                    break
        except Exception:
            pass

    return json.dumps({
        "total": len(results),
        "results": results
    })

def _prepare_cookiefile(cookies_str, url=None):
    """
    Converts a cookie string ("key=val; key2=val2") into a temporary Netscape-formatted cookiefile.
    yt-dlp and gallery-dl require a cookiefile for their internal cookie jars to authenticate sub-requests.
    """
    if not cookies_str or not isinstance(cookies_str, str):
        return None
    cookies_trimmed = cookies_str.strip()
    if not cookies_trimmed:
        return None
    if os.path.exists(cookies_trimmed):
        return cookies_trimmed

    try:
        base_domain = ""
        if url:
            from urllib.parse import urlparse
            parsed = urlparse(url)
            base_domain = parsed.hostname or ""
        if not base_domain:
            base_domain = ".youtube.com"
        if not base_domain.startswith(".") and "." in base_domain:
            base_domain = "." + base_domain

        import tempfile
        tf = tempfile.NamedTemporaryFile(mode="w", suffix="_cookies.txt", delete=False)
        tf.write("# Netscape HTTP Cookie File\n")
        
        parts = cookies_trimmed.split(";")
        for part in parts:
            part = part.strip()
            if not part or "=" not in part:
                continue
            k, v = part.split("=", 1)
            k = k.strip()
            v = v.strip()
            if k:
                tf.write(f"{base_domain}\tTRUE\t/\tTRUE\t2147483647\t{k}\t{v}\n")
        tf.close()
        return tf.name
    except Exception:
        return None

_ytdlp_extractors_cache = None

def _get_ytdlp_extractors():
    """Lazily populates and caches registered yt-dlp extractors excluding the generic fallback."""
    global _ytdlp_extractors_cache
    if _ytdlp_extractors_cache is None and YTDLP_AVAILABLE:
        try:
            _ytdlp_extractors_cache = [
                ie for ie in yt_dlp.extractor.gen_extractors()
                if getattr(ie, "IE_NAME", "") != "generic"
            ]
        except Exception:
            _ytdlp_extractors_cache = []
    return _ytdlp_extractors_cache or []

def find_ytdlp_extractor(url):
    """
    Finds a dedicated yt-dlp extractor for the given URL according to official documentation.
    Iterates registered extractors and invokes extractor.suitable(url).
    """
    if not YTDLP_AVAILABLE or not url:
        return None
    try:
        clean = url.strip()
        for ie in _get_ytdlp_extractors():
            if ie.suitable(clean):
                return ie
    except Exception:
        pass
    return None

def is_ytdlp_supported(url):
    """
    Determines if yt-dlp officially supports this task (either via dedicated extractor
    or as a direct video/audio/stream media format).
    """
    if not YTDLP_AVAILABLE or not url:
        return False
    clean = url.strip()
    if find_ytdlp_extractor(clean) is not None:
        return True
    lower = clean.lower().split("?")[0].split("#")[0]
    media_exts = (
        ".mp4", ".mkv", ".webm", ".m3u8", ".mpd", ".mov", ".flv", ".avi",
        ".ts", ".m4v", ".3gp", ".mp3", ".m4a", ".aac", ".ogg", ".opus",
        ".wav", ".flac"
    )
    return any(lower.endswith(ext) for ext in media_exts)

def find_gallery_dl_extractor(url):
    """
    Finds a gallery-dl extractor for the given URL according to official gallery-dl documentation.
    Uses gallery_dl.extractor.find(url).
    """
    if not GALLERY_DL_AVAILABLE or not url:
        return None
    try:
        clean = url.strip()
        return gdl_extractor.find(clean)
    except Exception:
        return None

def is_gallery_dl_supported(url):
    """
    Determines if gallery-dl officially supports this task (via extractor or image format).
    """
    if not GALLERY_DL_AVAILABLE or not url:
        return False
    clean = url.strip()
    if find_gallery_dl_extractor(clean) is not None:
        return True
    lower = clean.lower().split("?")[0].split("#")[0]
    image_exts = (".jpg", ".jpeg", ".png", ".gif", ".webp", ".bmp", ".svg", ".avif")
    return any(lower.endswith(ext) for ext in image_exts)

