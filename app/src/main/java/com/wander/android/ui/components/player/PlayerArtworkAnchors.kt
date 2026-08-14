package com.wander.android.ui.components.player

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.layout.LayoutCoordinates

/**
 * Where the cover art sits at each end of the sheet's travel, in the sheet's own coordinates.
 *
 * The mini player and the full player each reserve space for the artwork and report the bounds of
 * that space here; the artwork itself is drawn once, by the sheet, and interpolated between the
 * two. That is what makes expanding continuous — the previous cross-fade used two separate artwork
 * composables, so the small one faded out before the large one existed.
 *
 * Bounds survive the full player leaving composition, so a re-expand starts from a known target
 * rather than re-measuring.
 */
@Stable
internal class PlayerArtworkAnchors {

    private var root: LayoutCoordinates? = null

    var miniBounds: Rect? by mutableStateOf(null)
        private set

    var fullBounds: Rect? by mutableStateOf(null)
        private set

    fun onRootPositioned(coordinates: LayoutCoordinates) {
        root = coordinates
    }

    fun onMiniPositioned(coordinates: LayoutCoordinates) {
        coordinates.toSheetRect()?.let { miniBounds = it }
    }

    fun onFullPositioned(coordinates: LayoutCoordinates) {
        coordinates.toSheetRect()?.let { fullBounds = it }
    }

    /**
     * Converted through the root's own coordinates rather than by subtracting a cached root
     * offset: the sheet is translated every frame while dragging, and a cached offset would be one
     * frame stale, so the artwork would visibly lag the finger.
     */
    private fun LayoutCoordinates.toSheetRect(): Rect? {
        val rootCoordinates = root?.takeIf { it.isAttached } ?: return null
        if (!isAttached) return null
        val topLeft = rootCoordinates.localPositionOf(this, Offset.Zero)
        return Rect(topLeft, Size(size.width.toFloat(), size.height.toFloat()))
    }
}
