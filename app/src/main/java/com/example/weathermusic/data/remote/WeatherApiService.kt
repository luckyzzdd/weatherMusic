package com.example.weathermusic.data.remote

import retrofit2.http.GET
import retrofit2.http.Query

interface WeatherApiService {
    // 1. 仅获取实时天气（extensions=base）
    @GET("v3/weather/weatherInfo")
    suspend fun getRealtimeWeather(
        @Query("key") key: String,
        @Query("city") cityCode: String,
        @Query("extensions") extensions: String = "base"
    ): WeatherResponse

    // 2. 仅获取预报天气（extensions=all）
    @GET("v3/weather/weatherInfo")
    suspend fun getForecastWeather(
        @Query("key") key: String,
        @Query("city") cityCode: String,
        @Query("extensions") extensions: String = "all"
    ): WeatherResponse

    // 逆地理编码接口（保留）
    @GET("v3/geocode/regeo")
    suspend fun getReGeo(
        @Query("key") key: String,
        @Query("location") location: String
    ): ReGeoResponse

    // 新增：地理编码（城市名 → adcode）
    @GET("v3/geocode/geo")
    suspend fun getGeoCode(
        @Query("key") key: String,
        @Query("address") address: String // 传入"内乡县"/"郑州市"等
    ): GeoCodeResponse

}