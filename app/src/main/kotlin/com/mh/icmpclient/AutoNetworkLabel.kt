package com.mh.icmpclient

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build

// NetworkCapabilities.TRANSPORT_SATELLITE == 7 (API 34+); not always on compile classpath.
private const val TRANSPORT_SATELLITE = 7

/**
 * When the user leaves routing on "Auto", records which transport the default network uses
 * (e.g. `auto/wifi`, `auto/mobile`, `auto/satellite`).
 */
fun Context.resolveAutoNetworkSessionLabel(): String {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return "auto/none"
    val caps = cm.getNetworkCapabilities(network) ?: return "auto/unknown"

    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "auto/wifi"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "auto/ethernet"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "auto/mobile"
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            caps.hasTransport(TRANSPORT_SATELLITE) -> "auto/satellite"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_BLUETOOTH) -> "auto/bluetooth"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_USB) -> "auto/usb"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_LOWPAN) -> "auto/lowpan"
        caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN) -> "auto/vpn"
        else -> "auto/other"
    }
}
