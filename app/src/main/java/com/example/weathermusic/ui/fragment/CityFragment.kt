package com.example.weathermusic.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.weathermusic.databinding.FragmentCityBinding
import com.example.weathermusic.ui.adapter.CityAdapter
import com.example.weathermusic.ui.main.MainActivity
import com.example.weathermusic.viewmodel.CityViewModel
import com.example.weathermusic.viewmodel.WeatherViewModel

class CityFragment : Fragment() {
    private lateinit var binding: FragmentCityBinding
    private lateinit var cityViewModel: CityViewModel
    private lateinit var weatherViewModel: WeatherViewModel
    private lateinit var cityAdapter: CityAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = FragmentCityBinding.inflate(inflater, container, false)
        //绑定fragment周期
        cityViewModel = ViewModelProvider(requireActivity())[CityViewModel::class.java]
        weatherViewModel = ViewModelProvider(requireActivity())[WeatherViewModel::class.java]
        //初始化RecyclerView
        initRecyclerView()
        initView()
        observeRecyclerView()

        return binding.root
    }

    private fun initView() {
        // 绑定添加按钮点击事件
        binding.btnAddCity.setOnClickListener {
            val cityName = binding.etCityInput.text.toString().trim()
            // 1. 简单校验（避免空输入/短输入）
            if (cityName.isBlank() || cityName.length < 2) {
                Toast.makeText(context, "请输入有效的城市/区县名", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            // 2. 调用ViewModel新增方法（自动查code+添加）
            cityViewModel.addCityByUserNameInput(cityName)
            // 3. 清空输入框
            binding.etCityInput.setText("")
        }
    }

    private fun initRecyclerView() {
        cityAdapter = CityAdapter()

        binding.rvCityList.apply {
            adapter = cityAdapter
            layoutManager = LinearLayoutManager(context)
//            addItemDecoration(DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL))
        }

        cityAdapter.onCityClick = { city ->
            //改变当前和其他被选中的选中状态
            cityViewModel.selectCity(city.copy(isSelected = true))
            //查询选中城市的天气
            //先做网络请求
//        然后在隐藏和显示页面
            // 2. 直接请求天气（核心：WeatherFragment已订阅，会自动更新UI）
            city.cityCode?.let{
                weatherViewModel.fetchRealtimeWeather(it, null)
                weatherViewModel.fetchForecastWeather(it)
            }
//        切换回WeatherFragment
            (activity as? MainActivity)?.apply {
                val weatherFragment = getFragmentList().firstOrNull()
                weatherFragment?.let {
                    showFragment(it)
                    updateBottomNavSelection(it)
                }
            }
        }

        cityAdapter.onCityDelete = {
            cityCode->
            cityViewModel.deleteCity(cityCode)
        }
    }


    private fun observeRecyclerView() {
        cityViewModel.allCities.observe(viewLifecycleOwner) { cities ->
            cityAdapter.submitList(cities)
        }
    }

}