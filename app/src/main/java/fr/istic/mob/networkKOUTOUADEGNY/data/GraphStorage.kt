package fr.istic.mob.networkKOUTOUADEGNY.data

import android.content.Context
import fr.istic.mob.networkKOUTOUADEGNY.model.Graph

class GraphStorage(context: Context) {
    private val appContext = context.applicationContext

    fun save(graph: Graph) {
        appContext.openFileOutput(FILE_NAME, Context.MODE_PRIVATE).bufferedWriter().use { writer ->
            writer.write(GraphJsonCodec.toJson(graph))
        }
    }

    fun load(): Graph? {
        if (!hasSavedGraph()) {
            return null
        }

        return appContext.openFileInput(FILE_NAME).bufferedReader().use { reader ->
            GraphJsonCodec.fromJson(reader.readText())
        }
    }

    fun hasSavedGraph(): Boolean {
        return appContext.getFileStreamPath(FILE_NAME).exists()
    }

    companion object {
        private const val FILE_NAME = "saved_network_graph.json"
    }
}
