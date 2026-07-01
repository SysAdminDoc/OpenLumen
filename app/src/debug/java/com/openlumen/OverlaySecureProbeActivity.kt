package com.openlumen

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView

class OverlaySecureProbeActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContentView(
            LinearLayout(this).apply {
                gravity = Gravity.CENTER
                orientation = LinearLayout.VERTICAL
                setPadding(64, 64, 64, 64)
                setBackgroundColor(Color.rgb(24, 24, 37))
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                addView(
                    TextView(context).apply {
                        gravity = Gravity.CENTER
                        setTextColor(Color.rgb(205, 214, 244))
                        text = "OpenLumen secure-window smoke probe\nFLAG_SECURE enabled"
                        textSize = 20f
                    }
                )
                addView(
                    EditText(context).apply {
                        hint = "Smoke input"
                        setText("Smoke input")
                        selectAll()
                        setSingleLine()
                    },
                    LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                    )
                )
            }
        )
    }
}
