import hashlib
import json
import os

from engine_runtime import (
    DEFAULT_USER_AGENT,
    GALLERY_DL_AVAILABLE,
    _gdl_lock,
    gdl_config,
    gdl_extractor,
    gdl_job,
    parse_options,
)

VIDEO_EXTS = ("mp4", "webm", "mkv", "mov", "avi")
STREAM_EXTS = ("m3u8", "mpd")
AUDIO_EXTS = ("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus")
IMAGE_MIME_EXTS = ("png", "jpeg", "jpg", "webp", "gif", "avif")


def _extract_entry_url_and_meta(entry):
    if not isinstance(entry, (list, tuple)) or len(entry) < 2:
        return None, {}
    if len(entry) >= 3 and isinstance(entry[1], str) and (
        entry[1].startswith("http://") or entry[1].startswith("https://")
    ):
        kwdict = entry[2] if isinstance(entry[2], dict) else {}
        return entry[1], kwdict
    if isinstance(entry[1], dict):
        kwdict = entry[1]
        return kwdict.get("url") or kwdict.get("file_url"), kwdict
    return None, {}


def _classify_media(item_url, ext_suffix):
    lower_url = item_url.lower()
    lower_ext = ext_suffix.lower() if ext_suffix else ""

    is_video = (
        lower_ext in VIDEO_EXTS
        or lower_url.endswith(".mp4")
        or lower_url.endswith(".webm")
        or ".mp4?" in lower_url
    )
    is_stream = (
        lower_ext in STREAM_EXTS
        or lower_url.endswith(".m3u8")
        or ".m3u8?" in lower_url
        or lower_url.endswith(".mpd")
    )
    is_audio = (
        lower_ext in AUDIO_EXTS
        or lower_url.endswith(".mp3")
        or lower_url.endswith(".m4a")
    )

    if is_stream:
        return "STREAMS", "HLS/DASH", "application/x-mpegURL", True, False, True
    if is_video:
        fmt = ext_suffix.upper() if ext_suffix else "MP4"
        mime = f"video/{lower_ext}" if lower_ext else "video/mp4"
        return "VIDEOS", fmt, mime, True, False, False
    if is_audio:
        fmt = ext_suffix.upper() if ext_suffix else "MP3"
        mime = f"audio/{lower_ext}" if lower_ext else "audio/mpeg"
        return "AUDIO", fmt, mime, False, True, False

    fmt = ext_suffix.upper() if ext_suffix else "IMAGE"
    mime = f"image/{lower_ext}" if lower_ext in IMAGE_MIME_EXTS else "image/jpeg"
    return "IMAGES", fmt, mime, False, False, False


def _build_gdl_item(item_url, kwdict, index):
    fn = kwdict.get("filename") or kwdict.get("name") or kwdict.get("title") or ""
    ext_suffix = kwdict.get("extension") or ""
    if fn and ext_suffix and not fn.endswith("." + ext_suffix):
        item_title = f"{fn}.{ext_suffix}"
    elif fn:
        item_title = fn
    else:
        clean_path = item_url.split("?")[0].split("#")[0]
        item_title = os.path.basename(clean_path) or f"media_{index + 1}.bin"

    category, format_label, mime_type, is_video, is_audio, is_stream = _classify_media(
        item_url, ext_suffix
    )

    width = kwdict.get("width")
    height = kwdict.get("height")
    resolution_hint = f"{width}x{height}" if width and height else None
    size_bytes = kwdict.get("filesize") or kwdict.get("size")
    try:
        size_bytes = int(size_bytes) if size_bytes else None
    except Exception:
        size_bytes = None

    thumb = (
        kwdict.get("thumbnail")
        or kwdict.get("thumb")
        or (item_url if category == "IMAGES" else None)
    )
    url_hash = hashlib.sha256(item_url.encode("utf-8")).hexdigest()[:12]

    return {
        "id": f"gdl_{index}_{url_hash}",
        "url": item_url,
        "title": item_title,
        "category": category,
        "format": format_label,
        "mimeType": mime_type,
        "isVideo": is_video,
        "isAudio": is_audio,
        "isStream": is_stream,
        "thumbnailUrl": thumb,
        "resolutionHint": resolution_hint,
        "sizeBytes": size_bytes,
        "source": "gallery-dl",
    }


def extract_gallery_dl(url, options_json="{}"):
    """Extracts media items using gallery-dl exclusively."""
    clean_url = url.strip() if url else ""
    if not clean_url or not GALLERY_DL_AVAILABLE:
        return json.dumps({
            "success": False,
            "items": [],
            "error": "gallery-dl not available or empty URL",
        })

    opts = parse_options(options_json)
    user_agent = opts.get("user_agent") or DEFAULT_USER_AGENT
    cookies = opts.get("cookies")

    items = []
    page_title = ""
    seen_urls = set()

    try:
        with _gdl_lock:
            gdl_config.set(("extractor",), "user-agent", user_agent)
            if cookies:
                gdl_config.set(("extractor",), "cookies", cookies)

            ext = gdl_extractor.find(clean_url)
            if ext:
                data_job = gdl_job.DataJob(ext, file=None, resolve=True)
                data_job.run()

                for post in getattr(data_job, "data_post", []):
                    if isinstance(post, dict):
                        candidate_title = (
                            post.get("title")
                            or post.get("gallery_title")
                            or post.get("album_title")
                            or post.get("post_title")
                            or post.get("name")
                        )
                        if candidate_title and not page_title:
                            page_title = str(candidate_title)

                for entry in getattr(data_job, "data", []):
                    item_url, kwdict = _extract_entry_url_and_meta(entry)
                    if not item_url or item_url in seen_urls:
                        continue
                    seen_urls.add(item_url)

                    if not page_title:
                        page_title = (
                            kwdict.get("gallery_title")
                            or kwdict.get("album_title")
                            or kwdict.get("title")
                            or kwdict.get("author")
                            or ""
                        )

                    items.append(_build_gdl_item(item_url, kwdict, len(items)))
    except Exception as e:
        return json.dumps({
            "success": False,
            "error": f"{e.__class__.__name__}: {str(e)}",
            "items": [],
        })

    return json.dumps({
        "success": len(items) > 0,
        "engine": "gallery-dl",
        "pageTitle": str(page_title) if page_title else "",
        "itemsCount": len(items),
        "items": items,
    })
