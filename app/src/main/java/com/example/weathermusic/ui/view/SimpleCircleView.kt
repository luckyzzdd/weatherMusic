package com.example.weathermusic.ui.view

import android.content.Context
import android.graphics.*
import android.graphics.drawable.BitmapDrawable
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.example.weathermusic.R
import com.example.weathermusic.utils.MusicPlayerManager

class SimpleCircleView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {


    // 播放模式
    enum class Mode {
        LIST_LOOP,   // 列表循环
        SINGLE_LOOP, // 单曲循环
        RANDOM       // 随机播放
    }
    // 当前模式
    private var currentMode = Mode.LIST_LOOP

    // 画笔
    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    // 图标资源
    private val listLoopIcon: Bitmap
    private val singleLoopIcon: Bitmap
    private val randomIcon: Bitmap



    // ====================== 固定尺寸（你直接改这两个数） ======================
    private val CIRCLE_DP = 50   // 圆形大小 180dp
    private val ICON_DP = 30     // 图标大小 100dp
    var onModeChanged: ((Mode) -> Unit)? = null
    init {
        // 初始化你的图标
        listLoopIcon = getBitmap(R.drawable.ic_loop_list)
        singleLoopIcon = getBitmap(R.drawable.ic_loop_single)
        randomIcon = getBitmap(R.drawable.ic_shuffle)

        // 白色圆形背景
        bgPaint.color = Color.WHITE
        bgPaint.style = Paint.Style.FILL
    }

    // 读取图片
    private fun getBitmap(resId: Int): Bitmap {
        return (resources.getDrawable(resId, null) as BitmapDrawable).bitmap
    }

    // dp转px（核心：安卓必须转像素才能用）
    private fun dp2px(dpValue: Int): Int {
        val scale = resources.displayMetrics.density
        return (dpValue * scale + 0.5f).toInt()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val centerX = width / 2f
        val centerY = height / 2f
        val radius = dp2px(CIRCLE_DP) / 2f

        // 1. 画白色正圆
        canvas.drawCircle(centerX, centerY, radius, bgPaint)

        // 2. 获取当前图标
        val icon = when (currentMode) {
            Mode.LIST_LOOP -> listLoopIcon
            Mode.SINGLE_LOOP -> singleLoopIcon
            Mode.RANDOM -> randomIcon
        }

        // 3. 图标强制缩放到 100dp，居中绘制
        val iconSize = dp2px(ICON_DP)
        val left = centerX - iconSize / 2f
        val top = centerY - iconSize / 2f

        canvas.drawBitmap(
            icon,
            null,
            RectF(left, top, left + iconSize, top + iconSize),
            iconPaint
        )
    }

    // 点击切换模式
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_UP) {
            currentMode = when (currentMode) {
                Mode.LIST_LOOP -> Mode.RANDOM
                Mode.RANDOM -> Mode.SINGLE_LOOP
                Mode.SINGLE_LOOP -> Mode.LIST_LOOP
            }
            onModeChanged?.invoke(currentMode)
            invalidate()

        }
        return true
    }

    // ====================== 修复：控件固定180dp ======================
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val size = dp2px(CIRCLE_DP)
        setMeasuredDimension(size, size)
    }

    // 回收图片
    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
    }
}