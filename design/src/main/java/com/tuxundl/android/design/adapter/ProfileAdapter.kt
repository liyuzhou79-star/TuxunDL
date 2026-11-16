package com.tuxundl.android.design.adapter

import android.content.Context
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.tuxundl.android.design.databinding.AdapterProfileBinding
import com.tuxundl.android.design.model.ProfilePageState
import com.tuxundl.android.design.model.ProxyPageState
import com.tuxundl.android.design.ui.ObservableCurrentTime
import com.tuxundl.android.design.util.layoutInflater
import com.tuxundl.android.service.model.Profile

class ProfileAdapter(
    private val context: Context,
    private val onClicked: (Profile) -> Unit,
    private val onMenuClicked: (Profile) -> Unit,
) : RecyclerView.Adapter<ProfileAdapter.Holder>() {
    class Holder(val binding: AdapterProfileBinding) : RecyclerView.ViewHolder(binding.root)

    private val currentTime = ObservableCurrentTime()

    var profiles: List<Profile> = emptyList()
    val states = ProfilePageState()

    fun updateElapsed() {
        currentTime.update()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return Holder(
            AdapterProfileBinding
                .inflate(context.layoutInflater, parent, false)
                .also { it.currentTime = currentTime }
        )
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val current = profiles[position]
        val binding = holder.binding

        if (current === binding.profile)
            return

        binding.profile = current
        binding.setClicked {
            onClicked(current)
        }
        binding.setMenu {
            onMenuClicked(current)
        }
    }

    override fun getItemCount(): Int {
        return profiles.size
    }
}