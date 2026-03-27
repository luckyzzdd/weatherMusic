package com.example.weathermusic.utils

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import com.example.weathermusic.data.local.Music
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 本地音乐扫描工具类：仅负责扫描，不负责存储
 */
object MusicScanner {
    private val TAG: String = "Music_Scanner"

    /**
     * 扫描本地音乐
     * @param context 上下文
     * @return 扫描到的音乐列表（Music实体）
     */
    suspend fun scanLocalMusic(context: Context):List<Music> = withContext(Dispatchers.IO){
        //存储扫描结果的列表
        val musicList = mutableListOf<Music>()
        // 要查询的音乐字段（只查需要的，减少性能消耗）
        val projection = arrayOf(
            MediaStore.Audio.Media.DATA,// 文件路径（对应Music的path）
            MediaStore.Audio.Media.TITLE, // 歌曲名（对应name）
            MediaStore.Audio.Media.ARTIST, // 歌手名（对应singer）
            MediaStore.Audio.Media.DURATION, // 时长（毫秒，对应duration）
            MediaStore.Audio.Media.ALBUM, // 专辑名（对应album）
            MediaStore.Audio.Media.SIZE // 文件大小（对应size）
        )
        // 筛选条件：仅扫描时长>10秒的音频（过滤短音频/广告）
        val selection = "${MediaStore.Audio.Media.DURATION} > ? AND ${MediaStore.Audio.Media.IS_MUSIC} = ?"
        val selectionArgs = arrayOf("100000", "1") // 10秒=10000毫秒，IS_MUSIC=1表示是音乐文件

        // 排序：按歌曲名升序
        val sortOrder = "${MediaStore.Audio.Media.TITLE} ASC"

        //用ContentResolver查询媒体库
        var cursor: Cursor?= null
        try {
            //其实是向 Android 的媒体库（本质是数据库）发起了一个查询请求
            //媒体库会返回所有符合条件的音乐数据
            cursor =context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                sortOrder
            )

            cursor?.let {
                // 获取各字段的索引（避免重复调用getColumnIndex，提升性能）
                val pathIndex = it.getColumnIndex(MediaStore.Audio.Media.DATA)
                val nameIndex = it.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val singerIndex = it.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val durationIndex = it.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val albumIndex = it.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val sizeIndex = it.getColumnIndex(MediaStore.Audio.Media.SIZE)
                //一开始在0行，
                while (it.moveToNext()) {
                    val path = it.getString(pathIndex)?:continue // 路径为空则跳过
                    val name = it.getString(nameIndex) ?: "未知歌曲"
                    val singer = it.getString(singerIndex) ?: "未知歌手"
                    val duration = it.getLong(durationIndex) ?: 0L
                    val album = it.getString(albumIndex) ?: "未知专辑"
                    val size = it.getLong(sizeIndex) ?: 0L
                    // 构建Music实体，加入列表
                    val music = Music(
                        path = path,
                        name = name,
                        singer = singer,
                        duration = duration,
                        album = album,
                        size = size
                    )
                    musicList.add(music)
                }
                LogUtil.d("扫描完成，共找到${musicList.size}首音乐",TAG)

           }


        }catch (e: Exception){
            // 捕获异常，避免扫描失败导致App崩溃
            LogUtil.d("扫描本地音乐失败"+e.message,TAG)
        }finally {
            // 关闭游标，释放资源（新手必做，避免内存泄漏）
            cursor?.close()
        }
        return@withContext musicList
    }
    /**
     * 格式化时长（毫秒→分:秒，比如 180000→3:00）
     * 后续UI展示用，先封装
     *
     * 思路，先变成秒
     */
    fun formatDuration(duration: Long): String {
        val minutes = (duration / 1000) / 60
        val seconds = (duration / 1000) % 60
        return String.format("%d:%02d", minutes, seconds)
    }


}