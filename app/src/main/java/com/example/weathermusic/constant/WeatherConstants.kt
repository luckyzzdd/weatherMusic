package com.example.weathermusic.constant

object WeatherConstants {
    // 所有可选的天气名称（和tag一一对应）
    val ALL_WEATHER_NAMES = listOf(
        "雪天" to "SNOWY",
        "多云" to "CLOUDY_MORE",
        "大风" to "WINDY",
        "雾天" to "FOGGY",
        "雷阵雨" to "THUNDERSTORM",
        "晴天" to "SUNNY", // 默认已加，会被过滤
        "阴天" to "CLOUDY", // 默认已加，会被过滤
        "雨天" to "RAINY"  // 默认已加，会被过滤
    )
}