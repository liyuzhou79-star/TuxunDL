package com.tuxundl.android.service

import android.content.Intent
import android.os.IBinder
import com.tuxundl.android.service.remote.IClashManager
import com.tuxundl.android.service.remote.IRemoteService
import com.tuxundl.android.service.remote.IProfileManager
import com.tuxundl.android.service.remote.wrap
import com.tuxundl.android.service.util.cancelAndJoinBlocking

class RemoteService : BaseService(), IRemoteService {
    private val binder = this.wrap()

    private var clash: ClashManager? = null
    private var profile: ProfileManager? = null
    private var clashBinder: IClashManager? = null
    private var profileBinder: IProfileManager? = null

    override fun onCreate() {
        super.onCreate()

        clash = ClashManager(this)
        profile = ProfileManager(this)
        clashBinder = clash?.wrap() as IClashManager?
        profileBinder = profile?.wrap() as IProfileManager?
    }

    override fun onDestroy() {
        super.onDestroy()

        clash?.cancelAndJoinBlocking()
        profile?.cancelAndJoinBlocking()
    }

    override fun onBind(intent: Intent?): IBinder {
        return binder
    }

    override fun clash(): IClashManager {
        return clashBinder!!
    }

    override fun profile(): IProfileManager {
        return profileBinder!!
    }
}