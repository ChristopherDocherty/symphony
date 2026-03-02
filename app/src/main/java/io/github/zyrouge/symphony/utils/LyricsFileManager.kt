package io.github.zyrouge.symphony.utils

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract

object LyricsFileManager {
    /**
     * Overwrite an existing sidecar file at [uri].
     * If [backupFirst] is true and [existingBakUri] is null, reads the current content and writes
     * it to a new .bak file in the same directory before overwriting.
     * Returns the URI of the newly created backup file, or null if no backup was created.
     */
    fun writeSidecar(
        context: Context,
        uri: Uri,
        content: String,
        existingBakUri: Uri?,
        backupFirst: Boolean,
    ): Uri? {
        var createdBakUri: Uri? = null
        if (backupFirst && existingBakUri == null) {
            val originalContent = try {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            } catch (e: Exception) {
                Logger.warn("LyricsFileManager", "Could not read original for backup", e)
                null
            }
            if (originalContent != null) {
                try {
                    val docId = DocumentsContract.getDocumentId(uri)
                    val parentDocId = docId.substringBeforeLast('/', docId)
                    val parentUri = DocumentsContract.buildDocumentUriUsingTree(uri, parentDocId)
                    val fileName = docId.substringAfterLast('/', docId)
                    val bakName = "$fileName.bak"
                    val bakUri = DocumentsContract.createDocument(
                        context.contentResolver,
                        parentUri,
                        "text/plain",
                        bakName,
                    )
                    if (bakUri != null) {
                        context.contentResolver.openOutputStream(bakUri)?.use {
                            it.write(originalContent.toByteArray())
                        }
                        createdBakUri = bakUri
                    }
                } catch (e: Exception) {
                    Logger.warn("LyricsFileManager", "Backup creation failed", e)
                }
            }
        }
        // Overwrite the original file
        context.contentResolver.openOutputStream(uri, "wt")?.use {
            it.write(content.toByteArray())
        }
        return createdBakUri
    }

    /**
     * Create a new .lrc sidecar file next to the audio file at [audioUri].
     * [basename] should be the filename without extension (e.g. "Song").
     * Returns the URI of the newly created file, or null on failure.
     */
    fun createLrcSidecar(
        context: Context,
        audioUri: Uri,
        basename: String,
        content: String,
    ): Uri? {
        return try {
            val docId = DocumentsContract.getDocumentId(audioUri)
            val parentDocId = docId.substringBeforeLast('/', docId)
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(audioUri, parentDocId)
            val lrcName = "$basename.lrc"
            val newUri = DocumentsContract.createDocument(
                context.contentResolver,
                parentUri,
                "text/plain",
                lrcName,
            ) ?: return null
            context.contentResolver.openOutputStream(newUri)?.use {
                it.write(content.toByteArray())
            }
            newUri
        } catch (e: Exception) {
            Logger.error("LyricsFileManager", "createLrcSidecar failed", e)
            null
        }
    }
}
