package com.example.weathermusic.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.weathermusic.data.remote.Casts
import com.example.weathermusic.data.remote.RealtimeWeather
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

// 扩展函数创建DataStore实例（单例）
/**
 * DataStore：数据存储的核心类，负责读写数据；
 * Preferences：DataStore 专门用于存储键值对的类型
 * by preferencesDataStore(name = "weather_cache")
 * by：Kotlin 的委托属性关键字 —— 把 weatherDataStore 的创建、管理逻辑委托给 preferencesDataStore 这个函数；
 * preferencesDataStore(name = "weather_cache")：Google 提供的内置函数，作用是：
 * ① 创建一个 DataStore 实例；
 * ② 保证这个实例是单例（无论你调用多少次 context.weatherDataStore，都只会创建一个实例，避免重复创建导致的资源浪费）；
 * ③ name = "weather_cache"：指定这个 DataStore 的存储文件名（最终会在应用的私有目录下生成一个名为 weather_cache.preferences_pb 的文件，用来持久化存储数据）。
 */
val Context.weatherDataStore: DataStore<Preferences> by  preferencesDataStore(name = "weather_cache")
object WeatherDataStore {
    //定义缓存
    private val KEY_REALTIME_WEATHER = stringPreferencesKey("realtime_weather")
    private val KEY_FORECAST_WEATHER = stringPreferencesKey("forecast_weather")
    private val KEY_LAST_CITY_CODE = stringPreferencesKey("last_city_code")
    //处理序列化的
    private val gson = Gson()
    //缓存实时天气
    suspend fun saveRealtimeWeather(context: Context, weather: RealtimeWeather){
        context.weatherDataStore.edit {
            pref->
            pref[KEY_REALTIME_WEATHER] = gson.toJson(weather)
        }
    }
    // 读取缓存的实时天气//异步读取
    suspend fun getRealtimeWeather(context: Context): RealtimeWeather? {
        //map不是键值对的意思，是对数据项做转换。。。data是数据流Flow<Pref>
        return context.weatherDataStore.data.map {
            pref->
            //pref是一堆键值对，pref[KEY_REALTIME_WEATHER]，是key的一个键值对，它把这个value给变成Weather对象
            pref[KEY_REALTIME_WEATHER]?.let {
                json->
                gson.fromJson(json, RealtimeWeather::class.java)
            }
        }.first()
    }
    // 缓存预报天气
    suspend fun saveForecastWeather(context: Context, forecasts: List<Casts>) {
        context.weatherDataStore.edit { pref ->
            pref[KEY_FORECAST_WEATHER] = gson.toJson(forecasts)
        }
    }

    // 读取缓存的预报天气
    suspend fun getForecastWeather(context: Context): List<Casts>? {
        return context.weatherDataStore.data.map {
                pref ->
                pref[KEY_FORECAST_WEATHER]?.let { json ->
                    val type = object : TypeToken<List<Casts>>() {}.type
                    //编译器无法从 Type 类型的参数中自动推导 T 是 List<Casts>
                    gson.fromJson<List<Casts>>(json, type)
                }
            }.first()
    }

    // 缓存最后使用的城市编码（用于定时同步）
    suspend fun saveLastCityCode(context: Context, cityCode: String) {
        context.weatherDataStore.edit { pref ->
            pref[KEY_LAST_CITY_CODE] = cityCode
        }
    }
    suspend fun getLastCityCode(context: Context): String?{
        return context.weatherDataStore.data.map {
            preferences ->
            preferences[KEY_LAST_CITY_CODE]
        }.first()
    }



}