"""Regression tests for choosing audio/video representations without yt-dlp installed."""
import importlib.util
import pathlib
import unittest

MODULE_PATH = pathlib.Path(__file__).parents[1] / "app/src/main/python/gallery_dl_runner.py"
SPEC = importlib.util.spec_from_file_location("gallery_dl_runner", MODULE_PATH)
runner = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(runner)


class YtDlpAudioFormatSelectionTests(unittest.TestCase):
    def test_prefers_best_separate_video_and_audio_tracks(self):
        formats = [
            {"format_id": "18", "vcodec": "avc1", "acodec": "mp4a", "height": 360, "tbr": 700},
            {"format_id": "137", "vcodec": "avc1", "acodec": "none", "height": 1080, "tbr": 4000},
            {"format_id": "248", "vcodec": "vp9", "acodec": "none", "height": 720, "tbr": 1800},
            {"format_id": "140", "vcodec": "none", "acodec": "mp4a", "abr": 128},
            {"format_id": "251", "vcodec": "none", "acodec": "opus", "abr": 160},
        ]
        selected = runner.select_yt_dlp_formats(formats)
        self.assertEqual("split", selected["kind"])
        self.assertEqual("137", selected["video_format_id"])
        self.assertEqual("251", selected["audio_format_id"])
        self.assertTrue(selected["has_video"])
        self.assertTrue(selected["has_audio"])

    def test_uses_muxed_format_when_separate_tracks_are_unavailable(self):
        selected = runner.select_yt_dlp_formats([
            {"format_id": "18", "vcodec": "avc1", "acodec": "mp4a", "height": 360},
            {"format_id": "22", "vcodec": "avc1", "acodec": "mp4a", "height": 720},
        ])
        self.assertEqual("single", selected["kind"])
        self.assertEqual("22", selected["format_id"])
        self.assertTrue(selected["has_audio"])

    def test_video_only_is_not_misreported_as_audio_capable(self):
        selected = runner.select_yt_dlp_formats([
            {"format_id": "137", "vcodec": "avc1", "acodec": "none", "height": 1080},
        ])
        self.assertEqual("video_only", selected["kind"])
        self.assertFalse(selected["has_audio"])

    def test_audio_only_format_is_preserved(self):
        selected = runner.select_yt_dlp_formats([
            {"format_id": "251", "vcodec": "none", "acodec": "opus", "abr": 160},
        ])
        self.assertEqual("audio_only", selected["kind"])
        self.assertTrue(selected["has_audio"])
        self.assertFalse(selected["has_video"])

    def test_drm_and_incomplete_entries_are_ignored(self):
        selected = runner.select_yt_dlp_formats([
            {"format_id": "drm", "vcodec": "avc1", "acodec": "mp4a", "has_drm": True},
            {"format_id": "bad", "vcodec": "none", "acodec": "none"},
        ])
        self.assertIsNone(selected)


if __name__ == "__main__":
    unittest.main()
