import hashlib
import json

from engine_runtime import (
    DEFAULT_USER_AGENT,
    YTDLP_AVAILABLE,
    parse_options,
    yt_dlp,
)


def _build_ytdlp_item(entry, clean_url, direct_url, index):
    v_title = entry.get("title") or entry.get("id") or f"media_{index + 1}"
    v_ext = entry.get("ext") or "mp4"
    item_title = f"{v_title}.{v_ext}" if not v_title.endswith(f".{v_ext}") else v_title

    width = entry.get("width")
    height = entry.get("height")
    resolution_hint = f"{width}x{height}" if width and height else None
    thumb = entry.get("thumbnail")
    filesize = entry.get("filesize") or entry.get("filesize_approx")

    is_audio_only = entry.get("vcodec") == "none" or v_ext in ["mp3", "m4a", "opus", "aac"]
    is_stream = (".m3u8" in clean_url) or (".mpd" in clean_url)
    category = "AUDIO" if is_audio_only else ("STREAMS" if is_stream else "VIDEOS")
    mime_type = (
        f"audio/{v_ext}"
        if is_audio_only
        else ("application/x-mpegURL" if is_stream else f"video/{v_ext}")
    )

    url_hash = hashlib.sha256(direct_url.encode("utf-8")).hexdigest()[:12]
    item = {
        "id": f"ytdlp_{index}_{url_hash}",
        "url": direct_url,
        "title": item_title,
        "category": category,
        "format": v_ext.upper(),
        "mimeType": mime_type,
        "isVideo": not is_audio_only,
        "isAudio": is_audio_only,
        "isStream": is_stream,
        "thumbnailUrl": thumb,
        "resolutionHint": resolution_hint,
        "sizeBytes": filesize,
        "source": "yt-dlp",
    }
    return item, v_title


def extract_yt_dlp(url, options_json="{}"):
    """
    Extracts media items using yt-dlp exclusively.
    Selects single pre-muxed format and preserves page URL for resilient save-time downloads.
    """
    clean_url = url.strip() if url else ""
    if not clean_url or not YTDLP_AVAILABLE:
        return json.dumps({
            "success": False,
            "items": [],
            "error": "yt-dlp not available or empty URL",
        })

    opts = parse_options(options_json)
    user_agent = opts.get("user_agent") or DEFAULT_USER_AGENT

    items = []
    page_title = ""
    seen_urls = set()

    try:
        ydl_opts = {
            "quiet": True,
            "no_warnings": True,
            "skip_download": True,
            "format": "b[vcodec!=none][acodec!=none]/best[vcodec!=none][acodec!=none]/best",
            "socket_timeout": 20,
            "user_agent": user_agent,
        }
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(clean_url, download=False)
            if info:
                entries = info.get("entries") or [info]
                for entry in entries:
                    if not entry or not isinstance(entry, dict):
                        continue

                    direct_url = entry.get("webpage_url") or clean_url
                    if not direct_url or direct_url in seen_urls:
                        continue

                    seen_urls.add(direct_url)
                    item, v_title = _build_ytdlp_item(entry, clean_url, direct_url, len(items))
                    if not page_title:
                        page_title = v_title
                    items.append(item)
    except Exception as e:
        return json.dumps({
            "success": False,
            "error": f"{e.__class__.__name__}: {str(e)}",
            "items": [],
        })

    return json.dumps({
        "success": len(items) > 0,
        "engine": "yt-dlp",
        "pageTitle": str(page_title) if page_title else "",
        "itemsCount": len(items),
        "items": items,
    })
