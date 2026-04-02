package fr.istic.mob.networkKOUTOUADEGNY.model

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import fr.istic.mob.networkKOUTOUADEGNY.R

/**
 * Enumération définissant la palette de couleurs disponible pour les éléments du graphe.
 * Chaque couleur lie une identité JSON, une valeur de rendu et un label traduisible.
 *
 * @property jsonName Identifiant textuel utilisé pour la sauvegarde/chargement JSON.
 * @property colorInt Valeur entière de la couleur (format ARGB) pour le Canvas.
 * @property labelRes Identifiant de la chaîne de caractères (R.string) pour l'affichage UI.
 */
enum class NetworkColor(
    val jsonName: String,
    @ColorInt val colorInt: Int,
    @StringRes val labelRes: Int,
) {
    // Définition de la palette avec des couleurs Material Design
    RED("red", Color.parseColor("#D32F2F"), R.string.color_red),
    GREEN("green", Color.parseColor("#2E7D32"), R.string.color_green),
    BLUE("blue", Color.parseColor("#1565C0"), R.string.color_blue),
    ORANGE("orange", Color.parseColor("#EF6C00"), R.string.color_orange),
    CYAN("cyan", Color.parseColor("#00838F"), R.string.color_cyan),
    MAGENTA("magenta", Color.parseColor("#8E24AA"), R.string.color_magenta),
    BLACK("black", Color.parseColor("#212121"), R.string.color_black);

    companion object {
        /**
         * Convertit une chaîne de caractères issue du JSON en instance de NetworkColor.
         *
         * @param name Le nom de la couleur (ex: "red").
         * @return La couleur correspondante ou BLUE par défaut si non trouvée.
         */
        fun fromJsonName(name: String?): NetworkColor {
            // "entries" est la méthode moderne (Kotlin 1.9+) pour itérer sur les enums
            return entries.firstOrNull { it.jsonName == name } ?: BLUE
        }
    }
}