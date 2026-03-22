package fr.istic.mob.networkKOUTOUADEGNY.model
import androidx.annotation.StringRes
import fr.istic.mob.networkKOUTOUADEGNY.R

// Une petite classe interne ou data class pour stocker les noms des pièces
data class PlanWall(
    val startXDp: Float,
    val startYDp: Float,
    val endXDp: Float,
    val endYDp: Float,
)

data class PlanLabel(
    val xDp: Float,
    val yDp: Float,
    @StringRes val textRes: Int,
)

enum class ApartmentPlan(
    @StringRes val labelRes: Int,
    val widthDp: Int,
    val heightDp: Int,
    val walls: List<PlanWall>,
    val roomLabels: List<PlanLabel>,
) {
    STUDIO(
        labelRes = R.string.plan_studio,
        widthDp = 1400,
        heightDp = 920,
        walls = listOf(
            PlanWall(540f, 80f, 540f, 360f),
            PlanWall(540f, 360f, 940f, 360f),
            PlanWall(940f, 360f, 940f, 820f),
            PlanWall(940f, 540f, 1280f, 540f),
        ),
        roomLabels = listOf(
            PlanLabel(280f, 180f, R.string.room_entrance),
            PlanLabel(260f, 560f, R.string.room_living),
            PlanLabel(740f, 170f, R.string.room_bathroom),
            PlanLabel(1160f, 250f, R.string.room_bedroom),
            PlanLabel(1160f, 700f, R.string.room_kitchen),
        ),
    ),
    FAMILY(
        labelRes = R.string.plan_family,
        widthDp = 1720,
        heightDp = 1120,
        walls = listOf(
            PlanWall(620f, 80f, 620f, 460f),
            PlanWall(620f, 460f, 1040f, 460f),
            PlanWall(1040f, 80f, 1040f, 460f),
            PlanWall(1300f, 80f, 1300f, 780f),
            PlanWall(620f, 760f, 1300f, 760f),
            PlanWall(620f, 760f, 620f, 1040f),
            PlanWall(1040f, 760f, 1040f, 1040f),
        ),
        roomLabels = listOf(
            PlanLabel(290f, 220f, R.string.room_garage),
            PlanLabel(290f, 760f, R.string.room_living),
            PlanLabel(810f, 250f, R.string.room_bedroom_one),
            PlanLabel(1160f, 250f, R.string.room_bedroom_two),
            PlanLabel(1500f, 260f, R.string.room_kitchen),
            PlanLabel(820f, 910f, R.string.room_bathroom),
            PlanLabel(1180f, 920f, R.string.room_office),
        ),
    ),
    LOFT(
        labelRes = R.string.plan_loft,
        widthDp = 1560,
        heightDp = 980,
        walls = listOf(
            PlanWall(360f, 80f, 360f, 340f),
            PlanWall(360f, 340f, 760f, 340f),
            PlanWall(1100f, 80f, 1100f, 520f),
            PlanWall(760f, 640f, 1450f, 640f),
            PlanWall(760f, 340f, 760f, 900f),
        ),
        roomLabels = listOf(
            PlanLabel(180f, 190f, R.string.room_storage),
            PlanLabel(560f, 190f, R.string.room_bathroom),
            PlanLabel(1280f, 210f, R.string.room_bedroom),
            PlanLabel(500f, 560f, R.string.room_living),
            PlanLabel(1180f, 790f, R.string.room_open_space),
        ),
    );

    companion object {
        fun fromName(name: String?): ApartmentPlan {
            return entries.firstOrNull { it.name == name } ?: STUDIO
        }
    }
}