package com.example.weathermusic.viewmodel

import android.widget.Toast
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.weathermusic.App
import com.example.weathermusic.R
import com.example.weathermusic.data.local.City
import com.example.weathermusic.data.local.WeatherDataStore
import com.example.weathermusic.data.remote.Casts
import com.example.weathermusic.data.remote.RealtimeWeather
import com.example.weathermusic.data.repository.CityRepository
import com.example.weathermusic.data.repository.WeatherRepository
import com.example.weathermusic.ui.main.LocationState
import com.example.weathermusic.utils.LogUtil
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class WeatherViewModel : ViewModel() {
    private var _realtimeWeather = MutableLiveData<RealtimeWeather?>()
    var realtimeWeather: LiveData<RealtimeWeather?> = _realtimeWeather

    private var _forecastList = MutableLiveData<List<Casts>?>()
    var forecastList: LiveData<List<Casts>?> = _forecastList

    private var _isLoading = MutableLiveData<Boolean>(false)
    val isLoading: LiveData<Boolean> = _isLoading

    //监控状态，状态变化，吐司提示
    private val _locationState = MutableLiveData<LocationState>()
    val locationState: LiveData<LocationState> = _locationState
    private val TAG = "Weather_View_Model"

    private val _slideIconRes = MutableLiveData<Int>(R.drawable.weather)
    val slideIconRes: LiveData<Int> = _slideIconRes

    // 新增：更新图标数据的方法（供Fragment调用）
    fun updateSlideIcon(newIconRes: Int) {
        _slideIconRes.postValue(newIconRes) // 响应式更新
    }
    // 1. 单独获取实时天气（用市级别adcode）
    fun fetchRealtimeWeather(cityCode: String, city: String?) {
        _isLoading.value = true
        viewModelScope.launch {
            // 1. 先试网络请求
            val weatherData = WeatherRepository.getRealtimeWeather(cityCode)
            if (weatherData != null) {
                city?.let { weatherData.city = it + weatherData.city }
                _realtimeWeather.value = weatherData
            } else {
                // 2. 网络失败：读缓存
                val cachedWeather = WeatherDataStore.getRealtimeWeather(App.instance)
                _realtimeWeather.value = cachedWeather
                Toast.makeText(App.instance, "网络失败，使用缓存天气", Toast.LENGTH_SHORT).show()
            }
            _isLoading.value = false
        }
    }

    // 2. 单独获取预报天气（区/市级别adcode都可以）
    fun fetchForecastWeather(cityCode: String) {
        _isLoading.value = true
        viewModelScope.launch {
            val forecastData = WeatherRepository.getForecastWeather(cityCode)
            if (forecastData!=null){
                _forecastList.value = forecastData

            }else{
                val cacheForecastsWeather = WeatherDataStore.getForecastWeather(App.instance)
                _forecastList.value = cacheForecastsWeather
            }
            _isLoading.value = false
        }
    }

    // 定位成功后：同时调用实时（市编码）+ 预报（区编码）
    fun handleLocationSuccess(latitude: String, longitude: String) {
        LogUtil.d("定位成gong",TAG)
        _locationState.postValue(LocationState.Success(latitude, longitude))
        viewModelScope.launch {
            _isLoading.postValue(true)
            val cityInfo = WeatherRepository.getCityInfoByLatLng(latitude, longitude)
            cityInfo?.let { address ->
                // 区编码（比如410105）→ 拿预报
                address.adcode?.let {
                    fetchForecastWeather(it)
                    fetchRealtimeWeather(it, address.city)
                    //定位成功，发送天气请求
                    LogUtil.d("定位成功，发送天气请求:${latitude},${longitude},,,${it}",TAG)
                    val realtimeWeatherValue = waitForRealtimeWeather()
                    //把当前的城市加入列表
                    var cityName = realtimeWeatherValue?.city ?: "未知城市"
                    var adcode = realtimeWeatherValue?.adcode ?: "未知"
                    LogUtil.d("${cityName},${adcode}", TAG)
                    var city = City(cityName = cityName, cityCode = adcode, isSelected = true)
                    CityRepository.addCityWithSelection(city)
                }

            } ?: run {
                // 2. 网络失败：读缓存
                val cachedWeather = WeatherDataStore.getRealtimeWeather(App.instance)
                _realtimeWeather.value = cachedWeather
                Toast.makeText(App.instance, "网络失败，使用缓存天气", Toast.LENGTH_SHORT).show()
                val cacheForecastsWeather = WeatherDataStore.getForecastWeather(App.instance)
                _forecastList.value = cacheForecastsWeather
            }
            _isLoading.postValue(false)
        }
    }

    private suspend fun waitForRealtimeWeather(): RealtimeWeather? {
        var weatherData: RealtimeWeather? = null
        // 循环检查，直到拿到值或超时（避免无限等）
        repeat(10) { // 最多等5秒（每次delay 500ms，10次=5秒）
            weatherData = realtimeWeather.value
            if (weatherData != null) {
                return weatherData // 拿到值就返回
            }
            delay(500) // 每500ms检查一次
        }
        return null // 超时返回null
    }


    // 其他方法（handleLocationError/handlePermissionDenied等）保留不变
    fun handleLocationError(message: String) {
        _locationState.postValue(LocationState.Error(message))
    }

    fun handlePermissionDenied() {
        _locationState.postValue(LocationState.PermissionDenied)
    }

    fun markLocationLoading() {
        _locationState.postValue(LocationState.Loading)
    }
}