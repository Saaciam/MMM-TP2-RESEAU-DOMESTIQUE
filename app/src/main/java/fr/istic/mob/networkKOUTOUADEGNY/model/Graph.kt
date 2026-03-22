package fr.istic.mob.networkKOUTOUADEGNY.model

data class GraphNode(
    val id: Long,
    val label: String,
    val xDp: Float,
    val yDp: Float,
    val color: NetworkColor,
)

data class GraphConnection(
    val id: Long,
    val startNodeId: Long,
    val endNodeId: Long,
    val label: String,
    val color: NetworkColor,
    val strokeWidthDp: Float = 6f,
    val curvatureDp: Float = 0f,
)

data class Graph(
    val plan: ApartmentPlan = ApartmentPlan.STUDIO,
    val nodes: List<GraphNode> = emptyList(),
    val connections: List<GraphConnection> = emptyList(),
    val nextNodeId: Long = 1L,
    val nextConnectionId: Long = 1L,
) {
    fun findNode(nodeId: Long): GraphNode? = nodes.firstOrNull { it.id == nodeId }
}