package fr.istic.mob.networkKOUTOUADEGNY.ui

import android.content.res.Resources
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.drawable.Drawable
import kotlin.math.max
import fr.istic.mob.networkKOUTOUADEGNY.model.Graph
import fr.istic.mob.networkKOUTOUADEGNY.model.GraphConnection
import fr.istic.mob.networkKOUTOUADEGNY.model.GraphNode


class GraphDrawable(private val resources: Resources) : Drawable() {
    private var graph: Graph = Graph()
    private var alphaValue: Int = 255
    private var colorFilterValue: ColorFilter? = null

    private val density = resources.displayMetrics.density
    private val fontScale = resources.configuration.fontScale

    private val canvasBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E7EDF3")
    }
    private val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FAFAF7")
    }
    private val outerWallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#37474F")
        style = Paint.Style.STROKE
        strokeWidth = 12f
    }
    private val innerWallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#546E7A")
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }
    private val nodeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nodeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#263238")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val objectLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#13212B")
        textSize = 16f * fontScale
    }
    private val connectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val connectionLabelFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
    }
    private val connectionLabelStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#90A4AE")
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
    }
    private val connectionLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#13212B")
        textAlign = Paint.Align.CENTER
        textSize = 14f * fontScale
    }
    private val planLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#607D8B")
        textAlign = Paint.Align.CENTER
        textSize = 20f * fontScale
    }

    fun setGraph(graph: Graph) {
        this.graph = graph
        invalidateSelf()
    }

    override fun draw(canvas: Canvas) {
        applyAlphaAndFilter()
        canvas.drawRect(bounds, canvasBackgroundPaint)

        canvas.save()
        canvas.scale(density, density)
        drawPlan(canvas)
        drawConnections(canvas)
        drawNodes(canvas)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        alphaValue = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        colorFilterValue = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getIntrinsicWidth(): Int {
        return GraphGeometry.planWidthPx(graph.plan, density)
    }

    override fun getIntrinsicHeight(): Int {
        return GraphGeometry.planHeightPx(graph.plan, density)
    }

    private fun applyAlphaAndFilter() {
        val paints = listOf(
            canvasBackgroundPaint,
            floorPaint,
            outerWallPaint,
            innerWallPaint,
            nodeFillPaint,
            nodeStrokePaint,
            objectLabelPaint,
            connectionPaint,
            connectionLabelFillPaint,
            connectionLabelStrokePaint,
            connectionLabelPaint,
            planLabelPaint,
        )
        paints.forEach { paint ->
            paint.alpha = alphaValue
            paint.colorFilter = colorFilterValue
        }
    }

    private fun drawPlan(canvas: Canvas) {
        val planRect = RectF(
            GraphGeometry.PLAN_BORDER_INSET_DP,
            GraphGeometry.PLAN_BORDER_INSET_DP,
            graph.plan.widthDp - GraphGeometry.PLAN_BORDER_INSET_DP,
            graph.plan.heightDp - GraphGeometry.PLAN_BORDER_INSET_DP,
        )
        canvas.drawRoundRect(planRect, 20f, 20f, floorPaint)
        canvas.drawRoundRect(planRect, 20f, 20f, outerWallPaint)

        graph.plan.walls.forEach { wall ->
            canvas.drawLine(wall.startXDp, wall.startYDp, wall.endXDp, wall.endYDp, innerWallPaint)
        }

        val planLabelBaselineOffset = centeredTextOffset(planLabelPaint)
        graph.plan.roomLabels.forEach { label ->
            canvas.drawText(
                resources.getString(label.textRes),
                label.xDp,
                label.yDp + planLabelBaselineOffset,
                planLabelPaint,
            )
        }
    }

    private fun drawConnections(canvas: Canvas) {
        graph.connections.forEach { connection ->
            val path = GraphGeometry.buildConnectionPath(graph, connection) ?: return@forEach
            connectionPaint.color = connection.color.colorInt
            connectionPaint.strokeWidth = max(connection.strokeWidthDp, 1f)
            canvas.drawPath(path, connectionPaint)
            drawConnectionLabel(canvas, connection)
        }
    }

    private fun drawConnectionLabel(canvas: Canvas, connection: GraphConnection) {
        val labelPosition = GraphGeometry.connectionLabelPosition(graph, connection) ?: return
        val text = connection.label
        val horizontalPadding = 14f
        val verticalPadding = 8f
        val textWidth = connectionLabelPaint.measureText(text)
        val labelHeight = connectionLabelPaint.fontMetrics.run { bottom - top }
        val rect = RectF(
            labelPosition.x - textWidth / 2f - horizontalPadding,
            labelPosition.y - labelHeight / 2f - verticalPadding,
            labelPosition.x + textWidth / 2f + horizontalPadding,
            labelPosition.y + labelHeight / 2f + verticalPadding,
        )
        canvas.drawRoundRect(rect, 14f, 14f, connectionLabelFillPaint)
        canvas.drawRoundRect(rect, 14f, 14f, connectionLabelStrokePaint)
        canvas.drawText(
            text,
            labelPosition.x,
            labelPosition.y + centeredTextOffset(connectionLabelPaint),
            connectionLabelPaint,
        )
    }

    private fun drawNodes(canvas: Canvas) {
        graph.nodes.forEach { node ->
            val rect = GraphGeometry.nodeRect(node)
            nodeFillPaint.color = node.color.colorInt
            canvas.drawRoundRect(
                rect,
                GraphGeometry.NODE_CORNER_RADIUS_DP,
                GraphGeometry.NODE_CORNER_RADIUS_DP,
                nodeFillPaint,
            )
            canvas.drawRoundRect(
                rect,
                GraphGeometry.NODE_CORNER_RADIUS_DP,
                GraphGeometry.NODE_CORNER_RADIUS_DP,
                nodeStrokePaint,
            )

            canvas.drawText(
                node.label,
                rect.right + 16f,
                rect.centerY() + centeredTextOffset(objectLabelPaint),
                objectLabelPaint,
            )
        }
    }

    private fun centeredTextOffset(paint: Paint): Float {
        return -(paint.fontMetrics.ascent + paint.fontMetrics.descent) / 2f
    }
}
