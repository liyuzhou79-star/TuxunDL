package com.tuxundl.android

import android.content.pm.PackageManager
import com.tuxundl.android.common.compat.getDrawableCompat
import com.tuxundl.android.common.constants.Metadata
import com.tuxundl.android.core.Clash
import com.tuxundl.android.design.OverrideSettingsDesign
import com.tuxundl.android.design.model.AppInfo
import com.tuxundl.android.design.util.toAppInfo
import com.tuxundl.android.service.store.ServiceStore
import com.tuxundl.android.util.withClash
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withContext

class OverrideSettingsActivity : BaseActivity<OverrideSettingsDesign>() {
    override suspend fun main() {
        val configuration = withClash { queryOverride(Clash.OverrideSlot.Persist) }
        val service = ServiceStore(this)

        defer {
            withClash {
                patchOverride(Clash.OverrideSlot.Persist, configuration)
            }
        }

        val design = OverrideSettingsDesign(
            this,
            configuration
        )

        setContentDesign(design)

        while (isActive) {
            select<Unit> {
                events.onReceive {

                }
                design.requests.onReceive {
                    when (it) {
                        OverrideSettingsDesign.Request.ResetOverride -> {
                            if (design.requestResetConfirm()) {
                                defer {
                                    withClash {
                                        clearOverride(Clash.OverrideSlot.Persist)
                                    }
                                }

                                finish()
                            }
                        }
                    }
                }
            }
        }
    }
}