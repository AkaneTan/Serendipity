package org.akanework.gramophone.logic.ui.spans

import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import org.akanework.gramophone.logic.utils.CalculationUtils.lerp
import org.akanework.gramophone.logic.utils.CalculationUtils.lerpInv
import kotlin.math.max
import kotlin.math.min

// Hacks, hacks, hacks...
class MyGradientSpan(val grdWidth: Float, color: Int, highlightColor: Int) : CharacterStyle(), UpdateAppearance {
	private val matrix = Matrix()
	private var shader = LinearGradient(
		0f, 50f, grdWidth, 50f,
		highlightColor, color,
		Shader.TileMode.CLAMP
	)
	var progress = 1f
	lateinit var lineOffsets: List<Int>
	var lineCountDivider = 0
	var totalCharsForProgress = 0
	var lineCount = 0
	override fun updateDrawState(tp: TextPaint) {
		tp.color = Color.WHITE
		val o = 5 * ((lineCount / lineCountDivider) % (lineOffsets.size / 5))
		val ourProgress = max(0f, min(1f, lerpInv(lineOffsets[o + 3].toFloat(), lineOffsets[o + 4]
			.toFloat(), lerp(0f, totalCharsForProgress.toFloat(), progress))))
		shader.setLocalMatrix(matrix.apply {
			reset()
			postTranslate(lineOffsets[o].toFloat() + ((lineOffsets[o + 2]
					+ (grdWidth * 3)) * ourProgress) - (grdWidth * 2), 0f)
			postScale(1f, lineOffsets[o + 1] / 100f)
		})
		tp.shader = shader
		lineCount++
	}
}