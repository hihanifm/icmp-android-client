package com.mh.icmpclient

import android.app.Application

class IcmpApp : Application() {

    val pingRepository: PingRepository by lazy {
        PingRepository(com.mh.icmpclient.db.PingDatabase.getInstance(this).pingDao())
    }
}
