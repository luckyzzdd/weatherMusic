package com.example.weathermusic.data.repository

import android.util.Log
import com.example.weathermusic.App
import com.example.weathermusic.data.local.Music
import com.example.weathermusic.data.local.MusicFolder
import com.example.weathermusic.data.remote.ContentItem
import com.example.weathermusic.data.remote.DouBaoRequest
import com.example.weathermusic.data.remote.InputItem
import com.example.weathermusic.utils.MusicScanner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

object MusicRepository {
    private const val TAG = "MusicRepository"
    private val musicDao = App.instance.appDatabase.musicDao()

    // ========== 原有基础方法（保留） ==========
    suspend fun insertMusics(musics: List<Music>) {
        musicDao.insertMusics(musics)
    }

    fun observeAllMusics(): Flow<List<Music>> {
        return musicDao.getAllMusics()
    }

    suspend fun scanLocalMusic(): List<Music> = withContext(Dispatchers.IO) {
        MusicScanner.scanLocalMusic(App.instance)
    }
    // 新增：获取指定文件夹下的歌曲数量
    suspend fun getSongCountByFolderId(folderId: Int): Int {
        // 假设你的Room Dao有查询文件夹歌曲数的方法，核心逻辑是「根据folderId查关联的歌曲数」
        // 示例（替换成你实际的Dao调用）：
        return musicDao.getSongCountByFolderId(folderId)
    }
    suspend fun addFolder(folderName: String, folderTag: String): Boolean {
        val existingFolder = musicDao.getFolderByName(folderName)
        if (existingFolder != null) {
            return false
        }
        val newFolder = MusicFolder(
            folderName = folderName,
            folderTag = folderTag
        )
        musicDao.insertFolder(newFolder)
        return true
    }

    fun observeAllFolders(): Flow<List<MusicFolder>> {
        return musicDao.observeAllFolders()
    }

    suspend fun getAllFolderNames(): List<String> {
        return musicDao.getAllFolderNames()
    }

    // ========== 重构：多对一核心方法 ==========
    // 新增：把歌曲添加到指定收藏夹（本质是更新folderId）
    suspend fun assignMusicToFolder(musicPath: String, folderId: Int?) {
        withContext(Dispatchers.IO) {

            musicDao.updateMusicFolderId(musicPath, folderId)
            Log.d("SongSelector", musicPath)
            Log.d("SongSelector", folderId.toString())
        }
    }

    // ❶ 新增：直接查询指定收藏夹的歌曲（非Flow，适合单次加载）
    suspend fun getMusicsByFolderIdDirectly(folderId: Int): List<Music> {
        return withContext(Dispatchers.IO) {
            musicDao.getMusicsByFolderIdDirectly(folderId)
        }
    }

    // 新增：获取未归属任何收藏夹的歌曲（供添加列表使用）
    suspend fun getUnassignedMusics(): List<Music> {
        return withContext(Dispatchers.IO) {
            musicDao.getUnassignedMusics()
        }
    }



    /***************  ai   ***********/
    /**
     * 单首歌AI归类（小模块核心方法）
     * @param music 待归类歌曲
     * @param folderNames 收藏夹名称列表
     * @return 匹配的收藏夹名称 / "未匹配" / null（失败）
     */
    suspend fun aiClassifySingleSong(music: Music, folderNames: List<String>): String? =
        withContext(
            Dispatchers.IO
        ) {
            try {// 1. 构建AI指令文本（和你测试的Curl一致）
                // 替换原有prompt
                val prompt = """
                     严格按以下规则匹配收藏夹名称，仅返回名称无其他内容：
                     1. 分析歌曲《${music.name}》（歌手：${music.singer}）的情感/风格；
                     2. 收藏夹列表：【${folderNames.joinToString("、")}】；
                     3. 情感匹配规则（参考）：
                        - 欢快/明朗 → 晴天；
                        - 沉闷/忧郁 → 阴天；
                        - 伤感/悲伤 → 雨天；
                        - 清冷/孤寂 → 雪天；
                      4. 必须从列表中选最接近的1个，仅返回名称（如“雨天”），无空格、无标点、无解释。
""".trimIndent()
                //构建请求体
                val douBaoRequest = DouBaoRequest(
                    model = App.MODEL_ID,
                    input = listOf(
                        InputItem(
                            role = "user",
                            content = listOf(
                                ContentItem(
                                    type = "input_text",
                                    text = prompt
                                )
                            )
                        )
                    )
                )
                val response = App.instance.douBaoApi.classifySong(
                    token = "Bearer ${App.VOLC_API_KEY}",
                    request = douBaoRequest
                )
                // 4. 处理错误
                if (response.error != null) {
                    println("AI归类错误：${response.error?.message}")
                    return@withContext null
                }
                // 核心修正：解析实际响应结构
                //遍历集合，找到第一个满足 lambda 条件的元素并返回；
                val result = response.output
                    .firstOrNull { it.type == "message" && it.role == "assistant" } // 找message类型的项
                    ?.content?.firstOrNull { it.type == "output_text" } // 找output_text类型的内容
                    ?.text?.trim() // 提取最终名称
                // 校验结果
                return@withContext if (result != null && folderNames.contains(result)) {
                    result
                } else {
                    folderNames.first() // 兜底：返回第一个收藏夹（避免空值）
                }
            } catch (e: Exception) {
                e.printStackTrace()
                return@withContext null
            }
        }

    suspend fun observeSongCountByFolderIdFlow(folderId: Int): Flow<Int> {
        return withContext(Dispatchers.IO){
            musicDao.getSongCountByFolderIdFlow(folderId)
        }
    }

   suspend fun getFolderIdByName(matchFolderName: String): Int {
        return withContext(Dispatchers.IO){
            musicDao.getFolderIdByName(matchFolderName)
        }
    }

}