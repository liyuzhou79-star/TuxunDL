package com.tuxundl.android

import com.tuxundl.android.common.util.intent
import com.tuxundl.android.design.NetworkSettingsDesign
import com.tuxundl.android.service.store.ServiceStore
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select

class NetworkSettingsActivity : BaseActivity<NetworkSettingsDesign>() {
    override suspend fun main() {
        val design = NetworkSettingsDesign(
            this,
            uiStore,
            ServiceStore(this),
            clashRunning,
        )

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {
                    when (it) {
                        Event.ClashStart, Event.ClashStop, Event.ServiceRecreated ->
                            recreate()
                        else -> Unit
                    }
                }
                design.requests.onReceive {
                    when (it) {
                        NetworkSettingsDesign.Request.StartAccessControlList ->
                            startActivity(AccessControlActivity::class.intent)
                    }
                }
            }
        }
    }

}
