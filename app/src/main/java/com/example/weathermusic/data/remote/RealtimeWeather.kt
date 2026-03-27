package com.example.weathermusic.data.remote

data class RealtimeWeather(
    val province: String?, // 省份
    var city: String?, // 城市
    val adcode: String?, // 城市编码
    val weather: String?, // 天气（晴/雨/多云等）
    val temperature: String?, // 温度
    val winddirection: String?, // 风向
    val windpower: String?, // 风力
    val humidity: String?, // 湿度
    val reporttime: String? // 更新时间
)