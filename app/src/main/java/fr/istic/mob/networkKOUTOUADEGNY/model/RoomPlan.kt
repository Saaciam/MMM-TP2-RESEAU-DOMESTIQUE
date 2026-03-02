package fr.istic.mob.networkKOUTOUADEGNY.model

// Une petite classe interne ou data class pour stocker les noms des pièces
data class RoomLabel(val name: String, val col: Int, val row: Int)

class RoomPlan(val rows: Int, val cols: Int) {
    // 0 = Vide, 1 = Mur
    /*val grid: Array<IntArray> = Array(rows) { IntArray(cols) { 0 } }

    init {
        // Exemple : On crée un contour de murs pour tester
        for (i in 0 until cols) {
            grid[0][i] = 1 // Mur haut
            grid[rows - 1][i] = 1 // Mur bas
        }
        for (i in 0 until rows) {
            grid[i][0] = 1 // Mur gauche
            grid[i][cols - 1] = 1 // Mur droit
        }
    }*/
    // 0 = Vide, 1 = Mur, 2 = Porte/Fenêtre
    val grid: Array<IntArray> = Array(rows) { IntArray(cols) { 0 } }

    // Liste des étiquettes de pièces
    val labels = mutableListOf<RoomLabel>()

    init {
        // --- On trace un appartement avec 2 pièces ---
        // Contour extérieur
        for (i in 0 until cols) {
            grid[0][i] = 1; grid[rows - 1][i] = 1
        }
        for (i in 0 until rows) {
            grid[i][0] = 1; grid[i][cols - 1] = 1
        }

        // Mur de séparation au milieu (ligne 15)
        for (i in 0 until cols) {
            if (i != 5 && i != 15 && i != 30) { // On laisse un trou aux colonne 5 et 25 pour une porte
                grid[15][i] = 1
            }

        }

        // Mur vertical de séparation
        for (i in 0..15) {
            grid[i][10] = 1
        }

        // --- DÉFINITION DES PIÈCES (Watermarks) ---
        // On place le texte à peu près au centre de chaque zone
        labels.add(RoomLabel("CHAMBRE", 5, 7))       // Zone haut-gauche
        labels.add(RoomLabel("CUISINE", 17, 7))     // Zone haut-droite
        labels.add(RoomLabel("SALON", 7, 22))     // Zone du bas

    }
}