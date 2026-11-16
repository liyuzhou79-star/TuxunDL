package com.tuxundl.android.design.util

import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import com.tuxundl.android.common.compat.foreground
import com.tuxundl.android.design.model.AppInfo

fun PackageInfo.toAppInfo(pm: PackageManager): AppInfo {
    return AppInfo(
        packageName = packageName,
        icon = applicationInfo!!.loadIcon(pm).foreground(),
        label = applicationInfo!!.loadLabel(pm).toString(),
        installTime = firstInstallTime,
        updateDate = lastUpdateTime,
    )
}
