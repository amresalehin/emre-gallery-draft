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
from gallery_dl_extract_gdl import check_support, check_gallery_dl_support, extract_gallery_dl

def extract_yt_dlp(url, options_json="{}"):
    """
    Extracts media items using yt-dlp exclusively.
    Selects single pre-muxed format and preserves page URL for resilient save-time downloads.
    """
    clean_url = url.strip() if url else ""
    if not clean_url or not YTDLP_AVAILABLE:
        return json.dumps({"success": False, "items": [], "error": "yt-dlp not available or empty URL"})

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
        cookie_file = _prepare_cookiefile(cookies, clean_url)
        http_headers = {
            "User-Agent": user_agent,
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7",
            "Accept-Language": "en-US,en;q=0.9",
            "Sec-Ch-Ua": '"Chromium";v="131", "Not_A Brand";v="24"',
            "Sec-Ch-Ua-Mobile": "?1",
            "Sec-Ch-Ua-Platform": '"Android"',
            "Sec-Fetch-Dest": "document",
            "Sec-Fetch-Mode": "navigate",
            "Sec-Fetch-Site": "none",
            "Sec-Fetch-User": "?1",
            "Upgrade-Insecure-Requests": "1"
        }
        if cookies:
            http_headers["Cookie"] = cookies

        ydl_opts = {
            "quiet": True,
            "no_warnings": True,
            "skip_download": True,
            "format": "b[vcodec!=none][acodec!=none]/best[vcodec!=none][acodec!=none]/bestvideo+bestaudio/best",
            "socket_timeout": 35,
            "geo_bypass": True,
            "nocheckcertificate": True,
            "retries": 3,
            "fragment_retries": 3,
            "http_headers": http_headers,
            "extractor_args": {
                "youtube": {
                    "player_client": ["android", "ios", "mweb", "web"],
                    "player_skip": ["webpage", "configs"]
                },
                "instagram": {
                    "api": ["graphql", "web"]
                },
                "tiktok": {
                    "api": ["app", "web"]
                },
                "twitter": {
                    "api": ["syndication", "graphql"]
                }
            }
        }
        if cookie_file:
            ydl_opts["cookiefile"] = cookie_file

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(clean_url, download=False)
            if info:
                entries = info.get("entries") or [info]
                for entry in entries:
                    if not entry or not isinstance(entry, dict):
                        continue

                    page_url = entry.get("webpage_url") or clean_url

                    # Extract the direct playable media stream URL
                    direct_url = entry.get("url")
                    v_ext = entry.get("ext") or "mp4"

                    formats = entry.get("formats") or []
                    best_fmt = None
                    if not direct_url and formats:
                        # 1. Prefer pre-muxed video+audio format (itag 18, 22, etc.)
                        muxed = [f for f in formats if f.get("url") and f.get("vcodec") != "none" and f.get("acodec") != "none"]
                        if muxed:
                            best_fmt = muxed[-1]
                            direct_url = best_fmt.get("url")
                            v_ext = best_fmt.get("ext") or v_ext
                        else:
                            # 2. Prefer any video format with a url
                            video_fmts = [f for f in formats if f.get("url") and f.get("vcodec") != "none"]
                            if video_fmts:
                                best_fmt = video_fmts[-1]
                                direct_url = best_fmt.get("url")
                                v_ext = best_fmt.get("ext") or v_ext
                            else:
                                valid_fmts = [f for f in formats if f.get("url")]
                                if valid_fmts:
                                    best_fmt = valid_fmts[-1]
                                    direct_url = best_fmt.get("url")
                                    v_ext = best_fmt.get("ext") or v_ext

                    if not direct_url:
                        direct_url = page_url

                    if not direct_url or direct_url in seen_urls:
                        continue

                    seen_urls.add(direct_url)

                    v_title = entry.get("title") or entry.get("id") or f"media_{len(items) + 1}"
                    item_title = f"{v_title}.{v_ext}" if not v_title.endswith(f".{v_ext}") else v_title

                    if not page_title:
                        page_title = v_title

                    width = entry.get("width") or (best_fmt.get("width") if best_fmt else None)
                    height = entry.get("height") or (best_fmt.get("height") if best_fmt else None)
                    resolution_hint = f"{width}x{height}" if width and height else None
                    thumb = entry.get("thumbnail")
                    filesize = entry.get("filesize") or entry.get("filesize_approx") or (best_fmt.get("filesize") if best_fmt else None)

                    is_audio_only = entry.get("vcodec") == "none" or v_ext in ["mp3", "m4a", "opus", "aac"]
                    is_stream = (".m3u8" in direct_url) or (".mpd" in direct_url)
                    category = "AUDIO" if is_audio_only else ("STREAMS" if is_stream else "VIDEOS")

                    url_hash = hashlib.sha256(direct_url.encode('utf-8')).hexdigest()[:12]
                    items.append({
                        "id": f"ytdlp_{len(items)}_{url_hash}",
                        "url": direct_url,
                        "title": item_title,
                        "category": category,
                        "format": v_ext.upper(),
                        "mimeType": f"audio/{v_ext}" if is_audio_only else ("application/x-mpegURL" if is_stream else f"video/{v_ext}"),
                        "isVideo": not is_audio_only,
                        "isAudio": is_audio_only,
                        "isStream": is_stream,
                        "thumbnailUrl": thumb,
                        "resolutionHint": resolution_hint,
                        "sizeBytes": filesize,
                        "source": "yt-dlp",
                        "pageUrl": page_url
                    })

                    # Also provide the best standalone audio track if formats are present
                    audio_fmts = [f for f in formats if f.get("url") and f.get("vcodec") == "none" and f.get("acodec") != "none"]
                    if audio_fmts and not is_audio_only:
                        best_audio = audio_fmts[-1]
                        a_url = best_audio.get("url")
                        if a_url and a_url not in seen_urls:
                            seen_urls.add(a_url)
                            a_ext = best_audio.get("ext") or "m4a"
                            a_title = f"{v_title}.{a_ext}" if not v_title.endswith(f".{a_ext}") else v_title
                            a_hash = hashlib.sha256(a_url.encode('utf-8')).hexdigest()[:12]
                            items.append({
                                "id": f"ytdlp_audio_{len(items)}_{a_hash}",
                                "url": a_url,
                                "title": a_title,
                                "category": "AUDIO",
                                "format": a_ext.upper(),
                                "mimeType": f"audio/{a_ext}",
                                "isVideo": False,
                                "isAudio": True,
                                "isStream": False,
                                "thumbnailUrl": thumb,
                                "resolutionHint": None,
                                "sizeBytes": best_audio.get("filesize") or best_audio.get("filesize_approx"),
                                "source": "yt-dlp",
                                "pageUrl": page_url
                            })
    except Exception as e:
        return json.dumps({
            "success": False,
            "error": f"{e.__class__.__name__}: {str(e)}",
            "items": []
        })

    return json.dumps({
        "success": len(items) > 0,
        "engine": "yt-dlp",
        "pageTitle": str(page_title) if page_title else "",
        "itemsCount": len(items),
        "items": items
    })

def extract_media(url, options_json="{}"):
    """
    Primary: yt-dlp.
    If yt-dlp supports a task it should be handled exclusively by it.
    Goes to gallery-dl if yt-dlp is not supported or fails and that is supported by gallery-dl.
    """
    clean_url = url.strip() if url else ""
    if not clean_url:
        return json.dumps({
            "success": False,
            "error": "URL cannot be empty",
            "items": []
        })

    # Step 1: Check if yt-dlp supports this task
    if is_ytdlp_supported(clean_url):
        ytdlp_res_str = extract_yt_dlp(clean_url, options_json)
        try:
            ytdlp_res = json.loads(ytdlp_res_str)
            if ytdlp_res.get("success") and len(ytdlp_res.get("items", [])) > 0:
                # Handled exclusively by yt-dlp!
                return ytdlp_res_str
        except Exception:
            ytdlp_res = {"success": False, "error": "yt-dlp extraction failed"}

        # yt-dlp was supported but failed (e.g. no video, error, or 0 items).
        # Goes to gallery-dl if supported by gallery-dl
        if is_gallery_dl_supported(clean_url):
            gdl_res_str = extract_gallery_dl(clean_url, options_json)
            try:
                gdl_res = json.loads(gdl_res_str)
                if gdl_res.get("success") and len(gdl_res.get("items", [])) > 0:
                    return gdl_res_str
            except Exception:
                pass
            return gdl_res_str
        else:
            return ytdlp_res_str

    # Step 2: yt-dlp is not supported for this URL.
    # Check if supported by gallery-dl
    if is_gallery_dl_supported(clean_url):
        return extract_gallery_dl(clean_url, options_json)

    # Neither supported
    return json.dumps({
        "success": False,
        "error": f"URL is not supported by yt-dlp or gallery-dl: {clean_url}",
        "items": []
    })

