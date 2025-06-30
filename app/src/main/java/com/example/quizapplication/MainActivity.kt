package com.example.quizapplication

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : AppCompatActivity() {
    lateinit var addition: Button
    lateinit var subtraction: Button
    lateinit var multiplication: Button

    companion object {
        const val KEY_GAME_TYPE = "game_type"
        const val TYPE_ADDITION = "addition"
        const val TYPE_SUBTRACTION = "subtraction"
        const val TYPE_MULTIPLICATION = "multiplication"

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        addition = findViewById(R.id.button1)
        subtraction = findViewById(R.id.button2)
        multiplication = findViewById(R.id.button3)

        addition.setOnClickListener {
            startGame(TYPE_ADDITION)
        }
        subtraction.setOnClickListener {
            startGame(TYPE_SUBTRACTION)
        }
        multiplication.setOnClickListener {
            startGame(TYPE_MULTIPLICATION)
        }
    }
    private fun startGame(gameType:String){
        val intent = Intent(this@MainActivity, GameActivity::class.java)
        intent.putExtra(KEY_GAME_TYPE,gameType)
        startActivity(intent)
    }
}