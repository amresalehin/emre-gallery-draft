package com.amresalehin.emregallery.data

import com.chaquo.python.Python
import org.json.JSONObject

data class SupportedSiteItem(
    val name: String,
    val domain: String,
    val category: String,
    val engine: String,
    val exampleUrl: String,
    val description: String
)

/**
 * Official supported sites catalog backed by yt-dlp and gallery-dl extractors.
 * Provides curated top platforms and dynamic Python query capability.
 */
object SupportedSitesRepository {

    val curatedSites: List<SupportedSiteItem> = listOf(
        // Video & Streams (yt-dlp primary)
        SupportedSiteItem(
            name = "YouTube",
            domain = "youtube.com",
            category = "Video",
            engine = "yt-dlp",
            exampleUrl = "https://www.youtube.com/watch?v=...",
            description = "Videos, Shorts, Playlists, Channels, live streams"
        ),
        SupportedSiteItem(
            name = "TikTok",
            domain = "tiktok.com",
            category = "Video",
            engine = "yt-dlp",
            exampleUrl = "https://www.tiktok.com/@user/video/...",
            description = "Short-form vertical video, sounds, and profiles"
        ),
        SupportedSiteItem(
            name = "Instagram",
            domain = "instagram.com",
            category = "Social",
            engine = "yt-dlp / gallery-dl",
            exampleUrl = "https://www.instagram.com/reel/...",
            description = "Reels, Posts, Carousel albums, Stories, IGTV"
        ),
        SupportedSiteItem(
            name = "Reddit",
            domain = "reddit.com",
            category = "Social",
            engine = "gallery-dl / yt-dlp",
            exampleUrl = "https://www.reddit.com/r/.../comments/...",
            description = "Media posts, image galleries, v.redd.it videos"
        ),
        SupportedSiteItem(
            name = "Twitter / X",
            domain = "x.com",
            category = "Social",
            engine = "yt-dlp / gallery-dl",
            exampleUrl = "https://x.com/user/status/...",
            description = "High-resolution images, video clips, GIFs"
        ),
        SupportedSiteItem(
            name = "Pinterest",
            domain = "pinterest.com",
            category = "Image",
            engine = "gallery-dl",
            exampleUrl = "https://www.pinterest.com/pin/...",
            description = "Original full-size photo pins and boards"
        ),
        SupportedSiteItem(
            name = "Imgur",
            domain = "imgur.com",
            category = "Image",
            engine = "gallery-dl",
            exampleUrl = "https://imgur.com/a/...",
            description = "High-resolution image albums and animated GIFs"
        ),
        SupportedSiteItem(
            name = "Vimeo",
            domain = "vimeo.com",
            category = "Video",
            engine = "yt-dlp",
            exampleUrl = "https://vimeo.com/...",
            description = "High definition creative films and documentary streams"
        ),
        SupportedSiteItem(
            name = "SoundCloud",
            domain = "soundcloud.com",
            category = "Audio",
            engine = "yt-dlp",
            exampleUrl = "https://soundcloud.com/artist/track",
            description = "High-bitrate music tracks, DJ mixes, playlists"
        ),
        SupportedSiteItem(
            name = "Bandcamp",
            domain = "bandcamp.com",
            category = "Audio",
            engine = "yt-dlp",
            exampleUrl = "https://artist.bandcamp.com/album/...",
            description = "Independent music albums, discographies, tracks"
        ),
        SupportedSiteItem(
            name = "Twitch",
            domain = "twitch.tv",
            category = "Video",
            engine = "yt-dlp",
            exampleUrl = "https://www.twitch.tv/videos/...",
            description = "Gaming VODs, highlights, and community clips"
        ),
        SupportedSiteItem(
            name = "Pixiv",
            domain = "pixiv.net",
            category = "Image",
            engine = "gallery-dl",
            exampleUrl = "https://www.pixiv.net/artworks/...",
            description = "Original anime art, manga chapters, illustrations"
        ),
        SupportedSiteItem(
            name = "ArtStation",
            domain = "artstation.com",
            category = "Image",
            engine = "gallery-dl",
            exampleUrl = "https://www.artstation.com/artwork/...",
            description = "High-fidelity digital concept art and portfolios"
        ),
        SupportedSiteItem(
            name = "DeviantArt",
            domain = "deviantart.com",
            category = "Image",
            engine = "gallery-dl",
            exampleUrl = "https://www.deviantart.com/artist/art/...",
            description = "Original digital illustrations, fanart, galleries"
        ),
        SupportedSiteItem(
            name = "Flickr",
            domain = "flickr.com",
            category = "Image",
            engine = "gallery-dl",
            exampleUrl = "https://www.flickr.com/photos/user/...",
            description = "High-resolution RAW/JPEG photography albums"
        ),
        SupportedSiteItem(
            name = "Tumblr",
            domain = "tumblr.com",
            category = "Social",
            engine = "gallery-dl / yt-dlp",
            exampleUrl = "https://user.tumblr.com/post/...",
            description = "Blog photosets, GIFs, video posts"
        ),
        SupportedSiteItem(
            name = "Dailymotion",
            domain = "dailymotion.com",
            category = "Video",
            engine = "yt-dlp",
            exampleUrl = "https://www.dailymotion.com/video/...",
            description = "Global video content and web broadcasts"
        ),
        SupportedSiteItem(
            name = "Bilibili",
            domain = "bilibili.com",
            category = "Video",
            engine = "yt-dlp / gallery-dl",
            exampleUrl = "https://www.bilibili.com/video/...",
            description = "HD anime, community videos, illustrations"
        ),
        SupportedSiteItem(
            name = "Rumble",
            domain = "rumble.com",
            category = "Video",
            engine = "yt-dlp",
            exampleUrl = "https://rumble.com/v...",
            description = "Video streams and community channels"
        ),
        SupportedSiteItem(
            name = "Wallhaven",
            domain = "wallhaven.cc",
            category = "Image",
            engine = "gallery-dl",
            exampleUrl = "https://wallhaven.cc/w/...",
            description = "Ultra high-resolution 4K/8K wallpapers"
        ),
        SupportedSiteItem(
            name = "Mixcloud",
            domain = "mixcloud.com",
            category = "Audio",
            engine = "yt-dlp",
            exampleUrl = "https://www.mixcloud.com/dj/mix/",
            description = "Long-form DJ sets, radio broadcasts, podcasts"
        ),
        SupportedSiteItem(
            name = "MangaDex",
            domain = "mangadex.org",
            category = "Image",
            engine = "gallery-dl",
            exampleUrl = "https://mangadex.org/chapter/...",
            description = "High-res manga chapter pages and covers"
        ),
        SupportedSiteItem(
            name = "Danbooru",
            domain = "danbooru.donmai.us",
            category = "Image",
            engine = "gallery-dl",
            exampleUrl = "https://danbooru.donmai.us/posts/...",
            description = "Tagged anime artboard illustrations"
        ),
        SupportedSiteItem(
            name = "Threads",
            domain = "threads.net",
            category = "Social",
            engine = "yt-dlp / gallery-dl",
            exampleUrl = "https://www.threads.net/@user/post/...",
            description = "Photos, video clips, and conversation threads"
        ),
        SupportedSiteItem(
            name = "Bluesky",
            domain = "bsky.app",
            category = "Social",
            engine = "gallery-dl",
            exampleUrl = "https://bsky.app/profile/user/post/...",
            description = "AT protocol public photo and video posts"
        )
    )

    fun querySupportedSites(query: String = "", category: String = "All"): List<SupportedSiteItem> {
        val q = query.trim().lowercase()
        val cat = category.trim().lowercase()

        // 1. Filter curated
        val filteredCurated = curatedSites.filter { item ->
            val matchCat = cat == "all" || item.category.lowercase() == cat ||
                    (cat == "video" && item.category.contains("Video", ignoreCase = true)) ||
                    (cat == "image" && item.category.contains("Image", ignoreCase = true)) ||
                    (cat == "audio" && item.category.contains("Audio", ignoreCase = true)) ||
                    (cat == "social" && item.category.contains("Social", ignoreCase = true))

            val matchQ = q.isBlank() ||
                    item.name.lowercase().contains(q) ||
                    item.domain.lowercase().contains(q) ||
                    item.description.lowercase().contains(q)

            matchCat && matchQ
        }

        // 2. Query dynamic Python extractors if available and query is non-empty
        if (q.isNotBlank() && GalleryDlEngine.isAvailable()) {
            val dynamicList = fetchDynamicExtractors(q, category)
            val combined = (filteredCurated + dynamicList).distinctBy { it.domain }
            return combined
        }

        return filteredCurated
    }

    private fun fetchDynamicExtractors(query: String, category: String): List<SupportedSiteItem> {
        return try {
            val py = Python.getInstance()
            val runner = py.getModule("gallery_dl_runner")
            val opts = JSONObject().apply {
                put("query", query)
                put("category", category)
                put("limit", 60)
            }.toString()

            val raw = runner.callAttr("get_supported_sites", opts).toString()
            val root = JSONObject(raw)
            val arr = root.optJSONArray("results") ?: return emptyList()

            val list = mutableListOf<SupportedSiteItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val name = obj.optString("name")
                val domain = obj.optString("domain")
                val cat = obj.optString("category", "General")
                val engine = obj.optString("engine", "yt-dlp")
                list.add(
                    SupportedSiteItem(
                        name = name,
                        domain = domain,
                        category = cat,
                        engine = engine,
                        exampleUrl = "https://$domain",
                        description = "Official $engine extractor for $name"
                    )
                )
            }
            list
        } catch (_: Exception) {
            emptyList()
        }
    }
}
