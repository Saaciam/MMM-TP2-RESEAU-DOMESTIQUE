package fr.istic.mob.networkKOUTOUADEGNY.ui
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.PointF
import android.graphics.RectF
import kotlin.math.hypot
import kotlin.math.roundToInt
import fr.istic.mob.networkKOUTOUADEGNY.model.ApartmentPlan
import fr.istic.mob.networkKOUTOUADEGNY.model.Graph
import fr.istic.mob.networkKOUTOUADEGNY.model.GraphConnection
import fr.istic.mob.networkKOUTOUADEGNY.model.GraphNode
object GraphGeometry {
    const val PLAN_BORDER_INSET_DP = 36f
    const val NODE_WIDTH_DP = 112f
    const val NODE_HEIGHT_DP = 64f
    const val NODE_CORNER_RADIUS_DP = 18f
    const val CONNECTION_LABEL_OFFSET_DP = 20f
    const val CONNECTION_HANDLE_RADIUS_DP = 28f
    const val MIN_CURVE_SPAN_DP = 64f
    const val MAX_CURVATURE_DP = 260f

    fun planWidthPx(plan: ApartmentPlan, density: Float): Int {
        return (plan.widthDp * density).roundToInt()
    }

    fun planHeightPx(plan: ApartmentPlan, density: Float): Int {
        return (plan.heightDp * density).roundToInt()
    }

    fun nodeRect(node: GraphNode): RectF {
        val halfWidth = NODE_WIDTH_DP / 2f
        val halfHeight = NODE_HEIGHT_DP / 2f
        return RectF(
            node.xDp - halfWidth,
            node.yDp - halfHeight,
            node.xDp + halfWidth,
            node.yDp + halfHeight,
        )
    }

    fun findNodeAt(graph: Graph, xDp: Float, yDp: Float): GraphNode? {
        return graph.nodes.lastOrNull { nodeRect(it).contains(xDp, yDp) }
    }

    fun findConnectionHandleAt(graph: Graph, xDp: Float, yDp: Float): GraphConnection? {
        return graph.connections.lastOrNull { connection ->
            val labelPosition = connectionLabelPosition(graph, connection) ?: return@lastOrNull false
            val midpointPosition =
                connectionMidpointPosition(graph, connection) ?: return@lastOrNull false
            isWithinRadius(labelPosition, xDp, yDp, CONNECTION_HANDLE_RADIUS_DP + 8f) ||
                    isWithinRadius(midpointPosition, xDp, yDp, CONNECTION_HANDLE_RADIUS_DP)
        }
    }

    fun buildConnectionPath(graph: Graph, connection: GraphConnection): Path? {
        val startNode = graph.findNode(connection.startNodeId) ?: return null
        val endNode = graph.findNode(connection.endNodeId) ?: return null
        val path = Path()
        path.moveTo(startNode.xDp, startNode.yDp)

        val dx = endNode.xDp - startNode.xDp
        val dy = endNode.yDp - startNode.yDp
        val distance = hypot(dx, dy)
        if (distance < MIN_CURVE_SPAN_DP || connection.curvatureDp == 0f) {
            path.lineTo(endNode.xDp, endNode.yDp)
            return path
        }

        val normalX = -dy / distance
        val normalY = dx / distance
        val midX = (startNode.xDp + endNode.xDp) / 2f
        val midY = (startNode.yDp + endNode.yDp) / 2f
        val controlX = midX + normalX * connection.curvatureDp * 2f
        val controlY = midY + normalY * connection.curvatureDp * 2f
        path.quadTo(controlX, controlY, endNode.xDp, endNode.yDp)
        return path
    }

    fun connectionMidpointPosition(graph: Graph, connection: GraphConnection): PointF? {
        val path = buildConnectionPath(graph, connection) ?: return null
        return samplePath(path).position
    }

    fun connectionLabelPosition(graph: Graph, connection: GraphConnection): PointF? {
        val sample = samplePath(buildConnectionPath(graph, connection) ?: return null)
        val normal = normalizedVector(-sample.tangent.y, sample.tangent.x)
        return PointF(
            sample.position.x + normal.x * CONNECTION_LABEL_OFFSET_DP,
            sample.position.y + normal.y * CONNECTION_LABEL_OFFSET_DP,
        )
    }

    fun signedDistanceToLine(
        pointXDp: Float,
        pointYDp: Float,
        startXDp: Float,
        startYDp: Float,
        endXDp: Float,
        endYDp: Float,
    ): Float {
        val dx = endXDp - startXDp
        val dy = endYDp - startYDp
        val length = hypot(dx, dy)
        if (length < 0.001f) {
            return 0f
        }
        val cross = ((pointXDp - startXDp) * dy) - ((pointYDp - startYDp) * dx)
        return -(cross / length)
    }

    fun distanceBetweenNodes(first: GraphNode, second: GraphNode): Float {
        return hypot(second.xDp - first.xDp, second.yDp - first.yDp)
    }

    fun clampNodePosition(plan: ApartmentPlan, xDp: Float, yDp: Float): PointF {
        val horizontalPadding = PLAN_BORDER_INSET_DP + NODE_WIDTH_DP / 2f
        val verticalPadding = PLAN_BORDER_INSET_DP + NODE_HEIGHT_DP / 2f
        return PointF(
            xDp.coerceIn(horizontalPadding, plan.widthDp - horizontalPadding),
            yDp.coerceIn(verticalPadding, plan.heightDp - verticalPadding),
        )
    }

    private fun samplePath(path: Path): PathSample {
        val position = FloatArray(2)
        val tangent = FloatArray(2)
        val measure = PathMeasure(path, false)
        measure.getPosTan(measure.length / 2f, position, tangent)
        return PathSample(
            position = PointF(position[0], position[1]),
            tangent = normalizedVector(tangent[0], tangent[1]),
        )
    }

    private fun normalizedVector(x: Float, y: Float): PointF {
        val length = hypot(x, y)
        if (length < 0.001f) {
            return PointF(0f, -1f)
        }
        return PointF(x / length, y / length)
    }

    private fun isWithinRadius(center: PointF, xDp: Float, yDp: Float, radiusDp: Float): Boolean {
        return hypot(center.x - xDp, center.y - yDp) <= radiusDp
    }

    private data class PathSample(
        val position: PointF,
        val tangent: PointF,
    )
}
