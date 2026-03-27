package com.example.weathermusic

import android.app.Application
import android.media.MediaPlayer
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.example.weathermusic.data.local.AppDatabase
import com.example.weathermusic.data.local.Music
import com.example.weathermusic.data.local.MusicFolder
import com.example.weathermusic.data.remote.DouBaoApiService
import com.example.weathermusic.data.remote.WeatherApiService
import com.example.weathermusic.data.repository.MusicRepository
import com.example.weathermusic.worker.WeatherSyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

/**
 * 实现全局Application类
 * App 类（继承自 Application）的创建和执行时机是：你的 App 进程被 Android 系统启动的那一刻，且整个 App 生命周期内只会执行一次。
 */
class App : Application() {
    lateinit var weatherApi: WeatherApiService
        private set
    lateinit var appDatabase: AppDatabase
        private set
    lateinit var douBaoApi: DouBaoApiService
    //伴生对象，把App类这个单例送出去
    companion object {
        lateinit var instance: App
        // 1. 替换为你的火山方舟API Key
        const val VOLC_API_KEY = "2cfd5151-7047-4455-ac5d-74f42b607908"
        // 2. 替换为你的模型ID（和Curl测试的一致）
        const val MODEL_ID = "doubao-seed-2-0-mini-260215"

    }

    //在这个里初始化
    override fun onCreate() {
        super.onCreate()
        instance = this
        val retrofit1 = Retrofit.Builder()
            .baseUrl("https://restapi.amap.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        weatherApi = retrofit1.create(WeatherApiService::class.java)

        val retrofit2 = Retrofit.Builder()
            .baseUrl("https://ark.cn-beijing.volces.com/")
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        douBaoApi = retrofit2.create(DouBaoApiService::class.java) // 初始化ApiService

        appDatabase = Room.databaseBuilder(
            this,
            AppDatabase::class.java,
            "weather_music_db"
        )
            .addCallback(FolderInitialCallback())
            .build()
        initWeatherSyncTask()
    }

    //Room 的 onCreate() 是「数据库文件首次创建」时执行，而非 App 每次启动，此时表一定为空，无需查询判断；
    private inner class FolderInitialCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            //用协程插入
            CoroutineScope(Dispatchers.IO).launch {
                // 第二步：插入默认3个收藏夹
                MusicRepository.apply {
                    addFolder("晴天", "SUNNY")
                    addFolder("阴天", "CLOUDY")
                    addFolder("雨天", "RAINY")
                }
                initDefaultSongs()

            }
        }
    }

    private suspend fun initDefaultSongs() {
        try {
            // 1. 从Assets复制3首预制歌曲到内部存储（免权限）
            val sunnyPath = copyAssetToInternal("sunny.mp3")
            val cloudyPath = copyAssetToInternal("cloudy.mp3")
            val rainyPath = copyAssetToInternal("rainy.mp3")

            // 2. 获取3个默认收藏夹的ID（复用你现有Repository方法）
            val sunnyFolderId = MusicRepository.getFolderIdByName("晴天")
            val cloudyFolderId = MusicRepository.getFolderIdByName("阴天")
            val rainyFolderId = MusicRepository.getFolderIdByName("雨天")

            // 3. 构建Music实体（字段名严格匹配你的实体类：name/singer/duration等）
            val sunnySong = Music(
                path = sunnyPath,          // 主键：内部存储的文件路径
                name = "晴天默认曲",        // 对应你的name字段（不是title）
                singer = "默认音乐",        // 对应你的singer字段（不是artist）
                duration = getAudioDuration(sunnyPath),
                album = "默认专辑",         // 专辑（可选，留空也可以）
                size = 500000L,            // 文件大小（字节，示例：500KB）
                folderId = sunnyFolderId   // 关联晴天收藏夹
            )
            val cloudySong = Music(
                path = cloudyPath,
                name = "阴天默认曲",
                singer = "默认音乐",
                duration = getAudioDuration(cloudyPath),
                album = "默认专辑",
                size = 550000L,
                folderId = cloudyFolderId
            )
            val rainySong = Music(
                path = rainyPath,
                name = "雨天默认曲",
                singer = "默认音乐",
                duration = getAudioDuration(rainyPath),
                album = "默认专辑",
                size = 600000L,
                folderId = rainyFolderId
            )

            // 4. 插入数据库（复用你现有Repository方法）
            MusicRepository.insertMusics(listOf(sunnySong, cloudySong, rainySong))
        } catch (e: Exception) {
            // 容错：就算复制失败，也不影响App运行
            e.printStackTrace()
        }
    }
    private fun getAudioDuration(filePath: String): Long {
        return try {
            val mediaPlayer = MediaPlayer()
            mediaPlayer.setDataSource(filePath)
            mediaPlayer.prepare() // 同步准备，只获取时长
            val duration = mediaPlayer.duration.toLong()
            mediaPlayer.release()
            duration
        } catch (e: Exception) {
            0L
        }
    }
    private fun copyAssetToInternal(fileName: String): String {
        val outputFile = filesDir.resolve(fileName) // 内部存储路径：/data/data/包名/files/xxx.mp3
        if (outputFile.exists()) return outputFile.absolutePath // 避免重复复制
        // 复制Assets文件到内部存储
        assets.open(fileName).use { inputStream ->
            outputFile.outputStream().use { outputStream ->
                inputStream.copyTo(outputStream)
            }
        }
        return outputFile.absolutePath
    }

    private fun initWeatherSyncTask() {


        val syncRequest = OneTimeWorkRequestBuilder<WeatherSyncWorker>()
            .setInitialDelay(1, TimeUnit.MINUTES)
            .build()

        //把任务交给WorkManager执行
        WorkManager.getInstance(this)
            .enqueueUniqueWork(
                "WeatherSyncTask",
                ExistingWorkPolicy.REPLACE,
                syncRequest
            )
        WorkManager.getInstance(this)
            .getWorkInfoByIdLiveData(syncRequest.id)
            .observeForever { workInfo ->
                if (workInfo?.state == WorkInfo.State.SUCCEEDED) {
                    // 任务执行成功，重新触发下一次
                    initWeatherSyncTask()
                }
            }

    }

}