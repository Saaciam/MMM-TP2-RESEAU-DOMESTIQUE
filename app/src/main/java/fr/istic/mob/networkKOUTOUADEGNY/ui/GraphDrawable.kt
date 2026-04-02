package fr.istic.mob.networkKOUTOUADEGNY.ui

import android.content.res.Resources
import android.graphics.*
import android.graphics.drawable.Drawable
import kotlin.math.max
import fr.istic.mob.networkKOUTOUADEGNY.model.Graph
import fr.istic.mob.networkKOUTOUADEGNY.model.GraphConnection
import fr.istic.mob.networkKOUTOUADEGNY.model.GraphNode

/**
 * GraphDrawable : Gère le rendu visuel complet du réseau.
 * Ce composant est responsable de transformer les données du modèle (Graph) en pixels.
 */
class GraphDrawable(private val resources: Resources) : Drawable() {
    private var graph: Graph = Graph()
    private var alphaValue: Int = 255
    private var colorFilterValue: ColorFilter? = null

    // Récupération des facteurs d'échelle pour l'adaptation aux écrans (densité et police)
    private val density = resources.displayMetrics.density
    private val fontScale = resources.configuration.fontScale

    // --- CONFIGURATION DES PINCEAUX (PAINTS) ---
    // On définit ici les styles (couleurs, épaisseurs, lissage) utilisés pour le dessin.

    private val canvasBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E7EDF3") // Fond de la zone hors plan
    }

    private val floorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FAFAF7") // Couleur du sol de l'appartement
    }

    private val outerWallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#37474F")
        style = Paint.Style.STROKE
        strokeWidth = 12f // Murs extérieurs épais
    }

    private val innerWallPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#546E7A")
        style = Paint.Style.STROKE
        strokeWidth = 8f // Murs intérieurs plus fins
    }

    // --- PAINTS POUR LES NOEUDS (Appareils) ---
    private val nodeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val nodeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#263238")
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }

    private val objectLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#13212B")
        textSize = 16f * fontScale // Taille de texte respectant les réglages utilisateur
    }

    // --- PAINTS POUR LES CONNEXIONS (Câbles) ---
    private val connectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND // Extrémités arrondies pour un look plus naturel
        strokeJoin = Paint.Join.ROUND
    }

    // --- PAINTS POUR LES ÉTIQUETTES DE LIGNES ---
    private val connectionLabelFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.FILL
    }

    private val connectionLabelStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }

    private val connectionLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 12f * fontScale
        textAlign = Paint.Align.CENTER // Texte centré horizontalement sur son point d'ancrage
    }

    private val planLabelPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#90A4AE")
        textSize = 24f * fontScale
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        textAlign = Paint.Align.CENTER
    }

    /** Met à jour les données et demande un rafraîchissement visuel */
    fun setGraph(graph: Graph) {
        this.graph = graph
        invalidateSelf()
    }

    /** Méthode centrale de dessin appelée par le système Android */
    override fun draw(canvas: Canvas) {
        applyAlphaAndFilter() // Synchronise l'opacité sur tous les Paints

        // 1. Dessine le fond de la vue
        canvas.drawRect(bounds, canvasBackgroundPaint)

        canvas.save()
        // Applique l'échelle de densité : toute la logique suivante est écrite en DP
        canvas.scale(density, density)

        // 2. Dessine les couches dans l'ordre (Plan -> Connexions -> Nœuds)
        drawPlan(canvas)
        drawConnections(canvas)
        drawNodes(canvas)

        canvas.restore()
    }

    // --- GESTION DE L'OPACITÉ ET DES FILTRES ---
    override fun setAlpha(alpha: Int) {
        alphaValue = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        colorFilterValue = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    // Indique au parent (ex: ScrollView) la taille réelle du contenu
    override fun getIntrinsicWidth(): Int = GraphGeometry.planWidthPx(graph.plan, density)
    override fun getIntrinsicHeight(): Int = GraphGeometry.planHeightPx(graph.plan, density)

    /** Applique l'alpha actuel à tous les pinceaux utilisés */
    private fun applyAlphaAndFilter() {
        val paints = listOf(
            canvasBackgroundPaint, floorPaint, outerWallPaint, innerWallPaint,
            nodeFillPaint, nodeStrokePaint, objectLabelPaint, connectionPaint,
            connectionLabelFillPaint, connectionLabelStrokePaint,
            connectionLabelPaint, planLabelPaint,
        )
        paints.forEach { paint ->
            paint.alpha = alphaValue
            paint.colorFilter = colorFilterValue
        }
    }

    /** Dessine le rectangle de l'appartement, les murs et les étiquettes de pièces */
    private fun drawPlan(canvas: Canvas) {
        val inset = GraphGeometry.PLAN_BORDER_INSET_DP
        val planRect = RectF(
            inset, inset,
            graph.plan.widthDp - inset,
            graph.plan.heightDp - inset
        )

        // Sol et murs extérieurs
        canvas.drawRoundRect(planRect, 20f, 20f, floorPaint)
        canvas.drawRoundRect(planRect, 20f, 20f, outerWallPaint)

        // Murs intérieurs
        graph.plan.walls.forEach { wall ->
            canvas.drawLine(wall.startXDp, wall.startYDp, wall.endXDp, wall.endYDp, innerWallPaint)
        }

        // Noms des pièces (Cuisine, Salon, etc.)
        val planLabelBaselineOffset = centeredTextOffset(planLabelPaint)
        graph.plan.roomLabels.forEach { label ->
            canvas.drawText(
                resources.getString(label.textRes),
                label.xDp,
                label.yDp + planLabelBaselineOffset,
                planLabelPaint
            )
        }
    }

    /** Dessine les liens incurvés entre les appareils */
    private fun drawConnections(canvas: Canvas) {
        graph.connections.forEach { connection ->
            // Récupère le tracé Path calculé par la géométrie
            val path = GraphGeometry.buildConnectionPath(graph, connection) ?: return@forEach
            connectionPaint.color = connection.color.colorInt
            connectionPaint.strokeWidth = max(connection.strokeWidthDp, 1f)

            canvas.drawPath(path, connectionPaint)
            drawConnectionLabel(canvas, connection)
        }
    }

    /** Dessine le badge et le texte de la connexion */
    private fun drawConnectionLabel(canvas: Canvas, connection: GraphConnection) {
        val labelPosition = GraphGeometry.connectionLabelPosition(graph, connection) ?: return
        val text = connection.label
        val horizontalPadding = 14f
        val verticalPadding = 8f

        val textWidth = connectionLabelPaint.measureText(text)
        val fontMetrics = connectionLabelPaint.fontMetrics
        // Calcul manuel de la hauteur du texte via les métriques de police standard
        val labelHeight = fontMetrics.descent - fontMetrics.ascent

        val rect = RectF(
            labelPosition.x - textWidth / 2f - horizontalPadding,
            labelPosition.y - labelHeight / 2f - verticalPadding,
            labelPosition.x + textWidth / 2f + horizontalPadding,
            labelPosition.y + labelHeight / 2f + verticalPadding,
        )

        // Dessin du badge d'arrière-plan du texte
        canvas.drawRoundRect(rect, 14f, 14f, connectionLabelFillPaint)
        canvas.drawRoundRect(rect, 14f, 14f, connectionLabelStrokePaint)

        // Dessin du texte au centre
        canvas.drawText(
            text,
            labelPosition.x,
            labelPosition.y + centeredTextOffset(connectionLabelPaint),
            connectionLabelPaint
        )
    }

    /** Dessine les rectangles représentant les appareils (nœuds) */
    private fun drawNodes(canvas: Canvas) {
        graph.nodes.forEach { node ->
            val rect = GraphGeometry.nodeRect(node)
            nodeFillPaint.color = node.color.colorInt

            // Rectangle arrondi pour l'appareil
            canvas.drawRoundRect(rect, GraphGeometry.NODE_CORNER_RADIUS_DP, GraphGeometry.NODE_CORNER_RADIUS_DP, nodeFillPaint)
            canvas.drawRoundRect(rect, GraphGeometry.NODE_CORNER_RADIUS_DP, GraphGeometry.NODE_CORNER_RADIUS_DP, nodeStrokePaint)

            // Label de l'appareil positionné à droite
            canvas.drawText(
                node.label,
                rect.right + 16f,
                rect.centerY() + centeredTextOffset(objectLabelPaint),
                objectLabelPaint
            )
        }
    }

    /**
     * Calcule l'ajustement vertical nécessaire pour centrer parfaitement
     * un texte sur sa ligne de base (baseline).
     */
    private fun centeredTextOffset(paint: Paint): Float {
        val metrics = paint.fontMetrics
        return -(metrics.ascent + metrics.descent) / 2f
    }
}