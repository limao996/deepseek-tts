package io.github.deepseektts.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Small original whale mark drawn in code; no external image asset is required. */
@Composable
fun WhaleMark(modifier: Modifier = Modifier, size: Dp = 54.dp, tint: Color = Color(0xFF376C94)) {
    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val body = Path().apply {
            moveTo(w * .12f, h * .57f)
            cubicTo(w * .18f, h * .28f, w * .55f, h * .22f, w * .75f, h * .38f)
            cubicTo(w * .84f, h * .44f, w * .88f, h * .51f, w * .91f, h * .57f)
            cubicTo(w * .82f, h * .59f, w * .76f, h * .69f, w * .66f, h * .70f)
            cubicTo(w * .42f, h * .77f, w * .20f, h * .70f, w * .12f, h * .57f)
            close()
        }
        drawPath(body, tint)
        val tail = Path().apply {
            moveTo(w * .15f, h * .57f)
            cubicTo(w * .05f, h * .53f, w * .06f, h * .42f, w * .07f, h * .38f)
            cubicTo(w * .17f, h * .43f, w * .21f, h * .48f, w * .22f, h * .55f)
            close()
        }
        drawPath(tail, tint)
        drawCircle(Color.White, radius = w * .027f, center = Offset(w * .72f, h * .47f))
        drawCircle(Color(0xFF20384B), radius = w * .012f, center = Offset(w * .73f, h * .47f))
        val wave = Path().apply {
            moveTo(w * .25f, h * .83f)
            cubicTo(w * .37f, h * .76f, w * .48f, h * .90f, w * .60f, h * .83f)
            cubicTo(w * .72f, h * .76f, w * .80f, h * .82f, w * .88f, h * .78f)
        }
        drawPath(wave, tint.copy(alpha = .56f), style = Stroke(width = w * .035f, cap = StrokeCap.Round))
        drawCircle(tint.copy(alpha = .56f), radius = w * .035f, center = Offset(w * .52f, h * .23f))
        drawCircle(tint.copy(alpha = .35f), radius = w * .02f, center = Offset(w * .44f, h * .15f))
    }
}
