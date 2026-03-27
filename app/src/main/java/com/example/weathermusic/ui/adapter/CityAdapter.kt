package com.example.weathermusic.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.weathermusic.R
import com.example.weathermusic.data.local.City
import com.example.weathermusic.databinding.ItemCityBinding

/**
 * ListAdapter 内置 DiffUtil，能自动对比新旧列表数据，只刷新变化的项（而非全量刷新），性能更优；
 * Android ，普通 RecyclerView.Adapter 需手动写 DiffUtil，新手易出错。
 */

class CityAdapter : ListAdapter<City, CityAdapter.CityViewHolder>(CityDiffCallback()) {
    //点击事件回调
    var onCityClick: ((City) -> Unit)? = null

    // 删除事件回调
    var onCityDelete: ((String) -> Unit)? = null

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): CityViewHolder {
        var binding = ItemCityBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CityViewHolder(binding)
    }

    override fun onBindViewHolder(
        holder: CityViewHolder,
        position: Int
    ) {
        holder.bind(getItem(position), onCityClick, onCityDelete)
    }

    class CityViewHolder(private val binding: ItemCityBinding) :
        RecyclerView.ViewHolder(binding.root) {
        //下面写视图绑定数据的逻辑
        fun bind(city: City, onCityClick: ((City) -> Unit)?, onCityDelete: ((String) -> Unit)?) {
            // 绑定数据
            binding.tvCityName.text = city.cityName
            // 显示/隐藏选中标记
            binding.ivSelected.visibility = if (city.isSelected) {
                binding.ivSelected.setImageResource(R.drawable.today)
                View.VISIBLE
            } else {
                View.GONE
            }
//          点击城市
            binding.root.setOnClickListener { v ->
                //安全调用符 ?.，如果回调还是 null
                onCityClick?.invoke(city)
            }
            // 点击删除按钮
            binding.ivDelete.setOnClickListener {
                city.cityCode?.let { p1 -> onCityDelete?.invoke(p1) }
            }
        }
    }

}

//DiffUtil：高效比较列表数据，只刷新变化的项
class CityDiffCallback : DiffUtil.ItemCallback<City>() {
    /**
     * 判断是不是同一项
     */
    override fun areItemsTheSame(oldItem: City, newItem: City): Boolean {
        return oldItem.cityCode == newItem.cityCode
    }

    /**
     * 判断内容是不是一样
     */
    override fun areContentsTheSame(oldItem: City, newItem: City): Boolean {
        return oldItem == newItem
    }

}

