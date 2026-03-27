package com.example.weathermusic.data.repository

import android.widget.Toast
import com.example.weathermusic.App
import com.example.weathermusic.data.local.WeatherDataStore
import com.example.weathermusic.data.remote.AddressComponent
import com.example.weathermusic.data.remote.Casts
import com.example.weathermusic.data.remote.ReGeoResponse
import com.example.weathermusic.data.remote.RealtimeWeather
import com.example.weathermusic.data.remote.WeatherResponse
import com.example.weathermusic.data.remote.WeatherResult
import com.example.weathermusic.utils.LogUtil
import com.google.android.material.transition.MaterialSharedAxis
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 天气数据仓库（Repository层）
 * 知识点：
 * 1. 单例模式（object）：Kotlin的单例，替代Java的static + 私有构造方法
 * 2. withContext(Dispatchers.IO)：指定协程在IO线程执行（网络请求必须在子线程）
 * 3. try-catch：捕获网络异常，新手友好的异常处理
 */
object WeatherRepository {
    //const val是编译时常量
    private val AMAP_API_KEY = "4352e10a10781923bf3890fe3ea93573"
    private const val TAG = "WeatherRepository"
    private const val MAX_RETRY_COUNT = 3 // 最大重试次数
    private const val RETRY_DELAY_MS = 500L // 重试间隔（毫秒）

    //带重试逻辑的通用函数
    private suspend fun <T> retryOnFailure(
        maxRetries: Int = MAX_RETRY_COUNT,
        block: suspend () -> T?
    ): T? {
        var retries = 0
        while (retries < maxRetries) {
            try {
                val result = block()//请求成功
                return result
            } catch (e: Exception) {
                retries++
                //请求失败
                LogUtil.e("请求失败，重试次数：$retries/$maxRetries，错误：${e.message}", TAG)
                // 达到最大重试次数，返回null
                if (retries >= maxRetries) {
                    e.printStackTrace()
                    return null
                }
                //延迟重试
                delay(RETRY_DELAY_MS)
            }
        }
        return null
    }

    /**
     * 获得实时天气
     * suspend 说明是挂起函数，明确函数的 “耗时 / 挂起” 特性
     */
    // ✅ 返回值改为WeatherResult，解耦实时+预报
    // 仅获取实时天气（市级别adcode才有效）
    suspend fun getRealtimeWeather(cityCode: String): RealtimeWeather? {
        return withContext(Dispatchers.IO) {
            retryOnFailure {
                val response = App.instance.weatherApi.getRealtimeWeather(
                    key = AMAP_API_KEY,
                    cityCode = cityCode
                )
                LogUtil.d("实时天气返回：${response.lives}", TAG)
                val realtime =  response.lives?.firstOrNull()
                realtime?.let {
                    //缓存实时天气
                    WeatherDataStore.saveRealtimeWeather(App.instance, it)
                    // 缓存城市编码（供定时同步用）
                    WeatherDataStore.saveLastCityCode(App.instance, cityCode)
                }

                realtime
            }
        }
    }

    // 仅获取预报天气（区/市级别adcode都有效）
    suspend fun getForecastWeather(cityCode: String): List<Casts>? {
        return withContext(Dispatchers.IO) {
            retryOnFailure {
                val response = App.instance.weatherApi.getForecastWeather(
                    key = AMAP_API_KEY,
                    cityCode = cityCode
                )
                LogUtil.d("预报天气返回：${response.forecasts}", TAG)
                val casts = response.forecasts?.firstOrNull()?.casts
                casts?.let {
                    WeatherDataStore.saveForecastWeather(App.instance,casts)
                }
                casts
            }
        }
    }

    /**
     * 新增：逆地理编码（经纬度→城市编码）
     * 适配需求：内乡县返回县编码，郑州市返回区编码
     */
    suspend fun getCityInfoByLatLng(latitude: String, longitude: String): AddressComponent? {
        return withContext(Dispatchers.IO) {
            retryOnFailure {
                // 调用高德逆地理编码API（格式：经度,纬度）
                val response: ReGeoResponse = App.instance.weatherApi.getReGeo(
                    key = AMAP_API_KEY,
                    location = "$longitude,$latitude",
                )
                LogUtil.d("逆地理编码返回：${response.toString()}", TAG)
                // 解析行政编码（核心：适配县/区场景）
                response.regeocode?.addressComponent
            }
        }
    }

    suspend fun getAdcodeByCityName(cityName: String): String? {
        return withContext(Dispatchers.IO) {
            retryOnFailure {
                val response = App.instance.weatherApi.getGeoCode(
                    key = AMAP_API_KEY,
                    address = cityName
                )
                LogUtil.d("地理编码返回：${response.toString()}", TAG)
                response.geocodes?.firstOrNull()?.adcode ?: run {
                    // 为空时切换到主线程弹Toast
                    withContext(Dispatchers.Main) {
                        Toast.makeText(
                            App.instance.applicationContext,
                            "输入的城市不存在",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    // 为空时返回null（函数返回值要求String?）
                    null
                }
            }
        }
    }


}