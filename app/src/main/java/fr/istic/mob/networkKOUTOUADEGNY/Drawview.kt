package fr.istic.mob.networkKOUTOUADEGNY

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import fr.istic.mob.networkKOUTOUADEGNY.viewmodel.NetworkViewModel

class DrawView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var viewModel: NetworkViewModel? = null

    // --- Paramètres de dessin ---
    private val cellSize = 60f // Taille d'une case de la matrice en pixels
    private val wallPaint = Paint().apply {
        color = Color.GRAY
        style = Paint.Style.FILL
    }
    private val planBackgroundPaint = Paint().apply {
        color = Color.WHITE
    }

    // --- Variables de navigation ---
    private var offsetX = 0f
    private var offsetY = 0f
    private var scaleFactor = 1.0f

    // 1. Détecteur pour le ZOOM (Pincer pour zoomer/dézoomer)
    private val scaleDetector = ScaleGestureDetector(context, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            scaleFactor *= detector.scaleFactor
            scaleFactor = scaleFactor.coerceIn(0.5f, 4.0f) // Limites de zoom
            limitScroll()
            invalidate()
            return true
        }
    })

    // 2. Détecteur pour le SCROLL (Glisser pour se déplacer) et le LONG PRESS
    private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
            offsetX -= distanceX
            offsetY -= distanceY
            limitScroll()
            invalidate()
            return true
        }

        override fun onLongPress(e: MotionEvent) {
            // Calcul de la position réelle sur le plan (indépendante du zoom/scroll)
            val realX = (e.x - offsetX) / scaleFactor
            val realY = (e.y - offsetY) / scaleFactor

            // TODO: Ici nous appellerons la boîte de dialogue pour ajouter un objet
            invalidate()
        }
    })

    private val watermarkPaint = Paint().apply {
        color = Color.parseColor("#E0E0E0") // Gris très clair
        textSize = 80f                      // Gros texte
        textAlign = Paint.Align.CENTER      // Centré sur le point
        isFakeBoldText = true               // Un peu de gras
        alpha = 100                         // Transparence (0-255)
    }

    /**
     * Lie le ViewModel à la vue et force un premier dessin
     */
    fun setViewModel(vm: NetworkViewModel) {
        this.viewModel = vm
        invalidate()
    }

    /**
     * Empêche l'utilisateur de sortir des limites du plan de l'appartement
     */
    private fun limitScroll() {
        val plan = viewModel?.roomPlan ?: return

        // Taille totale du plan en pixels
        val totalWidth = plan.cols * cellSize * scaleFactor
        val totalHeight = plan.rows * cellSize * scaleFactor

        // Calcul des limites minimales (pour ne pas voir de gris si le plan est plus grand que l'écran)
        val minX = if (totalWidth > width) width.toFloat() - totalWidth else 0f
        val minY = if (totalHeight > height) height.toFloat() - totalHeight else 0f

        offsetX = offsetX.coerceIn(minX, 0f)
        offsetY = offsetY.coerceIn(minY, 0f)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        //scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)
        return true
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val plan = viewModel?.roomPlan ?: return

        canvas.drawColor(Color.LTGRAY)

        // Fond gris (hors du plan)
        canvas.drawColor(Color.LTGRAY)
        // Appliquer la navigation (Déplacement)
        canvas.translate(offsetX, offsetY)

        canvas.save()


        // 1. Dessiner le rectangle blanc (Le sol de l'appartement)
        canvas.drawRect(0f, 0f, plan.cols * cellSize, plan.rows * cellSize, planBackgroundPaint)

        // 2. DESSIN DES WATERMARKS (Noms des pièces)
        for (label in plan.labels) {
            canvas.drawText(
                label.name,
                label.col * cellSize,
                label.row * cellSize,
                watermarkPaint
            )
        }
        // 2. Dessiner les murs (La matrice)
        for (r in 0 until plan.rows) {
            for (c in 0 until plan.cols) {
                if (plan.grid[r][c] == 1) { // 1 représente un mur
                    canvas.drawRect(
                        c * cellSize,
                        r * cellSize,
                        (c + 1) * cellSize,
                        (r + 1) * cellSize,
                        wallPaint
                    )
                }
            }
        }

        canvas.restore()
    }
}