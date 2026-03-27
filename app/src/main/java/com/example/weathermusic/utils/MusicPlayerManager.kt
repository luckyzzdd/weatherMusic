package com.example.weathermusic.utils

import android.content.Context
import android.media.MediaPlayer
import android.net.Uri
import com.example.weathermusic.data.local.Music
import com.example.weathermusic.ui.view.SimpleCircleView
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.TickerMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

interface OnPlayStateChangeListener{
    fun onPlayStateChanged(isPlaying: Boolean, currentMusic: Music?)
    fun onMusicChanged(newMusic: Music?)
    fun onPlayListEnd()
}

object MusicPlayerManager {
    // 多监听器列表
    private val playStateListeners = mutableListOf<OnPlayStateChangeListener>()

    private var mediaPlayer: MediaPlayer? = null
    private var currentMusic: Music? = null

    private var playList: List<Music> = emptyList()
    private var currentPlaylistIndex = -1

    private var progressJob: Job? = null
    private val mainScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private val _progressFlow = MutableStateFlow(0 to 0)
    val progressFlow: StateFlow<Pair<Int, Int>> = _progressFlow.asStateFlow()
    //默认列表循环
    private var mode: SimpleCircleView.Mode = SimpleCircleView.Mode.LIST_LOOP
    fun setMode(mode: SimpleCircleView.Mode){
        this.mode = mode
    }
    // 注册监听器
    fun addOnPlayStateChangeListener(listener: OnPlayStateChangeListener) {
        if (!playStateListeners.contains(listener)) {
            playStateListeners.add(listener)
        }
    }

    // 移除监听器
    fun removeOnPlayStateChangeListener(listener: OnPlayStateChangeListener) {
        playStateListeners.remove(listener)
    }

    fun setPlayList(list: List<Music>){
        playList = list
        currentPlaylistIndex = list.indexOfFirst { it.path == currentMusic?.path }
    }

    // 去掉参数 mode，直接用内部的 this.mode
    fun playNextMusic(context: Context) {
        if (playList.isEmpty()) {
            playStateListeners.forEach { it.onPlayListEnd() }
            return
        }

        when (mode) {
            SimpleCircleView.Mode.SINGLE_LOOP -> {
                // 不切换索引
            }
            SimpleCircleView.Mode.LIST_LOOP -> {
                //播放继续播放
                currentPlaylistIndex = (currentPlaylistIndex + 1) % playList.size
            }
            SimpleCircleView.Mode.RANDOM -> {

                // 1. 只有列表大于1首歌才随机
                if (playList.size > 1) {
                    var randomIndex = currentPlaylistIndex
                    // 2. while循环：随机到和当前索引不同为止
                    while (randomIndex == currentPlaylistIndex) {
                        randomIndex = (0 until playList.size).random()
                    }
                    // 3. 赋值新索引
                    currentPlaylistIndex = randomIndex
                }
            }
        }

        playMusic(context, playList[currentPlaylistIndex])
    }

    fun playMusic(context: Context, music: Music): Boolean {
        try {
            // 停止之前的进度条更新协程（防止多个协程同时发进度）
            stopProgressUpdate()
            if (currentMusic?.path == music.path) {
                // 判断：要播放的歌 = 正在播放的歌
                mediaPlayer?.start() // 继续播放（暂停→播放）
                startProgressUpdate()   // 重启进度更新协程
                //开启回调，播放状态改为播放种，1更新通知，2更新ui
                playStateListeners.forEach { it.onPlayStateChanged(true, music) }
                return true
            }
            //不是同一首歌，销毁上一首歌的MediaPlayer，释放内存，避免卡顿/杂音
            releasePlayer()

            mediaPlayer = MediaPlayer().apply {
                reset() // 重置播放器状态（必须！MediaPlayer有严格状态机）

                //处理要播放的歌曲路径
                // 判断路径类型：
                // 1. content://  → 手机系统媒体库的音乐（最常见）
                // 2. 普通路径    → 本地文件
                val uri = if (music.path.startsWith("content://")) {
                    Uri.parse(music.path)
                } else {
                    Uri.fromFile(File(music.path))
                }
                //转成正确的uri，设置播放源
                setDataSource(context.applicationContext, uri)
                //设置错误监听（播放失败处理）
                setOnErrorListener { _, _, _ ->
                    releasePlayer()
                    currentMusic = null
                    //通知监听器：播放失败，状态为暂停
                    playStateListeners.forEach { it.onPlayStateChanged(false, null) }
                    true
                }
                //设置准备完成监听（异步加载完成→播放）
                setOnPreparedListener {
                    start()               // 开始播放！
                    currentMusic = music  // 记录：当前正在播放的歌曲
                    // 记录歌曲在列表中的位置（用于切歌）
                    currentPlaylistIndex = playList.indexOfFirst { it.path == music.path }
                    startProgressUpdate() // 启动进度条更新协程
                    // 通知监听器：歌曲切换了
                    playStateListeners.forEach { it.onMusicChanged(music) }
                    // 通知监听器：播放状态变为【播放中】
                    playStateListeners.forEach { it.onPlayStateChanged(true, music) }
                }
                //设置播放完成监听（自动下一首）
                setOnCompletionListener {
                    stopProgressUpdate() // 停止进度更新
                    playNextMusic(context) // 自动播放下一首歌（根据播放模式：列表/随机/单曲）
                }
                //异步加载歌曲（不卡主线程）
                prepareAsync()
            }
            return true
        } catch (e: Exception) {
            e.printStackTrace()
            releasePlayer()
            return false
        }
    }

    fun pausePlay(): Boolean {
        if (mediaPlayer?.isPlaying == true) {
            mediaPlayer?.pause()
            stopProgressUpdate()
            playStateListeners.forEach { it.onPlayStateChanged(false, currentMusic) }
            return true
        }
        return false
    }

    fun resumePlay(): Boolean {
        if (mediaPlayer != null && !mediaPlayer!!.isPlaying && currentMusic != null) {
            mediaPlayer?.start()
            startProgressUpdate()
            playStateListeners.forEach { it.onPlayStateChanged(true, currentMusic) }
            return true
        }
        return false
    }

    fun stopPlay() {
        stopProgressUpdate()
        releasePlayer()
        currentMusic = null
        playStateListeners.forEach { it.onPlayStateChanged(false, null) }
    }

    private fun startProgressUpdate() {
        progressJob?.cancel()
        progressJob = mainScope.launch {
            while (mediaPlayer?.isPlaying == true) {
                delay(500)
                val pos = mediaPlayer?.currentPosition ?: 0
                val dur = mediaPlayer?.duration ?: 0
                _progressFlow.emit(pos to dur)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
    }

    private fun releasePlayer() {
        mediaPlayer?.let {
            try {
                if (it.isPlaying) it.stop()
                it.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        mediaPlayer = null
    }

    fun isPlaying() = mediaPlayer?.isPlaying ?: false
    fun getCurrentMusic() = currentMusic
    fun seekTo(position: Int) = mediaPlayer?.seekTo(position)
    fun getCurrentPosition() = mediaPlayer?.currentPosition ?: 0
    fun getCurrentDuration() = mediaPlayer?.duration ?: 0

    fun release() {
        stopPlay()
        mainScope.cancel()
        playList = emptyList()
        currentPlaylistIndex = -1
        playStateListeners.clear()
    }
}