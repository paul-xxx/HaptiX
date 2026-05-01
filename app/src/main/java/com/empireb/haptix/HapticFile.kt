package com.empireb.haptix

import android.net.Uri

data class HapticFile(
    val name: String,
    val uri: Uri,
    val size: Long,
    val dateModified: Long
)
