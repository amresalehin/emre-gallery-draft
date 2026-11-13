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
    Selects an audio-capable page format and preserves the original webpage URL for save-time extraction.
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
            # Discovery must retain every representation, including silent video-only
            # and audio-only entries. The downloader chooses exact formats later.
            "socket_timeout": 20,
            "http_headers": {
                "User-Agent": user_agent,
                **({"Cookie": str(opts.get("cookies"))} if opts.get("cookies") else {}),
                **({"Referer": str(opts.get("referer"))} if opts.get("referer") else {})
            }
        }
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(clean_url, download=False)
            if info:
                entries = info.get("entries") or [info]
                for entry in entries:
                    if not entry or not isinstance(entry, dict):
                        continue

                    # For social videos, preserve page URL so yt-dlp re-extracts non-expired muxed streams at save time
                    page_url = entry.get("webpage_url") or clean_url
                    direct_url = page_url

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

                    selected_tracks = select_yt_dlp_formats(entry.get("formats") or [])
                    is_audio_only = bool(selected_tracks and selected_tracks.get("kind") == "audio_only")
                    has_video = bool(selected_tracks and selected_tracks.get("has_video"))
                    has_audio = bool(selected_tracks and selected_tracks.get("has_audio"))
                    if not selected_tracks:
                        is_audio_only = entry.get("vcodec") == "none" or v_ext in ["mp3", "m4a", "m4b", "opus", "aac"]
                        has_video = not is_audio_only
                        has_audio = is_audio_only or entry.get("acodec") not in (None, "none")
                    is_stream = (".m3u8" in clean_url) or (".mpd" in clean_url)
                    is_video = has_video and not is_audio_only
                    category = "AUDIO" if is_audio_only else ("STREAMS" if is_stream else "VIDEOS")

                    url_hash = hashlib.sha256(direct_url.encode('utf-8')).hexdigest()[:12]
                    items.append({
                        "id": f"ytdlp_{len(items)}_{url_hash}",
                        "url": direct_url,
                        "title": item_title,
                        "category": category,
                        "format": ("HLS/DASH" if is_stream else ("AUDIO" if is_audio_only else ("VIDEO" if v_ext in ["m4a", "m4b", "aac", "opus", "mp3"] else v_ext.upper()))),
                        "mimeType": ("audio/vnd.apple.mpegurl" if is_stream and is_audio_only else ("application/x-mpegURL" if is_stream else (f"audio/{v_ext}" if is_audio_only else f"video/{'mp4' if v_ext in ['m4a', 'm4b', 'aac', 'opus', 'mp3'] else v_ext}"))),
                        "isVideo": is_video,
                        "isAudio": is_audio_only,
                        "isStream": is_stream,
                        "hasAudio": has_audio,
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


def _format_sort_key(fmt, video=True):
    """Stable quality ordering for yt-dlp format dictionaries."""
    def number(key, default=0):
        try:
            value = fmt.get(key)
            return float(value) if value is not None else default
        except (TypeError, ValueError):
            return default

    if video:
        return (
            number("height"), number("width"), number("fps"),
            number("vbr"), number("tbr"), number("filesize") or number("filesize_approx")
        )
    return (
        number("quality"), number("abr"), number("asr"),
        number("tbr"), number("filesize") or number("filesize_approx")
    )


def select_yt_dlp_formats(formats):
    """Choose separate best video/audio tracks when available, otherwise a muxed format."""
    if not isinstance(formats, list):
        return None

    usable = []
    for fmt in formats:
        if not isinstance(fmt, dict) or not fmt.get("format_id") or fmt.get("has_drm"):
            continue
        protocol = str(fmt.get("protocol") or "").lower()
        if protocol in ("mhtml", "http_dash_segments") and not fmt.get("url"):
            continue
        usable.append(fmt)

    def has_codec(value):
        return bool(value) and str(value).lower() != "none"

    video_only = [f for f in usable if has_codec(f.get("vcodec")) and not has_codec(f.get("acodec"))]
    audio_only = [f for f in usable if has_codec(f.get("acodec")) and not has_codec(f.get("vcodec"))]
    muxed = [f for f in usable if has_codec(f.get("vcodec")) and has_codec(f.get("acodec"))]

    if video_only and audio_only:
        video_fmt = max(video_only, key=lambda f: _format_sort_key(f, video=True))
        audio_fmt = max(audio_only, key=lambda f: _format_sort_key(f, video=False))
        return {
            "kind": "split",
            "video_format_id": str(video_fmt["format_id"]),
            "audio_format_id": str(audio_fmt["format_id"]),
            "has_video": True,
            "has_audio": True,
        }

    if muxed:
        fmt = max(muxed, key=lambda f: _format_sort_key(f, video=True))
        return {
            "kind": "single",
            "format_id": str(fmt["format_id"]),
            "has_video": True,
            "has_audio": True,
        }

    # Preserve legitimate video-only / audio-only media. The Kotlin layer decides
    # whether that result satisfies the discovered item's declared media type.
    if video_only:
        fmt = max(video_only, key=lambda f: _format_sort_key(f, video=True))
        return {"kind": "video_only", "format_id": str(fmt["format_id"]), "has_video": True, "has_audio": False}
    if audio_only:
        fmt = max(audio_only, key=lambda f: _format_sort_key(f, video=False))
        return {"kind": "audio_only", "format_id": str(fmt["format_id"]), "has_video": False, "has_audio": True}
    return None


def _resolve_ytdlp_download_path(ydl, info, work_dir, prefix):
    """Resolve the actual output file returned by yt-dlp, including postprocessor renames."""
    candidates = []
    if isinstance(info, dict):
        for entry in info.get("requested_downloads") or []:
            if isinstance(entry, dict):
                for key in ("filepath", "_filename"):
                    if entry.get(key):
                        candidates.append(entry[key])
        for key in ("filepath", "_filename"):
            if info.get(key):
                candidates.append(info[key])
        try:
            candidates.append(ydl.prepare_filename(info))
        except Exception:
            pass

    for candidate in candidates:
        try:
            path = os.path.abspath(str(candidate))
            if os.path.isfile(path) and os.path.getsize(path) > 0:
                return path
        except Exception:
            continue

    try:
        matches = sorted(
            os.path.join(work_dir, name)
            for name in os.listdir(work_dir)
            if name.startswith(prefix + ".")
            and not name.endswith((".part", ".ytdl", ".tmp"))
            and os.path.isfile(os.path.join(work_dir, name))
            and os.path.getsize(os.path.join(work_dir, name)) > 0
        )
        return matches[0] if matches else None
    except Exception:
        return None


def _download_ytdlp_format(url, format_id, work_dir, prefix, http_headers):
    """Download one exact yt-dlp format without triggering an external merger."""
    os.makedirs(work_dir, exist_ok=True)
    outtmpl = os.path.join(work_dir, prefix + ".%(ext)s")
    ydl_opts = {
        "format": str(format_id),
        "outtmpl": outtmpl,
        "quiet": True,
        "no_warnings": True,
        "noplaylist": True,
        "continuedl": False,
        "overwrites": True,
        "socket_timeout": 30,
        "retries": 5,
        "fragment_retries": 10,
        "file_access_retries": 5,
        "skip_unavailable_fragments": False,
        "concurrent_fragment_downloads": 1,
        "http_headers": http_headers,
    }
    with yt_dlp.YoutubeDL(ydl_opts) as ydl:
        info = ydl.extract_info(url, download=True)
        path = _resolve_ytdlp_download_path(ydl, info, work_dir, prefix)
    if not path:
        raise RuntimeError("yt-dlp reported completion but no non-empty output file was found")
    return path


def download_yt_dlp_tracks(url, work_dir, headers_json="{}"):
    """Resolve a page and download the best audio/video tracks independently.

    The function deliberately does not mux. Android's bundled FFmpegKit performs
    muxing and probes the output before the caller moves it into the media library.
    """
    clean_url = str(url or "").strip()
    if not clean_url or not YTDLP_AVAILABLE:
        return json.dumps({"handled": False, "success": False, "error": "yt-dlp unavailable or empty source URL"})

    try:
        extra_headers = json.loads(headers_json) if headers_json else {}
        if not isinstance(extra_headers, dict):
            extra_headers = {}
    except Exception:
        extra_headers = {}

    http_headers = {str(k): str(v) for k, v in extra_headers.items() if v is not None}
    if not any(k.lower() == "user-agent" for k in http_headers):
        http_headers["User-Agent"] = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    selection = None
    try:
        os.makedirs(work_dir, exist_ok=True)
        inspect_opts = {
            "quiet": True,
            "no_warnings": True,
            "skip_download": True,
            "noplaylist": True,
            "socket_timeout": 30,
            "retries": 3,
            "http_headers": http_headers,
            "extract_flat": False,
        }
        with yt_dlp.YoutubeDL(inspect_opts) as ydl:
            info = ydl.extract_info(clean_url, download=False)

        if isinstance(info, dict) and info.get("entries"):
            info = next((entry for entry in info["entries"] if isinstance(entry, dict)), None)
        if not isinstance(info, dict):
            return json.dumps({"handled": False, "success": False, "error": "No media entry returned by yt-dlp"})

        formats = info.get("formats") or []
        selection = select_yt_dlp_formats(formats)
        if selection is None:
            # A direct-media extractor may expose a single format on the top-level info.
            top = dict(info)
            top.setdefault("format_id", info.get("format_id") or "best")
            selection = select_yt_dlp_formats([top])
        if selection is None:
            return json.dumps({"handled": False, "success": False, "error": "yt-dlp found no usable audio/video formats"})

        source_url = info.get("webpage_url") or info.get("original_url") or clean_url
        title = info.get("title") or info.get("id") or "media"
        result = {
            "handled": True,
            "success": False,
            "kind": selection["kind"],
            "hasVideo": bool(selection.get("has_video")),
            "hasAudio": bool(selection.get("has_audio")),
            "title": str(title),
            "sourceUrl": str(source_url),
        }

        if selection["kind"] == "split":
            video_path = _download_ytdlp_format(source_url, selection["video_format_id"], work_dir, "video", http_headers)
            audio_path = _download_ytdlp_format(source_url, selection["audio_format_id"], work_dir, "audio", http_headers)
            result.update({"success": True, "videoPath": video_path, "audioPath": audio_path})
        else:
            media_path = _download_ytdlp_format(source_url, selection["format_id"], work_dir, "media", http_headers)
            result.update({"success": True, "mediaPath": media_path})

        return json.dumps(result)
    except Exception as exc:
        # Only block a raw-URL fallback once an actual representation was selected.
        # Extraction/network failures before selection should still allow direct HTTP.
        return json.dumps({
            "handled": selection is not None,
            "success": False,
            "hasVideo": bool(selection and selection.get("has_video")),
            "hasAudio": bool(selection and selection.get("has_audio")),
            "error": "%s: %s" % (exc.__class__.__name__, str(exc)),
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
    Downloads direct media or a genuinely muxed yt-dlp format. Separate-track downloads
    are handled by download_yt_dlp_tracks and the Android FFmpegKit muxer.
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

            ydl_opts = {
                "outtmpl": target_path,
                "quiet": True,
                "no_warnings": True,
                "format": "best[vcodec!=none][acodec!=none]/best[acodec!=none]",
                "socket_timeout": 30,
                "http_headers": http_headers
            }
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
                "format": "best[vcodec!=none][acodec!=none]/best[acodec!=none]",
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
