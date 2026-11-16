package com.tuxundl.android.design.util

import com.tuxundl.android.design.view.ObservableScrollView

val ObservableScrollView.isTop: Boolean
    get() = scrollX == 0 && scrollY == 0
