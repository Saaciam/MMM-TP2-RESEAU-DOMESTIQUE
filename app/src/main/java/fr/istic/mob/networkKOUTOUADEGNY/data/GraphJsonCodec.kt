package fr.istic.mob.networkKOUTOUADEGNY.data
import fr.istic.mob.networkKOUTOUADEGNY.model.ApartmentPlan
import fr.istic.mob.networkKOUTOUADEGNY.model.Graph
import fr.istic.mob.networkKOUTOUADEGNY.model.GraphConnection
import fr.istic.mob.networkKOUTOUADEGNY.model.GraphNode
import fr.istic.mob.networkKOUTOUADEGNY.model.NetworkColor

import org.json.JSONArray
import org.json.JSONObject

object GraphJsonCodec {
    fun toJson(graph: Graph): String {
        val root = JSONObject()
        root.put("plan", graph.plan.name)
        root.put("nextNodeId", graph.nextNodeId)
        root.put("nextConnectionId", graph.nextConnectionId)

        val nodesArray = JSONArray()
        graph.nodes.forEach { node ->
            nodesArray.put(
                JSONObject()
                    .put("id", node.id)
                    .put("label", node.label)
                    .put("xDp", node.xDp.toDouble())
                    .put("yDp", node.yDp.toDouble())
                    .put("color", node.color.jsonName),
            )
        }
        root.put("nodes", nodesArray)

        val connectionsArray = JSONArray()
        graph.connections.forEach { connection ->
            connectionsArray.put(
                JSONObject()
                    .put("id", connection.id)
                    .put("startNodeId", connection.startNodeId)
                    .put("endNodeId", connection.endNodeId)
                    .put("label", connection.label)
                    .put("color", connection.color.jsonName)
                    .put("strokeWidthDp", connection.strokeWidthDp.toDouble())
                    .put("curvatureDp", connection.curvatureDp.toDouble()),
            )
        }
        root.put("connections", connectionsArray)
        return root.toString()
    }

    fun fromJson(json: String): Graph {
        val root = JSONObject(json)
        val nodes = buildList {
            val array = root.optJSONArray("nodes") ?: JSONArray()
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    GraphNode(
                        id = item.optLong("id"),
                        label = item.optString("label"),
                        xDp = item.optDouble("xDp").toFloat(),
                        yDp = item.optDouble("yDp").toFloat(),
                        color = NetworkColor.fromJsonName(item.optString("color")),
                    ),
                )
            }
        }

        val connections = buildList {
            val array = root.optJSONArray("connections") ?: JSONArray()
            for (index in 0 until array.length()) {
                val item = array.getJSONObject(index)
                add(
                    GraphConnection(
                        id = item.optLong("id"),
                        startNodeId = item.optLong("startNodeId"),
                        endNodeId = item.optLong("endNodeId"),
                        label = item.optString("label"),
                        color = NetworkColor.fromJsonName(item.optString("color")),
                        strokeWidthDp = item.optDouble("strokeWidthDp", 6.0).toFloat(),
                        curvatureDp = item.optDouble("curvatureDp", 0.0).toFloat(),
                    ),
                )
            }
        }

        val nextNodeId = root.optLong("nextNodeId", (nodes.maxOfOrNull { it.id } ?: 0L) + 1L)
        val nextConnectionId =
            root.optLong("nextConnectionId", (connections.maxOfOrNull { it.id } ?: 0L) + 1L)

        return Graph(
            plan = ApartmentPlan.fromName(root.optString("plan")),
            nodes = nodes,
            connections = connections,
            nextNodeId = nextNodeId,
            nextConnectionId = nextConnectionId,
        )
    }
}
