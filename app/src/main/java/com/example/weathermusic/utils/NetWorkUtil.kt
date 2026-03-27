package com.example.weathermusic.utils

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import com.example.weathermusic.App

object NetWorkUtil {

    // 辅助方法：检查网络是否可用
    fun isNetworkAvailable(): Boolean {
        val context = App.instance.applicationContext
        val connectivityManager =
            context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // Android 6.0+ 推荐写法（更安全）
            val network = connectivityManager.activeNetwork ?: return false
            val networkCapabilities =
                connectivityManager.getNetworkCapabilities(network) ?: return false
            // 检查是否包含“可用互联网”能力（覆盖WiFi/移动网络/以太网）
            networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                    networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

        }else{
            false
        }

    }
}