package com.tuxundl.android

import com.tuxundl.android.common.compat.versionCodeCompat
import com.tuxundl.android.common.log.Log
import com.tuxundl.android.design.AppCrashedDesign
import com.tuxundl.android.log.SystemLogcat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

class AppCrashedActivity : BaseActivity<AppCrashedDesign>() {
    override suspend fun main() {
        val design = AppCrashedDesign(this)

        setContentDesign(design)

        val packageInfo = withContext(Dispatchers.IO) {
            packageManager.getPackageInfo(packageName, 0)
        }

        Log.i("App version: versionName = ${packageInfo.versionName} versionCode = ${packageInfo.versionCodeCompat}")

        val logs = withContext(Dispatchers.IO) {
            SystemLogcat.dumpCrash()
        }

        design.setAppLogs(logs)

        while (isActive) {
            events.receive()
        }
    }
}