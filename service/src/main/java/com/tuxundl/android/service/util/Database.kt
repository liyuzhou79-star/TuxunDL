package com.tuxundl.android.service.util

import com.tuxundl.android.service.data.ImportedDao
import com.tuxundl.android.service.data.PendingDao
import java.util.*

suspend fun generateProfileUUID(): UUID {
    var result = UUID.randomUUID()

    while (ImportedDao().exists(result) || PendingDao().exists(result)) {
        result = UUID.randomUUID()
    }

    return result
}
