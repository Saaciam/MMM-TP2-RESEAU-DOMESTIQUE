package fr.istic.mob.networkKOUTOUADEGNY.model

import androidx.annotation.StringRes
import fr.istic.mob.networkKOUTOUADEGNY.R

enum class EditorMode(@StringRes val labelRes: Int) {
    ADD_DEVICE(R.string.mode_add_object),
    ADD_CONNECTION(R.string.mode_add_connection),
    EDIT(R.string.mode_edit);

    companion object {
        fun fromName(name: String?): EditorMode {
            return entries.firstOrNull { it.name == name } ?: EDIT
        }
    }
}
