package com.tuxundl.android.util

import android.net.Uri

val Uri.fileName: String?
    get() = schemeSpecificPart.split("/").lastOrNull()