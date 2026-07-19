package com.videodownloader.app

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.videodownloader.app.databinding.ActivitySettingsBinding

class SettingsActivity : AppCompatActivity(), FolderPickerDialog.Listener {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var preferences: AppPreferences
    private var currentSettings: DownloadSettings = DownloadSettings()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        preferences = AppPreferences(this)
        currentSettings = preferences.getDownloadSettings()

        setupToolbar()
        setupDropdowns()
        bindSettingsToUi()
        setupActions()
    }

    private fun setupToolbar() {
        binding.settingsToolbar.setNavigationOnClickListener { finish() }
    }

    private fun setupDropdowns() {
        val qualityLabels = AppPreferences.qualityValues.map { value ->
            when (value) {
                "best" -> getString(R.string.quality_best)
                "worst" -> getString(R.string.quality_worst)
                "audio_only" -> getString(R.string.quality_audio_only)
                else -> "${value}p"
            }
        }
        binding.defaultQualityDropdown.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, qualityLabels),
        )

        binding.mergeFormatDropdown.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, AppPreferences.mergeFormats),
        )

        binding.audioFormatDropdown.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, AppPreferences.audioFormats),
        )

        binding.audioQualityDropdown.setAdapter(
            ArrayAdapter(this, android.R.layout.simple_dropdown_item_1line, AppPreferences.audioQualities),
        )
    }

    private fun bindSettingsToUi() {
        binding.defaultQualityDropdown.setText(
            when (currentSettings.quality) {
                "best" -> getString(R.string.quality_best)
                "worst" -> getString(R.string.quality_worst)
                "audio_only" -> getString(R.string.quality_audio_only)
                else -> "${currentSettings.quality}p"
            },
            false,
        )
        binding.outputDirInput.setText(currentSettings.outputDir)
        binding.filenameTemplateInput.setText(currentSettings.filenameTemplate)
        binding.mergeFormatDropdown.setText(currentSettings.mergeFormat, false)
        binding.preferHighestSwitch.isChecked = currentSettings.preferHighestResolution
        binding.noPlaylistSwitch.isChecked = currentSettings.noPlaylist
        binding.audioOnlySwitch.isChecked = currentSettings.audioOnly
        binding.audioFormatDropdown.setText(currentSettings.audioFormat, false)
        binding.audioQualityDropdown.setText(currentSettings.audioQuality, false)
        binding.retriesInput.setText(currentSettings.retries.toString())
        binding.fragmentRetriesInput.setText(currentSettings.fragmentRetries.toString())
        binding.concurrentFragmentsInput.setText(currentSettings.concurrentFragments.toString())
        binding.useRefererSwitch.isChecked = currentSettings.useCustomReferer
        binding.refererInput.setText(currentSettings.customReferer)
        binding.refererInput.isEnabled = currentSettings.useCustomReferer
    }

    private fun setupActions() {
        binding.browseOutputDirButton.setOnClickListener {
            FolderPickerDialog.newInstance(binding.outputDirInput.text?.toString().orEmpty())
                .show(supportFragmentManager, "settings_folder_picker")
        }

        binding.useRefererSwitch.setOnCheckedChangeListener { _, checked ->
            binding.refererInput.isEnabled = checked
        }

        binding.saveSettingsButton.setOnClickListener { saveSettings() }

        binding.resetSettingsButton.setOnClickListener {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.reset_settings_title)
                .setMessage(R.string.reset_settings_message)
                .setPositiveButton(R.string.reset) { _, _ ->
                    preferences.resetToDefaults()
                    currentSettings = preferences.getDownloadSettings()
                    bindSettingsToUi()
                    Toast.makeText(this, R.string.settings_reset, Toast.LENGTH_SHORT).show()
                }
                .setNegativeButton(android.R.string.cancel, null)
                .show()
        }
    }

    private fun saveSettings() {
        val quality = parseQualityValue(binding.defaultQualityDropdown.text.toString())
        val retries = binding.retriesInput.text?.toString()?.toIntOrNull() ?: 10
        val fragmentRetries = binding.fragmentRetriesInput.text?.toString()?.toIntOrNull() ?: 10
        val concurrentFragments = binding.concurrentFragmentsInput.text?.toString()?.toIntOrNull() ?: 4
        val outputDir = binding.outputDirInput.text?.toString()?.trim().orEmpty()

        if (outputDir.isBlank()) {
            binding.outputDirInput.error = getString(R.string.output_dir_required)
            return
        }

        currentSettings = DownloadSettings(
            quality = quality,
            audioOnly = binding.audioOnlySwitch.isChecked,
            outputDir = StorageHelper.normalizePath(outputDir),
            filenameTemplate = binding.filenameTemplateInput.text?.toString()?.trim()
                ?: "%(title)s [%(id)s].%(ext)s",
            mergeFormat = binding.mergeFormatDropdown.text?.toString() ?: "mp4",
            preferHighestResolution = binding.preferHighestSwitch.isChecked,
            noPlaylist = binding.noPlaylistSwitch.isChecked,
            audioFormat = binding.audioFormatDropdown.text?.toString() ?: "mp3",
            audioQuality = binding.audioQualityDropdown.text?.toString() ?: "192",
            retries = retries.coerceIn(1, 50),
            fragmentRetries = fragmentRetries.coerceIn(1, 50),
            concurrentFragments = concurrentFragments.coerceIn(1, 16),
            useCustomReferer = binding.useRefererSwitch.isChecked,
            customReferer = binding.refererInput.text?.toString()?.trim().orEmpty(),
        )

        preferences.saveDownloadSettings(currentSettings)
        Toast.makeText(this, R.string.settings_saved, Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun parseQualityValue(label: String): String {
        return when (label) {
            getString(R.string.quality_best) -> "best"
            getString(R.string.quality_worst) -> "worst"
            getString(R.string.quality_audio_only) -> "audio_only"
            else -> label.removeSuffix("p")
        }
    }

    override fun onFolderSelected(path: String) {
        binding.outputDirInput.setText(path)
    }
}
