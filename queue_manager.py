"""Sequential download queue used by the desktop UI."""

from __future__ import annotations

import threading
import uuid
from dataclasses import asdict, dataclass
from pathlib import Path
from urllib.parse import urlparse

import yt_dlp

from download import make_ydl_opts


class DownloadStopped(Exception):
    """Raised from a progress hook to abort the current download."""


@dataclass
class QueueItem:
    id: str
    url: str
    title: str = ""
    quality: str = "best"
    audio_only: bool = False
    output_dir: str = "./downloads"
    cookies: str | None = None
    referer: str | None = None
    status: str = "pending"
    progress: float = 0.0
    speed: str = ""
    eta: str = ""
    error: str = ""
    filename: str = ""

    def to_dict(self) -> dict:
        return asdict(self)


@dataclass
class QueueSettings:
    output_dir: str = "./downloads"
    quality: str = "best"
    audio_only: bool = False
    cookies: str = ""
    referer: str = ""

    def to_dict(self) -> dict:
        return asdict(self)


def parse_urls(text: str) -> list[str]:
    found: list[str] = []
    seen: set[str] = set()
    for raw_line in text.replace(",", "\n").splitlines():
        for part in raw_line.split():
            url = normalize_url(part)
            if url and url not in seen:
                seen.add(url)
                found.append(url)
    return found


def normalize_url(url: str) -> str | None:
    trimmed = url.strip()
    if not trimmed:
        return None
    parsed = urlparse(trimmed)
    if parsed.scheme not in ("http", "https") or not parsed.netloc:
        return None
    return trimmed


def _format_speed(speed: float | None) -> str:
    if not speed:
        return ""
    if speed >= 1024 * 1024:
        return f"{speed / 1024 / 1024:.1f} MB/s"
    if speed >= 1024:
        return f"{speed / 1024:.0f} KB/s"
    return f"{speed:.0f} B/s"


def _format_eta(eta: float | None) -> str:
    if eta is None:
        return ""
    seconds = int(eta)
    if seconds < 60:
        return f"{seconds}s"
    minutes, seconds = divmod(seconds, 60)
    if minutes < 60:
        return f"{minutes}m {seconds:02d}s"
    hours, minutes = divmod(minutes, 60)
    return f"{hours}h {minutes:02d}m"


class DownloadQueue:
    def __init__(self) -> None:
        self._items: list[QueueItem] = []
        self._lock = threading.Lock()
        self._worker: threading.Thread | None = None
        self._stop_current = threading.Event()
        self._pause_queue = threading.Event()
        self.settings = QueueSettings()

    @property
    def running(self) -> bool:
        return self._worker is not None and self._worker.is_alive()

    def snapshot(self) -> dict:
        with self._lock:
            items = [item.to_dict() for item in self._items]
            settings = self.settings.to_dict()
        pending = sum(1 for item in items if item["status"] == "pending")
        downloading = sum(1 for item in items if item["status"] == "downloading")
        completed = sum(1 for item in items if item["status"] == "completed")
        failed = sum(1 for item in items if item["status"] in ("failed", "cancelled"))
        return {
            "items": items,
            "running": self.running,
            "counts": {
                "total": len(items),
                "pending": pending,
                "downloading": downloading,
                "completed": completed,
                "failed": failed,
            },
            "settings": settings,
        }

    def update_settings(self, **kwargs) -> QueueSettings:
        with self._lock:
            for key, value in kwargs.items():
                if hasattr(self.settings, key) and value is not None:
                    setattr(self.settings, key, value)
            return self.settings

    def add(self, urls: list[str], quality: str | None = None, audio_only: bool | None = None) -> list[QueueItem]:
        added: list[QueueItem] = []
        with self._lock:
            existing = {
                item.url
                for item in self._items
                if item.status in ("pending", "downloading")
            }
            for url in urls:
                normalized = normalize_url(url)
                if not normalized or normalized in existing:
                    continue
                item = QueueItem(
                    id=uuid.uuid4().hex[:10],
                    url=normalized,
                    quality=quality or self.settings.quality,
                    audio_only=self.settings.audio_only if audio_only is None else audio_only,
                    output_dir=self.settings.output_dir,
                    cookies=self.settings.cookies or None,
                    referer=self.settings.referer or None,
                )
                self._items.append(item)
                existing.add(normalized)
                added.append(item)
        return added

    def remove(self, item_id: str) -> bool:
        with self._lock:
            item = self._find(item_id)
            if item is None:
                return False
            if item.status == "downloading":
                self._stop_current.set()
            self._items = [entry for entry in self._items if entry.id != item_id]
            return True

    def clear(self, finished_only: bool = False) -> None:
        with self._lock:
            if finished_only:
                self._items = [
                    item
                    for item in self._items
                    if item.status not in ("completed", "failed", "cancelled")
                ]
                return
            if any(item.status == "downloading" for item in self._items):
                self._stop_current.set()
            self._pause_queue.set()
            self._items.clear()

    def start(self) -> bool:
        with self._lock:
            has_pending = any(item.status == "pending" for item in self._items)
        if not has_pending:
            return False
        self._pause_queue.clear()
        self._stop_current.clear()
        if self.running:
            return True
        self._worker = threading.Thread(target=self._run, name="download-queue", daemon=True)
        self._worker.start()
        return True

    def stop(self) -> None:
        self._pause_queue.set()
        self._stop_current.set()

    def _find(self, item_id: str) -> QueueItem | None:
        return next((item for item in self._items if item.id == item_id), None)

    def _update(self, item_id: str, **changes) -> None:
        with self._lock:
            item = self._find(item_id)
            if item is None:
                return
            for key, value in changes.items():
                setattr(item, key, value)

    def _next_pending(self) -> QueueItem | None:
        with self._lock:
            return next((item for item in self._items if item.status == "pending"), None)

    def _run(self) -> None:
        while not self._pause_queue.is_set():
            item = self._next_pending()
            if item is None:
                break
            self._stop_current.clear()
            self._download_item(item)

    def _download_item(self, item: QueueItem) -> None:
        self._update(
            item.id,
            status="downloading",
            progress=0.0,
            speed="",
            eta="",
            error="",
        )

        def on_progress(status: dict) -> None:
            if self._stop_current.is_set():
                raise DownloadStopped()

            info = status.get("info_dict") or {}
            title = info.get("title") or ""
            filename = status.get("filename") or info.get("_filename") or ""
            state = status.get("status")

            if state == "downloading":
                total = status.get("total_bytes") or status.get("total_bytes_estimate")
                downloaded = status.get("downloaded_bytes") or 0
                percent = (downloaded / total * 100) if total else 0.0
                self._update(
                    item.id,
                    title=title or item.title,
                    filename=str(filename) if filename else item.filename,
                    progress=round(min(percent, 99.9), 1),
                    speed=_format_speed(status.get("speed")),
                    eta=_format_eta(status.get("eta")),
                )
            elif state == "finished":
                self._update(
                    item.id,
                    title=title or item.title,
                    filename=str(filename) if filename else item.filename,
                    progress=100.0,
                    speed="",
                    eta="",
                )

        try:
            opts = make_ydl_opts(
                output_dir=Path(item.output_dir),
                quality=item.quality,
                audio_only=item.audio_only,
                cookies=item.cookies,
                referer=item.referer,
                progress_hooks=[on_progress],
                quiet=True,
            )
            with yt_dlp.YoutubeDL(opts) as ydl:
                info = ydl.extract_info(item.url, download=True)

            if self._stop_current.is_set():
                self._update(item.id, status="cancelled", progress=0.0, speed="", eta="")
                return

            title = ""
            filename = ""
            if info:
                title = info.get("title") or ""
                filename = ydl.prepare_filename(info)
            self._update(
                item.id,
                status="completed",
                progress=100.0,
                title=title or item.title or item.url,
                filename=filename or item.filename,
                speed="",
                eta="",
                error="",
            )
        except DownloadStopped:
            self._update(item.id, status="cancelled", progress=0.0, speed="", eta="")
        except Exception as exc:
            if self._stop_current.is_set() or _is_stopped(exc):
                self._update(item.id, status="cancelled", progress=0.0, speed="", eta="")
                return
            self._update(
                item.id,
                status="failed",
                error=str(exc) or exc.__class__.__name__,
                speed="",
                eta="",
            )


def _is_stopped(exc: BaseException) -> bool:
    current: BaseException | None = exc
    while current is not None:
        if isinstance(current, DownloadStopped):
            return True
        current = current.__cause__ or current.__context__
    return False
