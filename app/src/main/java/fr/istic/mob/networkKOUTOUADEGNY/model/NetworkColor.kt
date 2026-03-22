package fr.istic.mob.networkKOUTOUADEGNY.model

import android.graphics.Color
import androidx.annotation.ColorInt
import androidx.annotation.StringRes
import fr.istic.mob.networkKOUTOUADEGNY.R

enum class NetworkColor(
    val jsonName: String,
    @ColorInt val colorInt: Int,
    @StringRes val labelRes: Int,
) {
    RED("red", Color.parseColor("#D32F2F"), R.string.color_red),
    GREEN("green", Color.parseColor("#2E7D32"), R.string.color_green),
    BLUE("blue", Color.parseColor("#1565C0"), R.string.color_blue),
    ORANGE("orange", Color.parseColor("#EF6C00"), R.string.color_orange),
    CYAN("cyan", Color.parseColor("#00838F"), R.string.color_cyan),
    MAGENTA("magenta", Color.parseColor("#8E24AA"), R.string.color_magenta),
    BLACK("black", Color.parseColor("#212121"), R.string.color_black);

    companion object {
        fun fromJsonName(name: String?): NetworkColor {
            return entries.firstOrNull { it.jsonName == name } ?: BLUE
        }
    }
}
