package fr.istic.mob.networkKOUTOUADEGNY.ui

import android.util.Log
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import fr.istic.mob.networkKOUTOUADEGNY.model.ApartmentPlan
import fr.istic.mob.networkKOUTOUADEGNY.model.EditorMode
import fr.istic.mob.networkKOUTOUADEGNY.model.Graph
import fr.istic.mob.networkKOUTOUADEGNY.model.GraphConnection
import fr.istic.mob.networkKOUTOUADEGNY.model.GraphNode
import fr.istic.mob.networkKOUTOUADEGNY.model.NetworkColor
import fr.istic.mob.networkKOUTOUADEGNY.data.GraphJsonCodec

/**
 * Liste des résultats possibles quand on essaie de créer une connexion.
 * Cela permet d'afficher le bon message d'erreur à l'utilisateur.
 */
enum class ConnectionCreationResult {
    SUCCESS,        // Réussi
    SAME_NODE,      // Impossible de relier un point à lui-même
    DUPLICATE,      // Cette connexion existe déjà
    MISSING_NODE,   // Un des points n'existe plus
}

/**
 * Le ViewModel est le "cerveau" de l'écran.
 * Il gère les données (le graphe) et survit quand on tourne le téléphone.
 */
class NetworkViewModel : ViewModel() {

    // Le graphe (points et traits). On utilise StateFlow pour que l'interface
    // se mette à jour automatiquement quand le graphe change.
    private val _graph = MutableStateFlow(Graph())
    val graph: StateFlow<Graph> = _graph.asStateFlow()

    // Le mode actuel (Modification ou Visualisation)
    private val _mode = MutableStateFlow(EditorMode.EDIT)
    val mode: StateFlow<EditorMode> = _mode.asStateFlow()

    // Sécurité pour ne pas charger les données deux fois
    private var initialized = false

    /**
     * Charge les données sauvegardées (au démarrage de l'app).
     */
    fun initialize(savedGraphJson: String?, savedModeName: String?) {
        if (initialized) return
        initialized = true

        // 1. On restaure le graphe depuis le texte JSON
        if (!savedGraphJson.isNullOrBlank()) {
            val decodedGraph = GraphJsonCodec.fromJson(savedGraphJson)
            if (decodedGraph != null) {
                // On nettoie le graphe (sanitize) pour être sûr qu'il est valide
                _graph.value = sanitizeGraph(decodedGraph)
            } else {
                Log.w("NetworkViewModel", "Impossible de charger le graphe, format invalide.")
            }
        }

        // 2. On restaure le mode (Édition ou Vue)
        _mode.value = EditorMode.fromName(savedModeName)
    }

    // --- Fonctions utilitaires pour obtenir l'état actuel ---
    fun graphSnapshot(): Graph = _graph.value
    fun modeSnapshot(): EditorMode = _mode.value

    /**
     * Change le mode (ex: passer de 'Modifier' à 'Regarder')
     */
    fun setMode(mode: EditorMode) {
        _mode.value = mode
    }

    /**
     * Change l'image de fond (le plan de l'appartement).
     * Si on change de plan, on doit vérifier que les points ne sortent pas des limites.
     */
    fun setPlan(plan: ApartmentPlan) {
        val current = _graph.value
        if (current.plan == plan) return

        // On ajuste la position de chaque point pour qu'il reste dans le nouveau cadre
        val clampedNodes = current.nodes.map { node ->
            val clampedPosition = GraphGeometry.clampNodePosition(plan, node.xDp, node.yDp)
            node.copy(xDp = clampedPosition.x, yDp = clampedPosition.y)
        }

        _graph.value = sanitizeGraph(current.copy(plan = plan, nodes = clampedNodes))
    }

    /**
     * Ajoute un nouveau point (Nœud) sur la carte.
     */
    fun addNode(label: String, color: NetworkColor, xDp: Float, yDp: Float) {
        val current = _graph.value
        // On s'assure que le point ne sort pas de l'écran
        val clampedPosition = GraphGeometry.clampNodePosition(current.plan, xDp, yDp)

        val newNode = GraphNode(
            id = current.nextNodeId, // On utilise l'ID suivant disponible
            label = label.trim(),
            xDp = clampedPosition.x,
            yDp = clampedPosition.y,
            color = color,
        )
        // On crée une copie du graphe avec le nouveau point en plus
        _graph.value = current.copy(
            nodes = current.nodes + newNode,
            nextNodeId = current.nextNodeId + 1L, // On prépare l'ID pour le prochain
        )
    }

    /**
     * Modifie le nom ou la couleur d'un point existant.
     */
    fun updateNode(nodeId: Long, label: String, color: NetworkColor) {
        val current = _graph.value
        _graph.value = current.copy(
            nodes = current.nodes.map { node ->
                if (node.id == nodeId) node.copy(label = label.trim(), color = color)
                else node
            },
        )
    }

    /**
     * Déplace un point sur la carte.
     */
    fun moveNode(nodeId: Long, xDp: Float, yDp: Float) {
        val current = _graph.value
        val clampedPosition = GraphGeometry.clampNodePosition(current.plan, xDp, yDp)
        val updatedGraph = current.copy(
            nodes = current.nodes.map { node ->
                if (node.id == nodeId) node.copy(xDp = clampedPosition.x, yDp = clampedPosition.y)
                else node
            },
        )
        _graph.value = sanitizeGraph(updatedGraph)
    }

    /**
     * Supprime un point et toutes les connexions qui y étaient reliées.
     */
    fun deleteNode(nodeId: Long) {
        val current = _graph.value
        _graph.value = current.copy(
            nodes = current.nodes.filterNot { it.id == nodeId },
            // On retire aussi les traits qui partent ou arrivent sur ce point
            connections = current.connections.filterNot {
                it.startNodeId == nodeId || it.endNodeId == nodeId
            },
        )
    }

    /**
     * Crée un trait (Connexion) entre deux points.
     */
    fun addConnection(
        startNodeId: Long,
        endNodeId: Long,
        label: String,
        color: NetworkColor,
        strokeWidthDp: Float,
    ): ConnectionCreationResult {
        val current = _graph.value

        // Vérifications de base (Est-ce que les points existent ? Sont-ils différents ?)
        if (current.findNode(startNodeId) == null || current.findNode(endNodeId) == null)
            return ConnectionCreationResult.MISSING_NODE
        if (startNodeId == endNodeId) return ConnectionCreationResult.SAME_NODE

        // Vérifier si une connexion existe déjà entre ces deux points
        val duplicateExists = current.connections.any {
            (it.startNodeId == startNodeId && it.endNodeId == endNodeId) ||
                    (it.startNodeId == endNodeId && it.endNodeId == startNodeId)
        }
        if (duplicateExists) return ConnectionCreationResult.DUPLICATE

        // On limite l'épaisseur du trait entre les bornes autorisées
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

    /**
     * Met à jour les réglages d'une connexion (nom, couleur, épaisseur).
     */
    fun updateConnection(connectionId: Long, label: String, color: NetworkColor, strokeWidthDp: Float) {
        val current = _graph.value
        val updatedGraph = current.copy(
            connections = current.connections.map { conn ->
                if (conn.id == connectionId) {
                    conn.copy(
                        label = label.trim(),
                        color = color,
                        strokeWidthDp = strokeWidthDp.coerceIn(MIN_CONNECTION_STROKE_DP, MAX_CONNECTION_STROKE_DP)
                    )
                } else conn
            },
        )
        _graph.value = sanitizeGraph(updatedGraph)
    }

    /**
     * Change la courbe (courbure) du trait.
     */
    fun updateConnectionCurvature(connectionId: Long, curvatureDp: Float) {
        val current = _graph.value
        val updatedGraph = current.copy(
            connections = current.connections.map { conn ->
                if (conn.id == connectionId) {
                    val bounded = curvatureDp.coerceIn(-GraphGeometry.MAX_CURVATURE_DP, GraphGeometry.MAX_CURVATURE_DP)
                    conn.copy(curvatureDp = bounded)
                } else conn
            },
        )
        _graph.value = sanitizeGraph(updatedGraph)
    }

    /**
     * Supprime une connexion.
     */
    fun deleteConnection(connectionId: Long) {
        val current = _graph.value
        _graph.value = current.copy(
            connections = current.connections.filterNot { it.id == connectionId },
        )
    }

    /**
     * Efface tout le dessin mais garde le même plan.
     */
    fun resetGraph() {
        val currentPlan = _graph.value.plan
        _graph.value = Graph(plan = currentPlan)
    }

    /**
     * Met à jour l'état complet du graphe avec une version chargée depuis le stockage.
     * @param restoredGraph Le graphe récupéré du fichier JSON.
     */
    fun restoreGraph(restoredGraph: Graph) {
        // On met à jour le StateFlow interne du ViewModel.
        // Cela va déclencher automatiquement le collect dans MainActivity et redessiner la vue.
        _graph.value = restoredGraph
    }

    /**
     * Fonction de nettoyage : parcourt toutes les connexions pour s'assurer qu'elles
     * sont toujours valides (points existants, etc.).
     */
    private fun sanitizeGraph(graph: Graph): Graph {
        val sanitizedConnections = graph.connections.mapNotNull { connection ->
            sanitizeConnection(graph, connection)
        }
        return graph.copy(connections = sanitizedConnections)
    }

    /**
     * Nettoie une seule connexion.
     */
    private fun sanitizeConnection(graph: Graph, connection: GraphConnection): GraphConnection? {
        val startNode = graph.findNode(connection.startNodeId) ?: return null
        val endNode = graph.findNode(connection.endNodeId) ?: return null
        if (startNode.id == endNode.id) return null

        val sanitizedStrokeWidth = connection.strokeWidthDp.coerceIn(MIN_CONNECTION_STROKE_DP, MAX_CONNECTION_STROKE_DP)
        val distance = GraphGeometry.distanceBetweenNodes(startNode, endNode)

        // Si les points sont trop proches, on force le trait à être droit (pas de courbe)
        val sanitizedCurvature = if (distance < GraphGeometry.MIN_CURVE_SPAN_DP) 0f
        else connection.curvatureDp.coerceIn(-GraphGeometry.MAX_CURVATURE_DP, GraphGeometry.MAX_CURVATURE_DP)

        return connection.copy(
            strokeWidthDp = sanitizedStrokeWidth,
            curvatureDp = sanitizedCurvature,
        )
    }

    companion object {
        // Limites pour l'épaisseur des traits (en DP)
        const val MIN_CONNECTION_STROKE_DP = 2f
        const val MAX_CONNECTION_STROKE_DP = 18f
    }
}