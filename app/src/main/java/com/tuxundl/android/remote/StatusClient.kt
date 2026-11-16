package com.tuxundl.android.remote

import android.content.Context
import android.net.Uri
import com.tuxundl.android.common.constants.Authorities
import com.tuxundl.android.common.log.Log
import com.tuxundl.android.service.StatusProvider

class StatusClient(private val context: Context) {
    private val uri: Uri
        get() {
            return Uri.Builder()
                .scheme("content")
                .authority(Authorities.STATUS_PROVIDER)
                .build()
        }

    fun currentProfile(): String? {
        return try {
            val result = context.contentResolver.call(
                uri,
                StatusProvider.METHOD_CURRENT_PROFILE,
                null,
                null
            )

            result?.getString("name")
        } catch (e: Exception) {
            Log.w("Query current profile: $e", e)

            null
        }
    }
}