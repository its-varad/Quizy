package com.example.quizapplication

import android.content.Intent
import android.os.Bundle
import android.os.CountDownTimer
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import com.example.quizapplication.R
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.color.MaterialColors
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Locale
import kotlin.random.Random
import kotlin.time.TimeSource



class GameActivity : AppCompatActivity() {

    lateinit var gameType: String
    lateinit var textScore: TextView
    lateinit var textLife: TextView
    lateinit var textTime: TextView

    lateinit var textQuestion: TextView
    lateinit var editTextAnswer: EditText

    lateinit var buttonOk: Button
    lateinit var buttonNext: Button
    var correctAnswer = 0
    var userScore = 0
    var userLife =3

    lateinit var timer: CountDownTimer
    private val startTimer : Long = 60000
    var timeLeft : Long = startTimer
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_game)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        gameType = intent.getStringExtra(MainActivity.KEY_GAME_TYPE)?: MainActivity.TYPE_ADDITION
//        supportActionBar!!.title="Addition"
        textScore = findViewById(R.id.total_Score)
        textLife = findViewById(R.id.life_count)
        textTime = findViewById(R.id.time_count)
        textQuestion=findViewById(R.id.question)
        editTextAnswer=findViewById(R.id.editTextAnswer)
        buttonOk = findViewById(R.id.button_ok)
        buttonNext = findViewById(R.id.button_Next)
        gameContinue()



        buttonOk.setOnClickListener {
            val input = editTextAnswer.text.toString()
            if(input==""){
                    showDismissibleSnackbar(findViewById(R.id.main),"Please enter your answer!")
            }
            else{
                pauseTimer()
                buttonOk.isClickable=false
                val userAnswer = input.toInt()

                if(userAnswer==correctAnswer){
                    userScore=userScore+10
                    textScore.text= userScore.toString()
                    lifecycleScope.launch {
                        buttonOk.setBackgroundColor(ContextCompat.getColor(this@GameActivity,R.color.button_correct_green))
                        delay(1000)
                        buttonOk.setBackgroundColor(getDefaultButtonColor())
                    }
                    loadNextQuestion()

                }
                else {

                    loseLife()

                    lifecycleScope.launch {
                        buttonOk.setBackgroundColor(ContextCompat.getColor(this@GameActivity,R.color.button_wrong_red))
                        delay(1000)
                        buttonOk.setBackgroundColor(getDefaultButtonColor())
                    }
                    buttonOk.isClickable=true

                }
            }
        }

        buttonNext.setOnClickListener {
            pauseTimer()
            resetTimer()
            gameContinue()
            editTextAnswer.setText("")
            buttonOk.isClickable=true


        }
    }

    fun gameContinue(){
        when(gameType){
            MainActivity.TYPE_ADDITION -> {
                val number1 =  Random.nextInt(0,100)
                val number2 =  Random.nextInt(0,100)
                textQuestion.text="What is ${number1}+${number2}?"
                correctAnswer=number1+number2
            }
            MainActivity.TYPE_SUBTRACTION -> {
                var number1 =  Random.nextInt(0,100)
                var number2 =  Random.nextInt(0,100)
                if(number1<number2){
                    val temp = number1
                    number1 = number2
                    number2= temp
                }
                textQuestion.text="What is ${number1}-${number2}?"
                correctAnswer=number1-number2
            }
            MainActivity.TYPE_MULTIPLICATION -> {
                val number1 =  Random.nextInt(1,10)
                val number2 =  Random.nextInt(1,10)
                textQuestion.text="What is ${number1}*${number2}?"
                correctAnswer=number1*number2
            }
        }

        startTimer()
    }
    fun startTimer(){
        timer = object : CountDownTimer(timeLeft, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                timeLeft = millisUntilFinished
                updateText()
            }


            override fun onFinish() {
                pauseTimer()
                userLife--
                textLife.text = userLife.toString()


                if (userLife <= 0) {

                    textTime.text = "00"



                    val intent = Intent(this@GameActivity, ResultActivity::class.java)
                    intent.putExtra("score", userScore)
                    startActivity(intent)

                } else {

                    showDismissibleSnackbar(findViewById(R.id.main), "Time is up! You lost a life.")

                    loadNextQuestion()
                }
            }
        }.start()
    }

    fun updateText(){
        val remainingTime: Int = (timeLeft/1000).toInt()
        textTime.text=String.format(Locale.getDefault(),"%02d",remainingTime)

    }
    fun pauseTimer(){
        timer.cancel()
    }
    fun resetTimer(){
        timeLeft=startTimer
        updateText()
    }
    fun endGame(){
        pauseTimer()
        buttonOk.isClickable=false
        buttonNext.isClickable=false
        val intent = Intent(this@GameActivity, ResultActivity::class.java)
        intent.putExtra("score",userScore)
        startActivity(intent)
        finish()
    }
    fun loseLife(){
        userLife--
        textLife.text=userLife.toString()
        if(userLife==0){
            endGame()
        }
    }
    private fun loadNextQuestion(){
        pauseTimer()
        resetTimer()
        gameContinue()
        editTextAnswer.setText("")
        buttonOk.isClickable=true
    }
    private fun getDefaultButtonColor():Int{
        return MaterialColors.getColor(
            this,com.google.android.material.R.attr.colorPrimary,
            ContextCompat.getColor(this, R.color.dark)
        )
    }

    fun showDismissibleSnackbar(view: View, message:String){
        val snackbar = Snackbar.make(view,message,Snackbar.LENGTH_INDEFINITE)
        snackbar.setAction("Close"){

        }
        snackbar.show()
    }
}