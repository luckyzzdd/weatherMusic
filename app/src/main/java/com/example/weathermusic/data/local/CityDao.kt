package com.example.weathermusic.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface CityDao {
    /**
     * 添加城市
     */
    @Insert
    suspend fun insertCity(city: City): Long

    /**
     * 删除城市
     */
    @Query("delete from city where cityCode = :cityCode")
    suspend fun deleteCityByCode(cityCode: String)
    /**
     * 更新城市（比如修改选中状态）
     * @param city 城市对象
     */
    @Update
    suspend fun updateCity(city: City)

    /**
     * 获取所有城市（返回Flow，可观察数据变化）
     * 当数据库中的 city 表数据发生变化（插入 / 删除 / 更新）时，这个 Flow 会自动发送新的查询结果，无需你手动重新查询；
     * Flow 是冷流，只有在收集（collect）时才会执行查询，且能感知生命周期，避免内存泄漏。
     */
    @Query("SELECT * FROM city ORDER BY id DESC")
    fun getAllCities(): Flow<List<City>>

    /**
     * 根据编码查询城市
     * @param cityCode 城市编码
     */
    @Query("SELECT * FROM city WHERE cityCode = :cityCode LIMIT 1")
    suspend fun getCityByCode(cityCode: String): City?

    /**
     *SQLite 是一个弱类型数据库，它并没有专门的 BOOLEAN 类型
     * true 会被等价处理为整数 1
     * false 会被等价处理为整数 0a
     */
    @Query("SELECT * FROM city WHERE isSelected = 1 LIMIT 1")
    suspend fun getSelectedCity(): City?
}