package com.videodownloader.app

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.videodownloader.app.databinding.DialogFolderPickerBinding
import com.videodownloader.app.databinding.ItemFolderBinding
import java.io.File

class FolderPickerDialog : DialogFragment() {
    interface Listener {
        fun onFolderSelected(path: String)
    }

    private var _binding: DialogFolderPickerBinding? = null
    private val binding get() = _binding!!

    private lateinit var adapter: FolderAdapter
    private var currentPath: String = AppPreferences.defaultOutputDir()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NORMAL, R.style.Theme_VideoDownloader_FullScreenDialog)
        currentPath = arguments?.getString(ARG_START_PATH) ?: AppPreferences.defaultOutputDir()
        currentPath = StorageHelper.normalizePath(currentPath)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogFolderPickerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = FolderAdapter { folder ->
            currentPath = folder.absolutePath
            refreshFolderList()
        }

        binding.folderList.layoutManager = LinearLayoutManager(requireContext())
        binding.folderList.adapter = adapter

        binding.closeButton.setOnClickListener { dismiss() }
        binding.upButton.setOnClickListener { navigateUp() }
        binding.selectButton.setOnClickListener { selectCurrentFolder() }
        binding.createFolderButton.setOnClickListener { showCreateFolderDialog() }

        binding.shortcutDownloads.setOnClickListener {
            jumpTo(StorageHelper.publicRoots().first().absolutePath)
        }
        binding.shortcutDocuments.setOnClickListener {
            val documents = StorageHelper.publicRoots().getOrNull(1) ?: return@setOnClickListener
            jumpTo(documents.absolutePath)
        }
        binding.shortcutMovies.setOnClickListener {
            val movies = StorageHelper.publicRoots().getOrNull(2) ?: return@setOnClickListener
            jumpTo(movies.absolutePath)
        }

        refreshFolderList()
    }

    private fun jumpTo(path: String) {
        currentPath = StorageHelper.normalizePath(path)
        refreshFolderList()
    }

    private fun navigateUp() {
        val parent = StorageHelper.parentDirectory(currentPath) ?: return
        currentPath = parent
        refreshFolderList()
    }

    private fun selectCurrentFolder() {
        if (!StorageHelper.isWritableDirectory(currentPath)) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle(R.string.folder_not_writable_title)
                .setMessage(R.string.folder_not_writable_message)
                .setPositiveButton(android.R.string.ok, null)
                .show()
            return
        }

        (parentFragment as? Listener ?: activity as? Listener)?.onFolderSelected(currentPath)
        dismiss()
    }

    private fun showCreateFolderDialog() {
        val input = com.google.android.material.textfield.TextInputEditText(requireContext()).apply {
            hint = getString(R.string.new_folder_name)
            setPadding(48, 32, 48, 16)
        }

        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.create_folder)
            .setView(input)
            .setPositiveButton(R.string.create) { _, _ ->
                val name = input.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    val newDir = File(currentPath, name)
                    if (newDir.mkdirs() || newDir.exists()) {
                        currentPath = newDir.absolutePath
                        refreshFolderList()
                    }
                }
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun refreshFolderList() {
        StorageHelper.ensureDirectory(currentPath)
        binding.currentPathText.text = currentPath
        binding.upButton.isEnabled = StorageHelper.parentDirectory(currentPath) != null
        val children = StorageHelper.listChildDirectories(currentPath)
        adapter.submitList(children)
        binding.emptyText.isVisible = children.isEmpty()
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT,
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_START_PATH = "start_path"

        fun newInstance(startPath: String): FolderPickerDialog {
            return FolderPickerDialog().apply {
                arguments = Bundle().apply {
                    putString(ARG_START_PATH, startPath)
                }
            }
        }
    }

    private class FolderAdapter(
        private val onClick: (File) -> Unit,
    ) : RecyclerView.Adapter<FolderAdapter.FolderViewHolder>() {
        private var folders: List<File> = emptyList()

        fun submitList(items: List<File>) {
            folders = items
            notifyDataSetChanged()
        }

        override fun getItemCount(): Int = folders.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FolderViewHolder {
            val binding = ItemFolderBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false,
            )
            return FolderViewHolder(binding)
        }

        override fun onBindViewHolder(holder: FolderViewHolder, position: Int) {
            holder.bind(folders[position], onClick)
        }

        class FolderViewHolder(
            private val binding: ItemFolderBinding,
        ) : RecyclerView.ViewHolder(binding.root) {
            fun bind(folder: File, onClick: (File) -> Unit) {
                binding.folderName.text = folder.name
                binding.root.setOnClickListener { onClick(folder) }
            }
        }
    }
}
