# gallery_dl_runner.py
# Python bridge for gallery-dl and yt-dlp using Chaquopy on Android
import json
import os
import sys
import traceback

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
        "python_version": sys.version.split()[0],
        "supported_categories_count": 287 if GALLERY_DL_AVAILABLE else 0,
        "popular_extractors": POPULAR_EXTRACTORS
    })

def check_support(url):
    """
    Checks if a URL is handled by gallery-dl or yt-dlp extractors.
    """
    if not url:
        return json.dumps({"supported": False, "category": "", "extractor": ""})

    clean_url = url.strip()

    # 1. Check gallery-dl
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
                    "engine": "gallery-dl"
                })
        except Exception:
            pass

    # 2. Check yt-dlp
    if YTDLP_AVAILABLE:
        try:
            lower = clean_url.lower()
            video_hosts = [
                "youtube.com", "youtu.be", "vimeo.com", "tiktok.com", "dailymotion.com",
                "twitch.tv", "soundcloud.com", "bandcamp.com", "facebook.com", "instagram.com/reel"
            ]
            if any(vh in lower for vh in video_hosts):
                return json.dumps({
                    "supported": True,
                    "category": "video",
                    "subcategory": "stream",
                    "extractor": "YoutubeDL",
                    "engine": "yt-dlp"
                })
        except Exception:
            pass

    return json.dumps({"supported": False, "category": "", "extractor": ""})

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

    user_agent = opts.get("user_agent") or "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    cookies = opts.get("cookies")

    items = []
    page_title = ""
    seen_urls = set()

    try:
        gdl_config.set(("extractor",), "user-agent", user_agent)
        if cookies:
            gdl_config.set(("extractor",), "cookies", cookies)

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

                items.append({
                    "id": f"gdl_{len(items)}_{abs(hash(item_url))}",
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

def extract_yt_dlp(url, options_json="{}"):
    """
    Extracts media items using yt-dlp exclusively.
    """
    clean_url = url.strip() if url else ""
    if not clean_url or not YTDLP_AVAILABLE:
        return json.dumps({"success": False, "items": [], "error": "yt-dlp not available or empty URL"})

    try:
        opts = json.loads(options_json) if options_json else {}
    except Exception:
        opts = {}

    user_agent = opts.get("user_agent") or "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    items = []
    page_title = ""
    seen_urls = set()

    try:
        ydl_opts = {
            "quiet": True,
            "no_warnings": True,
            "skip_download": True,
            "format": "bestvideo+bestaudio/best",
            "socket_timeout": 20,
            "user_agent": user_agent
        }
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(clean_url, download=False)
            if info:
                entries = info.get("entries") or [info]
                for entry in entries:
                    if not entry or not isinstance(entry, dict):
                        continue

                    direct_url = entry.get("url")
                    if not direct_url and "formats" in entry and entry["formats"]:
                        direct_url = entry["formats"][-1].get("url")

                    if not direct_url or direct_url in seen_urls:
                        continue

                    seen_urls.add(direct_url)

                    v_title = entry.get("title") or entry.get("id") or f"media_{len(items) + 1}"
                    v_ext = entry.get("ext") or "mp4"
                    item_title = f"{v_title}.{v_ext}" if not v_title.endswith(f".{v_ext}") else v_title

                    if not page_title:
                        page_title = v_title

                    width = entry.get("width")
                    height = entry.get("height")
                    resolution_hint = f"{width}x{height}" if width and height else None
                    thumb = entry.get("thumbnail")
                    filesize = entry.get("filesize") or entry.get("filesize_approx")

                    is_audio_only = entry.get("vcodec") == "none" or v_ext in ["mp3", "m4a", "opus", "aac"]
                    is_stream = (".m3u8" in direct_url) or (".mpd" in direct_url)
                    category = "AUDIO" if is_audio_only else ("STREAMS" if is_stream else "VIDEOS")

                    items.append({
                        "id": f"ytdlp_{len(items)}_{abs(hash(direct_url))}",
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
                        "source": "yt-dlp"
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
    Extracts all media items using gallery-dl AND yt-dlp progressively.
    Does not allow partial gallery-dl results to prevent yt-dlp from running.
    """
    clean_url = url.strip() if url else ""
    if not clean_url:
        return json.dumps({
            "success": False,
            "error": "URL cannot be empty",
            "items": []
        })

    all_items = []
    seen_urls = set()
    page_title = ""

    # 1. Run gallery-dl
    gdl_res_str = extract_gallery_dl(clean_url, options_json)
    try:
        gdl_res = json.loads(gdl_res_str)
        if gdl_res.get("pageTitle"):
            page_title = gdl_res["pageTitle"]
        for itm in gdl_res.get("items", []):
            u = itm.get("url")
            if u and u not in seen_urls:
                seen_urls.add(u)
                all_items.append(itm)
    except Exception:
        pass

    # 2. Run yt-dlp (ALWAYS run, even if gallery-dl found items, e.g. embedded video in social post)
    ytdlp_res_str = extract_yt_dlp(clean_url, options_json)
    try:
        ytdlp_res = json.loads(ytdlp_res_str)
        if not page_title and ytdlp_res.get("pageTitle"):
            page_title = ytdlp_res["pageTitle"]
        for itm in ytdlp_res.get("items", []):
            u = itm.get("url")
            if u and u not in seen_urls:
                seen_urls.add(u)
                all_items.append(itm)
    except Exception:
        pass

    return json.dumps({
        "success": len(all_items) > 0,
        "engine": "hybrid (gallery-dl + yt-dlp)",
        "pageTitle": str(page_title) if page_title else "",
        "itemsCount": len(all_items),
        "items": all_items
    })

def download_media_file(media_url, target_path, referer=None, user_agent=None):
    """
    Downloads media file using requests or yt-dlp fallback (e.g. for streams).
    """
    # For HLS/DASH streaming manifests, prioritize yt-dlp directly
    is_stream = (".m3u8" in media_url) or (".mpd" in media_url)
    if is_stream and YTDLP_AVAILABLE:
        try:
            ydl_opts = {
                "outtmpl": target_path,
                "quiet": True,
                "no_warnings": True,
                "format": "best"
            }
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                ydl.download([media_url])
            if os.path.exists(target_path) and os.path.getsize(target_path) > 0:
                return True
        except Exception:
            pass

    try:
        import requests
        headers = {}
        if user_agent:
            headers["User-Agent"] = user_agent
        else:
            headers["User-Agent"] = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        if referer:
            headers["Referer"] = referer

        resp = requests.get(media_url, headers=headers, stream=True, timeout=35)
        if resp.status_code in [200, 206]:
            content_type = (resp.headers.get("content-type") or "").lower()
            if "text/html" in content_type or "application/json" in content_type:
                return False

            os.makedirs(os.path.dirname(target_path), exist_ok=True)
            first_chunk = True
            with open(target_path, "wb") as f:
                for chunk in resp.iter_content(chunk_size=65536):
                    if chunk:
                        if first_chunk:
                            first_chunk = False
                            prefix = chunk[:512].lower()
                            if b"<!doctype html" in prefix or b"<html" in prefix or b"<head" in prefix:
                                f.close()
                                if os.path.exists(target_path):
                                    os.remove(target_path)
                                return False
                            trimmed = prefix.strip()
                            if trimmed.startswith(b"{") and (b'"error"' in trimmed or b'"message"' in trimmed):
                                f.close()
                                if os.path.exists(target_path):
                                    os.remove(target_path)
                                return False
                        f.write(chunk)
            return os.path.exists(target_path) and os.path.getsize(target_path) > 0
    except Exception:
        pass

    # Fallback to yt-dlp if direct request failed and target is a media stream or video
    if YTDLP_AVAILABLE and (is_stream or any(ext in media_url.lower() for ext in [".mp4", ".mkv", ".webm", ".m3u8", ".mpd", "youtube", "tiktok", "vimeo"])):
        try:
            ydl_opts = {
                "outtmpl": target_path,
                "quiet": True,
                "no_warnings": True,
                "format": "best"
            }
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                ydl.download([media_url])
            return os.path.exists(target_path) and os.path.getsize(target_path) > 0
        except Exception:
            pass

    return False
