package com.example.weathermusic.data.remote

import java.time.DayOfWeek

data class Forecasts (
    val casts: List<Casts>?
)

data class Casts (
    val week: String?,
    val dayweather: String?,
    val nightweather: String?,
    val daytemp: String?,
    val nighttemp: String?
)

data class WeatherResponse (
    val status: String?, // 1=成功，0=失败
    val info: String?, // 状态描述
    val infocode: String?, // 信息码
    val lives: List<RealtimeWeather>?, // 实时天气列表（只有1条）
    val forecasts:List<Forecasts>?
)