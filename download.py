#!/usr/bin/env python3
"""Download videos from VOD and streaming websites."""

from __future__ import annotations

import argparse
import shutil
import sys
from pathlib import Path

import yt_dlp


def impersonation_available() -> bool:
    try:
        import curl_cffi  # noqa: F401

        return True
    except ImportError:
        return False


def apply_impersonation_opts(opts: dict) -> None:
    if not impersonation_available():
        return

    extractor_args = opts.setdefault("extractor_args", {})
    generic_args = extractor_args.setdefault("generic", {})
    generic_args.setdefault("impersonate", ["chrome"])


def find_ffmpeg() -> str | None:
    ffmpeg = shutil.which("ffmpeg")
    ffprobe = shutil.which("ffprobe")
    if ffmpeg and ffprobe:
        return ffmpeg

    try:
        from static_ffmpeg.run import get_or_fetch_platform_executables_else_raise

        bundled_ffmpeg, _bundled_ffprobe = get_or_fetch_platform_executables_else_raise()
        return bundled_ffmpeg
    except ImportError:
        return ffmpeg
    except OSError:
        return ffmpeg


def ffprobe_available() -> bool:
    if shutil.which("ffprobe"):
        return True

    try:
        from static_ffmpeg.run import get_or_fetch_platform_executables_else_raise

        _ffmpeg, ffprobe = get_or_fetch_platform_executables_else_raise()
        return Path(ffprobe).is_file()
    except (ImportError, OSError):
        return False


def apply_ffmpeg_opts(opts: dict) -> None:
    ffmpeg = find_ffmpeg()
    if ffmpeg:
        opts["ffmpeg_location"] = ffmpeg


def warn_if_ffmpeg_missing() -> None:
    ffmpeg = find_ffmpeg()
    if ffmpeg and ffprobe_available():
        return

    if ffmpeg and not ffprobe_available():
        print(
            "Warning: ffprobe is missing. Metadata extraction and some fixups may fail.\n"
            "Install full ffmpeg with: pip install -r requirements.txt",
            file=sys.stderr,
        )
        return

    print(
        "Warning: ffmpeg is not installed. Some downloads need it to merge streams "
        "or fix container issues (e.g. MPEG-TS in MP4).\n"
        "Install it with: pip install -r requirements.txt\n"
        "Or install ffmpeg system-wide: https://ffmpeg.org/download.html",
        file=sys.stderr,
    )

def warn_if_impersonation_missing() -> None:
    if impersonation_available():
        return

    print(
        "Warning: curl-cffi is not installed. Some sites require browser impersonation.\n"
        "Install it with: pip install -r requirements.txt\n"
        "See https://github.com/yt-dlp/yt-dlp#impersonation",
        file=sys.stderr,
    )


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(
        description="Download videos from VOD and streaming websites.",
        epilog="Examples:\n"
        "  python download.py https://example.com/watch?v=123\n"
        "  python download.py URL -o ./downloads -q 720\n"
        "  python download.py URL --list-formats\n",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument("url", help="Video page URL")
    parser.add_argument(
        "-o",
        "--output",
        default="./downloads",
        help="Output directory (default: ./downloads)",
    )
    parser.add_argument(
        "-q",
        "--quality",
        default="best",
        help="Max resolution cap: best (default, highest available), worst, or height like 1080, 720, 480",
    )
    parser.add_argument(
        "--audio-only",
        action="store_true",
        help="Download audio only (best available audio stream)",
    )
    parser.add_argument(
        "--list-formats",
        action="store_true",
        help="List available formats for the URL and exit",
    )
    parser.add_argument(
        "--cookies",
        metavar="FILE",
        help="Netscape cookies file for sites that require login",
    )
    parser.add_argument(
        "--referer",
        help="Custom Referer header (useful for some embedded players)",
    )
    return parser


def format_selector(quality: str, audio_only: bool) -> str:
    if audio_only:
        return "bestaudio*/best"

    if quality == "best":
        return "bestvideo*+bestaudio/best"
    if quality == "worst":
        return "worstvideo+worstaudio/worst"

    height = quality.rstrip("p")
    if not height.isdigit():
        raise ValueError(f"Invalid quality '{quality}'. Use best, worst, or a height like 720.")

    return f"bestvideo[height<={height}]+bestaudio/best[height<={height}]"


def make_ydl_opts(
    output_dir: Path,
    quality: str,
    audio_only: bool,
    cookies: str | None,
    referer: str | None,
) -> dict:
    output_dir.mkdir(parents=True, exist_ok=True)

    opts: dict = {
        "outtmpl": str(output_dir / "%(title)s [%(id)s].%(ext)s"),
        "format": format_selector(quality, audio_only),
        "merge_output_format": "mp4",
        "noplaylist": True,
        "ignoreerrors": False,
        "continuedl": True,
        "retries": 10,
        "fragment_retries": 10,
        "concurrent_fragment_downloads": 4,
        "progress_hooks": [progress_hook],
    }

    if cookies:
        opts["cookiefile"] = cookies

    if referer:
        opts["referer"] = referer

    if quality == "best":
        opts["format_sort"] = ["res:9999", "fps", "size", "br"]
        opts["prefer_free_formats"] = False

    apply_impersonation_opts(opts)
    apply_ffmpeg_opts(opts)

    if audio_only:
        opts["postprocessors"] = [
            {
                "key": "FFmpegExtractAudio",
                "preferredcodec": "mp3",
                "preferredquality": "192",
            }
        ]

    return opts


def progress_hook(status: dict) -> None:
    if status.get("status") != "downloading":
        return

    total = status.get("total_bytes") or status.get("total_bytes_estimate")
    downloaded = status.get("downloaded_bytes", 0)
    speed = status.get("speed")
    eta = status.get("eta")

    if total:
        percent = downloaded / total * 100
        line = f"\rDownloading: {percent:5.1f}%"
    else:
        line = "\rDownloading..."

    if speed:
        line += f" | {speed / 1024 / 1024:.1f} MB/s"
    if eta is not None:
        line += f" | ETA {eta}s"

    print(line, end="", flush=True)


def list_formats(url: str, cookies: str | None, referer: str | None) -> int:
    opts: dict = {"quiet": True, "noplaylist": True}
    if cookies:
        opts["cookiefile"] = cookies
    if referer:
        opts["referer"] = referer
    apply_impersonation_opts(opts)

    with yt_dlp.YoutubeDL(opts) as ydl:
        info = ydl.extract_info(url, download=False)

    if not info:
        print("Could not extract video information.", file=sys.stderr)
        return 1

    formats = info.get("formats") or []
    if not formats:
        print("No formats found for this URL.")
        return 0

    print(f"Title: {info.get('title', 'Unknown')}")
    print(f"Duration: {info.get('duration', 'Unknown')}s")
    print()
    print(f"{'ID':<8} {'EXT':<6} {'RES':<12} {'FPS':<6} {'CODEC':<12} {'SIZE':<10} NOTE")
    print("-" * 70)

    for fmt in formats:
        fmt_id = fmt.get("format_id", "?")
        ext = fmt.get("ext", "?")
        width = fmt.get("width")
        height = fmt.get("height")
        res = f"{width}x{height}" if width and height else fmt.get("format_note", "?")
        fps = fmt.get("fps") or "-"
        vcodec = fmt.get("vcodec") or "-"
        if vcodec != "none" and fmt.get("acodec") not in (None, "none"):
            vcodec = "av"

        size = fmt.get("filesize") or fmt.get("filesize_approx")
        size_str = f"{size / 1024 / 1024:.1f}MB" if size else "-"
        note = fmt.get("format_note") or ""

        print(f"{fmt_id:<8} {ext:<6} {str(res):<12} {str(fps):<6} {str(vcodec):<12} {size_str:<10} {note}")

    return 0


def download_video(
    url: str,
    output_dir: Path,
    quality: str,
    audio_only: bool,
    cookies: str | None,
    referer: str | None,
) -> int:
    opts = make_ydl_opts(output_dir, quality, audio_only, cookies, referer)

    try:
        with yt_dlp.YoutubeDL(opts) as ydl:
            info = ydl.extract_info(url, download=True)
    except yt_dlp.utils.DownloadError as exc:
        print(f"\nDownload failed: {exc}", file=sys.stderr)
        return 1
    except ValueError as exc:
        print(str(exc), file=sys.stderr)
        return 1

    print()
    if info:
        title = info.get("title", "video")
        print(f"Done: {title}")
        print(f"Saved to: {output_dir.resolve()}")
    return 0


def main() -> int:
    parser = build_parser()
    args = parser.parse_args()

    warn_if_impersonation_missing()
    warn_if_ffmpeg_missing()

    if args.list_formats:
        return list_formats(args.url, args.cookies, args.referer)

    return download_video(
        url=args.url,
        output_dir=Path(args.output),
        quality=args.quality,
        audio_only=args.audio_only,
        cookies=args.cookies,
        referer=args.referer,
    )


if __name__ == "__main__":
    raise SystemExit(main())
