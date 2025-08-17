package io.github.zyrouge.symphony.services.database.store

import android.net.Uri
import android.util.Log
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.services.database.adapters.FileTreeDatabaseAdapter
import java.nio.file.Paths
import java.security.MessageDigest
import java.math.BigInteger

// Helper function for MD5 Hashing (can be private to this file or class)
private fun String.toMd5(): String {
    val md = MessageDigest.getInstance("MD5")
    val bigInt = BigInteger(1, md.digest(this.toByteArray(Charsets.UTF_8)))
    return String.format("%032x", bigInt)
}

class DirectoryArtworkCacheStore(symphony: Symphony) {
    private val adapter = FileTreeDatabaseAdapter(
        Paths
            .get(symphony.applicationContext.dataDir.absolutePath, "directory_artwork_cache")
            .toFile()
    )
    private val logTag = "DirArtworkCache"

    fun insert(directoryPathKey: String, imageUri: Uri) {
        if (directoryPathKey.isBlank()) {
            Log.w(logTag, "Attempted to insert blank directoryPathKey.")
            return
        }
        val hashedFilename = directoryPathKey.toMd5()
        val keyFile = adapter.get(hashedFilename)

        if (!keyFile.exists()) {
            try {
                // Store original path on the first line, URI on the second
                val content = "$directoryPathKey\n${imageUri.toString()}"
                keyFile.writeText(content)
            } catch (e: Exception) {
                Log.e(logTag, "Error writing to cache. Original key: '$directoryPathKey', Hashed key: '$hashedFilename'", e)
            }
        }
    }

    fun get(directoryPathKey: String): Uri? {
        if (directoryPathKey.isBlank()) {
            Log.w(logTag, "Attempted to get blank directoryPathKey.")
            return null
        }
        val hashedFilename = directoryPathKey.toMd5()
        val keyFile = adapter.get(hashedFilename)

        if (keyFile.exists() && keyFile.isFile) {
            try {
                val lines = keyFile.readLines()
                if (lines.size >= 2) {
                    val uriString = lines[1]
                    if (uriString.isNotBlank()) {
                        return Uri.parse(uriString)
                    } else {
                        Log.w(logTag, "URI string in cache file is blank. Original key: '$directoryPathKey', Hashed key: '$hashedFilename'")
                    }
                } else {
                    Log.w(logTag, "Cache file does not have enough lines. Original key: '$directoryPathKey', Hashed key: '$hashedFilename'")
                }
            } catch (e: Exception) {
                Log.e(logTag, "Error reading from cache. Original key: '$directoryPathKey', Hashed key: '$hashedFilename'", e)
            }
        }
        return null
    }

    // Returns a Set of ORIGINAL directory path keys
    fun keys(): Set<String> {
        val originalKeys = mutableSetOf<String>()
        val hashedFilenames = adapter.list() // Gets List<String> of filenames (hashes)

        for (hashedFilename in hashedFilenames) {
            val keyFile = adapter.get(hashedFilename)
            if (keyFile.exists() && keyFile.isFile) {
                try {
                    val lines = keyFile.readLines()
                    if (lines.isNotEmpty()) {
                        val originalPath = lines[0]
                        if (originalPath.isNotBlank()) {
                            originalKeys.add(originalPath)
                        } else {
                             Log.w(logTag, "Original path in cache file is blank. Hashed key: '$hashedFilename'")
                        }
                    } else {
                        Log.w(logTag, "Cache file is empty. Hashed key: '$hashedFilename'")
                    }
                } catch (e: Exception) {
                    Log.e(logTag, "Error reading original key from cache file. Hashed key: '$hashedFilename'", e)
                }
            }
        }
        return originalKeys
    }

    fun delete(directoryPathKeys: Collection<String>) {
        directoryPathKeys.forEach { originalPath ->
            if (originalPath.isNotBlank()) {
                val hashedFilename = originalPath.toMd5()
                val keyFile = adapter.get(hashedFilename)
                if (keyFile.exists()) {
                    if (!keyFile.delete()) {
                        Log.w(logTag, "Failed to delete cache file. Original key: '$originalPath', Hashed key: '$hashedFilename'")
                    }
                }
            } else {
                Log.w(logTag, "Attempted to delete blank directoryPathKey from collection.")
            }
        }
    }

    fun clear() {
        adapter.clear() // Deletes all files (hashed entries) in the cache directory
    }
}
