import json

from engine_runtime import (
    POPULAR_EXTRACTORS,
    check_support,
    get_engine_status,
)
from gdl_extractor_runner import extract_gallery_dl
from media_downloader import download_media_file
from ytdlp_extractor_runner import extract_yt_dlp


def _collect_items(result_json, all_items, seen_urls, current_title):
    try:
        parsed = json.loads(result_json)
        if not current_title and parsed.get("pageTitle"):
            current_title = parsed["pageTitle"]
        for itm in parsed.get("items", []):
            url = itm.get("url")
            if url and url not in seen_urls:
                seen_urls.add(url)
                all_items.append(itm)
    except Exception:
        pass
    return current_title


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
            "items": [],
        })

    all_items = []
    seen_urls = set()
    page_title = ""

    # 1. Run gallery-dl
    page_title = _collect_items(
        extract_gallery_dl(clean_url, options_json),
        all_items,
        seen_urls,
        page_title,
    )

    # 2. Run yt-dlp (ALWAYS run, even if gallery-dl found items, e.g. embedded video in social post)
    page_title = _collect_items(
        extract_yt_dlp(clean_url, options_json),
        all_items,
        seen_urls,
        page_title,
    )

    return json.dumps({
        "success": len(all_items) > 0,
        "engine": "hybrid (gallery-dl + yt-dlp)",
        "pageTitle": str(page_title) if page_title else "",
        "itemsCount": len(all_items),
        "items": all_items,
    })


__all__ = [
    "POPULAR_EXTRACTORS",
    "get_engine_status",
    "check_support",
    "extract_gallery_dl",
    "extract_yt_dlp",
    "extract_media",
    "download_media_file",
]
