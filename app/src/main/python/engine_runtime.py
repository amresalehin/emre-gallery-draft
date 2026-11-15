import json
import sys
import threading

DEFAULT_USER_AGENT = (
    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 "
    "(KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
)

_gdl_lock = threading.Lock()

try:
    import gallery_dl
    from gallery_dl import (
        extractor as gdl_extractor,
        job as gdl_job,
        config as gdl_config,
    )
    GALLERY_DL_AVAILABLE = True
except Exception:
    gallery_dl = None
    gdl_extractor = None
    gdl_job = None
    gdl_config = None
    GALLERY_DL_AVAILABLE = False

try:
    import yt_dlp
    YTDLP_AVAILABLE = True
except Exception:
    yt_dlp = None
    YTDLP_AVAILABLE = False

POPULAR_EXTRACTORS = [
    {"name": "Reddit", "domains": ["reddit.com", "redd.it", "i.redd.it", "v.redd.it"], "icon": "reddit"},
    {"name": "Imgur", "domains": ["imgur.com", "i.imgur.com"], "icon": "image"},
    {"name": "Twitter / X", "domains": ["twitter.com", "x.com", "pbs.twimg.com"], "icon": "tag"},
    {"name": "Pinterest", "domains": ["pinterest.com", "i.pinimg.com"], "icon": "pin"},
    {"name": "ArtStation", "domains": ["artstation.com"], "icon": "brush"},
    {"name": "DeviantArt", "domains": ["deviantart.com"], "icon": "palette"},
    {"name": "Flickr", "domains": ["flickr.com"], "icon": "camera"},
]

YTDLP_VIDEO_HOSTS = [
    "youtube.com",
    "youtu.be",
    "vimeo.com",
    "tiktok.com",
    "dailymotion.com",
    "twitch.tv",
    "soundcloud.com",
    "bandcamp.com",
    "facebook.com",
    "instagram.com/reel",
]


def parse_options(options_json):
    """Safely parses JSON extractor options."""
    if not options_json:
        return {}
    try:
        parsed = json.loads(options_json)
        return parsed if isinstance(parsed, dict) else {}
    except Exception:
        return {}


def build_http_headers(user_agent=None, referer=None, cookies=None, headers_json=None):
    """Constructs merged HTTP headers for extraction or download requests."""
    headers = {"User-Agent": user_agent or DEFAULT_USER_AGENT}
    if referer:
        headers["Referer"] = referer
    if cookies:
        headers["Cookie"] = cookies
    if headers_json:
        try:
            parsed = json.loads(headers_json)
            if isinstance(parsed, dict):
                headers.update(parsed)
        except Exception:
            pass
    return headers


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
        "python_version": sys.version.split()[0],
        "supported_categories_count": 287 if GALLERY_DL_AVAILABLE else 0,
        "popular_extractors": POPULAR_EXTRACTORS,
    })


def check_support(url):
    """Checks if a URL is handled by gallery-dl or yt-dlp extractors."""
    if not url:
        return json.dumps({"supported": False, "category": "", "extractor": ""})

    clean_url = url.strip()

    if GALLERY_DL_AVAILABLE:
        try:
            ext = gdl_extractor.find(clean_url)
            if ext is not None:
                cat = getattr(ext, "category", "") or ext.__class__.__name__.lower().replace("extractor", "")
                subcat = getattr(ext, "subcategory", "") or ""
                name = ext.__class__.__name__
                return json.dumps({
                    "supported": True,
                    "category": cat,
                    "subcategory": subcat,
                    "extractor": name,
                    "engine": "gallery-dl",
                })
        except Exception:
            pass

    if YTDLP_AVAILABLE:
        try:
            lower = clean_url.lower()
            if any(vh in lower for vh in YTDLP_VIDEO_HOSTS):
                return json.dumps({
                    "supported": True,
                    "category": "video",
                    "subcategory": "stream",
                    "extractor": "YoutubeDL",
                    "engine": "yt-dlp",
                })
        except Exception:
            pass

    return json.dumps({"supported": False, "category": "", "extractor": ""})
