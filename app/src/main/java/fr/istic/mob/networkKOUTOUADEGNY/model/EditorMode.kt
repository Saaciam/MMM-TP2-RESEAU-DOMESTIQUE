package fr.istic.mob.networkKOUTOUADEGNY.model

import androidx.annotation.StringRes
import fr.istic.mob.networkKOUTOUADEGNY.R

/**
 * Définit les différents modes d'interaction de l'éditeur de graphe.
 * Chaque mode est associé à une ressource de texte pour l'affichage dans l'interface (UI).
 *
 * @property labelRes L'identifiant de la chaîne de caractères (Resource ID) décrivant le mode.
 */
enum class EditorMode(@StringRes val labelRes: Int) {

    /** Mode pour placer de nouveaux appareils (nœuds) sur le plan. */
    ADD_DEVICE(R.string.mode_add_object),

    /** Mode pour créer des liens de connexion entre deux appareils existants. */
    ADD_CONNECTION(R.string.mode_add_connection),

    /** Mode pour déplacer les appareils ou modifier la courbure des liens. */
    EDIT(R.string.mode_edit);

    companion object {
        /**
         * Convertit une chaîne de caractères (souvent issue d'une sauvegarde ou d'un Intent)
         * en une instance de EditorMode.
         *
         * @param name Le nom du mode (ex: "ADD_DEVICE").
         * @return L'instance correspondante, ou le mode [EDIT] par défaut si non trouvé.
         */
        fun fromName(name: String?): EditorMode {
            // "entries" permet d'itérer sur toutes les valeurs de l'énumération.
            return entries.firstOrNull { it.name == name } ?: EDIT
        }
    }
}