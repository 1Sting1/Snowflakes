import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.View
import androidx.lifecycle.findViewTreeLifecycleOwner
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class SnowflakesView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val snowflakes = mutableListOf<Snowflake>()
    private var snowdrift: FloatArray? = null
    private var animationJob: Job? = null

    private val numSnowflakes = 400
    private val smoothingFactor = 128f
    private val horizontalWanderFactor = 30f
    private val snowSmoothingPasses = 4

    data class Snowflake(
        var x: Float, var y: Float, var radius: Float,
        var speed: Float, var color: Int, var phase: Float
    )

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        animationJob?.cancel()
        animationJob = findViewTreeLifecycleOwner()?.lifecycleScope?.launch {
            while (width == 0 || height == 0) {
                delay(100)
            }
            while (isActive) {
                updateState()
                invalidate()
                delay(16)
            }
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animationJob?.cancel()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0 && h > 0) {
            if (snowdrift == null) {
                snowdrift = FloatArray(w) { h.toFloat() }
                initializeSnowflakes(w, h)
            }
        }
    }

    private fun initializeSnowflakes(width: Int, height: Int) {
        snowflakes.clear()
        for (i in 0 until numSnowflakes) {
            snowflakes.add(createSnowflake(width, height, isInitial = true))
        }
    }

    private fun createSnowflake(width: Int, height: Int, isInitial: Boolean = false): Snowflake {
        val random = Random.Default
        val x = random.nextFloat() * width
        val y = if (isInitial) random.nextFloat() * height else -10f
        val radius = random.nextFloat() * 4f + 2f
        val speed = random.nextFloat() * 5f + 4f
        val greyTone = 200 + random.nextInt(56)
        val blueTint = random.nextInt(20)
        val color = Color.rgb(greyTone - blueTint, greyTone - blueTint, greyTone)
        val phase = random.nextFloat() * 2 * Math.PI.toFloat()
        return Snowflake(x, y, radius, speed, color, phase)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(10, 10, 40))

        snowflakes.forEach {
            paint.color = it.color
            canvas.drawCircle(it.x, it.y, it.radius, paint)
        }

        paint.color = Color.WHITE
        snowdrift?.let {
            for (i in it.indices) {
                canvas.drawRect(i.toFloat(), it[i], (i + 1).toFloat(), height.toFloat(), paint)
            }
        }
    }

    private fun updateState() {
        val h = height.toFloat()
        val w = width.toFloat()
        if (w == 0f || h == 0f) return

        snowflakes.forEach { snowflake ->
            val speedFactor = (1f - (snowflake.y / h)).coerceAtLeast(0.1f)
            snowflake.y += snowflake.speed * speedFactor
            val horizontalSpeedFactor = (snowflake.y / h)
            snowflake.x += sin(snowflake.y / 50 + snowflake.phase) * horizontalWanderFactor * horizontalSpeedFactor

            val snowdriftLevel = snowdrift?.getOrNull(snowflake.x.toInt()) ?: h
            if (snowflake.y + snowflake.radius > snowdriftLevel) {
                accumulateSnow(snowflake.x.toInt(), snowflake.radius)
                resetSnowflake(snowflake, width)
            }

            if (snowflake.y > h + snowflake.radius) {
                resetSnowflake(snowflake, width)
            }

            if (snowflake.x < -snowflake.radius) snowflake.x = w + snowflake.radius
            if (snowflake.x > w + snowflake.radius) snowflake.x = -snowflake.radius
        }

        for (i in 0 until snowSmoothingPasses) {
            smoothSnowdrift()
        }
    }

    private fun accumulateSnow(x: Int, radius: Float) {
        snowdrift?.let {
            val snowAccumulation = radius * 18f

            for (i in -radius.toInt()..radius.toInt()) {
                val index = x + i
                if (index >= 0 && index < it.size) {
                    val influence = cos(i / radius * (Math.PI / 2)).toFloat()
                    it[index] -= snowAccumulation * influence
                }
            }
        }
    }

    private fun smoothSnowdrift() {
        snowdrift?.let {
            for (i in 0 until it.size - 1) {
                val leftY = it[i]
                val rightY = it[i + 1]
                if (leftY < rightY - 1f) {
                    val diff = (rightY - leftY) / smoothingFactor
                    it[i] += diff
                    it[i + 1] -= diff
                }
            }
            for (i in (it.size - 1) downTo 1) {
                val leftY = it[i - 1]
                val rightY = it[i]
                if (rightY < leftY - 1f) {
                    val diff = (leftY - rightY) / smoothingFactor
                    it[i] += diff
                    it[i - 1] -= diff
                }
            }
        }
    }

    private fun resetSnowflake(snowflake: Snowflake, width: Int) {
        val random = Random.Default
        snowflake.y = -snowflake.radius
        snowflake.x = random.nextFloat() * width
    }
}