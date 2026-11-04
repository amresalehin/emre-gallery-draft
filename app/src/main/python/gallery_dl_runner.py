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
    """Return independently detected capabilities for both supported extractors."""
    if not url:
        return json.dumps({"supported": False, "gallery_dl_supported": False, "yt_dlp_supported": False})

    clean_url = url.strip()
    gallery_supported = False
    gallery_category = ""
    gallery_name = ""
    if GALLERY_DL_AVAILABLE:
        try:
            ext = gdl_extractor.find(clean_url)
            if ext is not None:
                gallery_supported = True
                gallery_category = getattr(ext, "category", "") or ext.__class__.__name__.lower().replace("extractor", "")
                gallery_name = ext.__class__.__name__
        except Exception:
            pass

    yt_supported = False
    yt_category = ""
    yt_name = ""
    if YTDLP_AVAILABLE:
        try:
            for ie in yt_dlp.extractor.gen_extractors():
                ie_name = getattr(ie, "IE_NAME", "") or ie.__class__.__name__
                if ie_name.lower() in ("generic", "default"):
                    continue
                if ie.suitable(clean_url):
                    yt_supported = True
                    yt_name = ie_name
                    yt_category = "audio" if "soundcloud" in ie_name.lower() else "video"
                    break
        except Exception:
            pass

    preferred = "yt-dlp" if yt_supported else ("gallery-dl" if gallery_supported else "")
    return json.dumps({
        "supported": gallery_supported or yt_supported,
        "gallery_dl_supported": gallery_supported,
        "yt_dlp_supported": yt_supported,
        "preferred_engine": preferred,
        "category": yt_category or gallery_category,
        "subcategory": "",
        "extractor": yt_name or gallery_name,
        "display_name": yt_name or gallery_name
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

def _format_quality_score(fmt):
    if not isinstance(fmt, dict):
        return (0, 0.0, 0.0, 0)
    width = int(fmt.get("width") or 0)
    height = int(fmt.get("height") or 0)
    fps = float(fmt.get("fps") or 0.0)
    bitrate = float(fmt.get("tbr") or fmt.get("vbr") or fmt.get("abr") or 0.0)
    size = int(fmt.get("filesize") or fmt.get("filesize_approx") or 0)
    return (width * height, fps, bitrate, size)


def _has_video(fmt):
    return isinstance(fmt, dict) and fmt.get("vcodec") not in (None, "none")


def _has_audio(fmt):
    return isinstance(fmt, dict) and fmt.get("acodec") not in (None, "none")


def extract_yt_dlp(url, options_json="{}"):
    """
    Return one logical item per video/audio post, with split A/V represented as linked streams.
    Thumbnail URLs are preview metadata only and never become downloadable media items.
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
    try:
        ydl_opts = {
            "quiet": True,
            "no_warnings": True,
            "skip_download": True,
            "format": "bv*+ba/b",
            "socket_timeout": int(opts.get("timeout", 30)),
            "user_agent": user_agent,
            "noplaylist": False
        }
        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            info = ydl.extract_info(clean_url, download=False)
            if not info:
                raise ValueError("yt-dlp returned no media metadata")

            for entry in (info.get("entries") or [info]):
                if not isinstance(entry, dict):
                    continue

                formats = [f for f in (entry.get("formats") or []) if isinstance(f, dict) and f.get("url")]
                requested = [f for f in (entry.get("requested_formats") or []) if isinstance(f, dict) and f.get("url")]
                candidates = requested if requested else formats

                video_only = [f for f in candidates if _has_video(f) and not _has_audio(f)]
                audio_only = [f for f in candidates if _has_audio(f) and not _has_video(f)]
                muxed = [f for f in candidates if _has_video(f) and _has_audio(f)]

                video_format = max(video_only, key=_format_quality_score) if video_only else None
                audio_format = max(audio_only, key=_format_quality_score) if audio_only else None

                # If the selector metadata is incomplete, inspect the available format list.
                if not requested and formats:
                    all_video = [f for f in formats if _has_video(f) and not _has_audio(f)]
                    all_audio = [f for f in formats if _has_audio(f) and not _has_video(f)]
                    if not video_format and all_video:
                        video_format = max(all_video, key=_format_quality_score)
                    if not audio_format and all_audio:
                        audio_format = max(all_audio, key=_format_quality_score)

                best_muxed = max(muxed, key=_format_quality_score) if muxed else None
                if video_format and not audio_format and best_muxed:
                    # Do not choose a silent video-only format when no separate audio rendition exists.
                    video_format = None

                if video_format:
                    primary = video_format
                    is_audio_only = False
                elif best_muxed:
                    primary = best_muxed
                    audio_format = None
                    is_audio_only = False
                elif audio_format:
                    primary = audio_format
                    is_audio_only = True
                else:
                    primary = entry if entry.get("url") else None
                    if not primary:
                        continue
                    is_audio_only = entry.get("vcodec") == "none"

                direct_url = primary.get("url")
                if not direct_url:
                    continue
                separate_audio_url = None
                if not is_audio_only and audio_format and audio_format.get("url") != direct_url:
                    separate_audio_url = audio_format.get("url")

                vcodec = primary.get("vcodec")
                acodec = primary.get("acodec")
                has_audio = bool(separate_audio_url) or _has_audio(primary)
                is_stream = ".m3u8" in direct_url.lower() or ".mpd" in direct_url.lower()
                category = "AUDIO" if is_audio_only else ("STREAMS" if is_stream else "VIDEOS")

                media_id = str(entry.get("id") or primary.get("format_id") or abs(hash(direct_url)))
                extractor_key = str(entry.get("extractor_key") or entry.get("extractor") or "yt-dlp")
                canonical_id = extractor_key + ":" + media_id

                title = str(entry.get("title") or entry.get("id") or "media")
                ext = str(primary.get("ext") or entry.get("ext") or ("mkv" if separate_audio_url else "mp4")).lower()
                item_title = title if title.lower().endswith("." + ext) else title + "." + ext
                if not page_title:
                    page_title = str(info.get("title") or title)

                width = primary.get("width") or entry.get("width")
                height = primary.get("height") or entry.get("height")
                resolution_hint = str(width) + "x" + str(height) if width and height else primary.get("resolution")
                video_size = primary.get("filesize") or primary.get("filesize_approx") or 0
                audio_size = (audio_format or {}).get("filesize") or (audio_format or {}).get("filesize_approx") or 0
                try:
                    total_size = int(video_size or 0) + int(audio_size or 0)
                except Exception:
                    total_size = None
                if not total_size:
                    total_size = None

                fps = primary.get("fps") or entry.get("fps")
                tbr = primary.get("tbr") or entry.get("tbr")
                items.append({
                    "id": "ytdlp_" + canonical_id,
                    "canonicalMediaId": canonical_id,
                    "url": direct_url,
                    "audioUrl": separate_audio_url,
                    "hasAudio": has_audio,
                    "audioCodec": (audio_format or {}).get("acodec") or acodec,
                    "videoCodec": vcodec,
                    "title": item_title,
                    "category": category,
                    "format": ext.upper(),
                    "mimeType": "audio/" + ext if is_audio_only else ("application/x-mpegURL" if is_stream else "video/" + ext),
                    "isVideo": not is_audio_only,
                    "isAudio": is_audio_only,
                    "isStream": is_stream,
                    "thumbnailUrl": entry.get("thumbnail"),
                    "resolutionHint": resolution_hint,
                    "width": width,
                    "height": height,
                    "frameRate": fps,
                    "sizeBytes": total_size,
                    "metadata": {
                        "fps": str(fps or ""),
                        "tbr": str(tbr or ""),
                        "audio_codec": str((audio_format or {}).get("acodec") or acodec or ""),
                        "video_codec": str(vcodec or "")
                    },
                    "source": "yt-dlp"
                })

    except Exception as e:
        return json.dumps({"success": False, "error": f"{e.__class__.__name__}: {str(e)}", "items": []})

    return json.dumps({
        "success": len(items) > 0,
        "engine": "yt-dlp",
        "pageTitle": str(page_title) if page_title else "",
        "itemsCount": len(items),
        "items": items
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
                            prefix = chunk[:1024].lower()
                            if b"<!doctype html" in prefix or b"<html" in prefix or b"<head" in prefix or b"cloudflare" in prefix or b"just a moment..." in prefix or b"403 forbidden" in prefix or b"404 not found" in prefix or b"access denied" in prefix:
                                f.close()
                                if os.path.exists(target_path):
                                    os.remove(target_path)
                                return False
                            trimmed = prefix.strip()
                            if (trimmed.startswith(b"{") or trimmed.startswith(b"[")) and (b'"error"' in trimmed or b'"message"' in trimmed or b'"status"' in trimmed or b'"forbidden"' in trimmed):
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
