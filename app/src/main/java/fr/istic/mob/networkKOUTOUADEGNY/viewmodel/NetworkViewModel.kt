package fr.istic.mob.networkKOUTOUADEGNY.viewmodel

import androidx.lifecycle.ViewModel
import fr.istic.mob.networkKOUTOUADEGNY.model.Graph
import fr.istic.mob.networkKOUTOUADEGNY.model.RoomPlan

class NetworkViewModel : ViewModel() {
    // Cette instance de Graph ne sera JAMAIS détruite lors d'une rotation
    val graph: Graph = Graph()

    // Le plan de l'appartement
    val roomPlan: RoomPlan = RoomPlan(30, 30)

    // On peut ajouter ici des fonctions pour modifier le graphe
    fun resetNetwork() {
        graph.clear()
    }
}