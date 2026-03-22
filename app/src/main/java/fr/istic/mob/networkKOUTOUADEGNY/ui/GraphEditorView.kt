package fr.istic.mob.networkKOUTOUADEGNY.ui

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.PointF
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import kotlin.math.abs
import fr.istic.mob.networkKOUTOUADEGNY.model.EditorMode
import fr.istic.mob.networkKOUTOUADEGNY.model.Graph


class GraphEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {
    interface Listener {
        fun onAddNodeRequested(xDp: Float, yDp: Float)
        fun onNodeLongPressed(nodeId: Long)
        fun onConnectionLongPressed(connectionId: Long)
        fun onConnectionRequested(startNodeId: Long, endNodeId: Long)
        fun onNodeMoved(nodeId: Long, xDp: Float, yDp: Float)
        fun onConnectionCurvatureChanged(connectionId: Long, curvatureDp: Float)
    }

    var listener: Listener? = null

    private val graphDrawable = GraphDrawable(resources)
    private val density = resources.displayMetrics.density
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val touchSlopDp = ViewConfiguration.get(context).scaledTouchSlop / density

    private val temporaryConnectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#546E7A")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        pathEffect = DashPathEffect(floatArrayOf(16f, 14f), 0f)
    }
    private val connectionHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#455A64")
        style = Paint.Style.FILL
    }

    private var graph: Graph = Graph()
    private var mode: EditorMode = EditorMode.EDIT
    private var activeInteraction: TouchInteraction = TouchInteraction.None
    private var pendingLongPress: PendingLongPress? = null
    private var temporaryConnection: TemporaryConnection? = null

    private val longPressRunnable = Runnable {
        val pending = pendingLongPress ?: return@Runnable
        when (pending.target) {
            is LongPressTarget.Background -> {
                val point = pending.target.point
                listener?.onAddNodeRequested(point.x, point.y)
            }

            is LongPressTarget.Node -> {
                listener?.onNodeLongPressed(pending.target.nodeId)
            }

            is LongPressTarget.Connection -> {
                listener?.onConnectionLongPressed(pending.target.connectionId)
            }
        }
        pendingLongPress = null
    }

    fun setGraph(graph: Graph) {
        val planChanged = this.graph.plan != graph.plan
        this.graph = graph
        graphDrawable.setGraph(graph)
        if (planChanged) {
            requestLayout()
        }
        invalidate()
    }

    fun setMode(mode: EditorMode) {
        this.mode = mode
        cancelPendingLongPress()
        activeInteraction = TouchInteraction.None
        temporaryConnection = null
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = GraphGeometry.planWidthPx(graph.plan, density)
        val desiredHeight = GraphGeometry.planHeightPx(graph.plan, density)
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        graphDrawable.setBounds(0, 0, width, height)
        graphDrawable.draw(canvas)

        canvas.save()
        canvas.scale(density, density)
        drawConnectionHandles(canvas)
        drawTemporaryConnection(canvas)
        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val xDp = event.x / density
        val yDp = event.y / density

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleActionDown(xDp, yDp)
            MotionEvent.ACTION_MOVE -> handleActionMove(xDp, yDp)
            MotionEvent.ACTION_UP -> handleActionUp(xDp, yDp)
            MotionEvent.ACTION_CANCEL -> handleActionCancel()
            else -> super.onTouchEvent(event)
        }
    }

    private fun handleActionDown(xDp: Float, yDp: Float): Boolean {
        val touchedNode = GraphGeometry.findNodeAt(graph, xDp, yDp)
        val touchedConnection = GraphGeometry.findConnectionHandleAt(graph, xDp, yDp)

        return when (mode) {
            EditorMode.ADD_DEVICE -> {
                if (touchedNode == null && touchedConnection == null) {
                    pendingLongPress = PendingLongPress(
                        target = LongPressTarget.Background(PointF(xDp, yDp)),
                        origin = PointF(xDp, yDp),
                    )
                    longPressHandler.postDelayed(
                        longPressRunnable,
                        ViewConfiguration.getLongPressTimeout().toLong(),
                    )
                    true
                } else {
                    false
                }
            }

            EditorMode.ADD_CONNECTION -> {
                if (touchedNode != null) {
                    parent.requestDisallowInterceptTouchEvent(true)
                    temporaryConnection = TemporaryConnection(touchedNode.id, PointF(xDp, yDp))
                    activeInteraction = TouchInteraction.CreatingConnection(touchedNode.id)
                    invalidate()
                    true
                } else {
                    false
                }
            }

            EditorMode.EDIT -> {
                when {
                    touchedNode != null -> {
                        parent.requestDisallowInterceptTouchEvent(true)
                        val offsetX = xDp - touchedNode.xDp
                        val offsetY = yDp - touchedNode.yDp
                        activeInteraction = TouchInteraction.PendingNode(touchedNode.id, offsetX, offsetY)
                        pendingLongPress = PendingLongPress(
                            target = LongPressTarget.Node(touchedNode.id),
                            origin = PointF(xDp, yDp),
                        )
                        longPressHandler.postDelayed(
                            longPressRunnable,
                            ViewConfiguration.getLongPressTimeout().toLong(),
                        )
                        true
                    }

                    touchedConnection != null -> {
                        parent.requestDisallowInterceptTouchEvent(true)
                        activeInteraction = TouchInteraction.PendingConnection(touchedConnection.id)
                        pendingLongPress = PendingLongPress(
                            target = LongPressTarget.Connection(touchedConnection.id),
                            origin = PointF(xDp, yDp),
                        )
                        longPressHandler.postDelayed(
                            longPressRunnable,
                            ViewConfiguration.getLongPressTimeout().toLong(),
                        )
                        true
                    }

                    else -> false
                }
            }
        }
    }

    private fun handleActionMove(xDp: Float, yDp: Float): Boolean {
        when (val interaction = activeInteraction) {
            is TouchInteraction.None -> {
                if (mode == EditorMode.ADD_DEVICE && pendingLongPress != null) {
                    cancelPendingLongPressIfMoved(xDp, yDp)
                    return true
                }
                return false
            }

            is TouchInteraction.PendingNode -> {
                if (cancelPendingLongPressIfMoved(xDp, yDp)) {
                    activeInteraction = TouchInteraction.MovingNode(
                        interaction.nodeId,
                        interaction.offsetX,
                        interaction.offsetY,
                    )
                    listener?.onNodeMoved(
                        interaction.nodeId,
                        xDp - interaction.offsetX,
                        yDp - interaction.offsetY,
                    )
                }
                return true
            }

            is TouchInteraction.MovingNode -> {
                listener?.onNodeMoved(
                    interaction.nodeId,
                    xDp - interaction.offsetX,
                    yDp - interaction.offsetY,
                )
                return true
            }

            is TouchInteraction.PendingConnection -> {
                if (cancelPendingLongPressIfMoved(xDp, yDp)) {
                    activeInteraction = TouchInteraction.AdjustingConnection(interaction.connectionId)
                    updateConnectionCurvature(interaction.connectionId, xDp, yDp)
                }
                return true
            }

            is TouchInteraction.AdjustingConnection -> {
                updateConnectionCurvature(interaction.connectionId, xDp, yDp)
                return true
            }

            is TouchInteraction.CreatingConnection -> {
                temporaryConnection = TemporaryConnection(interaction.startNodeId, PointF(xDp, yDp))
                invalidate()
                return true
            }
        }
    }

    private fun handleActionUp(xDp: Float, yDp: Float): Boolean {
        cancelPendingLongPress()

        when (val interaction = activeInteraction) {
            is TouchInteraction.CreatingConnection -> {
                val endNode = GraphGeometry.findNodeAt(graph, xDp, yDp)
                if (endNode != null && endNode.id != interaction.startNodeId) {
                    listener?.onConnectionRequested(interaction.startNodeId, endNode.id)
                }
                temporaryConnection = null
                activeInteraction = TouchInteraction.None
                invalidate()
                return true
            }

            is TouchInteraction.MovingNode,
            is TouchInteraction.AdjustingConnection,
            is TouchInteraction.PendingNode,
            is TouchInteraction.PendingConnection
                -> {
                activeInteraction = TouchInteraction.None
                invalidate()
                return true
            }

            TouchInteraction.None -> {
                activeInteraction = TouchInteraction.None
                return mode == EditorMode.ADD_DEVICE
            }
        }
    }

    private fun handleActionCancel(): Boolean {
        cancelPendingLongPress()
        activeInteraction = TouchInteraction.None
        temporaryConnection = null
        invalidate()
        return true
    }

    private fun updateConnectionCurvature(connectionId: Long, xDp: Float, yDp: Float) {
        val connection = graph.connections.firstOrNull { it.id == connectionId } ?: return
        val startNode = graph.findNode(connection.startNodeId) ?: return
        val endNode = graph.findNode(connection.endNodeId) ?: return
        val curvature = GraphGeometry.signedDistanceToLine(
            pointXDp = xDp,
            pointYDp = yDp,
            startXDp = startNode.xDp,
            startYDp = startNode.yDp,
            endXDp = endNode.xDp,
            endYDp = endNode.yDp,
        )
        listener?.onConnectionCurvatureChanged(connectionId, curvature)
    }

    private fun drawConnectionHandles(canvas: Canvas) {
        if (mode != EditorMode.EDIT) {
            return
        }

        graph.connections.forEach { connection ->
            val handlePosition =
                GraphGeometry.connectionMidpointPosition(graph, connection) ?: return@forEach
            canvas.drawCircle(
                handlePosition.x,
                handlePosition.y,
                GraphGeometry.CONNECTION_HANDLE_RADIUS_DP / 2.2f,
                connectionHandlePaint,
            )
        }
    }

    private fun drawTemporaryConnection(canvas: Canvas) {
        val tempConnection = temporaryConnection ?: return
        val startNode = graph.findNode(tempConnection.startNodeId) ?: return
        canvas.drawLine(
            startNode.xDp,
            startNode.yDp,
            tempConnection.currentPoint.x,
            tempConnection.currentPoint.y,
            temporaryConnectionPaint,
        )
    }

    private fun cancelPendingLongPressIfMoved(xDp: Float, yDp: Float): Boolean {
        val pending = pendingLongPress ?: return false
        val moved = abs(xDp - pending.origin.x) > touchSlopDp || abs(yDp - pending.origin.y) > touchSlopDp
        if (moved) {
            cancelPendingLongPress()
        }
        return moved
    }

    private fun cancelPendingLongPress() {
        longPressHandler.removeCallbacks(longPressRunnable)
        pendingLongPress = null
    }

    private sealed interface TouchInteraction {
        data object None : TouchInteraction
        data class PendingNode(val nodeId: Long, val offsetX: Float, val offsetY: Float) : TouchInteraction
        data class MovingNode(val nodeId: Long, val offsetX: Float, val offsetY: Float) : TouchInteraction
        data class PendingConnection(val connectionId: Long) : TouchInteraction
        data class AdjustingConnection(val connectionId: Long) : TouchInteraction
        data class CreatingConnection(val startNodeId: Long) : TouchInteraction
    }

    private data class TemporaryConnection(
        val startNodeId: Long,
        val currentPoint: PointF,
    )

    private data class PendingLongPress(
        val target: LongPressTarget,
        val origin: PointF,
    )

    private sealed interface LongPressTarget {
        data class Background(val point: PointF) : LongPressTarget
        data class Node(val nodeId: Long) : LongPressTarget
        data class Connection(val connectionId: Long) : LongPressTarget
    }
}
