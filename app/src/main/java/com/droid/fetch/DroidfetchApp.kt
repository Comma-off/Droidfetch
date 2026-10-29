package com.droid.fetch

import android.app.Application
import com.droid.fetch.data.DeviceInfo
import com.droid.fetch.data.SettingsRepository

class DroidfetchApp : Application() {
    val settings by lazy { SettingsRepository(this) }
    val info by lazy { DeviceInfo(this) }
}
