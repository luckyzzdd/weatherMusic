package com.example.weathermusic.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity("music_folder")
data class MusicFolder (
    // 自增主键
    @PrimaryKey(autoGenerate = true) var folderId: Int = 0,
    // 收藏夹名称（晴天/阴天/雨天）
    var folderName: String,
    // 收藏夹标识（用于快速匹配天气，比如 SUNNY/Cloudy/RAINY）
    var folderTag: String
)