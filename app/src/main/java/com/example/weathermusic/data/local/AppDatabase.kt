package com.example.weathermusic.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
//提示

@Database(entities = [City::class,Music::class, MusicFolder::class], version = 1)
abstract class AppDatabase : RoomDatabase() {
    // 提供CityDao的实例（Room自动生成实现类）
    abstract fun cityDao(): CityDao

    abstract fun musicDao(): MusicDao
}