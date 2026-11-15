import os

from engine_runtime import (
    YTDLP_AVAILABLE,
    build_http_headers,
    yt_dlp,
)

SOCIAL_OR_VIDEO_HOSTS = (
    "youtube.com",
    "youtu.be",
    "tiktok.com",
    "instagram.com",
    "vimeo.com",
    "twitter.com",
    "x.com",
)

FALLBACK_VIDEO_MARKERS = (
    ".mp4",
    ".mkv",
    ".webm",
    ".m3u8",
    ".mpd",
    "youtube",
    "tiktok",
    "vimeo",
)

INVALID_HTML_MARKERS = (
    b"<!doctype html",
    b"<html",
    b"<head",
    b"cloudflare",
    b"just a moment...",
    b"403 forbidden",
    b"404 not found",
    b"access denied",
)


def _is_playlist_file(target_path):
    with open(target_path, "rb") as f:
        head = f.read(512).lower()
    return b"#extm3u" in head or b"<mpd" in head


def _is_invalid_first_chunk(chunk):
    prefix = chunk[:1024].lower()
    if any(marker in prefix for marker in INVALID_HTML_MARKERS):
        return True

    trimmed = prefix.strip()
    if (trimmed.startswith(b"{") or trimmed.startswith(b"[")) and (
        b'"error"' in trimmed
        or b'"message"' in trimmed
        or b'"status"' in trimmed
        or b'"forbidden"' in trimmed
    ):
        return True

    return b"#extm3u" in prefix or b"<mpd" in prefix


def _download_via_ytdlp(media_url, target_path, http_headers, socket_timeout=None):
    try:
        ydl_opts = {
            "outtmpl": target_path,
            "quiet": True,
            "no_warnings": True,
            "format": "b[vcodec!=none][acodec!=none]/best[vcodec!=none][acodec!=none]/best",
            "http_headers": http_headers,
        }
        if socket_timeout is not None:
            ydl_opts["socket_timeout"] = socket_timeout

        with yt_dlp.YoutubeDL(ydl_opts) as ydl:
            ydl.download([media_url])

        if os.path.exists(target_path) and os.path.getsize(target_path) > 0:
            if _is_playlist_file(target_path):
                os.remove(target_path)
                return False
            return True
    except Exception:
        pass
    return False


def _download_via_requests(media_url, target_path, headers):
    try:
        import requests

        resp = requests.get(media_url, headers=headers, stream=True, timeout=35)
        if resp.status_code not in (200, 206):
            return False

        content_type = (resp.headers.get("content-type") or "").lower()
        if "text/html" in content_type or "application/json" in content_type:
            return False

        parent_dir = os.path.dirname(target_path)
        if parent_dir:
            os.makedirs(parent_dir, exist_ok=True)

        first_chunk = True
        with open(target_path, "wb") as f:
            for chunk in resp.iter_content(chunk_size=65536):
                if not chunk:
                    continue
                if first_chunk:
                    first_chunk = False
                    if _is_invalid_first_chunk(chunk):
                        f.close()
                        if os.path.exists(target_path):
                            os.remove(target_path)
                        return False
                f.write(chunk)

        return os.path.exists(target_path) and os.path.getsize(target_path) > 0
    except Exception:
        return False


def download_media_file(
    media_url,
    target_path,
    referer=None,
    user_agent=None,
    cookies=None,
    headers_json=None,
):
    """
    Downloads media file using yt-dlp (with single pre-muxed format) or requests with cookies/headers.
    Never commits raw .m3u8 or .mpd playlist text as a finished file.
    """
    headers = build_http_headers(
        user_agent=user_agent,
        referer=referer,
        cookies=cookies,
        headers_json=headers_json,
    )
    lower_url = media_url.lower()
    is_stream = (".m3u8" in media_url) or (".mpd" in media_url)
    is_social_or_video = any(host in lower_url for host in SOCIAL_OR_VIDEO_HOSTS)

    # 1. For streams or social pages, prioritize yt-dlp directly
    if (is_stream or is_social_or_video) and YTDLP_AVAILABLE:
        if _download_via_ytdlp(media_url, target_path, headers, socket_timeout=30):
            return True

    # 2. Direct HTTP requests download
    if _download_via_requests(media_url, target_path, headers):
        return True

    # 3. Fallback to yt-dlp if direct request failed and target is a video/stream
    if YTDLP_AVAILABLE and (
        is_stream or any(marker in lower_url for marker in FALLBACK_VIDEO_MARKERS)
    ):
        if _download_via_ytdlp(media_url, target_path, headers):
            return True

    return False
