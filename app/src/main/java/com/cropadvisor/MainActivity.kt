package com.cropadvisor

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.cropadvisor.ui.AboutScreen
import com.cropadvisor.ui.AnalysingScreen
import com.cropadvisor.ui.LoginScreen
import com.cropadvisor.ui.AppTheme
import com.cropadvisor.ui.CameraScreen
import com.cropadvisor.ui.LanguageScreen
import com.cropadvisor.ui.MainShell
import com.cropadvisor.ui.Palette
import com.cropadvisor.ui.ResultScreen
import com.cropadvisor.ui.SplashScreen

enum class Route { MAIN, CAMERA, ANALYSING, RESULT, ABOUT }

class MainActivity : ComponentActivity() {

    private lateinit var state: AppState

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        state = AppState.get(applicationContext)

        setContent {
            val dark = when (state.theme) { "dark" -> true; "light" -> false; else -> androidx.compose.foundation.isSystemInDarkTheme() }
            AppTheme(dark) {
                var route by remember { mutableStateOf(Route.MAIN) }
                var tab by remember { mutableStateOf(0) }
                val ready = state.visionReady && state.llmDone

                fun startAnalysis(bmp: android.graphics.Bitmap) {
                    route = Route.ANALYSING
                    state.analyse(bmp, onDone = { route = Route.RESULT }, onError = { route = Route.MAIN })
                }

                BackHandler(enabled = route != Route.MAIN || tab != 0) {
                    when (route) {
                        Route.MAIN -> tab = 0
                        Route.ANALYSING -> {}
                        Route.RESULT -> { state.stopAdvice(); route = Route.MAIN }
                        else -> route = Route.MAIN
                    }
                }

                androidx.compose.runtime.LaunchedEffect(state.user?.phone) { route = Route.MAIN; tab = 0 }
                androidx.compose.runtime.SideEffect {
                    androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
                        .isAppearanceLightStatusBars = !dark && when {
                            !ready || state.user == null -> ready && !state.langChosen   // only the language picker is cream
                            route == Route.MAIN -> tab == 1 || tab == 2       // cream screens need dark icons
                            else -> false                                       // photo / gradient headers
                        }
                }
                // 0 splash · 1 language · 2 sign-in · 3 app — cross-faded so nothing "jumps"
                val stage = when { !ready -> 0; !state.langChosen -> 1; state.user == null -> 2; else -> 3 }
                Box(Modifier.fillMaxSize().background(Palette.Cream)) {
                    Crossfade(stage, animationSpec = tween(450), label = "stage") { st ->
                    when (st) {
                        0 -> SplashScreen(state)
                        1 -> LanguageScreen(state, first = true) {}
                        2 -> LoginScreen(state)
                        else -> AnimatedContent(route, label = "route",
                            transitionSpec = {
                                val deeper = targetState.ordinal > initialState.ordinal
                                (slideInHorizontally(tween(360)) { w -> if (deeper) w / 4 else -w / 4 } + fadeIn(tween(360))) togetherWith
                                    (slideOutHorizontally(tween(300)) { w -> if (deeper) -w / 6 else w / 6 } + fadeOut(tween(220)))
                            }) { r ->
                            when (r) {
                                Route.MAIN -> MainShell(
                                    state, tab, onTab = { tab = it },
                                    onScan = { route = Route.CAMERA },
                                    onPicked = { startAnalysis(it) },
                                    onOpenScan = { state.open(it); route = Route.RESULT },
                                    onAbout = { route = Route.ABOUT },
                                )
                                Route.CAMERA -> CameraScreen(state,
                                    onBack = { route = Route.MAIN },
                                    onCaptured = { startAnalysis(it) })
                                Route.ANALYSING -> AnalysingScreen(state)
                                Route.ABOUT -> AboutScreen(state, onBack = { route = Route.MAIN })
                                Route.RESULT -> ResultScreen(state,
                                    onBack = { state.stopAdvice(); route = Route.MAIN },
                                    onRetake = { state.stopAdvice(); route = Route.CAMERA })
                            }
                        }
                    }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (isFinishing) state.stopAdvice()     // the engine itself stays loaded for a fast re-open
        super.onDestroy()
    }
}
