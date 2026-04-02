package fr.istic.mob.networkKOUTOUADEGNY

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import fr.istic.mob.networkKOUTOUADEGNY.databinding.ActivityMainBinding
import fr.istic.mob.networkKOUTOUADEGNY.databinding.DialogConnectionEditorBinding
import fr.istic.mob.networkKOUTOUADEGNY.databinding.DialogNodeEditorBinding
import fr.istic.mob.networkKOUTOUADEGNY.model.*
import fr.istic.mob.networkKOUTOUADEGNY.ui.GraphDrawable
import fr.istic.mob.networkKOUTOUADEGNY.ui.GraphEditorView
import fr.istic.mob.networkKOUTOUADEGNY.ui.GraphGeometry
import fr.istic.mob.networkKOUTOUADEGNY.data.GraphStorage
import fr.istic.mob.networkKOUTOUADEGNY.data.GraphJsonCodec
import fr.istic.mob.networkKOUTOUADEGNY.ui.ConnectionCreationResult
import fr.istic.mob.networkKOUTOUADEGNY.ui.NetworkViewModel
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), GraphEditorView.Listener {

    // --- PROPRIÉTÉS ET ÉTATS ---
    private lateinit var binding: ActivityMainBinding
    private val viewModel: NetworkViewModel by viewModels()
    private lateinit var graphStorage: GraphStorage

    // Flags pour éviter les boucles infinies entre l'UI et le ViewModel
    private var syncingModeSelection = false
    private var syncingPlanSelection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        graphStorage = GraphStorage(this)

        // Initialise le ViewModel avec les données sauvegardées (si rotation d'écran)
        viewModel.initialize(
            savedGraphJson = savedInstanceState?.getString(STATE_GRAPH_JSON),
            savedModeName = savedInstanceState?.getString(STATE_MODE),
        )

        setSupportActionBar(binding.toolbar)
        // La vue personnalisée nous informe des clics/déplacements via cette interface
        binding.graphEditorView.listener = this

        // Configuration des composants UI
        configureModeSelector() // RadioButtons pour changer de mode
        configurePlanSelector() // Spinner pour changer de plan d'appartement
        observeViewModel()      // Écoute les changements de données
    }

    // --- GESTION DU MENU ---

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reset -> { showResetDialog(); true }
            R.id.action_save -> { saveGraph(); true }
            R.id.action_load -> { loadGraph(); true }
            R.id.action_share -> { shareGraph(); true }
            else -> super.onOptionsItemSelected(item)
        }
    }

    /** Sauvegarde l'état actuel lors d'une rotation d'écran */
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_GRAPH_JSON, GraphJsonCodec.toJson(viewModel.graphSnapshot()))
        outState.putString(STATE_MODE, viewModel.modeSnapshot().name)
        super.onSaveInstanceState(outState)
    }

    // --- CALLBACKS DE L'ÉDITEUR GRAPHIQUE (GraphEditorView.Listener) ---

    override fun onAddNodeRequested(xDp: Float, yDp: Float) {
        // Ouvre la boîte de dialogue pour créer un nouvel appareil
        showNodeEditorDialog(node = null, xDp = xDp, yDp = yDp)
    }

    override fun onNodeLongPressed(nodeId: Long) {
        // Options au clic long sur un appareil : Modifier ou Supprimer
        val node = viewModel.graphSnapshot().findNode(nodeId) ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(node.label)
            .setItems(arrayOf(getString(R.string.action_edit), getString(R.string.action_delete))) { _, index ->
                when (index) {
                    0 -> showNodeEditorDialog(node = node)
                    1 -> viewModel.deleteNode(node.id)
                }
            }
            .show()
    }

    override fun onConnectionLongPressed(connectionId: Long) {
        // Options au clic long sur un lien : Modifier ou Supprimer
        val connection = viewModel.graphSnapshot().connections.firstOrNull { it.id == connectionId } ?: return
        MaterialAlertDialogBuilder(this)
            .setTitle(connection.label)
            .setItems(arrayOf(getString(R.string.action_edit), getString(R.string.action_delete))) { _, index ->
                when (index) {
                    0 -> showConnectionEditorDialog(connection = connection)
                    1 -> viewModel.deleteConnection(connection.id)
                }
            }
            .show()
    }

    override fun onConnectionRequested(startNodeId: Long, endNodeId: Long) {
        // Appelé quand l'utilisateur tire un lien entre deux appareils
        showConnectionEditorDialog(connection = null, startNodeId = startNodeId, endNodeId = endNodeId)
    }

    override fun onNodeMoved(nodeId: Long, xDp: Float, yDp: Float) {
        // Met à jour la position du nœud en temps réel dans le ViewModel
        viewModel.moveNode(nodeId, xDp, yDp)
    }

    override fun onConnectionCurvatureChanged(connectionId: Long, curvatureDp: Float) {
        // Met à jour la courbe du lien dans le ViewModel
        viewModel.updateConnectionCurvature(connectionId, curvatureDp)
    }

    // --- CONFIGURATION UI (SELECTEURS) ---

    private fun configureModeSelector() {
        binding.modeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (syncingModeSelection) return@setOnCheckedChangeListener

            val mode = when (checkedId) {
                R.id.addObjectRadioButton -> EditorMode.ADD_DEVICE
                R.id.addConnectionRadioButton -> EditorMode.ADD_CONNECTION
                else -> EditorMode.EDIT
            }
            viewModel.setMode(mode)
        }
    }

    private fun configurePlanSelector() {
        val planLabels = ApartmentPlan.entries.map { getString(it.labelRes) }
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, planLabels)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.planSpinner.adapter = adapter
        binding.planSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: android.view.View?, position: Int, id: Long) {
                if (syncingPlanSelection) return
                viewModel.setPlan(ApartmentPlan.entries[position])
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

    /** Observe les flux de données du ViewModel pour mettre à jour l'UI */
    private fun observeViewModel() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.graph.collect { graph ->
                        binding.graphEditorView.setGraph(graph)
                        syncPlanSpinner(graph.plan)
                    }
                }
                launch {
                    viewModel.mode.collect { mode ->
                        binding.graphEditorView.setMode(mode)
                        syncModeRadioButtons(mode)
                    }
                }
            }
        }
    }

    // --- MÉTHODES DE SYNCHRONISATION (Évite les boucles) ---

    private fun syncModeRadioButtons(mode: EditorMode) {
        syncingModeSelection = true
        binding.modeRadioGroup.check(
            when (mode) {
                EditorMode.ADD_DEVICE -> R.id.addObjectRadioButton
                EditorMode.ADD_CONNECTION -> R.id.addConnectionRadioButton
                EditorMode.EDIT -> R.id.editRadioButton
            }
        )
        syncingModeSelection = false
    }

    private fun syncPlanSpinner(plan: ApartmentPlan) {
        val targetIndex = ApartmentPlan.entries.indexOf(plan)
        if (binding.planSpinner.selectedItemPosition == targetIndex) return
        syncingPlanSelection = true
        binding.planSpinner.setSelection(targetIndex, false)
        syncingPlanSelection = false
    }

    // --- DIALOGUES D'ÉDITION ---

    /** Affiche la boîte de dialogue pour ajouter ou modifier un appareil */
    private fun showNodeEditorDialog(node: GraphNode? = null, xDp: Float = 0f, yDp: Float = 0f) {
        val dialogBinding = DialogNodeEditorBinding.inflate(layoutInflater)
        val colors = NetworkColor.entries

        // Configuration du Spinner de couleurs
        val colorAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, colors.map { getString(it.labelRes) })
        dialogBinding.colorSpinner.adapter = colorAdapter

        // Pré-remplissage si modification
        dialogBinding.labelInputEditText.setText(node?.label.orEmpty())
        dialogBinding.colorSpinner.setSelection(colors.indexOf(node?.color ?: DEFAULT_NODE_COLOR))

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (node == null) R.string.dialog_add_object else R.string.dialog_edit_object)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_validate, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val label = dialogBinding.labelInputEditText.text?.toString()?.trim().orEmpty()
                if (label.isBlank()) {
                    dialogBinding.labelInputEditText.error = getString(R.string.label_required)
                    return@setOnClickListener
                }
                val color = colors[dialogBinding.colorSpinner.selectedItemPosition]
                if (node == null) viewModel.addNode(label, color, xDp, yDp)
                else viewModel.updateNode(node.id, label, color)
                dialog.dismiss()
            }
        }
        dialog.show()
    }

    /** Affiche la boîte de dialogue pour ajouter ou modifier un lien de connexion */
    private fun showConnectionEditorDialog(connection: GraphConnection? = null, startNodeId: Long? = null, endNodeId: Long? = null) {
        val dialogBinding = DialogConnectionEditorBinding.inflate(layoutInflater)
        val colors = NetworkColor.entries

        val colorAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, colors.map { getString(it.labelRes) })
        dialogBinding.colorSpinner.adapter = colorAdapter
        dialogBinding.labelInputEditText.setText(connection?.label.orEmpty())
        dialogBinding.colorSpinner.setSelection(colors.indexOf(connection?.color ?: DEFAULT_CONNECTION_COLOR))

        // Configuration de la barre d'épaisseur (SeekBar)
        val initialThickness = (connection?.strokeWidthDp ?: DEFAULT_CONNECTION_STROKE_DP).roundToInt()
        dialogBinding.thicknessSeekBar.progress = initialThickness - NetworkViewModel.MIN_CONNECTION_STROKE_DP.roundToInt()
        updateThicknessText(dialogBinding, initialThickness)

        dialogBinding.thicknessSeekBar.setOnSeekBarChangeListener(SimpleSeekBarListener { progress ->
            updateThicknessText(dialogBinding, NetworkViewModel.MIN_CONNECTION_STROKE_DP.roundToInt() + progress)
        })

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(if (connection == null) R.string.dialog_add_connection else R.string.dialog_edit_connection)
            .setView(dialogBinding.root)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_validate, null)
            .create()

        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val label = dialogBinding.labelInputEditText.text?.toString()?.trim().orEmpty()
                if (label.isBlank()) {
                    dialogBinding.labelInputEditText.error = getString(R.string.label_required)
                    return@setOnClickListener
                }
                val color = colors[dialogBinding.colorSpinner.selectedItemPosition]
                val thickness = NetworkViewModel.MIN_CONNECTION_STROKE_DP.roundToInt() + dialogBinding.thicknessSeekBar.progress

                if (connection == null) {
                    val result = viewModel.addConnection(startNodeId!!, endNodeId!!, label, color, thickness.toFloat())
                    if (result == ConnectionCreationResult.SUCCESS) dialog.dismiss()
                    else showMessage(R.string.connection_duplicate_error)
                } else {
                    viewModel.updateConnection(connection.id, label, color, thickness.toFloat())
                    dialog.dismiss()
                }
            }
        }
        dialog.show()
    }

    // --- FONCTIONS DE SAUVEGARDE ET PARTAGE ---

    private fun updateThicknessText(binding: DialogConnectionEditorBinding, thicknessDp: Int) {
        binding.thicknessValueTextView.text = getString(R.string.thickness_value, thicknessDp)
    }

    private fun showResetDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reset_dialog_title)
            .setMessage(R.string.reset_dialog_message)
            .setPositiveButton(R.string.menu_reset) { _, _ -> viewModel.resetGraph() }
            .setNegativeButton(R.string.action_cancel, null)
            .show()
    }

    private fun saveGraph() {
        try {
            graphStorage.save(viewModel.graphSnapshot())
            showMessage(R.string.save_success)
        } catch (e: Exception) { showMessage(R.string.save_error) }
    }

    private fun loadGraph() {
        // Tente de lire le fichier sur le disque via l'utilitaire GraphStorage
        val graph = graphStorage.load()

        if (graph == null) {
            // Si le fichier n'existe pas ou est corrompu, affiche un message d'erreur
            showMessage(R.string.no_saved_network)
        } else {
            // TRANSMISSION AU VIEWMODEL :
            // C'est ici que l'erreur se produit car restoreGraph n'est pas défini dans le ViewModel.
            viewModel.restoreGraph(graph)

            // Informe l'utilisateur que le chargement a réussi
            showMessage(R.string.load_success)
        }
    }

    /** Exporte le graphe en PNG et ouvre le sélecteur de partage Android */
    private fun shareGraph() {
        try {
            val imageFile = renderGraphToPng()
            // FileProvider permet de partager le fichier de manière sécurisée avec d'autres apps
            val imageUri = FileProvider.getUriForFile(this, "$packageName.fileprovider", imageFile)

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_network)))
        } catch (e: Exception) { showMessage(R.string.share_error) }
    }

    /** Transforme le dessin du graphe en un fichier image PNG */
    private fun renderGraphToPng(): File {
        val graph = viewModel.graphSnapshot()
        val density = resources.displayMetrics.density
        val width = GraphGeometry.planWidthPx(graph.plan, density)
        val height = GraphGeometry.planHeightPx(graph.plan, density)

        // On utilise le Drawable existant pour dessiner dans un Bitmap (image en mémoire)
        val drawable = GraphDrawable(resources).apply {
            setGraph(graph)
            setBounds(0, 0, width, height)
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.draw(canvas)

        // Sauvegarde du Bitmap dans un fichier temporaire
        val sharedDirectory = File(cacheDir, "shared").apply { mkdirs() }
        val outputFile = File(sharedDirectory, "network_graph.png")
        FileOutputStream(outputFile).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        return outputFile
    }

    private fun showMessage(messageResId: Int) {
        Snackbar.make(binding.root, messageResId, Snackbar.LENGTH_SHORT).show()
    }

    // --- CLASSES INTERNES ---

    private class SimpleSeekBarListener(private val onProgressChanged: (Int) -> Unit) : android.widget.SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(s: android.widget.SeekBar?, p: Int, f: Boolean) = onProgressChanged(p)
        override fun onStartTrackingTouch(s: android.widget.SeekBar?) = Unit
        override fun onStopTrackingTouch(s: android.widget.SeekBar?) = Unit
    }

    companion object {
        private const val STATE_GRAPH_JSON = "state_graph_json"
        private const val STATE_MODE = "state_mode"
        private const val DEFAULT_CONNECTION_STROKE_DP = 6f
        private val DEFAULT_CONNECTION_COLOR = NetworkColor.BLACK
        private val DEFAULT_NODE_COLOR = NetworkColor.BLUE
    }
}