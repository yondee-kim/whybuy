package com.whybuy.app.service

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import com.whybuy.app.overlay.Decision
import com.whybuy.app.overlay.Emotion
import com.whybuy.app.overlay.EndReason
import com.whybuy.app.overlay.OverlayStep

class OverlayController(private val context: Context) {

    private val windowManager =
        context.getSystemService(WindowManager::class.java)

    private var rootView: LinearLayout? = null
    private val handler = Handler(Looper.getMainLooper())

    private var step = OverlayStep.GREETING
    private var currentPackage: String? = null
    private var selectedEmotion: Emotion? = null
    private var shownAt = 0L

    private var timeoutRunnable: Runnable? = null

    // 색상
    private val colorBg = Color.parseColor("#F5EFE6")
    private val colorText = Color.parseColor("#3D3A35")
    private val colorButton = Color.parseColor("#FFFFFF")
    private val colorAccent = Color.parseColor("#E8A87C")

    fun show(packageName: String) {
        if (rootView != null) return

        handler.post {
            currentPackage = packageName
            selectedEmotion = null
            step = OverlayStep.GREETING
            shownAt = System.currentTimeMillis()

            val root = createRoot()

            val params = WindowManager.LayoutParams(
                dp(260),
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.BOTTOM or Gravity.END
                x = dp(20)
                y = dp(100)
            }

            runCatching {
                windowManager.addView(root, params)
                rootView = root
                animateIn(root)
                renderGreeting()
                Log.d(TAG, "Overlay 표시 · $packageName")
            }.onFailure {
                Log.e(TAG, "Overlay 표시 실패", it)
            }
        }
    }

    fun dismiss(reason: EndReason = EndReason.APP_CLOSED) {
        handler.post {
            cancelTimeout()
            val view = rootView ?: return@post
            rootView = null

            val duration = System.currentTimeMillis() - shownAt
            Log.d(
                TAG,
                "Overlay 종료 · reason=$reason · emotion=$selectedEmotion · ${duration}ms"
            )

            animateOut(view) {
                runCatching { windowManager.removeView(view) }
            }
        }
    }

    // ---------- 단계별 화면 ----------

    private fun renderGreeting() {
        step = OverlayStep.GREETING
        val root = rootView ?: return
        root.removeAllViews()

        root.addView(pyeon())
        root.addView(message("잠깐 같이\n생각해도 될까요?"))
        root.addView(space(12))
        root.addView(button("좋아요") { renderEmotion() })
        root.addView(space(6))
        root.addView(button("괜찮아요") { dismiss(EndReason.DECLINED) })

        startTimeout(8_000L)
    }

    private fun renderEmotion() {
        step = OverlayStep.EMOTION
        val root = rootView ?: return
        root.removeAllViews()

        root.addView(message("오늘은 어떤\n마음이었을까요?"))
        root.addView(space(12))

        Emotion.entries.forEach { emotion ->
            root.addView(button(emotion.label) {
                selectedEmotion = emotion
                Log.d(TAG, "감정 선택 · ${emotion.name}")
                renderDecision()
            })
            root.addView(space(6))
        }

        root.addView(subtleButton("다음에 이야기할게요") {
            renderDecision()
        })

        startTimeout(30_000L)
    }

    private fun renderDecision() {
        step = OverlayStep.DECISION
        val root = rootView ?: return
        root.removeAllViews()

        root.addView(message("괜찮다면\n한 번만 더 생각해봐도\n괜찮아요."))
        root.addView(space(12))

        Decision.entries.forEach { decision ->
            root.addView(button(decision.label) {
                Log.d(TAG, "결정 · ${decision.name}")
                renderFarewell(decision)
            })
            root.addView(space(6))
        }

        startTimeout(30_000L)
    }

    private fun renderFarewell(decision: Decision) {
        step = OverlayStep.FAREWELL
        val root = rootView ?: return
        root.removeAllViews()
        cancelTimeout()

        val text = when (decision) {
            Decision.BUY_NOW -> "고르셨군요."
            Decision.THINK_MORE -> "천천히 골라도\n괜찮아요."
            Decision.LATER -> "여기까지 함께해 주셔서\n고마워요."
        }

        root.addView(pyeon())
        root.addView(message(text))

        handler.postDelayed({ dismiss(EndReason.COMPLETED) }, 2_500L)
    }

    // ---------- 뷰 헬퍼 ----------

    private fun createRoot(): LinearLayout {
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(20))
            background = GradientDrawable().apply {
                setColor(colorBg)
                cornerRadius = dp(24).toFloat()
            }
            elevation = dp(8).toFloat()
        }
    }

    /** 편이 자리 (임시 회색 원) */
    private fun pyeon(): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(dp(56), dp(56)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                bottomMargin = dp(12)
            }
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#E0D8CC"))
                shape = GradientDrawable.OVAL
            }
        }
    }

    private fun message(text: String): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(colorText)
            textSize = 15f
            gravity = Gravity.CENTER
            setLineSpacing(dp(4).toFloat(), 1f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun button(text: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(colorText)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = GradientDrawable().apply {
                setColor(colorButton)
                cornerRadius = dp(14).toFloat()
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onClick() }
        }
    }

    private fun subtleButton(text: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            setTextColor(Color.parseColor("#9A9088"))
            textSize = 12f
            gravity = Gravity.CENTER
            setPadding(dp(8), dp(10), dp(8), dp(4))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setOnClickListener { onClick() }
        }
    }

    private fun space(height: Int): View {
        return View(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(height)
            )
        }
    }

    // ---------- 타임아웃 ----------

    private fun startTimeout(delay: Long) {
        cancelTimeout()
        timeoutRunnable = Runnable {
            dismiss(EndReason.DISMISSED_TIMEOUT)
        }.also { handler.postDelayed(it, delay) }
    }

    private fun cancelTimeout() {
        timeoutRunnable?.let { handler.removeCallbacks(it) }
        timeoutRunnable = null
    }

    // ---------- 애니메이션 ----------

    private fun animateIn(view: View) {
        view.alpha = 0f
        view.translationY = dp(24).toFloat()
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setDuration(1000L)
            .start()
    }

    private fun animateOut(view: View, onEnd: () -> Unit) {
        view.animate()
            .alpha(0f)
            .translationY(dp(16).toFloat())
            .setDuration(600L)
            .withEndAction(onEnd)
            .start()
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "WhyBuy"
    }
}