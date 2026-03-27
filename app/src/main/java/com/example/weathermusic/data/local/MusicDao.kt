package com.example.weathermusic.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.OnConflictStrategy.Companion.REPLACE
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {
    // ========== 原有Music表基础操作（保留） ==========
    @Insert(onConflict = REPLACE)
    suspend fun insertMusic(music: Music)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertMusics(musics: List<Music>)

    @Query("SELECT * FROM music")
    fun getAllMusics(): Flow<List<Music>>

    // ========== 新增：多对一核心方法 ==========
    // 更新歌曲的收藏夹归属（核心：多对一的添加逻辑）
    @Query("UPDATE music SET folderId = :targetFolderId WHERE path = :musicPath")
    suspend fun updateMusicFolderId(musicPath: String, targetFolderId: Int?)

    // 查询未归属任何收藏夹的歌曲（仅这类歌能被添加）
    @Query("SELECT * FROM music WHERE folderId IS NULL")
    suspend fun getUnassignedMusics(): List<Music>

    // 新增：直接返回列表（非Flow），避免first()的坑
    @Query("SELECT * FROM music WHERE folderId = :folderId")
    suspend fun getMusicsByFolderIdDirectly(folderId: Int): List<Music>


    // ========== MusicFolder表操作（保留，无修改） ==========
    @Insert
    suspend fun insertFolder(folder: MusicFolder)

    @Query("SELECT * FROM music_folder")
    fun observeAllFolders(): Flow<List<MusicFolder>>

    @Query("SELECT * FROM music_folder WHERE folderTag = :tag LIMIT 1")
    suspend fun getFolderByTag(tag: String): MusicFolder?

    @Query("SELECT * FROM music_folder WHERE folderName = :name LIMIT 1")
    suspend fun getFolderByName(name: String): MusicFolder?

    @Query("SELECT folderName FROM music_folder")
    suspend fun getAllFolderNames(): List<String>

    @Query("select folderId from music_folder where folderName= :name")
    //通过名字查id
    suspend fun agetFolderIdByName(name: String): Int
    @Query("SELECT COUNT(*) FROM music WHERE folderId = :folderId")
    suspend fun getSongCountByFolderId(folderId: Int): Int

    /**
     * 返回 Flow 的方法不能加 suspend 修饰符（Flow 本身就是异步的，无需挂起）
     */
    @Query("SELECT COUNT(*) FROM music WHERE folderId = :folderId")
    fun getSongCountByFolderIdFlow(folderId: Int): Flow<Int>
    @Query("select folderId from music_folder where folderName= :matchFolderName")
    fun getFolderIdByName(matchFolderName: String): Int


}