package org.akanework.gramophone.ui.components

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextDirectionHeuristics
import android.text.TextPaint
import android.util.AttributeSet
import android.view.View
import android.view.animation.PathInterpolator
import androidx.core.graphics.ColorUtils
import androidx.core.graphics.TypefaceCompat
import androidx.core.text.getSpans
import androidx.core.widget.NestedScrollView
import androidx.preference.PreferenceManager
import org.akanework.gramophone.R
import org.akanework.gramophone.logic.dpToPx
import org.akanework.gramophone.logic.getBooleanStrict
import org.akanework.gramophone.logic.ui.spans.MyForegroundColorSpan
import org.akanework.gramophone.logic.ui.spans.MyGradientSpan
import org.akanework.gramophone.logic.ui.spans.StaticLayoutBuilderCompat
import org.akanework.gramophone.logic.utils.CalculationUtils.lerp
import org.akanework.gramophone.logic.utils.CalculationUtils.lerpInv
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.logic.utils.SpeakerEntity
import org.akanework.gramophone.ui.MainActivity
import kotlin.math.max
import kotlin.math.min

// TODO react to clicks
class NewLyricsView(context: Context, attrs: AttributeSet) : View(context, attrs) {

	private val smallSizeFactor = 0.97f
	// TODO maybe reduce this to avoid really fast word skipping
	private val scaleInAnimTime = 650f / 2
	private val scaleColorInterpolator = PathInterpolator(0.4f, 0.2f, 0f, 1f)
	private val prefs = PreferenceManager.getDefaultSharedPreferences(context)
	private lateinit var typeface: Typeface
	private val grdWidth = context.resources.getDimension(R.dimen.lyric_gradient_size)
	private val defaultTextSize = context.resources.getDimension(R.dimen.lyric_text_size)
	private val translationTextSize = context.resources.getDimension(R.dimen.lyric_tl_text_size)
	private val translationBackgroundTextSize = context.resources.getDimension(R.dimen.lyric_tl_bg_text_size)
	private var colorSpanPool = mutableListOf<MyForegroundColorSpan>()
	private val bounds = Rect()
	private var spForRender: List<SbItem>? = null
	private var spForMeasure: Pair<Pair<Int, Int>, List<SbItem>>? = null
	private var lyrics: SemanticLyrics? = null
	private var posForRender = 0uL
	private val activity
		get() = context as MainActivity
	private val instance
		get() = activity.getPlayer()
	private val scrollView // TODO autoscroll
		get() = parent as NestedScrollView
	// -/M/F/D insanity starts here
	private var defaultTextColor = 0
	private var highlightTextColor = 0
	private var defaultTextColorM = 0
	private var highlightTextColorM = 0
	private var defaultTextColorF = 0
	private var highlightTextColorF = 0
	private var defaultTextColorD = 0
	private var highlightTextColorD = 0
	private val defaultTextPaint = TextPaint().apply { color = Color.RED
		textSize = defaultTextSize }
	private val translationTextPaint = TextPaint().apply { color = Color.GREEN
		textSize = translationTextSize }
	private val translationBackgroundTextPaint = TextPaint().apply { color = Color.BLUE
		textSize = translationBackgroundTextSize }
	private val defaultTextPaintM = lazy { TextPaint().apply { color = defaultTextColorM
		textSize = defaultTextSize; typeface = this@NewLyricsView.typeface } }
	private val translationTextPaintM = lazy { TextPaint().apply { color = defaultTextColorM
		textSize = translationTextSize; typeface = this@NewLyricsView.typeface } }
	private val defaultTextPaintF = lazy { TextPaint().apply { color = defaultTextColorF
		textSize = defaultTextSize; typeface = this@NewLyricsView.typeface } }
	private val translationTextPaintF = lazy { TextPaint().apply { color = defaultTextColorF
		textSize = translationTextSize; typeface = this@NewLyricsView.typeface } }
	private val defaultTextPaintD = lazy { TextPaint().apply { color = defaultTextColorD
		textSize = defaultTextSize; typeface = this@NewLyricsView.typeface } }
	private val translationTextPaintD = lazy { TextPaint().apply { color = defaultTextColorD
		textSize = translationTextSize; typeface = this@NewLyricsView.typeface } }
	private var wordActiveSpan = MyForegroundColorSpan(Color.CYAN)
	private var wordActiveSpanM = lazy { MyForegroundColorSpan(highlightTextColorM) }
	private var wordActiveSpanF = lazy { MyForegroundColorSpan(highlightTextColorF) }
	private var wordActiveSpanD = lazy { MyForegroundColorSpan(highlightTextColorD) }
	private var gradientSpanPool = mutableListOf<MyGradientSpan>()
	private var gradientSpanPoolM = lazy { mutableListOf<MyGradientSpan>() }
	private var gradientSpanPoolF = lazy { mutableListOf<MyGradientSpan>() }
	private var gradientSpanPoolD = lazy { mutableListOf<MyGradientSpan>() }
	private fun makeGradientSpan() = MyGradientSpan(grdWidth, defaultTextColor, highlightTextColor)
	private fun makeGradientSpanM() = MyGradientSpan(grdWidth, defaultTextColorM, highlightTextColorM)
	private fun makeGradientSpanF() = MyGradientSpan(grdWidth, defaultTextColorF, highlightTextColorF)
	private fun makeGradientSpanD() = MyGradientSpan(grdWidth, defaultTextColorD, highlightTextColorD)

	init {
		applyTypefaces()
	}

	fun updateTextColor(newColor: Int, newHighlightColor: Int, newColorM: Int,
	                    newHighlightColorM: Int, newColorF: Int, newHighlightColorF: Int,
	                    newColorD: Int, newHighlightColorD: Int) {
		var changed = false
		var changedM = false
		var changedF = false
		var changedD = false
		if (defaultTextColor != newColor) {
			defaultTextColor = newColor
			defaultTextPaint.color = defaultTextColor
			translationTextPaint.color = defaultTextColor
			translationBackgroundTextPaint.color = defaultTextColor
			changed = true
		}
		if (defaultTextColorM != newColorM) {
			defaultTextColorM = newColorM
			if (defaultTextPaintM.isInitialized()) {
				defaultTextPaintM.value.color = defaultTextColorM
				changedM = true
			}
			if (translationTextPaintM.isInitialized()) {
				translationTextPaintM.value.color = defaultTextColorM
				changedM = true
			}
		}
		if (defaultTextColorF != newColorF) {
			defaultTextColorF = newColorF
			if (defaultTextPaintF.isInitialized()) {
				defaultTextPaintF.value.color = defaultTextColorF
				changedF = true
			}
			if (translationTextPaintF.isInitialized()) {
				translationTextPaintF.value.color = defaultTextColorF
				changedF = true
			}
		}
		if (defaultTextColorD != newColorD) {
			defaultTextColorD = newColorD
			if (defaultTextPaintD.isInitialized()) {
				defaultTextPaintD.value.color = defaultTextColorD
				changedD = true
			}
			if (translationTextPaintD.isInitialized()) {
				translationTextPaintD.value.color = defaultTextColorD
				changedD = true
			}
		}
		if (highlightTextColor != newHighlightColor) {
			highlightTextColor = newHighlightColor
			wordActiveSpan.color = highlightTextColor
			changed = true
		}
		if (highlightTextColorM != newHighlightColorM) {
			highlightTextColorM = newHighlightColorM
			if (wordActiveSpanM.isInitialized()) {
				wordActiveSpanM.value.color = highlightTextColorM
				changedM = true
			}
		}
		if (highlightTextColorF != newHighlightColorF) {
			highlightTextColorF = newHighlightColorF
			if (wordActiveSpanF.isInitialized()) {
				wordActiveSpanF.value.color = highlightTextColorF
				changedF = true
			}
		}
		if (highlightTextColorD != newHighlightColorD) {
			highlightTextColorD = newHighlightColorD
			if (wordActiveSpanD.isInitialized()) {
				wordActiveSpanD.value.color = highlightTextColorD
				changedD = true
			}
		}
		if (changed) {
			gradientSpanPool.clear()
			(1..3).forEach { gradientSpanPool.add(makeGradientSpan()) }
		}
		if (changedM && gradientSpanPoolM.isInitialized()) {
			gradientSpanPoolM.value.clear()
			(1..3).forEach { gradientSpanPoolM.value.add(makeGradientSpanM()) }
		}
		if (changedF && gradientSpanPoolF.isInitialized()) {
			gradientSpanPoolF.value.clear()
			(1..3).forEach { gradientSpanPoolF.value.add(makeGradientSpanF()) }
		}
		if (changedD && gradientSpanPoolD.isInitialized()) {
			gradientSpanPoolD.value.clear()
			(1..3).forEach { gradientSpanPoolD.value.add(makeGradientSpanD()) }
		}
		if (changed || changedM || changedF || changedD) {
			spForRender?.forEach { it.text.getSpans<MyGradientSpan>()
				.forEach { s -> it.text.removeSpan(s) }}
			invalidate()
		}
	}

	fun updateLyrics(parsedLyrics: SemanticLyrics?) {
		spForRender = null
		spForMeasure = null
		requestLayout()
		lyrics = parsedLyrics
	}

	fun updateLyricPositionFromPlaybackPos() {
		if (instance?.currentPosition != posForRender.toLong())
			invalidate()
	}

	fun onPrefsChanged() {
		applyTypefaces()
		spForRender = null
		spForMeasure = null
		requestLayout()
	}

	private fun applyTypefaces() {
		typeface = if (prefs.getBooleanStrict("lyric_bold", false)) {
			TypefaceCompat.create(context, null, 700, false)
		} else {
			TypefaceCompat.create(context, null, 500, false)
		}
		defaultTextPaint.typeface = typeface
		translationTextPaint.typeface = typeface
		translationBackgroundTextPaint.typeface = typeface
		if (defaultTextPaintM.isInitialized())
			defaultTextPaintM.value.typeface = typeface
		if (translationTextPaintM.isInitialized())
			translationTextPaintM.value.typeface = typeface
		if (defaultTextPaintF.isInitialized())
			defaultTextPaintF.value.typeface = typeface
		if (translationTextPaintF.isInitialized())
			translationTextPaintF.value.typeface = typeface
		if (defaultTextPaintD.isInitialized())
			defaultTextPaintD.value.typeface = typeface
		if (translationTextPaintD.isInitialized())
			translationTextPaintD.value.typeface = typeface
	}

	override fun onDraw(canvas: Canvas) {
		posForRender = instance?.currentPosition?.toULong() ?: 0uL // TODO do some mp3s go backwards sometimes lol??
		if (spForRender == null) {
			requestLayout()
			return
		}
		var animating = false
		canvas.save()
		val lines = if (lyrics is SemanticLyrics.SyncedLyrics)
				(lyrics as SemanticLyrics.SyncedLyrics).text else null
		spForRender!!.forEachIndexed { i, it ->
			var spanEnd = -1
			var spanStartGradient = -1
			var wordIdx: Int? = null
			var gradientProgress = Float.NEGATIVE_INFINITY
			val firstTs = lines?.get(i)?.lyric?.start ?: ULong.MIN_VALUE
			val lastTs = lines?.get(i)?.lyric?.words?.lastOrNull()?.timeRange?.last ?: lines
				?.find { it.lyric.start > lines[i].lyric.start }?.lyric?.start ?: Long.MAX_VALUE.toULong()
			val timeOffsetForUse = min(scaleInAnimTime, min(lerp(firstTs.toFloat(), lastTs.toFloat(),
				0.5f) - firstTs.toFloat(), firstTs.toFloat()))
			val highlight = posForRender >= firstTs - timeOffsetForUse.toULong() &&
					posForRender <= lastTs + timeOffsetForUse.toULong()
			val scaleInProgress = if (lines == null) 1f else lerpInv(firstTs.toFloat() -
					timeOffsetForUse, firstTs.toFloat() + timeOffsetForUse, posForRender.toFloat())
			val scaleOutProgress = if (lines == null) 1f else lerpInv(lastTs.toFloat() -
					timeOffsetForUse, lastTs.toFloat() + timeOffsetForUse, posForRender.toFloat())
			val hlScaleFactor = if (lines == null) smallSizeFactor else {
				// lerp() argument order is swapped because we divide by this factor
				if (scaleOutProgress >= 0f && scaleOutProgress <= 1f)
					lerp(smallSizeFactor, 1f, scaleColorInterpolator.getInterpolation(scaleOutProgress))
				else if (scaleInProgress >= 0f && scaleInProgress <= 1f)
					lerp(1f, smallSizeFactor, scaleColorInterpolator.getInterpolation(scaleInProgress))
				else if (highlight)
					smallSizeFactor
				else 1f
			}
			val isRtl = it.layout.getParagraphDirection(0) == Layout.DIR_RIGHT_TO_LEFT
			val alignmentNormal = if (isRtl) it.layout.alignment == Layout.Alignment.ALIGN_OPPOSITE
				else it.layout.alignment == Layout.Alignment.ALIGN_NORMAL
			if ((scaleInProgress >= -.1f && scaleInProgress <= 1f) ||
				(scaleOutProgress >= -.1f && scaleOutProgress <= 1f))
				animating = true
			canvas.translate(0f, it.paddingTop.toFloat() / hlScaleFactor)
			if (highlight) {
				canvas.save()
				canvas.scale(1f / hlScaleFactor, 1f / hlScaleFactor)
				if (lines != null && lines[i].lyric.words != null) {
					wordIdx = lines[i].lyric.words?.indexOfLast { it.timeRange.start <= posForRender }
					if (wordIdx == -1) wordIdx = null
					if (wordIdx != null) {
						val word = lines[i].lyric.words!![wordIdx]
						spanEnd = word.charRange.last + 1 // get exclusive end
						val gradientEndTime = min(lastTs.toFloat() - timeOffsetForUse,
							word.timeRange.last.toFloat())
						val gradientStartTime = min(max(word.timeRange.start.toFloat(),
							firstTs.toFloat() - timeOffsetForUse), gradientEndTime - 1f)
						gradientProgress = lerpInv(gradientStartTime, gradientEndTime,
							posForRender.toFloat())
						if (gradientProgress >= 0f && gradientProgress <= 1f)
							spanStartGradient = word.charRange.first
					}
				} else {
					spanEnd = it.text.length
				}
			}
			if (!alignmentNormal) {
				if (!highlight)
					canvas.save()
				if (it.layout.alignment != Layout.Alignment.ALIGN_CENTER)
					canvas.translate(width * (1 - smallSizeFactor / hlScaleFactor), 0f)
				else // Layout.Alignment.ALIGN_CENTER
					canvas.translate(width * ((1 - smallSizeFactor / hlScaleFactor) / 2), 0f)
			}
			if (gradientProgress >= -.1f && gradientProgress <= 1f)
				animating = true
			val spanEndWithoutGradient = if (spanStartGradient == -1) spanEnd else spanStartGradient
			val inColorAnim = (scaleInProgress >= 0f && scaleInProgress <= 1f && gradientProgress ==
					Float.NEGATIVE_INFINITY) || (scaleOutProgress >= 0f && scaleOutProgress <= 1f)
			var colorSpan = it.text.getSpans<MyForegroundColorSpan>().firstOrNull()
			val cachedEnd = colorSpan?.let { j -> it.text.getSpanStart(j) } ?: -1
			val defaultColorForLine = when (lines?.get(i)?.lyric?.speaker) {
				SpeakerEntity.Male -> defaultTextColorM
				SpeakerEntity.Female -> defaultTextColorF
				SpeakerEntity.Duet -> defaultTextColorD
				else -> defaultTextColor
			}
			val highlightColorForLine = when (lines?.get(i)?.lyric?.speaker) {
				SpeakerEntity.Male -> highlightTextColorM
				SpeakerEntity.Female -> highlightTextColorF
				SpeakerEntity.Duet -> highlightTextColorD
				else -> highlightTextColor
			}
			val wordActiveSpanForLine = when (lines?.get(i)?.lyric?.speaker) {
				SpeakerEntity.Male -> wordActiveSpanM.value
				SpeakerEntity.Female -> wordActiveSpanF.value
				SpeakerEntity.Duet -> wordActiveSpanD.value
				else -> wordActiveSpan
			}
			val gradientSpanPoolForLine = when (lines?.get(i)?.lyric?.speaker) {
				SpeakerEntity.Male -> gradientSpanPoolM.value
				SpeakerEntity.Female -> gradientSpanPoolF.value
				SpeakerEntity.Duet -> gradientSpanPoolD.value
				else -> gradientSpanPool
			}
			val col = if (inColorAnim) ColorUtils.blendARGB(if (scaleOutProgress >= 0f &&
				scaleOutProgress <= 1f) highlightColorForLine else defaultColorForLine, if (
				scaleInProgress >= 0f && scaleInProgress <= 1f && gradientProgress == Float
					.NEGATIVE_INFINITY) highlightColorForLine else defaultColorForLine,
				scaleColorInterpolator.getInterpolation(if (scaleOutProgress >= 0f &&
					scaleOutProgress <= 1f) scaleOutProgress else scaleInProgress)) else 0
			if (cachedEnd != spanEndWithoutGradient || inColorAnim != (colorSpan != wordActiveSpanForLine)) {
				if (cachedEnd != -1) {
					it.text.removeSpan(colorSpan!!)
					if (colorSpan != wordActiveSpanForLine && (!inColorAnim || spanEndWithoutGradient == -1)) {
						if (colorSpanPool.size < 10)
							colorSpanPool.add(colorSpan)
						colorSpan = null
					} else if (inColorAnim && colorSpan == wordActiveSpanForLine)
						colorSpan = null
				}
				if (spanEndWithoutGradient != -1) {
					if (inColorAnim && colorSpan == null)
						colorSpan = colorSpanPool.getOrElse(0) { MyForegroundColorSpan(col) }
					else if (!inColorAnim)
						colorSpan = wordActiveSpanForLine
					it.text.setSpan(
						colorSpan, 0, spanEndWithoutGradient,
						Spanned.SPAN_INCLUSIVE_INCLUSIVE
					)
				}
			}
			if (inColorAnim && spanEndWithoutGradient != -1) {
				if (colorSpan!! == wordActiveSpanForLine)
					throw IllegalStateException("colorSpan == wordActiveSpan")
				colorSpan.color = col
			}
			var gradientSpan = it.text.getSpans<MyGradientSpan>().firstOrNull()
			val gradientSpanStart = gradientSpan?.let { j -> it.text.getSpanStart(j) } ?: -1
			if (gradientSpanStart != spanStartGradient) {
				if (gradientSpanStart != -1) {
					it.text.removeSpan(gradientSpan!!)
					if (spanStartGradient == -1) {
						if (gradientSpanPoolForLine.size < 10)
							gradientSpanPoolForLine.add(gradientSpan)
						gradientSpan = null
					}
				}
				if (spanStartGradient != -1) {
					if (gradientSpan == null)
						gradientSpan = gradientSpanPoolForLine.getOrElse(0) {
							when (lines?.get(i)?.lyric?.speaker) {
								SpeakerEntity.Male -> makeGradientSpanM()
								SpeakerEntity.Female -> makeGradientSpanF()
								SpeakerEntity.Duet -> makeGradientSpanD()
								else -> makeGradientSpan()
							}
						}
					it.text.setSpan(gradientSpan, spanStartGradient, spanEnd,
						Spanned.SPAN_INCLUSIVE_INCLUSIVE)
				}
			}
			if (gradientSpan != null) {
				gradientSpan.lineCount = 0
				gradientSpan.lineOffsets = it.words!![wordIdx!!]
				gradientSpan.totalCharsForProgress = spanEnd - spanStartGradient
				gradientSpan.lineCountDivider = if (!alignmentNormal) 2 else 1
				gradientSpan.progress = gradientProgress
			}
			it.layout.draw(canvas)
			if (highlight || !alignmentNormal)
				canvas.restore()
			canvas.translate(0f, (it.layout.height.toFloat() + it.paddingBottom) / hlScaleFactor)
		}
		canvas.restore()
		if (animating)
			invalidate()
	}

	override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
		val myWidth = getDefaultSize(minimumWidth, widthMeasureSpec)
		if (spForMeasure == null || spForMeasure!!.first.first != myWidth)
			spForMeasure = buildSpForMeasure(lyrics, myWidth)
		setMeasuredDimension(myWidth, getDefaultSize(spForMeasure!!.first.second, heightMeasureSpec))
	}

	override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
		if (spForMeasure == null || spForMeasure!!.first.first != right - left
			|| spForMeasure!!.first.second != bottom - top)
			spForMeasure = buildSpForMeasure(lyrics, right - left)
		spForRender = spForMeasure!!.second
		invalidate()
	}

	fun buildSpForMeasure(lyrics: SemanticLyrics?, width: Int): Pair<Pair<Int, Int>, List<SbItem>> {
		val lines = lyrics?.unsyncedText ?: listOf(context.getString(R.string.no_lyric_found))
		val syncedLines = (lyrics as? SemanticLyrics.SyncedLyrics?)?.text
		val spLines = lines.mapIndexed { i, it ->
			val sb = SpannableStringBuilder(it)
			val speaker = syncedLines?.get(i)?.lyric?.speaker
			val align = if (prefs.getBooleanStrict("lyric_center", false))
				Layout.Alignment.ALIGN_CENTER
			else if (syncedLines != null && speaker == SpeakerEntity.Voice2)
				Layout.Alignment.ALIGN_OPPOSITE
			else Layout.Alignment.ALIGN_NORMAL
			val tl = syncedLines != null && syncedLines[i].isTranslated
			val bg = speaker == SpeakerEntity.Background
			val paddingTop = if (syncedLines?.get(i)?.isTranslated == true) 2 else 18
			val paddingBottom = if (i + 1 < (syncedLines?.size ?: -1) &&
				syncedLines?.get(i + 1)?.isTranslated == true) 2 else 18
			val layout = StaticLayoutBuilderCompat.obtain(sb, when {
				speaker == SpeakerEntity.Male && tl -> translationTextPaintM.value
				speaker == SpeakerEntity.Male -> defaultTextPaintM.value
				speaker == SpeakerEntity.Female && tl -> translationTextPaintF.value
				speaker == SpeakerEntity.Female -> defaultTextPaintF.value
				speaker == SpeakerEntity.Duet && tl -> translationTextPaintD.value
				speaker == SpeakerEntity.Duet -> defaultTextPaintD.value
				tl && bg -> translationBackgroundTextPaint
				tl || bg -> translationTextPaint
				else -> defaultTextPaint
			}, (width * smallSizeFactor).toInt()).setAlignment(align).build()
			SbItem(layout, sb, paddingTop.dpToPx(context), paddingBottom.dpToPx(context),
				syncedLines?.get(i)?.lyric?.words?.map {
					val isRtl = layout.getParagraphDirection(0) == Layout.DIR_RIGHT_TO_LEFT
					val alignmentOpposite = if (isRtl) layout.alignment == Layout.Alignment.ALIGN_NORMAL
						else layout.alignment == Layout.Alignment.ALIGN_OPPOSITE
					val ia = mutableListOf<Int>()
					val firstLine = layout.getLineForOffset(it.charRange.first)
					val lastLine = layout.getLineForOffset(it.charRange.last + 1)
					for (line in firstLine..lastLine) {
						var baseOffset = if (line == firstLine && !alignmentOpposite) {
							val il = StaticLayoutBuilderCompat.obtain(sb, layout.paint, Int.MAX_VALUE)
								.setStart(layout.getLineStart(line)).setEnd(it.charRange.first)
								.setIsRtl(false).build()
							il.getLineWidth(0).toInt()
						} else if (alignmentOpposite) {
							val il = StaticLayoutBuilderCompat.obtain(sb, layout.paint,
								Int.MAX_VALUE).setStart(max(it.charRange.first,
								layout.getLineStart(line))).setEnd(layout.getLineEnd(line))
								.setIsRtl(false).build()
							width - il.getLineWidth(0).toInt()
						} else 0
						ia.add(baseOffset + if (layout.alignment == Layout.Alignment.ALIGN_CENTER) {
							val il = StaticLayoutBuilderCompat.obtain(sb, layout.paint, Int.MAX_VALUE)
								.setStart(layout.getLineStart(line)).setEnd(layout.getLineEnd(line))
								.setIsRtl(false).build()
							((width - il.getLineWidth(0)) / 2).toInt()
						} else 0) // offset from left to start from text on line
						ia.add(layout.getLineBottom(line) - layout.getLineTop(line)) // line height
						layout.paint.getTextBounds(sb.toString(), max(it.charRange.first, layout
							.getLineStart(line)), min(it.charRange.last + 1, layout.getLineEnd(line)), bounds)
						ia.add(bounds.width()) // width of text in this line
						ia.add(max(it.charRange.first, layout.getLineStart(line)) - it.charRange.first) // prefix chars
						ia.add(min(layout.getLineEnd(line), it.charRange.last + 1) - it.charRange.first) // suffix chars
					}
					return@map ia
				})
		}
		val heights = spLines.map { it.layout.height + it.paddingTop + it.paddingBottom }
		return Pair(Pair(width, (heights.max() * (1 - (1 / smallSizeFactor)) + heights.sum()).toInt()), spLines)
	}

	data class SbItem(val layout: StaticLayout, val text: SpannableStringBuilder,
	                  val paddingTop: Int, val paddingBottom: Int, val words: List<List<Int>>?)

}