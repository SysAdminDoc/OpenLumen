package com.openlumen.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.github.takahirom.roborazzi.captureRoboImage
import com.openlumen.ui.theme.OpenLumenTheme
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w360dp-h240dp-xxhdpi")
class ThemeTokenRoborazziTest {

    @Test
    fun lightThemeTokens() {
        captureRoboImage("src/test/roborazzi/theme_tokens_light.png") {
            OpenLumenTheme(darkTheme = false) {
                ThemeTokenFixture()
            }
        }
    }

    @Test
    fun darkThemeTokens() {
        captureRoboImage("src/test/roborazzi/theme_tokens_dark.png") {
            OpenLumenTheme(darkTheme = true) {
                ThemeTokenFixture()
            }
        }
    }
}

@Composable
private fun ThemeTokenFixture() {
    Surface(color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                )
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Swatch(MaterialTheme.colorScheme.primary)
                    Swatch(MaterialTheme.colorScheme.secondary)
                    Swatch(MaterialTheme.colorScheme.tertiary)
                    Swatch(MaterialTheme.colorScheme.error)
                }
            }
            Slider(value = 0.62f, onValueChange = {})
            LinearProgressIndicator(
                progress = { 0.72f },
                modifier = Modifier.fillMaxWidth()
            )
            Switch(checked = true, onCheckedChange = {})
        }
    }
}

@Composable
private fun Swatch(color: Color) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .background(color = color, shape = MaterialTheme.shapes.medium)
    )
}
