package com.amresalehin.emregallery.data

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL


internal fun MediaCaptureEngine.getMutationObserverScriptImpl(): String {
    return """
        (function() {
            if (window.__emreObserverInstalled) return;
            window.__emreObserverInstalled = true;

            function report(u) {
                if (!u || u.indexOf('data:') === 0 || u.indexOf('blob:') === 0) return;
                try {
                    var abs = new URL(u, document.baseURI || window.location.href).href;
                    if (window.EmreBridge && window.EmreBridge.reportMedia) {
                        window.EmreBridge.reportMedia(abs);
                    }
                } catch(e) {}
            }

            function checkNode(node) {
                if (!node || node.nodeType !== 1) return;
                var tag = node.tagName ? node.tagName.toUpperCase() : '';
                if (tag === 'IMG') {
                    report(node.src);
                    report(node.getAttribute('data-src'));
                    report(node.getAttribute('data-original'));
                    report(node.getAttribute('data-lazy-src'));
                    report(node.getAttribute('data-large-file'));
                    report(node.getAttribute('data-hi-res-src'));
                    var srcset = node.getAttribute('srcset') || node.getAttribute('data-srcset');
                    if (srcset) {
                        var parts = srcset.split(',');
                        for (var i = 0; i < parts.length; i++) {
                            var bit = parts[i].trim().split(/\s+/)[0];
                            if (bit) report(bit);
                        }
                    }
                } else if (tag === 'VIDEO') {
                    if (node.src && node.src.indexOf('blob:') !== 0 && node.src.indexOf('data:') !== 0) {
                        report(node.src);
                    } else if (node.querySelectorAll) {
                        var vSources = node.querySelectorAll('source');
                        for (var vs = 0; vs < vSources.length; vs++) {
                            var vsSrc = vSources[vs].src;
                            if (vsSrc && vsSrc.indexOf('blob:') !== 0 && vsSrc.indexOf('data:') !== 0) {
                                report(vsSrc);
                                break;
                            }
                        }
                    }
                    report(node.getAttribute('poster'));
                } else if (tag === 'AUDIO') {
                    if (node.src && node.src.indexOf('blob:') !== 0 && node.src.indexOf('data:') !== 0) {
                        report(node.src);
                    } else if (node.querySelectorAll) {
                        var aSources = node.querySelectorAll('source');
                        for (var as = 0; as < aSources.length; as++) {
                            var asSrc = aSources[as].src;
                            if (asSrc && asSrc.indexOf('blob:') !== 0 && asSrc.indexOf('data:') !== 0) {
                                report(asSrc);
                                break;
                            }
                        }
                    }
                } else if (tag === 'SOURCE') {
                    var parentTag = node.parentNode ? (node.parentNode.tagName || '').toUpperCase() : '';
                    if (parentTag !== 'VIDEO' && parentTag !== 'AUDIO') {
                        report(node.src);
                        report(node.getAttribute('srcset'));
                    }
                }

                if (node.querySelectorAll) {
                    var children = node.querySelectorAll('img, video, audio, source');
                    for (var c = 0; c < children.length; c++) {
                        checkNode(children[c]);
                    }
                }
            }

            var observer = new MutationObserver(function(mutations) {
                for (var m = 0; m < mutations.length; m++) {
                    var mut = mutations[m];
                    if (mut.type === 'childList') {
                        for (var i = 0; i < mut.addedNodes.length; i++) {
                            checkNode(mut.addedNodes[i]);
                        }
                    } else if (mut.type === 'attributes') {
                        checkNode(mut.target);
                    }
                }
            });

            observer.observe(document.body || document.documentElement, {
                childList: true,
                subtree: true,
                attributes: true,
                attributeFilter: ['src', 'data-src', 'data-original', 'data-lazy-src', 'srcset', 'data-srcset', 'poster']
            });
        })();
    """.trimIndent()
}

/**
 * Shared JavaScript adaptive scrolling script.
 */
internal fun MediaCaptureEngine.getAdaptiveScrollScriptImpl(): String {
    return """
        (function() {
            var doc = document.documentElement || document.body;
            var scrollY = Math.round(window.pageYOffset || doc.scrollTop || 0);
            var innerHeight = window.innerHeight || 0;
            var scrollHeight = doc.scrollHeight || 0;
            var remaining = scrollHeight - (scrollY + innerHeight);

            var scrollDelta = Math.max(innerHeight * 0.85, 400);
            window.scrollBy({ top: scrollDelta, behavior: 'smooth' });
            window.dispatchEvent(new Event('scroll'));

            return JSON.stringify({
                scrollY: scrollY,
                scrollHeight: scrollHeight,
                remaining: remaining
            });
        })();
    """.trimIndent()
}

/**
 * Builds and validates a ScrapedMediaItem with provenance, clean titles,
 * download headers (User-Agent, Referer, Cookie), and thumbnail classification.
 */
