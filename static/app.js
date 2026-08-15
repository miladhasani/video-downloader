const els = {
  urlInput: document.getElementById("urlInput"),
  qualitySelect: document.getElementById("qualitySelect"),
  outputDir: document.getElementById("outputDir"),
  audioOnly: document.getElementById("audioOnly"),
  refererInput: document.getElementById("refererInput"),
  cookiesInput: document.getElementById("cookiesInput"),
  addBtn: document.getElementById("addBtn"),
  startBtn: document.getElementById("startBtn"),
  stopBtn: document.getElementById("stopBtn"),
  clearFinishedBtn: document.getElementById("clearFinishedBtn"),
  clearAllBtn: document.getElementById("clearAllBtn"),
  browseBtn: document.getElementById("browseBtn"),
  queueBody: document.getElementById("queueBody"),
  emptyState: document.getElementById("emptyState"),
  queueSummary: document.getElementById("queueSummary"),
  runBadge: document.getElementById("runBadge"),
  countBadge: document.getElementById("countBadge"),
  toast: document.getElementById("toast"),
};

const STATUS_LABELS = {
  pending: "Waiting",
  downloading: "Downloading",
  completed: "Done",
  failed: "Failed",
  cancelled: "Cancelled",
};

let toastTimer = 0;

function showToast(message) {
  els.toast.textContent = message;
  els.toast.hidden = false;
  clearTimeout(toastTimer);
  toastTimer = setTimeout(() => {
    els.toast.hidden = true;
  }, 2800);
}

async function api(path, options = {}) {
  const response = await fetch(path, {
    headers: { "Content-Type": "application/json" },
    ...options,
  });
  const data = await response.json().catch(() => ({}));
  if (!response.ok) {
    throw new Error(data.error || "Request failed");
  }
  return data;
}

function qualityLabel(item) {
  if (item.audio_only) return "Audio";
  if (item.quality === "best") return "Best";
  if (item.quality === "worst") return "Lowest";
  return `${item.quality}p`;
}

function applySettings(settings) {
  if (!settings) return;
  els.qualitySelect.value = settings.quality || "best";
  els.outputDir.value = settings.output_dir || "./downloads";
  els.audioOnly.checked = Boolean(settings.audio_only);
  els.refererInput.value = settings.referer || "";
  els.cookiesInput.value = settings.cookies || "";
}

function render(state) {
  const items = state.items || [];
  const counts = state.counts || {};
  const running = Boolean(state.running);

  applySettings(state.settings);
  els.queueBody.innerHTML = items.map((item, index) => {
    const title = item.title || "Untitled video";
    const meta = [item.speed, item.eta ? `ETA ${item.eta}` : "", item.error]
      .filter(Boolean)
      .join(" · ");
    return `
      <div class="grid-row grid-body-row ${item.status}" role="row">
        <span>${index + 1}</span>
        <div class="title-cell">
          <strong title="${escapeHtml(title)}">${escapeHtml(title)}</strong>
          <span title="${escapeHtml(item.url)}">${escapeHtml(item.url)}</span>
        </div>
        <span>${qualityLabel(item)}</span>
        <span class="status ${item.status}">${STATUS_LABELS[item.status] || item.status}</span>
        <div class="progress-wrap">
          <div class="progress-bar" aria-hidden="true"><span style="width:${item.progress || 0}%"></span></div>
          <div class="progress-meta">${Math.round(item.progress || 0)}%${meta ? ` · ${escapeHtml(meta)}` : ""}</div>
        </div>
        <button class="icon-btn" data-remove="${item.id}" type="button" aria-label="Remove">✕</button>
      </div>
    `;
  }).join("");

  els.emptyState.classList.toggle("visible", items.length === 0);
  els.stopBtn.hidden = !running;
  els.startBtn.disabled = running || !counts.pending;
  els.runBadge.textContent = running ? "Downloading" : "Idle";
  els.runBadge.classList.toggle("live", running);
  els.countBadge.textContent = `${counts.total || 0} in queue`;
  els.queueSummary.textContent = items.length
    ? `${counts.pending || 0} waiting · ${counts.downloading || 0} active · ${counts.completed || 0} done`
    : "No items yet";
}

function escapeHtml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

async function persistSettings() {
  await api("/api/settings", {
    method: "POST",
    body: JSON.stringify({
      quality: els.qualitySelect.value,
      output_dir: els.outputDir.value,
      audio_only: els.audioOnly.checked,
      referer: els.refererInput.value.trim(),
      cookies: els.cookiesInput.value.trim(),
    }),
  });
}

async function refresh() {
  const state = await api("/api/queue");
  render(state);
}

els.addBtn.addEventListener("click", async () => {
  try {
    await persistSettings();
    const state = await api("/api/queue", {
      method: "POST",
      body: JSON.stringify({
        urls: els.urlInput.value,
        quality: els.qualitySelect.value,
        audio_only: els.audioOnly.checked,
      }),
    });
    els.urlInput.value = "";
    render(state);
    showToast(`Added ${state.added} link${state.added === 1 ? "" : "s"} to the queue`);
  } catch (error) {
    showToast(error.message);
  }
});

els.startBtn.addEventListener("click", async () => {
  try {
    await persistSettings();
    render(await api("/api/queue/start", { method: "POST", body: "{}" }));
  } catch (error) {
    showToast(error.message);
  }
});

els.stopBtn.addEventListener("click", async () => {
  render(await api("/api/queue/stop", { method: "POST", body: "{}" }));
});

els.clearFinishedBtn.addEventListener("click", async () => {
  render(await api("/api/queue/clear", {
    method: "POST",
    body: JSON.stringify({ finished_only: true }),
  }));
});

els.clearAllBtn.addEventListener("click", async () => {
  render(await api("/api/queue/clear", {
    method: "POST",
    body: JSON.stringify({ finished_only: false }),
  }));
});

els.browseBtn.addEventListener("click", async () => {
  const state = await api("/api/browse-folder", { method: "POST", body: "{}" });
  if (state.path) {
    els.outputDir.value = state.path;
  }
  render(state);
});

els.queueBody.addEventListener("click", async (event) => {
  const button = event.target.closest("[data-remove]");
  if (!button) return;
  render(await api(`/api/queue/${button.dataset.remove}`, { method: "DELETE" }));
});

["change"].forEach((eventName) => {
  [els.qualitySelect, els.audioOnly, els.outputDir, els.refererInput, els.cookiesInput].forEach((el) => {
    el.addEventListener(eventName, () => {
      persistSettings().catch((error) => showToast(error.message));
    });
  });
});

refresh();
setInterval(refresh, 800);
