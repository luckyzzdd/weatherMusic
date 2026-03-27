package com.example.weathermusic.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.weathermusic.data.local.City
import com.example.weathermusic.data.repository.CityRepository
import com.example.weathermusic.data.repository.WeatherRepository
import kotlinx.coroutines.launch

class CityViewModel : ViewModel() {
    //model数据
    //获取所有城市（Flow转LiveData，UI层观察LiveData）
    val allCities = CityRepository.getAllCities().asLiveData()

    private val _cityText= MutableLiveData<String?>()

    private val cityText: LiveData<String?> = _cityText





    //添加城市
    fun addCity(cityName: String, cityCode: String) {
        viewModelScope.launch {
            val existing = CityRepository.getCityByCode(cityCode)
            if (existing == null) {
                val city = City(cityName = cityName, cityCode = cityCode)
                CityRepository.addCity(city)
            }
        }
    }

    /**
     * 删除城市
     * @param cityCode 城市编码
     */
    fun deleteCity(cityCode: String) {
        viewModelScope.launch {
            CityRepository.deleteCity(cityCode)
        }
    }

    /**
     * 选中城市（修改选中状态）
     * @param city 城市对象
     */
    fun selectCity(city: City) {
        viewModelScope.launch {
            //拿到已经选中的城市，然后取消他的状态
            val selectedCity = CityRepository.getSelectedCity()

            // 2. 仅取消这个已选中城市的状态（无则跳过）
            if (selectedCity != null && selectedCity.cityCode != city.cityCode) {
                CityRepository.updateCity(
                    selectedCity.copy(
                        isSelected = false,
                        id = selectedCity.id
                    )
                )
            }
            // 3. 选中目标城市
            CityRepository.updateCity(city.copy(isSelected = true, id = city.id))
        }
    }
    // 新增：通过城市名自动查code并添加（核心适配你的需求）
    fun addCityByUserNameInput(cityName: String){
        viewModelScope.launch {
            //调用api
            val adcode = WeatherRepository.getAdcodeByCityName(cityName)
            //添加到列表里
             adcode?.let {
                 code->
                 addCity(cityName,code)
             }
        }
    }
}