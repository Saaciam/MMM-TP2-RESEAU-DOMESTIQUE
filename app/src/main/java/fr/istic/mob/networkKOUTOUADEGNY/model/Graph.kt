package fr.istic.mob.networkKOUTOUADEGNY.model

import android.graphics.Color
import android.graphics.PointF

// 1. Représente un objet connecté (ex: TV, Imprimante)
data class NetworkObject(
    var label: String,
    var position: PointF, // Coordonnées X, Y
    var color: Int = Color.BLUE,
    val id: Long = System.currentTimeMillis() // Identifiant unique
)

// 2. Représente une connexion entre deux objets
data class Connection(
    val from: NetworkObject,
    val to: NetworkObject,
    var label: String,
    var color: Int = Color.BLACK,
    var thickness: Float = 5f,
    var curvature: Float = 0f // Pour gérer l'arc plus tard
)

// 3. Le Modèle principal demandé par le sujet
class Graph {
    val objects = mutableListOf<NetworkObject>()
    val connections = mutableListOf<Connection>()

    fun clear() {
        objects.clear()
        connections.clear()
    }
}