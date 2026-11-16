package com.tuxundl.android.common.constants

import android.content.ComponentName
import com.tuxundl.android.common.util.packageName

object Components {
    private const val componentsPackageName = "com.tuxundl.android"

    val MAIN_ACTIVITY = ComponentName(packageName, "$componentsPackageName.MainActivity")
    val PROPERTIES_ACTIVITY = ComponentName(packageName, "$componentsPackageName.PropertiesActivity")
}