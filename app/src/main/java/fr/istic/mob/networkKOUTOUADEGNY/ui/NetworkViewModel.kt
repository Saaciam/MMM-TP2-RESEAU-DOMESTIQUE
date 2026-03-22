package fr.istic.mob.networkKOUTOUADEGNY.ui

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.text.compareTo
import kotlin.unaryMinus
import fr.istic.mob.networkKOUTOUADEGNY.model.ApartmentPlan
import fr.istic.mob.networkKOUTOUADEGNY.model.EditorMode
import fr.istic.mob.networkKOUTOUADEGNY.model.Graph
import fr.istic.mob.networkKOUTOUADEGNY.model.GraphConnection
import fr.istic.mob.networkKOUTOUADEGNY.model.GraphNode
import fr.istic.mob.networkKOUTOUADEGNY.model.NetworkColor
import fr.istic.mob.networkKOUTOUADEGNY.data.GraphJsonCodec

enum class ConnectionCreationResult {
    SUCCESS,
    SAME_NODE,
    DUPLICATE,
    MISSING_NODE,
}

class NetworkViewModel : ViewModel() {
    private val _graph = MutableStateFlow(Graph())
    val graph: StateFlow<Graph> = _graph.asStateFlow()

    private val _mode = MutableStateFlow(EditorMode.EDIT)
    val mode: StateFlow<EditorMode> = _mode.asStateFlow()

    private var initialized = false

    fun initialize(savedGraphJson: String?, savedModeName: String?) {
        if (initialized) {
            return
        }
        initialized = true

        if (!savedGraphJson.isNullOrBlank()) {
            _graph.value = GraphJsonCodec.fromJson(savedGraphJson)
        }

        _mode.value = EditorMode.fromName(savedModeName)
    }

    fun graphSnapshot(): Graph = _graph.value

    fun modeSnapshot(): EditorMode = _mode.value

    fun setMode(mode: EditorMode) {
        _mode.value = mode
    }

    fun setPlan(plan: ApartmentPlan) {
        val current = _graph.value
        if (current.plan == plan) {
            return
        }

        val clampedNodes = current.nodes.map { node ->
            val clampedPosition = GraphGeometry.clampNodePosition(plan, node.xDp, node.yDp)
            node.copy(xDp = clampedPosition.x, yDp = clampedPosition.y)
        }

        _graph.value = sanitizeGraph(current.copy(plan = plan, nodes = clampedNodes))
    }

    fun restoreGraph(graph: Graph) {
        _graph.value = sanitizeGraph(graph)
    }

    fun addNode(label: String, color: NetworkColor, xDp: Float, yDp: Float) {
        val current = _graph.value
        val clampedPosition = GraphGeometry.clampNodePosition(current.plan, xDp, yDp)
        val newNode = GraphNode(
            id = current.nextNodeId,
            label = label.trim(),
            xDp = clampedPosition.x,
            yDp = clampedPosition.y,
            color = color,
        )
        _graph.value = current.copy(
            nodes = current.nodes + newNode,
            nextNodeId = current.nextNodeId + 1L,
        )
    }

    fun updateNode(nodeId: Long, label: String, color: NetworkColor) {
        val current = _graph.value
        _graph.value = current.copy(
            nodes = current.nodes.map { node ->
                if (node.id == nodeId) {
                    node.copy(label = label.trim(), color = color)
                } else {
                    node
                }
            },
        )
    }

    fun moveNode(nodeId: Long, xDp: Float, yDp: Float) {
        val current = _graph.value
        val clampedPosition = GraphGeometry.clampNodePosition(current.plan, xDp, yDp)
        val updatedGraph = current.copy(
            nodes = current.nodes.map { node ->
                if (node.id == nodeId) {
                    node.copy(xDp = clampedPosition.x, yDp = clampedPosition.y)
                } else {
                    node
                }
            },
        )
        _graph.value = sanitizeGraph(updatedGraph)
    }

    fun deleteNode(nodeId: Long) {
        val current = _graph.value
        _graph.value = current.copy(
            nodes = current.nodes.filterNot { it.id == nodeId },
            connections = current.connections.filterNot {
                it.startNodeId == nodeId || it.endNodeId == nodeId
            },
        )
    }

    fun addConnection(
        startNodeId: Long,
        endNodeId: Long,
        label: String,
        color: NetworkColor,
        strokeWidthDp: Float,
    ): ConnectionCreationResult {
        val current = _graph.value
        val startNode = current.findNode(startNodeId)
        val endNode = current.findNode(endNodeId)
        if (startNode == null || endNode == null) {
            return ConnectionCreationResult.MISSING_NODE
        }
        if (startNodeId == endNodeId) {
            return ConnectionCreationResult.SAME_NODE
        }

        val duplicateExists = current.connections.any { connection ->
            val sameForward =
                connection.startNodeId == startNodeId && connection.endNodeId == endNodeId
            val sameBackward =
                connection.startNodeId == endNodeId && connection.endNodeId == startNodeId
            sameForward || sameBackward
        }
        if (duplicateExists) {
            return ConnectionCreationResult.DUPLICATE
        }

        val sanitizedStrokeWidth = strokeWidthDp.coerceIn(MIN_CONNECTION_STROKE_DP, MAX_CONNECTION_STROKE_DP)
        val newConnection = GraphConnection(
            id = current.nextConnectionId,
            startNodeId = startNodeId,
            endNodeId = endNodeId,
            label = label.trim(),
            color = color,
            strokeWidthDp = sanitizedStrokeWidth,
        )
        _graph.value = current.copy(
            connections = current.connections + newConnection,
            nextConnectionId = current.nextConnectionId + 1L,
        )
        return ConnectionCreationResult.SUCCESS
    }

    fun updateConnection(
        connectionId: Long,
        label: String,
        color: NetworkColor,
        strokeWidthDp: Float,
    ) {
        val current = _graph.value
        val updatedGraph = current.copy(
            connections = current.connections.map { connection ->
                if (connection.id == connectionId) {
                    connection.copy(
                        label = label.trim(),
                        color = color,
                        strokeWidthDp = strokeWidthDp.coerceIn(
                            MIN_CONNECTION_STROKE_DP,
                            MAX_CONNECTION_STROKE_DP,
                        ),
                    )
                } else {
                    connection
                }
            },
        )
        _graph.value = sanitizeGraph(updatedGraph)
    }

    fun updateConnectionCurvature(connectionId: Long, curvatureDp: Float) {
        val current = _graph.value
        val updatedGraph = current.copy(
            connections = current.connections.map { connection ->
                if (connection.id == connectionId) {
                    val boundedCurvature = curvatureDp.coerceIn(
                        -GraphGeometry.MAX_CURVATURE_DP,
                        GraphGeometry.MAX_CURVATURE_DP,
                    )
                    connection.copy(curvatureDp = boundedCurvature)
                } else {
                    connection
                }
            },
        )
        _graph.value = sanitizeGraph(updatedGraph)
    }

    fun deleteConnection(connectionId: Long) {
        val current = _graph.value
        _graph.value = current.copy(
            connections = current.connections.filterNot { it.id == connectionId },
        )
    }

    fun resetGraph() {
        val currentPlan = _graph.value.plan
        _graph.value = Graph(plan = currentPlan)
    }

    private fun sanitizeGraph(graph: Graph): Graph {
        val sanitizedConnections = graph.connections.mapNotNull { connection ->
            sanitizeConnection(graph, connection)
        }
        return graph.copy(connections = sanitizedConnections)
    }

    private fun sanitizeConnection(graph: Graph, connection: GraphConnection): GraphConnection? {
        val startNode = graph.findNode(connection.startNodeId) ?: return null
        val endNode = graph.findNode(connection.endNodeId) ?: return null
        if (startNode.id == endNode.id) {
            return null
        }

        val sanitizedStrokeWidth = connection.strokeWidthDp.coerceIn(
            MIN_CONNECTION_STROKE_DP,
            MAX_CONNECTION_STROKE_DP,
        )
        val distance = GraphGeometry.distanceBetweenNodes(startNode, endNode)
        val sanitizedCurvature =
            if (distance < GraphGeometry.MIN_CURVE_SPAN_DP) 0f else connection.curvatureDp.coerceIn(
                -GraphGeometry.MAX_CURVATURE_DP,
                GraphGeometry.MAX_CURVATURE_DP,
            )

        return connection.copy(
            strokeWidthDp = sanitizedStrokeWidth,
            curvatureDp = sanitizedCurvature,
        )
    }

    companion object {
        const val MIN_CONNECTION_STROKE_DP = 2f
        const val MAX_CONNECTION_STROKE_DP = 18f
    }
}
