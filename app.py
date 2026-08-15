#!/usr/bin/env python3
"""Desktop web UI for the video downloader queue."""

from __future__ import annotations

import threading
import webbrowser
from pathlib import Path

from flask import Flask, jsonify, render_template, request

from queue_manager import DownloadQueue, parse_urls

HOST = "127.0.0.1"
PORT = 8765

app = Flask(__name__)
queue = DownloadQueue()


@app.get("/")
def index():
    return render_template("index.html")


@app.get("/api/queue")
def get_queue():
    return jsonify(queue.snapshot())


@app.post("/api/queue")
def add_to_queue():
    payload = request.get_json(silent=True) or {}
    urls = parse_urls(str(payload.get("urls") or payload.get("text") or ""))
    if not urls:
        return jsonify({"error": "Enter at least one valid http:// or https:// URL."}), 400

    quality = payload.get("quality")
    audio_only = payload.get("audio_only")
    added = queue.add(urls, quality=quality, audio_only=audio_only)
    return jsonify({"added": len(added), "items": [item.to_dict() for item in added], **queue.snapshot()})


@app.delete("/api/queue/<item_id>")
def remove_from_queue(item_id: str):
    if not queue.remove(item_id):
        return jsonify({"error": "Item not found."}), 404
    return jsonify(queue.snapshot())


@app.post("/api/queue/start")
def start_queue():
    if not queue.start():
        return jsonify({"error": "Nothing pending in the queue."}), 400
    return jsonify(queue.snapshot())


@app.post("/api/queue/stop")
def stop_queue():
    queue.stop()
    return jsonify(queue.snapshot())


@app.post("/api/queue/clear")
def clear_queue():
    payload = request.get_json(silent=True) or {}
    queue.clear(finished_only=bool(payload.get("finished_only")))
    return jsonify(queue.snapshot())


@app.post("/api/settings")
def update_settings():
    payload = request.get_json(silent=True) or {}
    allowed = ("output_dir", "quality", "audio_only", "cookies", "referer")
    changes = {key: payload[key] for key in allowed if key in payload}
    if "output_dir" in changes:
        output_dir = str(changes["output_dir"]).strip() or "./downloads"
        changes["output_dir"] = output_dir
    queue.update_settings(**changes)
    return jsonify(queue.snapshot())


@app.post("/api/browse-folder")
def browse_folder():
    path = ""
    try:
        import tkinter as tk
        from tkinter import filedialog

        root = tk.Tk()
        root.withdraw()
        root.attributes("-topmost", True)
        path = filedialog.askdirectory(initialdir=queue.settings.output_dir) or ""
        root.destroy()
    except Exception:
        path = ""

    if path:
        queue.update_settings(output_dir=path)
    return jsonify({"path": path or queue.settings.output_dir, **queue.snapshot()})


def main() -> None:
    Path(queue.settings.output_dir).mkdir(parents=True, exist_ok=True)
    url = f"http://{HOST}:{PORT}"
    threading.Timer(0.8, lambda: webbrowser.open(url)).start()
    print(f"Video Downloader UI: {url}")
    app.run(host=HOST, port=PORT, debug=False, use_reloader=False, threaded=True)


if __name__ == "__main__":
    main()
