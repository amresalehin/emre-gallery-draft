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
from gallery_dl_support import _prepare_cookiefile

def _download_with_ytdlp(url, target_path, referer, user_agent, cookies, extra_headers):
    """Downloads media exclusively via yt-dlp."""
    if not YTDLP_AVAILABLE:
        return False
    try:
        http_headers = {
            "User-Agent": user_agent or "Mozilla/5.0 (Linux; Android 14; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.6778.200 Mobile Safari/537.36",
            "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "Accept-Language": "en-US,en;q=0.9",
            "Sec-Ch-Ua": '"Chromium";v="131", "Not_A Brand";v="24"',
            "Sec-Ch-Ua-Mobile": "?1",
            "Sec-Ch-Ua-Platform": '"Android"',
        }
        if referer:
            http_headers["Referer"] = referer
        if cookies:
            http_headers["Cookie"] = cookies
        if extra_headers:
            http_headers.update(extra_headers)

        cookie_file = _prepare_cookiefile(cookies, url)
        ydl_opts = {
            "outtmpl": target_path,
            "quiet": True,
            "no_warnings": True,
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
            ydl.download([url])

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

def _download_with_gallery_dl(url, target_path, referer, user_agent, cookies, extra_headers):
    """Downloads media via gallery-dl job runner or gallery-dl network session."""
    if not GALLERY_DL_AVAILABLE:
        return False
    try:
        with _gdl_lock:
            gdl_config.set(("extractor",), "user-agent", user_agent or "Mozilla/5.0")
            cookie_file = _prepare_cookiefile(cookies, url)
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

            target_dir = os.path.dirname(target_path)
            target_filename = os.path.basename(target_path)
            os.makedirs(target_dir, exist_ok=True)

            ext = gdl_extractor.find(url)
            if ext:
                gdl_config.set((), "base-directory", target_dir)
                gdl_config.set((), "directory", [""])
                gdl_config.set((), "filename", target_filename)
                job = gdl_job.DownloadJob(ext)
                job.run()
                if os.path.exists(target_path) and os.path.getsize(target_path) > 0:
                    return True

            from gallery_dl import net
            session = net.HttpUserAgent()
            if user_agent:
                session.headers["User-Agent"] = user_agent
            if referer:
                session.headers["Referer"] = referer
            if cookies:
                session.headers["Cookie"] = cookies
            if extra_headers:
                session.headers.update(extra_headers)

            resp = session.request("GET", url, stream=True, timeout=35)
            if resp.status_code in [200, 206]:
                content_type = (resp.headers.get("content-type") or "").lower()
                if "text/html" in content_type or "application/json" in content_type:
                    return False
                with open(target_path, "wb") as f:
                    for chunk in resp.iter_content(chunk_size=65536):
                        if chunk:
                            f.write(chunk)
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

def download_media_file(media_url, target_path, referer=None, user_agent=None, cookies=None, headers_json=None):
    """
    Downloads media file using yt-dlp (primary) and gallery-dl (secondary fallback).
    Ditches all other engines/workflows.
    If yt-dlp supports the task, handled exclusively by yt-dlp.
    Goes to gallery-dl if yt-dlp is not supported or fails and that is supported by gallery-dl.
    """
    if not media_url:
        return False

    extra_headers = {}
    if headers_json:
        try:
            parsed = json.loads(headers_json)
            if isinstance(parsed, dict):
                extra_headers = parsed
        except Exception:
            pass

    ua = user_agent or "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"

    # 1. Primary: yt-dlp
    if is_ytdlp_supported(media_url) or (referer and is_ytdlp_supported(referer)):
        if _download_with_ytdlp(media_url, target_path, referer, ua, cookies, extra_headers):
            return True

    # 2. Secondary fallback: gallery-dl
    if is_gallery_dl_supported(media_url) or (referer and is_gallery_dl_supported(referer)):
        if _download_with_gallery_dl(media_url, target_path, referer, ua, cookies, extra_headers):
            return True

    # 3. Fallback: try yt-dlp then gallery-dl if not caught by heuristics
    if _download_with_ytdlp(media_url, target_path, referer, ua, cookies, extra_headers):
        return True

    if _download_with_gallery_dl(media_url, target_path, referer, ua, cookies, extra_headers):
        return True

    return False
