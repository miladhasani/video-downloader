package com.videodownloader.app

import android.os.Environment
import java.io.File

object StorageHelper {
    fun publicRoots(): List<File> {
        return listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES),
        ).distinctBy { it.absolutePath }
    }

    fun ensureDirectory(path: String): File {
        val dir = File(path)
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun isWritableDirectory(path: String): Boolean {
        val dir = ensureDirectory(path)
        return dir.exists() && dir.isDirectory && dir.canWrite()
    }

    fun listChildDirectories(path: String): List<File> {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) {
            return emptyList()
        }
        return dir.listFiles()
            ?.filter { it.isDirectory && !it.isHidden }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    fun parentDirectory(path: String): String? {
        val parent = File(path).parentFile ?: return null
        val roots = publicRoots().map { it.absolutePath }.toSet()
        if (path in roots) {
            return null
        }
        return parent.absolutePath
    }

    fun isUnderPublicStorage(path: String): Boolean {
        val normalized = File(path).absolutePath
        return publicRoots().any { root ->
            normalized == root.absolutePath || normalized.startsWith(root.absolutePath + File.separator)
        }
    }

    fun normalizePath(path: String): String = File(path).absolutePath
}
