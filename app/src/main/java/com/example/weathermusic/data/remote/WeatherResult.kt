package com.example.weathermusic.data.remote

data class WeatherResult (
    val realtime: RealtimeWeather?, // 实时天气（区编码可能为null）
    val forecast: List<Casts>?
)