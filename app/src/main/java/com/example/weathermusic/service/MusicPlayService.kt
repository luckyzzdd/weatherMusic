package com.example.weathermusic.service

import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.example.weathermusic.data.local.Music
import com.example.weathermusic.utils.MusicPlayerManager
import com.example.weathermusic.utils.NotificationHelper
import com.example.weathermusic.utils.OnPlayStateChangeListener
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel

class MusicPlayService : Service() {
    private val binder = LocalBinder()
    private val serviceScope = CoroutineScope(Dispatchers.Main)
    private val NOTIFICATION_ID = 101

    private val stateListener = object : OnPlayStateChangeListener {
        override fun onPlayStateChanged(isPlaying: Boolean, currentMusic: Music? ) {
            currentMusic?.let { updateNotification(it, isPlaying) }
        }
        override fun onMusicChanged(newMusic: Music?) {}
        override fun onPlayListEnd() {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
    }

    inner class LocalBinder : Binder() { fun getService() = this@MusicPlayService }

    companion object {
        const val ACTION_PLAY = "PLAY"
        const val ACTION_PAUSE = "PAUSE"
        const val ACTION_RESUME = "RESUME"
        const val EXTRA_MUSIC = "music"

        fun startPlay(context: Context, music: Music) {
            val intent = Intent(context, MusicPlayService::class.java).apply {
                action = ACTION_PLAY
                putExtra(EXTRA_MUSIC, music)
            }
            if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }

        fun pausePlay(context: Context) = startService(context, ACTION_PAUSE)
        fun resumePlay(context: Context) = startService(context, ACTION_RESUME)

        private fun startService(context: Context, action: String) {
            val intent = Intent(context, MusicPlayService::class.java).apply { this.action = action }
            if (Build.VERSION.SDK_INT >= 26) ContextCompat.startForegroundService(context, intent)
            else context.startService(intent)
        }
    }

    override fun onCreate() {
        super.onCreate()
        // 注册服务监听器，和UI监听器共存
        MusicPlayerManager.addOnPlayStateChangeListener(stateListener)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> {
                val music = intent.getParcelableExtra<Music>(EXTRA_MUSIC)
                music?.let {
                    MusicPlayerManager.playMusic(applicationContext, it)
                    startForeground(NOTIFICATION_ID, NotificationHelper.getMusicNotification(this, it, true))
                }
            }
            ACTION_PAUSE -> {
                MusicPlayerManager.pausePlay()
                stopForeground(STOP_FOREGROUND_DETACH)
            }
            ACTION_RESUME -> {
                MusicPlayerManager.resumePlay()
                val music = MusicPlayerManager.getCurrentMusic()
                music?.let {
                    startForeground(NOTIFICATION_ID, NotificationHelper.getMusicNotification(this, it, true))
                }
            }
        }
        return START_STICKY
    }

    private fun updateNotification(music: Music, isPlaying: Boolean) {
        val notification = NotificationHelper.getMusicNotification(this, music, isPlaying)
        val nm = ContextCompat.getSystemService(this, NotificationManager::class.java)
        nm?.notify(NOTIFICATION_ID, notification)
    }

    override fun onBind(intent: Intent): IBinder = binder
    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        MusicPlayerManager.release()
    }
}