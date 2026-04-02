package fr.istic.mob.networkKOUTOUADEGNY.ui

import android.content.Context
import android.graphics.*
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import fr.istic.mob.networkKOUTOUADEGNY.model.EditorMode
import fr.istic.mob.networkKOUTOUADEGNY.model.Graph
import kotlin.math.abs

/**
 * Vue personnalisée responsable de l'affichage interactif du graphe.
 * Elle gère le dessin, les déplacements de nœuds et la création de connexions.
 */
class GraphEditorView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : View(context, attrs, defStyleAttr) {

    /** Interface pour notifier l'activité ou le fragment des actions utilisateur */
    interface Listener {
        fun onAddNodeRequested(xDp: Float, yDp: Float)
        fun onNodeLongPressed(nodeId: Long)
        fun onConnectionLongPressed(connectionId: Long)
        fun onConnectionRequested(startNodeId: Long, endNodeId: Long)
        fun onNodeMoved(nodeId: Long, xDp: Float, yDp: Float)
        fun onConnectionCurvatureChanged(connectionId: Long, curvatureDp: Float)
    }

    var listener: Listener? = null

    // --- Composants de rendu ---
    private val graphDrawable = GraphDrawable(resources) // Gère le dessin des nœuds et lignes
    private val density = resources.displayMetrics.density // Facteur de conversion DP vers Pixels

    // --- Gestion du temps (Appui long) ---
    private val longPressHandler = Handler(Looper.getMainLooper())
    // Distance minimale de mouvement pour annuler un clic/appui long (convertie en DP)
    private val touchSlopDp = ViewConfiguration.get(context).scaledTouchSlop / density

    /** Style pour la ligne pointillée lors de la création d'un lien */
    private val temporaryConnectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#546E7A")
        style = Paint.Style.STROKE
        strokeWidth = 4f
        strokeCap = Paint.Cap.ROUND
        // Définit l'effet pointillé : 16px plein, 14px vide
        pathEffect = DashPathEffect(floatArrayOf(16f, 14f), 0f)
    }

    /** Style pour les petits cercles de manipulation de courbure */
    private val connectionHandlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#455A64")
        style = Paint.Style.FILL
    }

    // --- État de la vue ---
    private var graph: Graph = Graph()
    private var mode: EditorMode = EditorMode.EDIT
    private var activeInteraction: TouchInteraction = TouchInteraction.None
    private var pendingLongPress: PendingLongPress? = null
    private var temporaryConnection: TemporaryConnection? = null

    /** Runnable exécuté automatiquement après le délai d'appui long défini par le système */
    private val longPressRunnable = Runnable {
        val pending = pendingLongPress ?: return@Runnable
        when (val target = pending.target) {
            is LongPressTarget.Background -> listener?.onAddNodeRequested(target.point.x, target.point.y)
            is LongPressTarget.Node -> listener?.onNodeLongPressed(target.nodeId)
            is LongPressTarget.Connection -> listener?.onConnectionLongPressed(target.connectionId)
        }
        pendingLongPress = null
    }

    /** Met à jour les données du graphe et redessine la vue */
    fun setGraph(graph: Graph) {
        val planChanged = this.graph.plan != graph.plan
        this.graph = graph
        graphDrawable.setGraph(graph)
        // Si le plan a changé, on demande au système de recalculer la taille de la vue (onMeasure)
        if (planChanged) requestLayout()
        invalidate() // Provoque un appel à onDraw()
    }

    /** Change le mode d'interaction (Ajout, Edition...) et réinitialise les états */
    fun setMode(mode: EditorMode) {
        this.mode = mode
        cancelPendingLongPress()
        activeInteraction = TouchInteraction.None
        temporaryConnection = null
        invalidate()
    }

    /** Définit la taille de la vue selon les dimensions du plan converties en Pixels */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val desiredWidth = GraphGeometry.planWidthPx(graph.plan, density)
        val desiredHeight = GraphGeometry.planHeightPx(graph.plan, density)
        setMeasuredDimension(
            resolveSize(desiredWidth, widthMeasureSpec),
            resolveSize(desiredHeight, heightMeasureSpec),
        )
    }

    /** Orchestre le dessin de la vue */
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        // 1. Dessine les éléments statiques (Drawable)
        graphDrawable.setBounds(0, 0, width, height)
        graphDrawable.draw(canvas)

        // 2. Dessine les éléments interactifs (Calculés en DP pour la logique)
        canvas.save()
        canvas.scale(density, density) // On travaille désormais en coordonnées DP
        drawConnectionHandles(canvas)
        drawTemporaryConnection(canvas)
        canvas.restore()
    }

    /** Capte et distribue les événements tactiles */
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // Conversion immédiate PX -> DP pour simplifier la logique
        val xDp = event.x / density
        val yDp = event.y / density

        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> handleActionDown(xDp, yDp)
            MotionEvent.ACTION_MOVE -> handleActionMove(xDp, yDp)
            MotionEvent.ACTION_UP -> handleActionUp(xDp, yDp)
            MotionEvent.ACTION_CANCEL -> handleActionCancel()
            else -> super.onTouchEvent(event)
        }
    }

    /** Identifie l'élément touché lors de l'appui initial */
    private fun handleActionDown(xDp: Float, yDp: Float): Boolean {
        val touchedNode = GraphGeometry.findNodeAt(graph, xDp, yDp)
        val touchedConnection = GraphGeometry.findConnectionHandleAt(graph, xDp, yDp)

        return when (mode) {
            EditorMode.ADD_DEVICE -> {
                if (touchedNode == null && touchedConnection == null) {
                    startLongPressDetector(LongPressTarget.Background(PointF(xDp, yDp)), xDp, yDp)
                    true
                } else false
            }
            EditorMode.ADD_CONNECTION -> {
                if (touchedNode != null) {
                    // Désactive le scroll du parent pour ne pas interrompre le tracé du lien
                    parent.requestDisallowInterceptTouchEvent(true)
                    temporaryConnection = TemporaryConnection(touchedNode.id, PointF(xDp, yDp))
                    activeInteraction = TouchInteraction.CreatingConnection(touchedNode.id)
                    invalidate()
                    true
                } else false
            }
            EditorMode.EDIT -> {
                when {
                    touchedNode != null -> {
                        parent.requestDisallowInterceptTouchEvent(true)
                        // On stocke le décalage (offset) pour éviter que le nœud ne "saute" sous le doigt
                        activeInteraction = TouchInteraction.PendingNode(touchedNode.id, xDp - touchedNode.xDp, yDp - touchedNode.yDp)
                        startLongPressDetector(LongPressTarget.Node(touchedNode.id), xDp, yDp)
                        true
                    }
                    touchedConnection != null -> {
                        parent.requestDisallowInterceptTouchEvent(true)
                        activeInteraction = TouchInteraction.PendingConnection(touchedConnection.id)
                        startLongPressDetector(LongPressTarget.Connection(touchedConnection.id), xDp, yDp)
                        true
                    }
                    else -> false
                }
            }
        }
    }

    /** Gère le mouvement du doigt sur l'écran */
    private fun handleActionMove(xDp: Float, yDp: Float): Boolean {
        when (val interaction = activeInteraction) {
            is TouchInteraction.PendingNode -> {
                // Si l'utilisateur bouge assez, on annule l'appui long et on commence le déplacement
                if (cancelPendingLongPressIfMoved(xDp, yDp)) {
                    activeInteraction = TouchInteraction.MovingNode(interaction.nodeId, interaction.offsetX, interaction.offsetY)
                }
            }
            is TouchInteraction.MovingNode -> {
                // Notifie le listener pour mettre à jour la position du nœud
                listener?.onNodeMoved(interaction.nodeId, xDp - interaction.offsetX, yDp - interaction.offsetY)
            }
            is TouchInteraction.PendingConnection -> {
                if (cancelPendingLongPressIfMoved(xDp, yDp)) {
                    activeInteraction = TouchInteraction.AdjustingConnection(interaction.connectionId)
                }
            }
            is TouchInteraction.AdjustingConnection -> {
                updateConnectionCurvature(interaction.connectionId, xDp, yDp)
            }
            is TouchInteraction.CreatingConnection -> {
                // Met à jour la position de l'extrémité de la ligne élastique
                temporaryConnection = temporaryConnection?.copy(endPoint = PointF(xDp, yDp))
                invalidate()
            }
            else -> cancelPendingLongPressIfMoved(xDp, yDp)
        }
        return true
    }

    /** Finalise l'action lors du retrait du doigt */
    private fun handleActionUp(xDp: Float, yDp: Float): Boolean {
        if (activeInteraction is TouchInteraction.CreatingConnection) {
            val endNode = GraphGeometry.findNodeAt(graph, xDp, yDp)
            val startId = (activeInteraction as TouchInteraction.CreatingConnection).startNodeId
            // Si on relâche sur un nœud différent du départ, on demande la création du lien
            if (endNode != null && endNode.id != startId) {
                listener?.onConnectionRequested(startId, endNode.id)
            }
        }
        cancelPendingLongPress()
        activeInteraction = TouchInteraction.None
        temporaryConnection = null
        invalidate()
        return true
    }

    /** Réinitialise tout en cas d'annulation système */
    private fun handleActionCancel(): Boolean {
        cancelPendingLongPress()
        activeInteraction = TouchInteraction.None
        temporaryConnection = null
        invalidate()
        return true
    }

    /** Lance le timer pour l'appui long */
    private fun startLongPressDetector(target: LongPressTarget, xDp: Float, yDp: Float) {
        pendingLongPress = PendingLongPress(target, PointF(xDp, yDp))
        longPressHandler.postDelayed(longPressRunnable, ViewConfiguration.getLongPressTimeout().toLong())
    }

    /** Arrête le timer de l'appui long */
    private fun cancelPendingLongPress() {
        longPressHandler.removeCallbacks(longPressRunnable)
        pendingLongPress = null
    }

    /** Annule l'appui long si le doigt a bougé au-delà du seuil de tolérance (Touch Slop) */
    private fun cancelPendingLongPressIfMoved(xDp: Float, yDp: Float): Boolean {
        val pending = pendingLongPress ?: return true
        if (abs(xDp - pending.origin.x) > touchSlopDp || abs(yDp - pending.origin.y) > touchSlopDp) {
            cancelPendingLongPress()
            return true
        }
        return false
    }

    /** Calcule la courbure en fonction de la distance entre le doigt et la ligne droite entre les nœuds */
    private fun updateConnectionCurvature(connectionId: Long, xDp: Float, yDp: Float) {
        val conn = graph.connections.find { it.id == connectionId } ?: return
        val s = graph.findNode(conn.startNodeId) ?: return
        val e = graph.findNode(conn.endNodeId) ?: return
        val curv = GraphGeometry.signedDistanceToLine(xDp, yDp, s.xDp, s.yDp, e.xDp, e.yDp)
        listener?.onConnectionCurvatureChanged(connectionId, curv)
    }

    /** Dessine la ligne de prévisualisation (élastique) */
    private fun drawTemporaryConnection(canvas: Canvas) {
        val temp = temporaryConnection ?: return
        val start = graph.findNode(temp.startNodeId) ?: return
        canvas.drawLine(start.xDp, start.yDp, temp.endPoint.x, temp.endPoint.y, temporaryConnectionPaint)
    }

    /** Dessine les poignées de contrôle sur chaque connexion en mode édition */
    private fun drawConnectionHandles(canvas: Canvas) {
        if (mode != EditorMode.EDIT) return
        graph.connections.forEach { conn ->
            GraphGeometry.connectionMidpointPosition(graph, conn)?.let {
                canvas.drawCircle(it.x, it.y, 8f, connectionHandlePaint)
            }
        }
    }

    /** Sécurité : nettoie le Handler si la vue est détruite pour éviter les fuites mémoire */
    override fun onDetachedFromWindow() {
        cancelPendingLongPress()
        super.onDetachedFromWindow()
    }

    // --- MACHINE À ÉTATS : Définition des types d'interactions ---

    private sealed interface TouchInteraction {
        object None : TouchInteraction
        /** Le doigt est posé sur un nœud mais n'a pas encore bougé */
        data class PendingNode(val nodeId: Long, val offsetX: Float, val offsetY: Float) : TouchInteraction
        /** Le nœud est en cours de déplacement */
        data class MovingNode(val nodeId: Long, val offsetX: Float, val offsetY: Float) : TouchInteraction
        /** Le doigt est sur une poignée de ligne mais n'a pas bougé */
        data class PendingConnection(val connectionId: Long) : TouchInteraction
        /** La courbure de la ligne est en cours de modification */
        data class AdjustingConnection(val connectionId: Long) : TouchInteraction
        /** Un nouveau lien est en cours de tracé */
        data class CreatingConnection(val startNodeId: Long) : TouchInteraction
    }

    /** Stocke les infos d'un appui long en attente */
    private data class PendingLongPress(val target: LongPressTarget, val origin: PointF)

    /** Cible potentielle d'un appui long */
    private sealed class LongPressTarget {
        data class Background(val point: PointF) : LongPressTarget()
        data class Node(val nodeId: Long) : LongPressTarget()
        data class Connection(val connectionId: Long) : LongPressTarget()
    }

    /** État de la ligne élastique (création) */
    private data class TemporaryConnection(val startNodeId: Long, val endPoint: PointF)
}