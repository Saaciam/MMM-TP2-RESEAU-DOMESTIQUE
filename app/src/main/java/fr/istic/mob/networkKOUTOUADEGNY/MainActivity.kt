package fr.istic.mob.networkKOUTOUADEGNY

import android.content.ActivityNotFoundException
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
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
import fr.istic.mob.networkKOUTOUADEGNY.data.GraphJsonCodec
import fr.istic.mob.networkKOUTOUADEGNY.data.GraphStorage
import fr.istic.mob.networkKOUTOUADEGNY.ActivityMainBinding
import fr.istic.mob.networkKOUTOUADEGNY.databinding.DialogConnectionEditorBinding
import fr.istic.mob.networkKOUTOUADEGNY.databinding.DialogNodeEditorBinding
import fr.istic.mob.networkKOUTOUADEGNY.model.ApartmentPlan
import fr.istic.mob.networkKOUTOUADEGNY.model.EditorMode
import fr.istic.mob.networkKOUTOUADEGNY.model.GraphConnection
import fr.istic.mob.networkKOUTOUADEGNY.model.GraphNode
import fr.istic.mob.networkKOUTOUADEGNY.model.NetworkColor
import fr.istic.mob.networkKOUTOUADEGNY.ui.ConnectionCreationResult
import fr.istic.mob.networkKOUTOUADEGNY.ui.GraphDrawable
import fr.istic.mob.networkKOUTOUADEGNY.ui.GraphEditorView
import fr.istic.mob.networkKOUTOUADEGNY.ui.GraphGeometry
import fr.istic.mob.networkKOUTOUADEGNY.ui.NetworkViewModel

import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity(), GraphEditorView.Listener {
    private val viewModel: NetworkViewModel by viewModels()

    private lateinit var binding: ActivityMainBinding
    private lateinit var graphStorage: GraphStorage

    private var syncingModeSelection = false
    private var syncingPlanSelection = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        graphStorage = GraphStorage(this)
        viewModel.initialize(
            savedGraphJson = savedInstanceState?.getString(STATE_GRAPH_JSON),
            savedModeName = savedInstanceState?.getString(STATE_MODE),
        )

        setSupportActionBar(binding.toolbar)
        binding.graphEditorView.listener = this

        configureModeSelector()
        configurePlanSelector()
        observeViewModel()
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_reset -> {
                showResetDialog()
                true
            }

            R.id.action_save -> {
                saveGraph()
                true
            }

            R.id.action_load -> {
                loadGraph()
                true
            }

            R.id.action_share -> {
                shareGraph()
                true
            }

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString(STATE_GRAPH_JSON, GraphJsonCodec.toJson(viewModel.graphSnapshot()))
        outState.putString(STATE_MODE, viewModel.modeSnapshot().name)
        super.onSaveInstanceState(outState)
    }

    override fun onAddNodeRequested(xDp: Float, yDp: Float) {
        showNodeEditorDialog(node = null, xDp = xDp, yDp = yDp)
    }

    override fun onNodeLongPressed(nodeId: Long) {
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
        val connection =
            viewModel.graphSnapshot().connections.firstOrNull { it.id == connectionId } ?: return
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
        showConnectionEditorDialog(
            connection = null,
            startNodeId = startNodeId,
            endNodeId = endNodeId,
        )
    }

    override fun onNodeMoved(nodeId: Long, xDp: Float, yDp: Float) {
        viewModel.moveNode(nodeId, xDp, yDp)
    }

    override fun onConnectionCurvatureChanged(connectionId: Long, curvatureDp: Float) {
        viewModel.updateConnectionCurvature(connectionId, curvatureDp)
    }

    private fun configureModeSelector() {
        binding.modeRadioGroup.setOnCheckedChangeListener { _, checkedId ->
            if (syncingModeSelection) {
                return@setOnCheckedChangeListener
            }

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
            override fun onItemSelected(
                parent: AdapterView<*>?,
                view: android.view.View?,
                position: Int,
                id: Long,
            ) {
                if (syncingPlanSelection) {
                    return
                }
                viewModel.setPlan(ApartmentPlan.entries[position])
            }

            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
    }

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

    private fun syncModeRadioButtons(mode: EditorMode) {
        syncingModeSelection = true
        binding.modeRadioGroup.check(
            when (mode) {
                EditorMode.ADD_DEVICE -> R.id.addObjectRadioButton
                EditorMode.ADD_CONNECTION -> R.id.addConnectionRadioButton
                EditorMode.EDIT -> R.id.editRadioButton
            },
        )
        syncingModeSelection = false
    }

    private fun syncPlanSpinner(plan: ApartmentPlan) {
        val targetIndex = ApartmentPlan.entries.indexOf(plan)
        if (binding.planSpinner.selectedItemPosition == targetIndex) {
            return
        }

        syncingPlanSelection = true
        binding.planSpinner.setSelection(targetIndex, false)
        syncingPlanSelection = false
    }

    private fun showNodeEditorDialog(node: GraphNode? = null, xDp: Float = 0f, yDp: Float = 0f) {
        val dialogBinding = DialogNodeEditorBinding.inflate(layoutInflater)
        val colors = NetworkColor.entries
        val colorAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            colors.map { getString(it.labelRes) },
        )
        colorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.colorSpinner.adapter = colorAdapter
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
                if (node == null) {
                    viewModel.addNode(label, color, xDp, yDp)
                } else {
                    viewModel.updateNode(node.id, label, color)
                }
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showConnectionEditorDialog(
        connection: GraphConnection? = null,
        startNodeId: Long? = null,
        endNodeId: Long? = null,
    ) {
        val dialogBinding = DialogConnectionEditorBinding.inflate(layoutInflater)
        val colors = NetworkColor.entries
        val colorAdapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_item,
            colors.map { getString(it.labelRes) },
        )
        colorAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        dialogBinding.colorSpinner.adapter = colorAdapter
        dialogBinding.labelInputEditText.setText(connection?.label.orEmpty())
        dialogBinding.colorSpinner.setSelection(colors.indexOf(connection?.color ?: DEFAULT_CONNECTION_COLOR))

        val initialThickness = (connection?.strokeWidthDp ?: DEFAULT_CONNECTION_STROKE_DP).roundToInt()
            .coerceIn(
                NetworkViewModel.MIN_CONNECTION_STROKE_DP.roundToInt(),
                NetworkViewModel.MAX_CONNECTION_STROKE_DP.roundToInt(),
            )
        dialogBinding.thicknessSeekBar.max =
            NetworkViewModel.MAX_CONNECTION_STROKE_DP.roundToInt() -
                    NetworkViewModel.MIN_CONNECTION_STROKE_DP.roundToInt()
        dialogBinding.thicknessSeekBar.progress =
            initialThickness - NetworkViewModel.MIN_CONNECTION_STROKE_DP.roundToInt()
        updateThicknessText(dialogBinding, initialThickness)
        dialogBinding.thicknessSeekBar.setOnSeekBarChangeListener(SimpleSeekBarListener { progress ->
            val thickness = NetworkViewModel.MIN_CONNECTION_STROKE_DP.roundToInt() + progress
            updateThicknessText(dialogBinding, thickness)
        })

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(
                if (connection == null) {
                    R.string.dialog_add_connection
                } else {
                    R.string.dialog_edit_connection
                },
            )
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
                val thickness =
                    NetworkViewModel.MIN_CONNECTION_STROKE_DP.roundToInt() +
                            dialogBinding.thicknessSeekBar.progress

                if (connection == null) {
                    val result = viewModel.addConnection(
                        startNodeId = startNodeId ?: return@setOnClickListener,
                        endNodeId = endNodeId ?: return@setOnClickListener,
                        label = label,
                        color = color,
                        strokeWidthDp = thickness.toFloat(),
                    )
                    when (result) {
                        ConnectionCreationResult.SUCCESS -> dialog.dismiss()
                        ConnectionCreationResult.DUPLICATE -> showMessage(R.string.connection_duplicate_error)
                        ConnectionCreationResult.SAME_NODE -> showMessage(R.string.connection_loop_error)
                        ConnectionCreationResult.MISSING_NODE -> showMessage(R.string.connection_node_missing_error)
                    }
                } else {
                    viewModel.updateConnection(
                        connectionId = connection.id,
                        label = label,
                        color = color,
                        strokeWidthDp = thickness.toFloat(),
                    )
                    dialog.dismiss()
                }
            }
        }

        dialog.show()
    }

    private fun updateThicknessText(
        dialogBinding: DialogConnectionEditorBinding,
        thicknessDp: Int,
    ) {
        dialogBinding.thicknessValueTextView.text =
            getString(R.string.thickness_value, thicknessDp)
    }

    private fun showResetDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.reset_dialog_title)
            .setMessage(R.string.reset_dialog_message)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.menu_reset) { _, _ ->
                viewModel.resetGraph()
            }
            .show()
    }

    private fun saveGraph() {
        try {
            graphStorage.save(viewModel.graphSnapshot())
            showMessage(R.string.save_success)
        } catch (_: Exception) {
            showMessage(R.string.save_error)
        }
    }

    private fun loadGraph() {
        try {
            val graph = graphStorage.load()
            if (graph == null) {
                showMessage(R.string.no_saved_network)
                return
            }
            viewModel.restoreGraph(graph)
            showMessage(R.string.load_success)
        } catch (_: Exception) {
            showMessage(R.string.load_error)
        }
    }

    private fun shareGraph() {
        try {
            val imageFile = renderGraphToPng()
            val imageUri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                imageFile,
            )
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_SUBJECT, getString(R.string.share_subject))
                putExtra(Intent.EXTRA_TEXT, getString(R.string.share_body))
                putExtra(Intent.EXTRA_STREAM, imageUri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_network)))
        } catch (_: ActivityNotFoundException) {
            showMessage(R.string.share_error)
        } catch (_: Exception) {
            showMessage(R.string.share_error)
        }
    }

    private fun renderGraphToPng(): File {
        val graph = viewModel.graphSnapshot()
        val density = resources.displayMetrics.density
        val width = GraphGeometry.planWidthPx(graph.plan, density)
        val height = GraphGeometry.planHeightPx(graph.plan, density)

        val drawable = GraphDrawable(resources).apply {
            setGraph(graph)
            setBounds(0, 0, width, height)
        }

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.draw(canvas)

        val sharedDirectory = File(cacheDir, "shared").apply { mkdirs() }
        val outputFile = File(sharedDirectory, "networkdk_graph.png")
        FileOutputStream(outputFile).use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
        }
        return outputFile
    }

    private fun showMessage(messageResId: Int) {
        Snackbar.make(binding.root, messageResId, Snackbar.LENGTH_SHORT).show()
    }

    private class SimpleSeekBarListener(
        private val onProgressChanged: (Int) -> Unit,
    ) : android.widget.SeekBar.OnSeekBarChangeListener {
        override fun onProgressChanged(
            seekBar: android.widget.SeekBar?,
            progress: Int,
            fromUser: Boolean,
        ) {
            onProgressChanged(progress)
        }

        override fun onStartTrackingTouch(seekBar: android.widget.SeekBar?) = Unit

        override fun onStopTrackingTouch(seekBar: android.widget.SeekBar?) = Unit
    }

    companion object {
        private const val STATE_GRAPH_JSON = "state_graph_json"
        private const val STATE_MODE = "state_mode"
        private const val DEFAULT_CONNECTION_STROKE_DP = 6f
        private val DEFAULT_CONNECTION_COLOR = NetworkColor.BLACK
        private val DEFAULT_NODE_COLOR = NetworkColor.BLUE
    }
}
