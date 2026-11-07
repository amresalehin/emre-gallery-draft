# Crawl4AI service for Emre

This folder runs the official Crawl4AI self-hosted API beside the Android app. The browser can use it for JavaScript-rendered page crawling and clean Markdown extraction instead of relying only on the WebView's basic `document.body.innerText` reader.

## Requirements

- Docker Desktop (Windows/macOS) or Docker Engine with the Compose plugin
- At least 4 GB of RAM available to Docker; Chromium uses shared memory
- Network access from the host to the pages being crawled

The service is pinned to **Crawl4AI 0.9.5**. It does not add Python, Playwright, or browser binaries to the Android APK.

## Start on Windows (PowerShell)

Run from the repository root:

```powershell
$env:CRAWL4AI_API_TOKEN = [Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(32)).ToLower()
docker compose -f backend/crawl4ai/compose.yaml up -d
```

Keep the token in the current PowerShell session. Do not commit it or put it in Android resources.

## Start on Linux / macOS

Run from the repository root:

```bash
export CRAWL4AI_API_TOKEN="$(openssl rand -hex 32)"
docker compose -f backend/crawl4ai/compose.yaml up -d
```

## Verify

The health endpoint is intentionally unauthenticated:

```bash
curl http://127.0.0.1:11235/health
```

A real extraction request requires the bearer token:

```bash
curl -X POST http://127.0.0.1:11235/md \
  -H "Authorization: Bearer $CRAWL4AI_API_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com"}'
```

On PowerShell, use `$env:CRAWL4AI_API_TOKEN` in the Authorization header.

## Operations

```bash
docker compose -f backend/crawl4ai/compose.yaml logs -f
docker compose -f backend/crawl4ai/compose.yaml ps
docker compose -f backend/crawl4ai/compose.yaml down
```

## Network and security

- The Compose port is bound to `127.0.0.1` only. Other devices, including a physical Android phone, cannot reach it by default.
- For a physical-device integration, expose it only through a private LAN/VPN route and preferably a TLS-terminating reverse proxy with access controls. Do not publish port 11235 directly to the public internet.
- Do not embed `CRAWL4AI_API_TOKEN` in the APK. A mobile client needs a trusted backend/proxy that keeps this credential server-side.
- The Compose service provides the Crawl4AI API; the Android browser is not yet wired to call it. That client integration should be a separate change with a configurable service URL and an authenticated, private request path.

## References

- [Crawl4AI installation](https://docs.crawl4ai.com/basic/installation/)
- [Crawl4AI self-hosting and API security](https://docs.crawl4ai.com/core/self-hosting/)
- [Crawl4AI 0.9.5 release](https://github.com/unclecode/crawl4ai/releases/tag/v0.9.5)
