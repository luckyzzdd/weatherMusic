package com.example.weathermusic.ui.main
// 定位状态密封类：封装定位的所有可能状态
sealed class LocationState {
    object Loading: LocationState()
    data class Success(
        val latitude: String,
        val longitude: String
    ): LocationState()
    data class Error(val message: String) : LocationState() // 定位失败
    object PermissionDenied : LocationState() // 权限拒绝
}