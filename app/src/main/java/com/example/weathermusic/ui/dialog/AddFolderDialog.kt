package com.example.weathermusic.ui.dialog

import android.content.res.ColorStateList
import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.lifecycleScope
import com.example.weathermusic.R
import com.example.weathermusic.constant.WeatherConstants
import com.example.weathermusic.data.repository.MusicRepository
import com.example.weathermusic.databinding.DialogAddFolderBinding
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.shape.ShapeAppearanceModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddFolderDialog : DialogFragment() {
    private lateinit var binding: DialogAddFolderBinding
    // 存储“天气名称 - tag”的映射（用于创建收藏夹）
    private val weatherTagMap = mutableMapOf<String, String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        binding = DialogAddFolderBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // 1. 加载可选的天气CheckBox（过滤已存在的收藏夹）
        loadAvailableWeatherCheckBox()
        // 2. 绑定按钮点击事件
        bindButtonClick()
    }

    // 绑定确认/取消按钮点击事件
    private fun bindButtonClick() {
        // 取消按钮：关闭弹窗
        binding.btnCancel.setOnClickListener {
            dismiss()
        }

        // 确认按钮：批量创建选中的收藏夹
        binding.btnConfirm.setOnClickListener {
            CoroutineScope(Dispatchers.IO).launch {
                var successCount = 0
                var failCount = 0

                // 遍历所有CheckBox，收集选中的天气名称
                for (i in 0 until  binding.llCheckboxGroup.childCount) {
                    val child = binding.llCheckboxGroup.getChildAt(i)
                    if (child is CheckBox && child.isChecked) {
                        val weatherName = child.text.toString()
                        val weatherTag = weatherTagMap[weatherName] ?: continue

                        // 调用Repository创建收藏夹（自动校验重复）
                        val isSuccess = MusicRepository.addFolder(weatherName, weatherTag)
                        if (isSuccess) {
                            successCount++
                        } else {
                            failCount++
                        }
                    }
                }

                // 切回主线程提示结果 + 关闭弹窗
                withContext(Dispatchers.Main) {
                    val toastMsg = when {
                        successCount > 0 && failCount == 0 -> "成功添加$successCount 个收藏夹"
                        successCount > 0 && failCount > 0 -> "成功添加$successCount 个，失败$failCount 个（名称重复）"
                        else -> "未选中任何收藏夹"
                    }
                    Toast.makeText(requireContext(), toastMsg, Toast.LENGTH_SHORT).show()
                    dismiss() // 关闭弹窗
                }
            }
        }
    }


    // 核心：加载可选的天气CheckBox（过滤已存在的收藏夹）
    private fun loadAvailableWeatherCheckBox() {
        lifecycleScope.launch(Dispatchers.IO) {
            // 第一步：查已存在的收藏夹名称
            val existingFolderNames = MusicRepository.getAllFolderNames()
            // 第二步：过滤预定义列表，只保留未创建的天气
            //ALL_WEATHER_NAMES这是一个包含 “键值对” 的集合
            //filter { ... }，，作用是遍历集合中的每个元素，只保留大括号内条件为 true 的元素

            val availableWeathers = WeatherConstants.ALL_WEATHER_NAMES.filter {
                !existingFolderNames.contains(it.first)
            }

            // 第三步：动态生成CheckBox（切回主线程操作UI）
            withContext(Dispatchers.Main) {
                availableWeathers.forEach { (weatherName, weatherTag) ->
                    // 存储名称和tag的映射（后续创建收藏夹用）
                    weatherTagMap[weatherName] = weatherTag
                    // 创建CheckBox
                    val checkBox = MaterialCheckBox(requireContext()).apply {
                        text = weatherName
                        textSize = 16f
                        buttonTintList =  ColorStateList.valueOf(requireContext().resources.getColor(R.color.blue_soft,null))

                        layoutParams = LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.MATCH_PARENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT
                        )
                    }

                    // 添加到CheckBox组，，往布局组件里加控件
                    binding.llCheckboxGroup.addView(checkBox)
                }

                // 无可选天气时的提示
                if (availableWeathers.isEmpty()) {
                    binding.llCheckboxGroup.addView(
                        androidx.appcompat.widget.AppCompatTextView(requireContext()).apply {
                            text = "已添加所有天气收藏夹，暂无可选"
                            textSize = 16f
                            layoutParams = LinearLayout.LayoutParams(
                                LinearLayout.LayoutParams.MATCH_PARENT,
                                LinearLayout.LayoutParams.WRAP_CONTENT
                            )
                        }
                    )
                    // 禁用确认按钮
                    binding.btnConfirm.isEnabled = false
                }
            }
        }
    }




}