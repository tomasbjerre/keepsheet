package com.github.tomasbjerre.keepsheet.ui

import android.net.Uri
import java.io.File

/**
 * One page captured with the camera or added via Import, while still part of a running
 * Capture session (see specs/capture-and-processing.md#multi-page-capture) — not yet a
 * persisted [com.github.tomasbjerre.keepsheet.data.Page]. [ownedFile] is the temp file this
 * screen wrote (for a camera shot) and is responsible for cleaning up; null for an imported
 * photo, whose Uri belongs to the photo picker/provider, not this screen.
 */
data class CapturedPage(
    val id: String,
    val uri: Uri,
    val ownedFile: File?,
)
