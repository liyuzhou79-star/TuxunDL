package com.tuxundl.android.service.remote

import com.tuxundl.android.core.model.FetchStatus
import com.github.kr328.kaidl.BinderInterface

@BinderInterface
fun interface IFetchObserver {
    fun updateStatus(status: FetchStatus)
}