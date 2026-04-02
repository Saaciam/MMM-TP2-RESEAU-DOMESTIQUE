package fr.istic.mob.networkKOUTOUADEGNY.data

import android.content.Context
import fr.istic.mob.networkKOUTOUADEGNY.model.Graph

/**
 * Classe responsable de la persistance des données sur le stockage interne de l'appareil.
 * Elle permet de sauvegarder et de charger l'état du réseau (graphe) en format JSON.
 */
class GraphStorage(context: Context) {

    // On utilise le contexte de l'application pour éviter les fuites de mémoire
    // si le GraphStorage est conservé plus longtemps qu'une Activity.
    private val appContext = context.applicationContext

    /**
     * Sauvegarde le graphe actuel dans un fichier JSON privé.
     * @param graph L'objet Graph contenant tous les nœuds et connexions.
     */
    fun save(graph: Graph) {
        // openFileOutput crée ou remplace un fichier dans le stockage interne de l'app (/data/data/...)
        // MODE_PRIVATE garantit que le fichier n'est accessible que par cette application.
        appContext.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).bufferedWriter().use { writer ->
            // On convertit l'objet Graph en chaîne de caractères JSON et on l'écrit.
            writer.write(GraphJsonCodec.toJson(graph))
        }
    }

    /**
     * Charge le graphe depuis le stockage interne.
     * @return Le graphe chargé ou null si aucun fichier n'existe ou si une erreur survient.
     */
    fun load(): Graph? {
        // Vérifie d'abord si un fichier de sauvegarde existe
        if (!hasSavedGraph()) {
            return null
        }

        return try {
            // Ouvre le fichier en lecture
            appContext.openFileInput(FILE_NAME).bufferedReader().use { reader ->
                // Lit tout le contenu textuel et le transforme en objet Graph
                GraphJsonCodec.fromJson(reader.readText())
            }
        } catch (e: Exception) {
            // En cas de fichier corrompu ou d'erreur de lecture, on retourne null
            e.printStackTrace()
            null
        }
    }

    /**
     * Vérifie si un fichier de sauvegarde est présent sur le disque.
     */
    fun hasSavedGraph(): Boolean {
        // getFileStreamPath permet d'accéder au chemin du fichier dans le dossier privé de l'app
        return appContext.getFileStreamPath(FILE_NAME).exists()
    }

    companion object {
        /** Nom du fichier de stockage sur le disque */
        private const val FILE_NAME = "saved_network_graph.json"
    }
}