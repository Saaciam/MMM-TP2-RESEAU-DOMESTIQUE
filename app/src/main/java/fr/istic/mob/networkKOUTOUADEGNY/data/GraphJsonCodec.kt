package fr.istic.mob.networkKOUTOUADEGNY.data

import android.util.Log
import fr.istic.mob.networkKOUTOUADEGNY.model.ApartmentPlan
import fr.istic.mob.networkKOUTOUADEGNY.model.Graph
import fr.istic.mob.networkKOUTOUADEGNY.model.GraphConnection
import fr.istic.mob.networkKOUTOUADEGNY.model.GraphNode
import fr.istic.mob.networkKOUTOUADEGNY.model.NetworkColor
import org.json.JSONArray
import org.json.JSONObject

/**
 * Cet objet sert à transformer un Graph en texte (JSON) pour l'enregistrer,
 * et à transformer ce texte à nouveau en objet Graph pour le charger.
 */
object GraphJsonCodec {

    private const val TAG = "GraphJsonCodec"

    /**
     * Convertit l'objet Graph en une chaîne de caractères au format JSON.
     */
    fun toJson(graph: Graph): String {
        return try {
            val root = JSONObject()

            // On enregistre les informations générales du graphe
            root.put("plan", graph.plan.name)
            root.put("nextNodeId", graph.nextNodeId)
            root.put("nextConnectionId", graph.nextConnectionId)

            // On crée une liste JSON pour les nœuds (les points)
            val nodesArray = JSONArray()
            graph.nodes.forEach { node ->
                val nodeJson = JSONObject()
                    .put("id", node.id)
                    .put("label", node.label)
                    .put("xDp", node.xDp.toDouble())
                    .put("yDp", node.yDp.toDouble())
                    .put("color", node.color.jsonName)
                nodesArray.put(nodeJson)
            }
            root.put("nodes", nodesArray)

            // On crée une liste JSON pour les connexions (les traits)
            val connectionsArray = JSONArray()
            graph.connections.forEach { connection ->
                val connJson = JSONObject()
                    .put("id", connection.id)
                    .put("startNodeId", connection.startNodeId)
                    .put("endNodeId", connection.endNodeId)
                    .put("label", connection.label)
                    .put("color", connection.color.jsonName)
                    .put("strokeWidthDp", connection.strokeWidthDp.toDouble())
                    .put("curvatureDp", connection.curvatureDp.toDouble())
                connectionsArray.put(connJson)
            }
            root.put("connections", connectionsArray)

            // On retourne le tout sous forme de texte
            root.toString()

        } catch (e: Exception) {
            Log.e(TAG, "Erreur lors de la création du JSON", e)
            ""
        }
    }

    /**
     * Lit une chaîne JSON et reconstruit l'objet Graph.
     * Retourne null si le texte est invalide.
     */
    fun fromJson(json: String): Graph? {
        if (json.isBlank()) return null

        return try {
            val root = JSONObject(json)

            // 1. On récupère la liste des nœuds
            val nodes = buildList {
                val array = root.optJSONArray("nodes") ?: JSONArray()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(GraphNode(
                        id = item.optLong("id"),
                        label = item.optString("label", ""),
                        xDp = item.optDouble("xDp", 0.0).toFloat(),
                        yDp = item.optDouble("yDp", 0.0).toFloat(),
                        color = NetworkColor.fromJsonName(item.optString("color"))
                    ))
                }
            }

            // 2. On récupère la liste des connexions
            val connections = buildList {
                val array = root.optJSONArray("connections") ?: JSONArray()
                for (i in 0 until array.length()) {
                    val item = array.getJSONObject(i)
                    add(GraphConnection(
                        id = item.optLong("id"),
                        startNodeId = item.optLong("startNodeId"),
                        endNodeId = item.optLong("endNodeId"),
                        label = item.optString("label", ""),
                        color = NetworkColor.fromJsonName(item.optString("color")),
                        strokeWidthDp = item.optDouble("strokeWidthDp", 6.0).toFloat(),
                        curvatureDp = item.optDouble("curvatureDp", 0.0).toFloat()
                    ))
                }
            }

            // 3. On récupère les compteurs d'ID (ou on les recalcule s'ils manquent)
            val nextNodeId = root.optLong("nextNodeId", (nodes.maxOfOrNull { it.id } ?: 0L) + 1L)
            val nextConnectionId = root.optLong("nextConnectionId", (connections.maxOfOrNull { it.id } ?: 0L) + 1L)

            // 4. On crée l'objet Graph final
            Graph(
                plan = ApartmentPlan.fromName(root.optString("plan")),
                nodes = nodes,
                connections = connections,
                nextNodeId = nextNodeId,
                nextConnectionId = nextConnectionId
            )

        } catch (e: Exception) {
            // Si le JSON est mal formé, on affiche une erreur au lieu de faire planter l'app
            Log.e(TAG, "Erreur lors de la lecture du JSON", e)
            null
        }
    }
}