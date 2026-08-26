package com.wander.android.ui.screens.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Picks the one folder the on-device scan looks in.
 *
 * A phone's audio is not all music. Ringtones, podcast downloads, voice memos and whatever a
 * messaging app decided to save all satisfy MediaStore's `IS_MUSIC`, so on a full device the
 * library is mostly things nobody wants to see. Choosing a folder is the fix, and it is a long
 * press on the row rather than a row of its own — rescanning is still the common action, and this
 * is set once and forgotten.
 *
 * The tree URI is only used to *derive* a path and to name the folder in Settings. The scan itself
 * still goes through MediaStore, which already has the metadata indexed — walking the tree with
 * `DocumentFile` instead would mean re-reading tags for every file on every scan.
 */
@Composable
internal fun rememberLocalFolderPicker(onPicked: (path: String, label: String) -> Unit): () -> Unit {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Without this the grant dies with the Activity, and the next scan would be filtered to a
        // folder the app can no longer name.
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
        val path = uri.toRelativePath() ?: return@rememberLauncherForActivityResult
        onPicked(path, path.trimEnd('/'))
    }
    return remember(launcher) { { launcher.launch(null) } }
}

/**
 * `content://…/tree/primary:Music/Vinyl` → `Music/Vinyl/`, which is what MediaStore's
 * `RELATIVE_PATH` holds.
 *
 * Null for anything that is not on the primary volume. An SD card's documents are addressed by
 * volume id rather than `primary`, and MediaStore stores its `RELATIVE_PATH` relative to *that*
 * volume — so the same string would silently match the wrong files, or nothing at all. Refusing
 * is better than filtering to a folder that is not the one the user pointed at.
 */
private fun Uri.toRelativePath(): String? {
    val documentId = DocumentsContract.getTreeDocumentId(this) ?: return null
    val (volume, path) = documentId.split(':', limit = 2).let {
        it.getOrNull(0) to it.getOrNull(1)
    }
    if (volume != PRIMARY_VOLUME || path.isNullOrBlank()) return null
    return path.trimEnd('/') + "/"
}

private const val PRIMARY_VOLUME = "primary"

/** `RELATIVE_PATH` arrived in API 29; below that the scan cannot be narrowed at all. */
internal val supportsFolderScan: Boolean
    get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
