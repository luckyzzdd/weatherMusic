package com.example.weathermusic.data.remote

data class GeoCodeResponse(
    val geocodes: List<GeoCodeItem>?
)

data class GeoCodeItem(
    val adcode: String, // 核心：返回adcode（如内乡县=411325）
    val formatted_address: String, // 完整地址（河南省南阳市内乡县）
    val district: Any, // 区县名（内乡县）
    val city: String,
)