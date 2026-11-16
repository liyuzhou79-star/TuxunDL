package com.tuxundl.android.design.util

import android.view.View
import androidx.databinding.BindingAdapter

@BindingAdapter("android:minHeight")
fun bindMinHeight(view: View, value: Float) {
    view.minimumHeight = value.toInt()
}
