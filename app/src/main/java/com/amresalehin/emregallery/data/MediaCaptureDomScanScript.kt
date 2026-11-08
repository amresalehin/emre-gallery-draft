package com.amresalehin.emregallery.data

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL


 */
internal fun MediaCaptureEngine.getJsDomScanScriptImpl(): String {
    return """
        (function() {
            var list = [];
            function resolveAbs(u) {
                if (!u) return null;
                try {
                    return new URL(u, document.baseURI || window.location.href).href;
                } catch(e) {
                    if (u.indexOf('//') === 0) return 'https:' + u;
                    return u;
                }
            }

            function isChromeElement(el) {
                if (!el) return false;
                var chromeParent = el.closest('header, nav, footer, aside, menu, form, [role="banner"], [role="navigation"], [role="contentinfo"], .header, .site-header, .nav, .navbar, .site-nav, .menu, #header, #nav, #navbar, #footer, #mw-navigation, #p-logo, .footer, .site-footer, .breadcrumb, .breadcrumbs, .pagination, .toolbar, .ad, .ads, .advertisement, .cookie, .cookie-banner, .consent');
                if (chromeParent) {
                    var contentParent = el.closest('article, main, [role="main"], #content, #main-content, .mw-parser-output, figure, .gallery, .shots-grid, .shots, .dribbble-shot, .entry-content, .post-content');
                    if (!contentParent || chromeParent.contains(contentParent) === false) {
                        return true;
                    }
                }
                var idAndClass = ((el.id || '') + ' ' + (el.className || '') + ' ' + (el.getAttribute('alt') || '')).toLowerCase();
                if (/logo|favicon|avatar_default|badge|donate|donation|fundraising|paypal|magnifying-glass|nav-icon|menu-icon|close-icon|arrow-|chevron|bullet|spinner|1x1/.test(idAndClass)) {
                    return true;
                }
                return false;
            }

            // 1. Deep Image Inspection (img, picture, data attributes, srcset)
            var images = document.querySelectorAll('img, picture source');
            for (var i = 0; i < images.length; i++) {
                var img = images[i];
                if (isChromeElement(img)) continue;

                var candidateUrls = [];

                // Extract srcset highest resolution candidates
                var srcset = img.getAttribute('srcset') || img.getAttribute('data-srcset');
                if (srcset) {
                    var parts = srcset.split(',');
                    for (var p = 0; p < parts.length; p++) {
                        var trimmed = parts[p].trim();
                        if (trimmed) {
                            var splitBits = trimmed.split(/\s+/);
                            var u = splitBits[0];
                            var descriptor = splitBits[1] || '';
                            if (u && u.indexOf('data:') !== 0) {
                                var absSrcset = resolveAbs(u);
                                if (absSrcset) {
                                    list.push({u: absSrcset, s: 'SRCSET', r: descriptor});
                                }
                            }
                        }
                    }
                }

                // Direct src & data-lazy attributes
                var rawSrc = img.src || img.getAttribute('src');
                var dataSrc = img.getAttribute('data-src') || img.getAttribute('data-original') ||
                              img.getAttribute('data-lazy-src') || img.getAttribute('data-large-file') ||
                              img.getAttribute('data-hi-res-src') || img.getAttribute('data-zoom-image');

                if (dataSrc && dataSrc.indexOf('data:') !== 0) {
                    var absData = resolveAbs(dataSrc);
                    if (absData) candidateUrls.push({u: absData, s: 'DATA_SRC'});
                }
                if (rawSrc && rawSrc.indexOf('data:') !== 0) {
                    var absRaw = resolveAbs(rawSrc);
                    if (absRaw) candidateUrls.push({u: absRaw, s: 'DOM'});
                }

                for (var c = 0; c < candidateUrls.length; c++) {
                    list.push(candidateUrls[c]);
                }
            }

            // 2. Video Elements & Sources
            var videos = document.querySelectorAll('video');
            for (var v = 0; v < videos.length; v++) {
                var vid = videos[v];
                var poster = resolveAbs(vid.getAttribute('poster') || vid.poster);
                var chosenVideo = null;
                if (vid.src && !vid.src.startsWith('blob:') && !vid.src.startsWith('data:')) {
                    chosenVideo = resolveAbs(vid.src);
                } else {
                    var vsources = vid.querySelectorAll('source');
                    for (var j = 0; j < vsources.length; j++) {
                        var vsRaw = vsources[j].src;
                        if (vsRaw && !vsRaw.startsWith('blob:') && !vsRaw.startsWith('data:')) {
                            chosenVideo = resolveAbs(vsRaw);
                            break;
                        }
                    }
                }
                if (chosenVideo) {
                    list.push({u: chosenVideo, s: 'DOM', p: poster, r: 'video'});
                } else if (poster) {
                    list.push({u: poster, s: 'POSTER', r: 'poster'});
                }
            }

            // 3. Audio Elements
            var audios = document.querySelectorAll('audio');
            for (var a = 0; a < audios.length; a++) {
                var au = audios[a];
                var chosenAudio = null;
                if (au.src && !au.src.startsWith('blob:') && !au.src.startsWith('data:')) {
                    chosenAudio = resolveAbs(au.src);
                } else {
                    var asources = au.querySelectorAll('source');
                    for (var k = 0; k < asources.length; k++) {
                        var asRaw = asources[k].src;
                        if (asRaw && !asRaw.startsWith('blob:') && !asRaw.startsWith('data:')) {
                            chosenAudio = resolveAbs(asRaw);
                            break;
                        }
                    }
                }
                if (chosenAudio) {
                    list.push({u: chosenAudio, s: 'DOM', r: 'audio'});
                }
            }

            // 4. Schema.org JSON-LD (ImageObject, VideoObject)
            var jsonLdScripts = document.querySelectorAll('script[type="application/ld+json"]');
            for (var jl = 0; jl < jsonLdScripts.length; jl++) {
                try {
                    var parsed = JSON.parse(jsonLdScripts[jl].innerText);
                    var arr = Array.isArray(parsed) ? parsed : [parsed];
                    for (var idx = 0; idx < arr.length; idx++) {
                        var item = arr[idx];
                        if (item.contentUrl) {
                            var cu = resolveAbs(item.contentUrl);
                            if (cu) list.push({u: cu, s: 'DOM', r: 'json-ld'});
                        }
                        if (item.thumbnailUrl) {
                            var tu = resolveAbs(item.thumbnailUrl);
                            if (tu) list.push({u: tu, s: 'DOM', r: 'json-ld-thumb'});
                        }
                        if (item.image && typeof item.image === 'string') {
                            var iu = resolveAbs(item.image);
                            if (iu) list.push({u: iu, s: 'DOM', r: 'json-ld-image'});
                        }
                    }
                } catch(e) {}
            }

            // 5. CSS background-image in content containers
            var bgElements = document.querySelectorAll('main, article, figure, [role="main"], #content, .gallery, .shots-grid, .shots, .photo, .hero');
            var count = Math.min(bgElements.length, 60);
            for (var b = 0; b < count; b++) {
                var bEl = bgElements[b];
                if (bEl.clientWidth >= 120 && bEl.clientHeight >= 120 && !isChromeElement(bEl)) {
                    var style = window.getComputedStyle(bEl);
                    var bg = style.backgroundImage;
                    if (bg && bg.indexOf('url(') !== -1) {
                        var match = bg.match(/url\(['"]?(.*?)['"]?\)/);
                        if (match && match[1] && !match[1].startsWith('data:')) {
                            var bgu = resolveAbs(match[1]);
                            if (bgu) list.push({u: bgu, s: 'CSS', r: 'background'});
                        }
                    }
                }
            }

            // 6. Direct media IFrames
            var iframes = document.querySelectorAll('iframe');
            for (var f = 0; f < iframes.length; f++) {
                var ifr = resolveAbs(iframes[f].src);
                if (ifr && /\.(?:mp4|webm|mkv|mov|mp3|m4a|wav|jpe?g|png|webp|gif|avif)(?:[?#]|$)/i.test(ifr)) {
                    list.push({u: ifr, s: 'IFRAME', r: 'iframe'});
                }
            }

            return JSON.stringify(list);
        })();
    """.trimIndent()
}

/**
 * Shared JavaScript MutationObserver injection for dynamic DOM additions.
 */
