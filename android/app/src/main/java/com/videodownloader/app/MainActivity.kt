package com.videodownloader.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.videodownloader.app.databinding.ActivityMainBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity(), FolderPickerDialog.Listener {
    private lateinit var binding: ActivityMainBinding
    private lateinit var preferences: AppPreferences
    private lateinit var downloadQueue: DownloadQueueManager
    private lateinit var queueAdapter: QueueItemAdapter

    private var currentOutputDir: String = AppPreferences.defaultOutputDir()
    private var pendingAction: (() -> Unit)? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            pendingAction?.invoke()
        } else {
            Toast.makeText(this, R.string.storage_permission_required, Toast.LENGTH_LONG).show()
        }
        pendingAction = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = AppPreferences(this)
        downloadQueue = (application as VideoDownloaderApp).downloadQueue
        currentOutputDir = preferences.getDownloadSettings().outputDir

        setupToolbar()
        setupQueueList()
        setupActions()
        refreshUiFromSettings()
        observeQueue()
        waitForInitialization()
    }

    override fun onResume() {
        super.onResume()
        refreshUiFromSettings()
        syncQueueSettings()
    }

    private fun setupToolbar() {
        binding.mainToolbar.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
    }

    private fun setupQueueList() {
        queueAdapter = QueueItemAdapter { item ->
            downloadQueue.remove(item.id)
        }
        binding.queueList.layoutManager = LinearLayoutManager(this)
        binding.queueList.adapter = queueAdapter
    }

    private fun setupActions() {
        binding.pasteButton.setOnClickListener { pasteFromClipboard() }
        binding.clearUrlButton.setOnClickListener { binding.urlInput.text?.clear() }
        binding.browseFolderButton.setOnClickListener { openFolderPicker() }
        binding.openSettingsButton.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
        binding.addToQueueButton.setOnClickListener { requestAddToQueue() }
        binding.startQueueButton.setOnClickListener { requestStartQueue() }
        binding.clearQueueButton.setOnClickListener { clearQueue() }
        binding.stopButton.setOnClickListener { stopQueue() }
    }

    private fun observeQueue() {
        lifecycleScope.launch {
            downloadQueue.queue.collectLatest { items ->
                queueAdapter.submitList(items)
                updateQueueUi(items)
            }
        }
    }

    private fun updateQueueUi(items: List<QueuedDownload>) {
        val pending = items.count { it.status == QueueStatus.PENDING }
        val active = items.firstOrNull { it.status == QueueStatus.DOWNLOADING }
        val isRunning = downloadQueue.isRunning

        binding.queueCard.isVisible = items.isNotEmpty()
        binding.queueEmptyText.isVisible = items.isEmpty()
        binding.queueSummaryText.text = getString(R.string.queue_summary, pending, items.size)
        binding.startQueueButton.isVisible = pending > 0 && !isRunning
        binding.stopButton.isVisible = isRunning || active != null
        binding.clearQueueButton.isEnabled = items.isNotEmpty()

        val app = application as VideoDownloaderApp
        setInputEnabled(app.isInitialized)

        binding.progressCard.isVisible = active != null || isRunning
        if (active != null) {
            binding.progressBar.progress = active.progress
            binding.statusText.text = active.statusMessage.ifBlank {
                getString(R.string.queue_status_downloading, active.progress)
            }
        } else if (pending > 0 && !isRunning) {
            binding.progressBar.progress = 0
            binding.statusText.text = getString(R.string.queue_paused, pending)
        } else if (items.isNotEmpty() && pending == 0 && !isRunning) {
            binding.progressBar.progress = 100
            binding.statusText.text = getString(R.string.queue_finished)
        }
    }

    private fun refreshUiFromSettings() {
        val settings = preferences.getDownloadSettings()
        currentOutputDir = settings.outputDir
        binding.outputDirInput.setText(currentOutputDir)
        binding.qualitySummaryText.text = getString(
            R.string.summary_quality,
            AppPreferences.qualityLabel(this, settings.quality, settings.audioOnly),
        )
        binding.formatSummaryText.text = getString(
            R.string.summary_format,
            if (settings.effectiveAudioOnly()) settings.audioFormat.uppercase() else settings.mergeFormat.uppercase(),
        )
    }

    private fun syncQueueSettings() {
        preferences.saveOutputDir(currentOutputDir)
    }

    private fun openFolderPicker() {
        FolderPickerDialog.newInstance(currentOutputDir)
            .show(supportFragmentManager, "main_folder_picker")
    }

    override fun onFolderSelected(path: String) {
        currentOutputDir = StorageHelper.normalizePath(path)
        binding.outputDirInput.setText(currentOutputDir)
        preferences.saveOutputDir(currentOutputDir)
    }

    private fun waitForInitialization() {
        lifecycleScope.launch {
            val app = application as VideoDownloaderApp
            while (!app.isInitialized) {
                binding.statusText.text = getString(R.string.initializing)
                setInputEnabled(false)
                kotlinx.coroutines.delay(250)
            }
            binding.statusText.text = getString(R.string.ready)
            setInputEnabled(true)
        }
    }

    private fun pasteFromClipboard() {
        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = clipboard.primaryClip ?: return
        if (clip.itemCount == 0) return

        val text = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty()
        if (text.isBlank()) return

        val urls = DownloadQueueManager.parseUrls(text)
        if (urls.size > 1) {
            val added = addUrlsToQueue(urls)
            Toast.makeText(this, getString(R.string.queue_added_count, added), Toast.LENGTH_SHORT).show()
            binding.urlInput.text?.clear()
        } else {
            binding.urlInput.setText(text.trim())
            binding.urlInput.setSelection(text.trim().length)
        }
    }

    private fun requestAddToQueue() {
        val text = binding.urlInput.text?.toString()?.trim().orEmpty()
        if (text.isBlank()) {
            binding.urlInputLayout.error = getString(R.string.url_required)
            return
        }
        binding.urlInputLayout.error = null

        if (!validateReady()) {
            return
        }

        val urls = DownloadQueueManager.parseUrls(text)
        if (urls.isEmpty()) {
            binding.urlInputLayout.error = getString(R.string.url_invalid)
            return
        }

        val added = addUrlsToQueue(urls)
        if (added == 0) {
            Toast.makeText(this, R.string.queue_duplicate, Toast.LENGTH_SHORT).show()
            return
        }

        binding.urlInput.text?.clear()
        Toast.makeText(this, getString(R.string.queue_added_count, added), Toast.LENGTH_SHORT).show()
        downloadQueue.start()
    }

    private fun requestStartQueue() {
        if (!validateReady()) {
            return
        }
        downloadQueue.start()
    }

    private fun addUrlsToQueue(urls: List<String>): Int {
        val settings = preferences.getDownloadSettings().copy(outputDir = currentOutputDir)
        preferences.saveDownloadSettings(settings)
        return downloadQueue.addAll(urls)
    }

    private fun clearQueue() {
        downloadQueue.clearAll()
        binding.progressCard.isVisible = false
        binding.statusText.text = getString(R.string.ready)
        Toast.makeText(this, R.string.queue_cleared, Toast.LENGTH_SHORT).show()
    }

    private fun stopQueue() {
        downloadQueue.pause()
        Toast.makeText(this, R.string.queue_stopped, Toast.LENGTH_SHORT).show()
    }

    private fun validateReady(): Boolean {
        val app = application as VideoDownloaderApp
        if (!app.isInitialized) {
            Toast.makeText(this, R.string.still_initializing, Toast.LENGTH_SHORT).show()
            return false
        }

        if (!StorageHelper.isWritableDirectory(currentOutputDir)) {
            Toast.makeText(this, R.string.folder_not_writable_message, Toast.LENGTH_LONG).show()
            return false
        }

        if (needsStoragePermission() && !hasStoragePermission()) {
            pendingAction = { requestAddToQueue() }
            permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            return false
        }

        return true
    }

    private fun setInputEnabled(enabled: Boolean) {
        binding.addToQueueButton.isEnabled = enabled
        binding.startQueueButton.isEnabled = enabled
        binding.pasteButton.isEnabled = enabled
        binding.clearUrlButton.isEnabled = enabled
        binding.browseFolderButton.isEnabled = enabled
        binding.urlInput.isEnabled = enabled
        binding.openSettingsButton.isEnabled = enabled
    }

    private fun needsStoragePermission(): Boolean {
        return Build.VERSION.SDK_INT <= Build.VERSION_CODES.P
    }

    private fun hasStoragePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        ) == PackageManager.PERMISSION_GRANTED
    }
}
