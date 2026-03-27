package com.example.weathermusic.data.repository

import com.example.weathermusic.App
import com.example.weathermusic.data.local.City
import com.example.weathermusic.data.local.CityDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * 单例模式
 */
object CityRepository {
    //获取CityDao实例
    private val cityDao: CityDao = App.instance.appDatabase.cityDao()
    /**
     * 添加城市
     * 调用 suspend 函数的函数，必须也标记为 suspend
     */
    suspend fun addCity(city: City){
        withContext(Dispatchers.IO){
            cityDao.insertCity(city)
        }
    }
    /**
     * 删除城市（根据编码）
     * @param cityCode 城市编码
     *
     */
    suspend fun deleteCity(cityCode: String) {

            cityDao.deleteCityByCode(cityCode)

    }

    /**
     * 更新城市（比如修改选中状态）
     * @param city 城市对象
     */
    suspend fun updateCity(city: City) {
        withContext(Dispatchers.IO) {
            cityDao.updateCity(city)
        }
    }

    fun getAllCities(): Flow<List<City>>{
        // Flow本身就在后台线程执行，不用withContext
        return cityDao.getAllCities()
    }
    /**
     * 根据编码查询城市
     * @param cityCode 城市编码
     */
    suspend fun getCityByCode(cityCode: String): City? {
        return withContext(Dispatchers.IO) {
            cityDao.getCityByCode(cityCode)
        }
    }
    suspend fun getSelectedCity(): City?{
        return cityDao.getSelectedCity()
    }
    suspend fun addCityWithSelection(newCity: City) {
        withContext(Dispatchers.IO) {
            val cityCode = newCity.cityCode ?: return@withContext
            // 1. 检查是否已存在（避免重复添加）
            val existingCity = cityDao.getCityByCode(cityCode)
            if (existingCity == null) {
                // 不存在则插入
                cityDao.insertCity(newCity)
            }

            // 2. 获取当前已选中的城市
            val selectedCity = cityDao.getSelectedCity()
            // 3. 取消原有选中城市的状态（如果有且不是当前要添加的城市）
            if (selectedCity != null && selectedCity.cityCode != cityCode){
                cityDao.updateCity(selectedCity.copy(isSelected = false))
            }

            // 4. 将目标城市设为选中（兼容：不管是新增的还是已存在的，都确保选中）
            val targetCity = existingCity ?: newCity
            cityDao.updateCity(targetCity.copy(isSelected = true))
        }
    }
}