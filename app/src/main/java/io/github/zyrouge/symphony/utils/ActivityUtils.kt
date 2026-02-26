package io.github.zyrouge.symphony.utils

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import io.github.zyrouge.symphony.Symphony
import io.github.zyrouge.symphony.R

object ActivityUtils {
    fun startBrowserActivity(activity: Context, uri: Uri) {
        activity.startActivity(Intent(Intent.ACTION_VIEW).setData(uri))
    }

    fun copyToClipboardAndNotify(symphony: Symphony, text: String) {
        val context = symphony.applicationContext
        val clipboardManager = context.getSystemService(ClipboardManager::class.java)
        clipboardManager.setPrimaryClip(ClipData.newPlainText(null, text))
        Toast.makeText(context, symphony.applicationContext.getString(R.string.CopiedXToClipboard, text), Toast.LENGTH_SHORT).show()
    }

    fun makePersistableReadableUri(context: Context, uri: Uri) {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }

    fun makePersistableReadWriteUri(context: Context, uri: Uri) {
        try {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        } catch (_: SecurityException) {
            // Write permission not available (e.g. folder was added before write support).
            // Fall back to read-only so scanning still works.
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    fun releasePersistableReadableUri(context: Context, uri: Uri) {
        context.contentResolver.releasePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}
