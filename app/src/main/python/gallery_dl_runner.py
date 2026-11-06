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

    user_agent = opts.get("user_agent") or "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    cookies = opts.get("cookies")

    items = []
    page_title = ""
    seen_urls = set()

    try:
        cookie_file = _prepare_cookiefile(cookies, clean_url)
        http_headers = {"User-Agent": user_agent}
        if cookies:
            http_headers["Cookie"] = cookies

        ydl_opts = {
            "quiet": True,
            "no_warnings": True,
            "skip_download": True,
            "format": "b[vcodec!=none][acodec!=none]/best[vcodec!=none][acodec!=none]/bestvideo+bestaudio/best",
            "socket_timeout": 30,
            "http_headers": http_headers,
            "extractor_args": {
                "youtube": {
                    "player_client": ["android", "ios", "mweb", "tv_embedded"],
                    "player_skip": ["webpage", "configs"]
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

def download_media_file(media_url, target_path, referer=None, user_agent=None, cookies=None, headers_json=None):
    """
    Downloads media file using yt-dlp (with single pre-muxed format) or requests with cookies/headers.
    Never commits raw .m3u8 or .mpd playlist text as a finished file.
    """
    extra_headers = {}
    if headers_json:
        try:
            parsed = json.loads(headers_json)
            if isinstance(parsed, dict):
                extra_headers = parsed
        except Exception:
            pass

    ua = user_agent or "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
    is_stream = (".m3u8" in media_url) or (".mpd" in media_url)
    is_social_or_video = any(h in media_url.lower() for h in [
        "youtube.com", "youtu.be", "tiktok.com", "instagram.com", "vimeo.com", "twitter.com", "x.com"
    ])

    # 1. For streams or social pages, prioritize yt-dlp directly
    if (is_stream or is_social_or_video) and YTDLP_AVAILABLE:
        try:
            http_headers = {"User-Agent": ua}
            if referer:
                http_headers["Referer"] = referer
            if cookies:
                http_headers["Cookie"] = cookies
            http_headers.update(extra_headers)

            cookie_file = _prepare_cookiefile(cookies, media_url)
            ydl_opts = {
                "outtmpl": target_path,
                "quiet": True,
                "no_warnings": True,
                "format": "b[vcodec!=none][acodec!=none]/best[vcodec!=none][acodec!=none]/bestvideo+bestaudio/best",
                "socket_timeout": 30,
                "http_headers": http_headers,
                "extractor_args": {
                    "youtube": {
                        "player_client": ["android", "ios", "mweb", "tv_embedded"],
                        "player_skip": ["webpage", "configs"]
                    }
                }
            }
            if cookie_file:
                ydl_opts["cookiefile"] = cookie_file

            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                ydl.download([media_url])

            if os.path.exists(target_path) and os.path.getsize(target_path) > 0:
                # Ensure yt-dlp didn't just save a playlist text file
                with open(target_path, "rb") as f:
                    head = f.read(512).lower()
                if b"#extm3u" in head or b"<mpd" in head:
                    os.remove(target_path)
                    return False
                return True
        except Exception:
            pass

    # 2. Direct HTTP requests download
    try:
        import requests
        headers = {"User-Agent": ua}
        if referer:
            headers["Referer"] = referer
        if cookies:
            headers["Cookie"] = cookies
        headers.update(extra_headers)

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
                            prefix = chunk[:1024].lower()
                            if (b"<!doctype html" in prefix or b"<html" in prefix or b"<head" in prefix or
                                b"cloudflare" in prefix or b"just a moment..." in prefix or
                                b"403 forbidden" in prefix or b"404 not found" in prefix or b"access denied" in prefix):
                                f.close()
                                if os.path.exists(target_path):
                                    os.remove(target_path)
                                return False
                            trimmed = prefix.strip()
                            if (trimmed.startswith(b"{") or trimmed.startswith(b"[")) and (
                                b'"error"' in trimmed or b'"message"' in trimmed or b'"status"' in trimmed or b'"forbidden"' in trimmed
                            ):
                                f.close()
                                if os.path.exists(target_path):
                                    os.remove(target_path)
                                return False
                            # Never save raw playlists (.m3u8 or .mpd)
                            if b"#extm3u" in prefix or b"<mpd" in prefix:
                                f.close()
                                if os.path.exists(target_path):
                                    os.remove(target_path)
                                return False
                        f.write(chunk)
            return os.path.exists(target_path) and os.path.getsize(target_path) > 0
    except Exception:
        pass

    # 3. Fallback to yt-dlp if direct request failed and target is a video/stream
    if YTDLP_AVAILABLE and (is_stream or any(ext in media_url.lower() for ext in [".mp4", ".mkv", ".webm", ".m3u8", ".mpd", "youtube", "tiktok", "vimeo"])):
        try:
            http_headers = {"User-Agent": ua}
            if referer:
                http_headers["Referer"] = referer
            if cookies:
                http_headers["Cookie"] = cookies
            http_headers.update(extra_headers)

            ydl_opts = {
                "outtmpl": target_path,
                "quiet": True,
                "no_warnings": True,
                "format": "b[vcodec!=none][acodec!=none]/best[vcodec!=none][acodec!=none]/best",
                "http_headers": http_headers
            }
            with yt_dlp.YoutubeDL(ydl_opts) as ydl:
                ydl.download([media_url])
            if os.path.exists(target_path) and os.path.getsize(target_path) > 0:
                with open(target_path, "rb") as f:
                    head = f.read(512).lower()
                if b"#extm3u" in head or b"<mpd" in head:
                    os.remove(target_path)
                    return False
                return True
        except Exception:
            pass

    return False
