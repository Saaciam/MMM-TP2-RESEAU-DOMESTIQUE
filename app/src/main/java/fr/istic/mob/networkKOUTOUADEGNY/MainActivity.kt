package fr.istic.mob.networkKOUTOUADEGNY

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.ViewModelProvider
import com.example.NetworkKOUTOUADEGNY.R
import com.example.NetworkKOUTOUADEGNY.databinding.ActivityMainBinding
import fr.istic.mob.networkKOUTOUADEGNY.viewmodel.NetworkViewModel

class MainActivity : AppCompatActivity() {

    // On initialise le ViewBinding et le ViewModel
    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: NetworkViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // On demande au système Android de nous donner le ViewModel
        // S'il existe déjà (après rotation), il nous rend l'ancien.
        viewModel = ViewModelProvider(this).get(NetworkViewModel::class.java)

        // On donne le androidx . lifecycle . ViewModel à notre vue de dessin
        val drawView = findViewById<DrawView>(R.id.my_draw_view)
        drawView.setViewModel(viewModel)

        binding = ActivityMainBinding.inflate(layoutInflater)

        supportActionBar?.title = "Mon Dessin Dynamique"
    }

    // Cette fonction sert à charger ton fichier de menu (le XML que tu m'as montré)
    override fun onCreateOptionsMenu(menu: android.view.Menu?): Boolean {
        // R.menu.nom_de_ton_fichier_menu (vérifie bien le nom du fichier !)
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }
}
