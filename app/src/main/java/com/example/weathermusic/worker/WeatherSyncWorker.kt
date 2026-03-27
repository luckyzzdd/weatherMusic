package com.example.weathermusic.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.weathermusic.data.local.WeatherDataStore
import com.example.weathermusic.data.repository.WeatherRepository
import com.example.weathermusic.utils.LogUtil
import com.example.weathermusic.utils.NetWorkUtil.isNetworkAvailable
import com.example.weathermusic.utils.NotificationHelper

//// CoroutineWorker：支持协程的Worker（因为我们的网络请求/缓存都是协程）
/**
 * class WeatherSyncWorker(
 *     private val context: Context, // 关键：声明为 private val 成员变量
 *     params: WorkerParameters
 * ) ,不然用不了context
 */
class WeatherSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    private val TAG = "WeatherSyncWorker"
    override suspend fun doWork(): Result {
        LogUtil.d("开始执行定时同步天气任务", TAG)

        // 步骤1：取缓存的城市编码（没有的话任务失败）
        val cityCode = WeatherDataStore.getLastCityCode(applicationContext)

        if (cityCode.isNullOrEmpty()) {
            LogUtil.e("没有缓存的城市编码，同步失败", TAG)
            return Result.failure() // 任务失败
        }
        try {// 步骤2：判断网络状态
            if (isNetworkAvailable()) {
                LogUtil.d("有网络，请求最新天气", TAG)
                // 2.1 有网络：请求最新天气
                val realtimeWeather = WeatherRepository.getRealtimeWeather(cityCode)
                if (realtimeWeather != null) {
                    // 缓存最新数据
                    WeatherDataStore.saveRealtimeWeather(applicationContext, realtimeWeather)
                    // 发通知
                    NotificationHelper.sendWeatherNotification(applicationContext, realtimeWeather)
                } else {
                    // 网络请求失败：读缓存
                    val cachedWeather = WeatherDataStore.getRealtimeWeather(applicationContext)
                    cachedWeather?.let {
                        NotificationHelper.sendWeatherNotification(applicationContext, it)
                    }
                }
            } else {
                LogUtil.d("无网络，使用缓存天气", TAG)
                //读缓存发网络
                val cacheWeather = WeatherDataStore.getRealtimeWeather(applicationContext)
                cacheWeather?.let {
                    NotificationHelper.sendWeatherNotification(applicationContext, cacheWeather)
                } ?: run {
                    LogUtil.e("无网络且无缓存，同步失败", TAG)
                    return Result.failure()
                }

            }
        } catch (e: Exception) {
            LogUtil.e("同步任务出错：${e.message}", TAG)
            e.printStackTrace()
            // 任务失败，让系统重试（默认最多重试3次）
            return Result.retry()
        }
        return Result.success()
    }
}