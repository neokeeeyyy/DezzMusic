package com.dezzmusic.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

class CoverFlowView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var items: List<CoverFlowItem> = emptyList()
    private var currentIndex = 0
    private var scrollX = 0f
    private var lastTouchX = 0f
    private var isDragging = false

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val shadowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val reflectionPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val itemWidth = 200f
    private val itemHeight = 200f
    private val itemSpacing = 40f
    private val maxRotation = 45f
    private val maxScale = 1.2f
    private val minScale = 0.6f
    private val reflectionHeight = 60f

    private var onItemSelectedListener: ((Int) -> Unit)? = null

    init {
        shadowPaint.color = Color.argb(80, 0, 0, 0)
        shadowPaint.maskFilter = BlurMaskFilter(20f, BlurMaskFilter.Blur.NORMAL)

        reflectionPaint.colorFilter = ColorMatrixColorFilter(ColorMatrix().apply {
            setScale(0.8f, 0.8f, 0.8f, 1f)
        })
    }

    fun setItems(items: List<CoverFlowItem>) {
        this.items = items
        invalidate()
    }

    fun setOnItemSelectedListener(listener: (Int) -> Unit) {
        onItemSelectedListener = listener
    }

    fun setSelectedIndex(index: Int) {
        val animator = ValueAnimator.ofFloat(currentIndex.toFloat(), index.toFloat())
        animator.duration = 300
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener {
            currentIndex = it.animatedValue as Float
            invalidate()
        }
        animator.start()
        currentIndex = index.toFloat()
        onItemSelectedListener?.invoke(index)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (items.isEmpty()) return

        val centerX = width / 2f
        val centerY = height / 2f - reflectionHeight / 2

        for (i in items.indices) {
            val distance = i - currentIndex
            val absDistance = abs(distance)

            if (absDistance > 3) continue

            val scale = minScale + (maxScale - minScale) * (1 - absDistance / 3f).coerceIn(0f, 1f)
            val rotation = (distance * maxRotation / 3f).coerceIn(-maxRotation, maxRotation)
            val offsetX = distance * (itemWidth * scale + itemSpacing)

            val itemX = centerX + offsetX
            val itemY = centerY

            canvas.save()
            canvas.translate(itemX, itemY)
            canvas.rotate(rotation, 0f, 0f)
            canvas.scale(scale, scale, 0f, 0f)

            // Draw shadow
            canvas.save()
            canvas.translate(10f, 10f)
            canvas.drawRect(
                -itemWidth / 2,
                -itemHeight / 2,
                itemWidth / 2,
                itemHeight / 2,
                shadowPaint
            )
            canvas.restore()

            // Draw item
            val item = items[i]
            val rect = RectF(
                -itemWidth / 2,
                -itemHeight / 2,
                itemWidth / 2,
                itemHeight / 2
            )

            // Draw cover
            item.cover?.let { bitmap ->
                val src = Rect(0, 0, bitmap.width, bitmap.height)
                canvas.drawBitmap(bitmap, src, rect, paint)
            } ?: run {
                paint.color = Color.DKGRAY
                canvas.drawRoundRect(rect, 12f, 12f, paint)
                paint.color = Color.WHITE
                paint.textSize = 40f
                paint.textAlign = Paint.Align.CENTER
                canvas.drawText("♪", 0f, 15f, paint)
            }

            // Draw reflection
            canvas.save()
            canvas.translate(0f, itemHeight / 2 + 5f)
            canvas.scale(1f, -0.4f)

            item.cover?.let { bitmap ->
                val src = Rect(0, 0, bitmap.width, bitmap.height)
                canvas.drawBitmap(bitmap, src, rect, reflectionPaint)
            }

            canvas.restore()

            // Draw title
            paint.color = Color.WHITE
            paint.textSize = 16f
            paint.textAlign = Paint.Align.CENTER
            canvas.drawText(
                item.title.take(15),
                0f,
                itemHeight / 2 + reflectionHeight + 30f,
                paint
            )

            canvas.restore()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchX = event.x
                isDragging = true
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    val deltaX = event.x - lastTouchX
                    val itemsPerScreen = width / (itemWidth + itemSpacing)
                    currentIndex -= deltaX / (itemWidth + itemSpacing) * 0.5f
                    currentIndex = currentIndex.coerceIn(0f, (items.size - 1).toFloat())
                    lastTouchX = event.x
                    invalidate()
                }
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                isDragging = false
                snapToNearest()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun snapToNearest() {
        val nearest = Math.round(currentIndex).toFloat()
        val animator = ValueAnimator.ofFloat(currentIndex, nearest)
        animator.duration = 200
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener {
            currentIndex = it.animatedValue as Float
            invalidate()
        }
        animator.start()
        currentIndex = nearest
        onItemSelectedListener?.invoke(nearest.toInt())
    }

    data class CoverFlowItem(
        val title: String,
        val artist: String,
        val cover: Bitmap?
    )
}
