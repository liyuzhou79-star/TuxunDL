@file:Suppress("DEPRECATION")

package com.tuxundl.android.common.compat

import android.content.pm.PackageInfo

val PackageInfo.versionCodeCompat: Long
    get() {
        return if (android.os.Build.VERSION.SDK_INT >= 28) {
            longVersionCode
        } else {
            versionCode.toLong()
        }
    }
