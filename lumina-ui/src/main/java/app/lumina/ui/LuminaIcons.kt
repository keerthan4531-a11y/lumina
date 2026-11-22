package app.lumina.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Lumina's own icon set — hand-built ImageVectors, no `material-icons-extended`.
 *
 * Why not the material set? It adds ~30 MB to a debug APK and pulls a dependency that has to
 * match whatever Compose version the host project uses. These are 24dp / 1.7 stroke / round caps,
 * exactly matching preview/index.html's SVG sprite, and cost nothing.
 */
object LuminaIcons {

    private fun icon(name: String, stroke: Float = 1.7f, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = null,
                stroke = SolidColor(Color.White),
                strokeLineWidth = stroke,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
                pathBuilder = block,
            )
        }.build()

    private fun filledIcon(name: String, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            path(
                fill = SolidColor(Color.White),
                stroke = null,
                pathBuilder = block,
            )
        }.build()

    /** circle helper: two arcs */
    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx + r, y1 = cy)
        arcTo(r, r, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = cx - r, y1 = cy)
        close()
    }

    val search: ImageVector = icon("search") {
        circle(11f, 11f, 7f)
        moveTo(16.5f, 16.5f); lineTo(21f, 21f)
    }

    val ai: ImageVector = icon("ai") {
        moveTo(12f, 3f); lineTo(13.9f, 8.1f); lineTo(19f, 10f); lineTo(13.9f, 11.9f)
        lineTo(12f, 17f); lineTo(10.1f, 11.9f); lineTo(5f, 10f); lineTo(10.1f, 8.1f); close()
        moveTo(18.5f, 15.5f); lineTo(19.3f, 17.7f); lineTo(21.5f, 18.5f); lineTo(19.3f, 19.3f)
        lineTo(18.5f, 21.5f); lineTo(17.7f, 19.3f); lineTo(15.5f, 18.5f); lineTo(17.7f, 17.7f); close()
    }

    val shield: ImageVector = icon("shield") {
        moveTo(12f, 3f); lineTo(19f, 6f); lineTo(19f, 11.5f)
        curveTo(19f, 15.7f, 16.1f, 19.4f, 12f, 21f)
        curveTo(7.9f, 19.4f, 5f, 15.7f, 5f, 11.5f)
        lineTo(5f, 6f); close()
        moveTo(9f, 12.2f); lineTo(11f, 14.2f); lineTo(15f, 10f)
    }

    val home: ImageVector = icon("home") {
        moveTo(4f, 10.5f); lineTo(12f, 4f); lineTo(20f, 10.5f); lineTo(20f, 20f)
        lineTo(14f, 20f); lineTo(14f, 14f); lineTo(10f, 14f); lineTo(10f, 20f); lineTo(4f, 20f); close()
    }

    val tabs: ImageVector = icon("tabs") {
        moveTo(3.5f, 9f); lineTo(13f, 9f); lineTo(13f, 19.5f); lineTo(3.5f, 19.5f); close()
        moveTo(8.5f, 9f); lineTo(8.5f, 6f)
        curveTo(8.5f, 5.2f, 9.2f, 4.5f, 10f, 4.5f)
        lineTo(18f, 4.5f)
        curveTo(18.8f, 4.5f, 19.5f, 5.2f, 19.5f, 6f)
        lineTo(19.5f, 14f)
        curveTo(19.5f, 14.8f, 18.8f, 15.5f, 18f, 15.5f)
        lineTo(15.5f, 15.5f)
    }

    val menu: ImageVector = icon("menu") {
        moveTo(4f, 7f); lineTo(20f, 7f)
        moveTo(4f, 12f); lineTo(20f, 12f)
        moveTo(4f, 17f); lineTo(20f, 17f)
    }

    val close: ImageVector = icon("close") {
        moveTo(6f, 6f); lineTo(18f, 18f)
        moveTo(18f, 6f); lineTo(6f, 18f)
    }

    val back: ImageVector = icon("back") {
        moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f)
    }

    val plus: ImageVector = icon("plus") {
        moveTo(12f, 5f); lineTo(12f, 19f)
        moveTo(5f, 12f); lineTo(19f, 12f)
    }

    val globe: ImageVector = icon("globe") {
        circle(12f, 12f, 8.5f)
        moveTo(3.5f, 12f); lineTo(20.5f, 12f)
        moveTo(12f, 3.5f)
        curveTo(15.2f, 5.8f, 16.8f, 8.8f, 16.8f, 12f)
        curveTo(16.8f, 15.2f, 15.2f, 18.2f, 12f, 20.5f)
        curveTo(8.8f, 18.2f, 7.2f, 15.2f, 7.2f, 12f)
        curveTo(7.2f, 8.8f, 8.8f, 5.8f, 12f, 3.5f)
        close()
    }

    val lock: ImageVector = icon("lock") {
        moveTo(6.5f, 10.5f); lineTo(17.5f, 10.5f); lineTo(17.5f, 20f); lineTo(6.5f, 20f); close()
        moveTo(9f, 10.5f); lineTo(9f, 8f)
        arcTo(3f, 3f, 0f, isMoreThanHalf = false, isPositiveArc = true, x1 = 15f, y1 = 8f)
        lineTo(15f, 10.5f)
    }

    val mic: ImageVector = icon("mic") {
        moveTo(12f, 3.5f)
        arcTo(2.6f, 2.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 8.7f)
        arcTo(2.6f, 2.6f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 12f, y1 = 3.5f)
        close()
        moveTo(12f, 8.7f); lineTo(12f, 15f)
        moveTo(6.5f, 11.5f)
        curveTo(6.5f, 14.5f, 9f, 17f, 12f, 17f)
        curveTo(15f, 17f, 17.5f, 14.5f, 17.5f, 11.5f)
        moveTo(12f, 17f); lineTo(12f, 21f)
    }

    val refresh: ImageVector = icon("refresh") {
        moveTo(19f, 12f)
        arcTo(7f, 7f, 0f, isMoreThanHalf = true, isPositiveArc = true, x1 = 5f, y1 = 12f)
        moveTo(19.5f, 4.5f); lineTo(19.5f, 9f); lineTo(15f, 9f)
    }

    val more: ImageVector = filledIcon("more") {
        circle(5f, 12f, 1.6f)
        circle(12f, 12f, 1.6f)
        circle(19f, 12f, 1.6f)
    }

    val star: ImageVector = icon("star") {
        moveTo(12f, 3.5f); lineTo(14.6f, 9.1f); lineTo(20.5f, 9.9f); lineTo(16.2f, 14.1f)
        lineTo(17.3f, 20f); lineTo(12f, 17.1f); lineTo(6.7f, 20f); lineTo(7.8f, 14.1f)
        lineTo(3.5f, 9.9f); lineTo(9.4f, 9.1f); close()
    }

    val history: ImageVector = icon("history") {
        circle(12f, 12f, 8.5f)
        moveTo(12f, 7.5f); lineTo(12f, 12f); lineTo(15f, 14f)
    }

    val fire: ImageVector = icon("fire") {
        moveTo(12f, 3f)
        curveTo(17f, 7.2f, 17f, 9.6f, 17f, 12f)
        curveTo(17f, 14.8f, 14.8f, 17f, 12f, 17f)
        curveTo(9.2f, 17f, 7f, 14.8f, 7f, 12f)
        curveTo(7f, 10.4f, 7.7f, 9f, 8.6f, 7.8f)
        curveTo(9.1f, 9f, 10f, 9.8f, 11f, 9.8f)
        curveTo(12.6f, 9.8f, 13.4f, 8.2f, 12f, 3f)
        close()
    }
}
