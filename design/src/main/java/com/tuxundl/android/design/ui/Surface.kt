package com.tuxundl.android.design.ui

import androidx.databinding.BaseObservable
import androidx.databinding.Bindable
import com.tuxundl.android.design.BR

class Surface : BaseObservable() {
    var insets: Insets = Insets.EMPTY
        @Bindable get
        set(value) {
            field = value

            notifyPropertyChanged(BR.insets)
        }
}