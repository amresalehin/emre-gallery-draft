# FFmpeg integration notices

- Dependency: `dev.ffmpegkit-maintained:ffmpeg-kit-full:8.1.7`.
- API namespace: `com.arthenica.ffmpegkit`.
- The selected artifact is the non-GPL full variant; do not replace it with a `-gpl` artifact without reviewing distribution obligations.
- The downloader uses FFmpeg stream-copy remuxing (`-c copy`), not re-encoding.
- This is an independent community-maintained continuation, not the original FFmpegKit publisher.
- Review and retain the exact AAR's LGPL and third-party license notices in distributed builds.
