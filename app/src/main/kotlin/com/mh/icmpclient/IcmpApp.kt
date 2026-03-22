package com.mh.icmpclient

import android.app.Application
import com.mh.icmpclient.db.PingDatabase
import com.mh.icmpclient.repository.PingRepository

class IcmpApp : Application() {

    val pingRepository: PingRepository by lazy {
        PingRepository(PingDatabase.getInstance(this).pingDao())
    }
}
