package com.example.weathermusic.utils

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.weathermusic.R
import com.example.weathermusic.data.local.Music
import com.example.weathermusic.data.remote.RealtimeWeather
import com.example.weathermusic.service.MusicPlayService

object NotificationHelper {
    // 原有代码不变 ↓
    private const val CHANNEL_ID = "WEATHER_CHANNEL"
    private const val CHANNEL_NAME = "天气通知"
    private const val NOTIFICATION_ID = 1001

    private fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description="天气同步和提醒通知"
            enableVibration(true)
        }
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    private fun getWeatherSuggestion(weatherType: String): String{
        return when{
            weatherType.contains("雨") -> "今天有雨，记得带雨伞，出门注意路滑～"
            weatherType.contains("晴") -> "今天晴天，阳光充足，记得做好防晒哦～"
            weatherType.contains("雪") -> "今天有雪，气温较低，注意保暖，出行小心路滑～"
            weatherType.contains("雾") || weatherType.contains("霾") -> "今天有雾/霾，减少外出，出门记得戴口罩～"
            weatherType.contains("阴") -> "今天阴天，气温适中，适合户外活动～"
            weatherType.contains("云")->"今天多云，很多云，云很多"
            else -> "今天天气比较小众，你多保重"
        }
    }

    fun sendWeatherNotification(context: Context, weather: RealtimeWeather){
        createNotificationChannel(context)
        val title = "${weather.city} 天气更新"
        val content = "当前温度：${weather.temperature}℃，${weather.weather}。${getWeatherSuggestion(weather.weather ?: "")}"
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(com.example.weathermusic.R.drawable.app_icon)
            .setContentTitle(title)
            .setContentText(content)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()
        val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID,notification)
    }

    private fun createMusicNotificationChannel(context: Context): String {
        val channelId = "MUSIC_CHANNEL"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "音乐播放",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "音乐播放通知"
            }
            val nm  =context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
        return channelId
    }

    fun getMusicNotification(context: Context, music: Music,isPlaying: Boolean): Notification {
        val channelId = createMusicNotificationChannel(context)


        val action = if (isPlaying) {
            NotificationCompat.Action(
                R.drawable.ic_pause, // 暂停图标（确保资源存在）
                "暂停",
                getPausePendingIntent(context)
            )
        } else {
            NotificationCompat.Action(
                R.drawable.ic_play, // 播放图标（确保资源存在）
                "播放",
                getPlayPendingIntent(context, music)
            )
        }
// 强制折叠态显示图标（关键）

        return NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.music)
            .setContentTitle("正在播放")
            .setContentText(music.name)
            .setOngoing(true)
            .addAction(action)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun getPausePendingIntent(context: Context): PendingIntent {
        val pauseIntent = Intent(context, MusicPlayService::class.java).apply {
            action = MusicPlayService.ACTION_PAUSE
        }
        return PendingIntent.getService(
            context,
            102, // 暂停按钮用固定值即可（只有1个暂停动作）
            pauseIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    // 仅修改这个方法 ↓（requestCode 关联歌曲唯一标识）
    private fun getPlayPendingIntent(context: Context, music: Music): PendingIntent {
        val playIntent = Intent(context, MusicPlayService::class.java).apply {
            action = MusicPlayService.ACTION_PLAY
            putExtra(MusicPlayService.EXTRA_MUSIC, music)
        }
        // 关键优化：用music.path的hashCode作为requestCode，确保不同歌曲的播放Intent不冲突
        val requestCode = music.path.hashCode() // 替换固定值103
        return PendingIntent.getService(
            context,
            requestCode,
            playIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }


}