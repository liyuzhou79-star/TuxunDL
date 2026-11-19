package com.tuxundl.android.core.model

import android.os.Parcel
import android.os.Parcelable
import com.tuxundl.android.core.util.Parcelizer
import kotlinx.serialization.Serializable

@Serializable
data class Proxy(
    val name: String,
    val title: String,
    val subtitle: String,
    val type: String,
    val delay: Int,
    // A val, not a var (1.6.1): nothing ever mutates this after
    // construction, and keeping every property immutable is what
    // lets the Compose compiler treat Proxy as stable — node rows
    // can then skip recomposing when their inputs did not change
    // instead of redrawing on every unrelated state update.
    val isGroup: Boolean,
) : Parcelable {
    override fun writeToParcel(parcel: Parcel, flags: Int) {
        Parcelizer.encodeToParcel(serializer(), parcel, this)
    }

    override fun describeContents(): Int {
        return 0
    }

    companion object CREATOR : Parcelable.Creator<Proxy> {
        override fun createFromParcel(parcel: Parcel): Proxy {
            return Parcelizer.decodeFromParcel(serializer(), parcel)
        }

        override fun newArray(size: Int): Array<Proxy?> {
            return arrayOfNulls(size)
        }
    }
}
