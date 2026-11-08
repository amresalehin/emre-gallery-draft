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


from gallery_dl_support import *
from gallery_dl_support import _prepare_cookiefile, _get_ytdlp_extractors

def check_support(url):
    """
    Checks support for a URL following the strict priority:
    1. Primary: yt-dlp (official extractor inspection).
    2. Fallback: gallery-dl (if yt-dlp does not support, check gallery-dl extractor).
    """
    if not url:
        return json.dumps({"supported": False, "category": "", "extractor": "", "engine": ""})

    clean_url = url.strip()

    # 1. Primary: Check yt-dlp
    if is_ytdlp_supported(clean_url):
        ie = find_ytdlp_extractor(clean_url)
        ie_name = getattr(ie, "IE_NAME", "YoutubeDL") if ie else "YoutubeDL_Direct"
        display_name = getattr(ie, "IE_DESC", ie_name) if ie else "yt-dlp Direct"
        return json.dumps({
            "supported": True,
            "category": "video",
            "subcategory": "stream",
            "extractor": ie_name,
            "display_name": display_name,
            "engine": "yt-dlp"
        })

    # 2. Secondary: Check gallery-dl
    if is_gallery_dl_supported(clean_url):
        ext = find_gallery_dl_extractor(clean_url)
        if ext is not None:
            cat = getattr(ext, "category", "") or ext.__class__.__name__.lower().replace("extractor", "")
            subcat = getattr(ext, "subcategory", "") or ""
            name = ext.__class__.__name__
            return json.dumps({
                "supported": True,
                "category": cat,
                "subcategory": subcat,
                "extractor": name,
                "display_name": cat or name,
                "engine": "gallery-dl"
            })
        else:
            return json.dumps({
                "supported": True,
                "category": "images",
                "subcategory": "direct",
                "extractor": "GalleryDlDirect",
                "display_name": "gallery-dl Image",
                "engine": "gallery-dl"
            })

    return json.dumps({
        "supported": False,
        "category": "",
        "subcategory": "",
        "extractor": "",
        "display_name": "",
        "engine": ""
    })

def check_gallery_dl_support(url):
    """Directly checks if gallery-dl supports a URL according to official docs."""
    clean_url = url.strip() if url else ""
    if not is_gallery_dl_supported(clean_url):
        return json.dumps({"supported": False, "category": "", "extractor": "", "engine": "gallery-dl"})
    ext = find_gallery_dl_extractor(clean_url)
    if ext is not None:
        cat = getattr(ext, "category", "") or ext.__class__.__name__.lower().replace("extractor", "")
        name = ext.__class__.__name__
        return json.dumps({
            "supported": True,
            "category": cat,
            "subcategory": getattr(ext, "subcategory", ""),
            "extractor": name,
            "display_name": cat or name,
            "engine": "gallery-dl"
        })
    return json.dumps({
        "supported": True,
        "category": "images",
        "subcategory": "direct",
        "extractor": "GalleryDlDirect",
        "display_name": "gallery-dl Image",
        "engine": "gallery-dl"
    })

def extract_gallery_dl(url, options_json="{}"):
    """
    Extracts media items using gallery-dl exclusively.
    """
    clean_url = url.strip() if url else ""
    if not clean_url or not GALLERY_DL_AVAILABLE:
        return json.dumps({"success": False, "items": [], "error": "gallery-dl not available or empty URL"})

    try:
        opts = json.loads(options_json) if options_json else {}
    except Exception:
        opts = {}

    user_agent = opts.get("user_agent") or "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.200 Mobile Safari/537.36"
    cookies = opts.get("cookies")

    items = []
    page_title = ""
    seen_urls = set()

    try:
        with _gdl_lock:
            gdl_config.set(("extractor",), "user-agent", user_agent)
            cookie_file = _prepare_cookiefile(cookies, clean_url)
            if cookie_file:
                try:
                    gdl_config.set(("extractor",), "cookies", cookie_file)
                except Exception:
                    pass
            elif cookies:
                try:
                    gdl_config.set(("extractor",), "cookies", cookies)
                except Exception:
                    pass

            ext = gdl_extractor.find(clean_url)
            if ext:
                data_job = gdl_job.DataJob(ext, file=None, resolve=True)
                data_job.run()

                for post in getattr(data_job, "data_post", []):
                    if isinstance(post, dict):
                        candidate_title = post.get("title") or post.get("gallery_title") or post.get("album_title") or post.get("post_title") or post.get("name")
                        if candidate_title and not page_title:
                            page_title = str(candidate_title)

                for entry in getattr(data_job, "data", []):
                    if not isinstance(entry, (list, tuple)) or len(entry) < 2:
                        continue

                    item_url = None
                    kwdict = {}

                    if len(entry) >= 3 and isinstance(entry[1], str) and (entry[1].startswith("http://") or entry[1].startswith("https://")):
                        item_url = entry[1]
                        if isinstance(entry[2], dict):
                            kwdict = entry[2]
                    elif isinstance(entry[1], dict):
                        kwdict = entry[1]
                        item_url = kwdict.get("url") or kwdict.get("file_url")

                    if not item_url or item_url in seen_urls:
                        continue

                    seen_urls.add(item_url)

                    fn = kwdict.get("filename") or kwdict.get("name") or kwdict.get("title") or ""
                    ext_suffix = kwdict.get("extension") or ""
                    if fn and ext_suffix and not fn.endswith("." + ext_suffix):
                        item_title = f"{fn}.{ext_suffix}"
                    elif fn:
                        item_title = fn
                    else:
                        clean_path = item_url.split("?")[0].split("#")[0]
                        item_title = os.path.basename(clean_path) or f"media_{len(items) + 1}.bin"

                    if not page_title:
                        page_title = kwdict.get("gallery_title") or kwdict.get("album_title") or kwdict.get("title") or kwdict.get("author") or ""

                    lower_url = item_url.lower()
                    lower_ext = ext_suffix.lower() if ext_suffix else ""
                    is_video = lower_ext in ["mp4", "webm", "mkv", "mov", "avi"] or lower_url.endswith(".mp4") or lower_url.endswith(".webm") or ".mp4?" in lower_url
                    is_stream = lower_ext in ["m3u8", "mpd"] or lower_url.endswith(".m3u8") or ".m3u8?" in lower_url or lower_url.endswith(".mpd")
                    is_audio = lower_ext in ["mp3", "m4a", "aac", "wav", "flac", "ogg", "opus"] or lower_url.endswith(".mp3") or lower_url.endswith(".m4a")

                    if is_stream:
                        category = "STREAMS"
                        format_label = "HLS/DASH"
                        mime_type = "application/x-mpegURL"
                    elif is_video:
                        category = "VIDEOS"
                        format_label = ext_suffix.upper() if ext_suffix else "MP4"
                        mime_type = f"video/{lower_ext}" if lower_ext else "video/mp4"
                    elif is_audio:
                        category = "AUDIO"
                        format_label = ext_suffix.upper() if ext_suffix else "MP3"
                        mime_type = f"audio/{lower_ext}" if lower_ext else "audio/mpeg"
                    else:
                        category = "IMAGES"
                        format_label = ext_suffix.upper() if ext_suffix else "IMAGE"
                        mime_type = f"image/{lower_ext}" if lower_ext in ["png", "jpeg", "jpg", "webp", "gif", "avif"] else "image/jpeg"

                    width = kwdict.get("width")
                    height = kwdict.get("height")
                    resolution_hint = f"{width}x{height}" if width and height else None
                    size_bytes = kwdict.get("filesize") or kwdict.get("size")
                    try:
                        size_bytes = int(size_bytes) if size_bytes else None
                    except Exception:
                        size_bytes = None

                    thumb = kwdict.get("thumbnail") or kwdict.get("thumb") or (item_url if category == "IMAGES" else None)

                    url_hash = hashlib.sha256(item_url.encode('utf-8')).hexdigest()[:12]
                    items.append({
                        "id": f"gdl_{len(items)}_{url_hash}",
                        "url": item_url,
                        "title": item_title,
                        "category": category,
                        "format": format_label,
                        "mimeType": mime_type,
                        "isVideo": is_video or is_stream,
                        "isAudio": is_audio,
                        "isStream": is_stream,
                        "thumbnailUrl": thumb,
                        "resolutionHint": resolution_hint,
                        "sizeBytes": size_bytes,
                        "source": "gallery-dl"
                    })
    except Exception as e:
        return json.dumps({
            "success": False,
            "error": f"{e.__class__.__name__}: {str(e)}",
            "items": []
        })

    return json.dumps({
        "success": len(items) > 0,
        "engine": "gallery-dl",
        "pageTitle": str(page_title) if page_title else "",
        "itemsCount": len(items),
        "items": items
    })

