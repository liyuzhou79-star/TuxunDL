package com.tuxundl.android.service.remote

import com.tuxundl.android.core.model.LogMessage
import com.github.kr328.kaidl.BinderInterface

@BinderInterface
interface ILogObserver {
    fun newItem(log: LogMessage)
}