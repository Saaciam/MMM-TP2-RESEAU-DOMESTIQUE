package fr.istic.mob.networkKOUTOUADEGNY.model

/**
 * Représente un appareil ou un point d'accès (nœud) dans le réseau.
 * Les coordonnées sont stockées en DP pour garantir une mise à l'échelle automatique.
 */
data class GraphNode(
    val id: Long,               // Identifiant unique du nœud
    val label: String,          // Nom affiché de l'appareil
    val xDp: Float,             // Position horizontale (en DP) sur le plan
    val yDp: Float,             // Position verticale (en DP) sur le plan
    val color: NetworkColor,    // Couleur visuelle du nœud
)

/**
 * Représente un lien physique ou logique entre deux nœuds du réseau.
 */
data class GraphConnection(
    val id: Long,               // Identifiant unique du lien
    val startNodeId: Long,      // ID du nœud de départ
    val endNodeId: Long,        // ID du nœud d'arrivée
    val label: String,          // Nom/Description du lien
    val color: NetworkColor,    // Couleur de la ligne
    val strokeWidthDp: Float = 6f, // Épaisseur du trait (par défaut 6 DP)
    val curvatureDp: Float = 0f,   // Niveau de courbe (0 = ligne droite)
)

/**
 * Classe racine regroupant l'ensemble des données du réseau.
 * Elle contient le plan de fond, les éléments du graphe et les compteurs d'ID.
 */
data class Graph(
    // Le plan d'appartement servant de fond (par défaut : Studio)
    val plan: ApartmentPlan = ApartmentPlan.STUDIO,

    // Liste de tous les appareils présents sur le plan
    val nodes: List<GraphNode> = emptyList(),

    // Liste de toutes les connexions entre les appareils
    val connections: List<GraphConnection> = emptyList(),

    // Compteurs pour générer automatiquement des IDs uniques lors de l'ajout
    val nextNodeId: Long = 1L,
    val nextConnectionId: Long = 1L,
) {
    /**
     * Recherche un nœud spécifique par son identifiant.
     * @return Le nœud trouvé ou null s'il n'existe pas.
     */
    fun findNode(nodeId: Long): GraphNode? = nodes.firstOrNull { it.id == nodeId }
}