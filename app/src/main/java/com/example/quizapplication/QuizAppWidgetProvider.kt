package com.example.quizapplication

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import android.widget.RemoteViews
import kotlin.random.Random

class QuizAppWidgetProvider : AppWidgetProvider() {
    companion object {
        private const val TAG = "QuizAppWidgetProvider"
        private const val PREFS_NAME = "QuizWidgetPrefs"
        private const val KEY_STATE = "state"
        private const val KEY_SCORE = "score"
        private const val KEY_CORRECT = "correct"
        private const val KEY_WRONG = "wrong"
        private const val KEY_QUESTION = "question"
        private const val KEY_OPTION1 = "option1"
        private const val KEY_OPTION2 = "option2"
        private const val KEY_CORRECT_ANSWER = "correct_answer"
        private const val KEY_TIMER_START = "timer_start"
        private const val KEY_TIMER_LEFT = "timer_left"
        private const val KEY_LAST_UPDATE_TIME = "last_update_time"
        private const val STATE_START = "start"
        private const val STATE_QUIZ = "quiz"
        private const val STATE_END = "end"
        private const val ACTION_START = "com.example.quizapplication.ACTION_START"
        private const val ACTION_ANSWER = "com.example.quizapplication.ACTION_ANSWER"
        private const val ACTION_TIMER_TICK = "com.example.quizapplication.ACTION_TIMER_TICK"
        private const val ACTION_RESTART = "com.example.quizapplication.ACTION_RESTART"
        private const val ACTION_NEXT_QUESTION = "com.example.quizapplication.ACTION_NEXT_QUESTION"
        private const val ACTION_REENABLE_BUTTONS = "com.example.quizapplication.ACTION_REENABLE_BUTTONS"
        private const val EXTRA_ANSWER = "com.example.quizapplication.EXTRA_ANSWER"
        private const val TIMER_TOTAL = 10_000L // 10 seconds in ms
        private const val TIMER_INTERVAL = 50L // 50ms for ultra-fluid updates (was 100ms)
        private const val FEEDBACK_DELAY = 100L // 100ms for better UX
        private const val TIMER_REQUEST_CODE = 1001
        private const val FEEDBACK_REQUEST_CODE = 1002
        private const val MIN_UPDATE_INTERVAL = 2L // 2ms minimum between updates (was 5ms)
        private const val MAX_TIMER_DRIFT = 2000L // Maximum allowed timer drift in ms
    }

    private fun canScheduleExactAlarms(alarmManager: AlarmManager): Boolean {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true // For older versions, assume we can schedule exact alarms
        }
    }

    private fun scheduleAlarm(alarmManager: AlarmManager, pendingIntent: PendingIntent, delay: Long) {
        val nextUpdateTime = SystemClock.elapsedRealtime() + delay
        
        if (canScheduleExactAlarms(alarmManager)) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, nextUpdateTime, pendingIntent)
        } else {
            // Fallback to inexact alarm
            alarmManager.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, nextUpdateTime, pendingIntent)
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            try {
                val prefs = getSharedPreferences(context)
                val state = prefs.getString(KEY_STATE, STATE_START) ?: STATE_START
                
                when (state) {
                    STATE_START -> updateStartScreen(context, appWidgetManager, appWidgetId)
                    STATE_QUIZ -> updateQuizScreen(context, appWidgetManager, appWidgetId)
                    STATE_END -> updateEndScreen(context, appWidgetManager, appWidgetId)
                    else -> updateStartScreen(context, appWidgetManager, appWidgetId)
                }
            } catch (e: Exception) {
                updateStartScreen(context, appWidgetManager, appWidgetId)
            }
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        
        try {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val thisWidget = ComponentName(context, QuizAppWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(thisWidget)
            val prefs = getSharedPreferences(context)
            
            when (intent.action) {
                ACTION_START -> handleStartAction(context, prefs, appWidgetManager, appWidgetIds)
                ACTION_ANSWER -> handleAnswerAction(context, intent, prefs, appWidgetManager, appWidgetIds)
                ACTION_TIMER_TICK -> handleTimerTick(context, prefs, appWidgetManager, appWidgetIds)
                ACTION_RESTART -> handleRestartAction(context, prefs, appWidgetManager, appWidgetIds)
                ACTION_NEXT_QUESTION -> handleNextQuestion(context, prefs, appWidgetManager, appWidgetIds)
                ACTION_REENABLE_BUTTONS -> handleReenableButtons(context, prefs, appWidgetManager, appWidgetIds)
            }
        } catch (e: Exception) {
            // Silent error handling
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        cancelAllAlarms(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        cancelAllAlarms(context)
        getSharedPreferences(context).edit().clear().apply()
    }

    private fun handleStartAction(context: Context, prefs: SharedPreferences, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        cancelAllAlarms(context)
        
        prefs.edit().apply {
            clear()
            putString(KEY_STATE, STATE_QUIZ)
            putInt(KEY_SCORE, 0)
            putInt(KEY_CORRECT, 0)
            putInt(KEY_WRONG, 0)
            apply()
        }
        
        startNewQuestion(context, prefs, appWidgetManager, appWidgetIds)
    }

    private fun handleAnswerAction(context: Context, intent: Intent, prefs: SharedPreferences, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val answer = intent.getIntExtra(EXTRA_ANSWER, -1)
        val correct = prefs.getInt(KEY_CORRECT_ANSWER, -1)
        val state = prefs.getString(KEY_STATE, STATE_START) ?: STATE_START
        
        if (state != STATE_QUIZ || answer == -1 || correct == -1) return
        
        cancelTimer(context)
        
        if (answer == correct) {
            val score = prefs.getInt(KEY_SCORE, 0) + 10
            val correctCount = prefs.getInt(KEY_CORRECT, 0) + 1
            
            prefs.edit().apply {
                putInt(KEY_SCORE, score)
                putInt(KEY_CORRECT, correctCount)
                apply()
            }
            
            showFeedbackAndNext(context, appWidgetManager, appWidgetIds, answer, true)
        } else {
            val wrongCount = prefs.getInt(KEY_WRONG, 0) + 1
            prefs.edit().putInt(KEY_WRONG, wrongCount).apply()
            
            showFeedbackAndNext(context, appWidgetManager, appWidgetIds, answer, false)
        }
    }

    private fun handleTimerTick(context: Context, prefs: SharedPreferences, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val timerStart = prefs.getLong(KEY_TIMER_START, 0L)
        val currentTime = SystemClock.elapsedRealtime()
        val elapsed = currentTime - timerStart
        val timeLeft = (TIMER_TOTAL - elapsed).coerceAtLeast(0L)
        
        // Check for timer drift and recover if necessary
        if (elapsed > TIMER_TOTAL + MAX_TIMER_DRIFT) {
            // Timer has drifted too much, restart the question
            startNewQuestion(context, prefs, appWidgetManager, appWidgetIds)
            return
        }
        
        if (timeLeft > 0) {
            prefs.edit().apply {
                putLong(KEY_TIMER_LEFT, timeLeft)
                putLong(KEY_LAST_UPDATE_TIME, currentTime)
                apply()
            }
            
            // Update timer progress immediately for fluid animation
            updateTimerProgress(context, appWidgetManager, appWidgetIds, timeLeft)
            scheduleTimer(context)
        } else {
            // Time's up, end quiz
            prefs.edit().putString(KEY_STATE, STATE_END).apply()
            
            for (appWidgetId in appWidgetIds) {
                updateEndScreen(context, appWidgetManager, appWidgetId)
            }
        }
    }

    private fun updateTimerProgress(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray, timeLeft: Long) {
        val percent = (timeLeft.toFloat() / TIMER_TOTAL).coerceIn(0f, 1f)
        val progressValue = (percent * 100).toInt().coerceIn(0, 100)
        
        for (appWidgetId in appWidgetIds) {
            try {
                val views = RemoteViews(context.packageName, R.layout.widget_quiz)
                // Update progress bar for fluid animation
                views.setProgressBar(R.id.widget_timer_progress, 100, progressValue, false)
                appWidgetManager.partiallyUpdateAppWidget(appWidgetId, views)
            } catch (e: Exception) {
                // Fallback to full update if partial update fails
                updateQuizScreen(context, appWidgetManager, appWidgetId)
            }
        }
    }

    private fun handleRestartAction(context: Context, prefs: SharedPreferences, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        cancelAllAlarms(context)
        
        prefs.edit().apply {
            clear()
            putString(KEY_STATE, STATE_START)
            apply()
        }
        
        for (appWidgetId in appWidgetIds) {
            updateStartScreen(context, appWidgetManager, appWidgetId)
        }
    }

    private fun handleNextQuestion(context: Context, prefs: SharedPreferences, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        startNewQuestion(context, prefs, appWidgetManager, appWidgetIds)
    }

    private fun handleReenableButtons(context: Context, prefs: SharedPreferences, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val state = prefs.getString(KEY_STATE, STATE_START) ?: STATE_START
        if (state == STATE_QUIZ) {
            val timerStart = prefs.getLong(KEY_TIMER_START, 0L)
            val currentTime = SystemClock.elapsedRealtime()
            val timeLeft = TIMER_TOTAL - (currentTime - timerStart)
            
            if (timeLeft > 0) {
                val newTimerStart = currentTime - (TIMER_TOTAL - timeLeft)
                prefs.edit().putLong(KEY_TIMER_START, newTimerStart).apply()
                
                for (appWidgetId in appWidgetIds) {
                    updateQuizScreen(context, appWidgetManager, appWidgetId)
                }
                
                scheduleTimer(context)
            } else {
                prefs.edit().putString(KEY_STATE, STATE_END).apply()
                for (appWidgetId in appWidgetIds) {
                    updateEndScreen(context, appWidgetManager, appWidgetId)
                }
            }
        }
    }

    private fun showFeedbackAndNext(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray, highlight: Int, isCorrect: Boolean) {
        for (appWidgetId in appWidgetIds) {
            updateQuizScreen(context, appWidgetManager, appWidgetId, highlight, disableButtons = true)
        }
        
        val action = if (isCorrect) ACTION_NEXT_QUESTION else ACTION_REENABLE_BUTTONS
        val intent = Intent(context, QuizAppWidgetProvider::class.java).apply {
            this.action = action
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context, 
            FEEDBACK_REQUEST_CODE, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        
        try {
            if (alarmManager != null) {
                scheduleAlarm(alarmManager, pendingIntent, FEEDBACK_DELAY)
            }
        } catch (e: SecurityException) {
            // Handle security exception by falling back to inexact alarm
            try {
                alarmManager?.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + FEEDBACK_DELAY, pendingIntent)
            } catch (e2: Exception) {
                // Silent error handling
            }
        } catch (e: Exception) {
            // Silent error handling
        }
    }

    private fun updateStartScreen(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        try {
            val views = RemoteViews(context.packageName, R.layout.widget_start)
            views.setOnClickPendingIntent(R.id.widget_start_button, getPendingIntent(context, ACTION_START))
            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            // Silent error handling
        }
    }

    private fun updateQuizScreen(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int, highlight: Int? = null, disableButtons: Boolean = false) {
        try {
            val prefs = getSharedPreferences(context)
            val question = prefs.getString(KEY_QUESTION, "?") ?: "?"
            val option1 = prefs.getInt(KEY_OPTION1, 0)
            val option2 = prefs.getInt(KEY_OPTION2, 0)
            val correct = prefs.getInt(KEY_CORRECT_ANSWER, 0)
            val timerStart = prefs.getLong(KEY_TIMER_START, 0L)
            val currentTime = SystemClock.elapsedRealtime()
            val elapsed = currentTime - timerStart
            val timeLeft = TIMER_TOTAL - elapsed
            val percent = (timeLeft.toFloat() / TIMER_TOTAL).coerceIn(0f, 1f)

            val views = RemoteViews(context.packageName, R.layout.widget_quiz)
            views.setTextViewText(R.id.widget_question, question)
            views.setTextViewText(R.id.widget_answer1, option1.toString())
            views.setTextViewText(R.id.widget_answer2, option2.toString())
            
            // Set button backgrounds safely
            val button1Background = when {
                highlight == 1 && correct == 1 -> R.drawable.filled_button_bg
                highlight == 1 -> R.drawable.red_button_bg
                else -> R.drawable.outlined_button_bg
            }
            
            val button2Background = when {
                highlight == 2 && correct == 2 -> R.drawable.filled_button_bg
                highlight == 2 -> R.drawable.red_button_bg
                else -> R.drawable.outlined_button_bg
            }
            
            views.setInt(R.id.widget_answer1, "setBackgroundResource", button1Background)
            views.setInt(R.id.widget_answer2, "setBackgroundResource", button2Background)
            
            if (!disableButtons) {
                views.setOnClickPendingIntent(R.id.widget_answer1, getAnswerPendingIntent(context, 1))
                views.setOnClickPendingIntent(R.id.widget_answer2, getAnswerPendingIntent(context, 2))
            } else {
                views.setOnClickPendingIntent(R.id.widget_answer1, null)
                views.setOnClickPendingIntent(R.id.widget_answer2, null)
            }
            
            // Update Material Design progress indicator
            val progressValue = (percent * 100).toInt().coerceIn(0, 100)
            views.setProgressBar(R.id.widget_timer_progress, 100, progressValue, false)
            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            // Silent error handling
        }
    }

    private fun updateEndScreen(context: Context, appWidgetManager: AppWidgetManager, appWidgetId: Int) {
        try {
            val prefs = getSharedPreferences(context)
            val score = prefs.getInt(KEY_SCORE, 0)
            
            val views = RemoteViews(context.packageName, R.layout.widget_end)
            views.setTextViewText(R.id.widget_results, "Score: $score")
            views.setOnClickPendingIntent(R.id.widget_restart_button, getPendingIntent(context, ACTION_RESTART))
            appWidgetManager.updateAppWidget(appWidgetId, views)
        } catch (e: Exception) {
            // Silent error handling
        }
    }

    private fun startNewQuestion(context: Context, prefs: SharedPreferences, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        try {
            cancelTimer(context)
            val op = if (Random.nextBoolean()) "+" else "x"
            val a = Random.nextInt(1, 101)
            val b = Random.nextInt(1, 101)
            val correctAnswer = if (op == "+") a + b else a * b
            var wrongAnswer: Int
            var attempts = 0
            do {
                wrongAnswer = correctAnswer + Random.nextInt(-50, 51)
                attempts++
            } while (wrongAnswer == correctAnswer && attempts < 10)
            if (wrongAnswer == correctAnswer) wrongAnswer = correctAnswer + if (correctAnswer > 0) -1 else 1
            
            val options = if (Random.nextBoolean()) listOf(correctAnswer, wrongAnswer) else listOf(wrongAnswer, correctAnswer)
            val correctIndex = if (options[0] == correctAnswer) 1 else 2
            
            prefs.edit().apply {
                putString(KEY_QUESTION, "$a $op $b = ?")
                putInt(KEY_OPTION1, options[0])
                putInt(KEY_OPTION2, options[1])
                putInt(KEY_CORRECT_ANSWER, correctIndex)
                putLong(KEY_TIMER_START, SystemClock.elapsedRealtime())
                putLong(KEY_TIMER_LEFT, TIMER_TOTAL)
                putLong(KEY_LAST_UPDATE_TIME, 0L)
                apply()
            }
            
            for (appWidgetId in appWidgetIds) {
                updateQuizScreen(context, appWidgetManager, appWidgetId)
            }
            scheduleTimer(context)
        } catch (e: Exception) {
            // Silent error handling
        }
    }

    private fun scheduleTimer(context: Context) {
        try {
            val intent = Intent(context, QuizAppWidgetProvider::class.java).apply { action = ACTION_TIMER_TICK }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 
                TIMER_REQUEST_CODE, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            
            if (alarmManager != null) {
                scheduleAlarm(alarmManager, pendingIntent, TIMER_INTERVAL)
            }
        } catch (e: SecurityException) {
            // Handle security exception by falling back to inexact alarm
            try {
                val intent = Intent(context, QuizAppWidgetProvider::class.java).apply { action = ACTION_TIMER_TICK }
                val pendingIntent = PendingIntent.getBroadcast(
                    context, 
                    TIMER_REQUEST_CODE, 
                    intent, 
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                alarmManager?.set(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime() + TIMER_INTERVAL, pendingIntent)
            } catch (e2: Exception) {
                // Silent error handling for timer scheduling
            }
        } catch (e: Exception) {
            // Silent error handling for timer scheduling
        }
    }

    private fun cancelTimer(context: Context) {
        try {
            val intent = Intent(context, QuizAppWidgetProvider::class.java).apply { action = ACTION_TIMER_TICK }
            val pendingIntent = PendingIntent.getBroadcast(
                context, 
                TIMER_REQUEST_CODE, 
                intent, 
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            alarmManager?.cancel(pendingIntent)
        } catch (e: Exception) {
            // Silent error handling for timer cancellation
        }
    }

    private fun cancelAllAlarms(context: Context) {
        cancelTimer(context)
        val feedbackIntent = Intent(context, QuizAppWidgetProvider::class.java)
        val feedbackPendingIntent = PendingIntent.getBroadcast(
            context, 
            FEEDBACK_REQUEST_CODE, 
            feedbackIntent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        alarmManager?.cancel(feedbackPendingIntent)
    }

    private fun getPendingIntent(context: Context, action: String): PendingIntent {
        val intent = Intent(context, QuizAppWidgetProvider::class.java).apply { this.action = action }
        return PendingIntent.getBroadcast(
            context, 
            action.hashCode(), 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getAnswerPendingIntent(context: Context, answer: Int): PendingIntent {
        val intent = Intent(context, QuizAppWidgetProvider::class.java).apply {
            action = ACTION_ANSWER
            putExtra(EXTRA_ANSWER, answer)
        }
        return PendingIntent.getBroadcast(
            context, 
            answer, 
            intent, 
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun getSharedPreferences(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }
}