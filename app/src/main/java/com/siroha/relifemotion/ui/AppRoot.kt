package com.siroha.relifemotion.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.Crossfade
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavBackStackEntry
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.siroha.relifemotion.ui.editor.EditorScreen
import com.siroha.relifemotion.ui.home.AccountScreen
import com.siroha.relifemotion.ui.home.HomeScreen
import com.siroha.relifemotion.ui.home.ReLifeLogo
import kotlinx.coroutines.delay

@Composable
fun AppRoot() {
    var booted by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(1000); booted = true }
    Crossfade(targetState = booted, label = "boot") { ready ->
        if (!ready) Boot() else Nav()
    }
}

@Composable
private fun Boot() {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize().background(cs.background), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            ReLifeLogo(size = 30.sp)
            Spacer(Modifier.height(28.dp))
            LoadingIndicator(Modifier.size(64.dp))
            Spacer(Modifier.height(14.dp))
            Text("Memuat…", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = cs.onSurfaceVariant)
        }
    }
}

private val spatial = spring<IntOffset>(dampingRatio = 0.82f, stiffness = 420f)

@Composable
private fun Nav() {
    val nav = rememberNavController()
    val enter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(spatial) { it / 5 } + fadeIn(spring(stiffness = 600f))
    }
    val exit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(spatial) { -it / 8 } + fadeOut(spring(stiffness = 700f))
    }
    val popEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
        slideInHorizontally(spatial) { -it / 8 } + fadeIn(spring(stiffness = 600f))
    }
    val popExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
        slideOutHorizontally(spatial) { it / 5 } + fadeOut(spring(stiffness = 700f))
    }
    NavHost(
        navController = nav, startDestination = "home",
        enterTransition = enter, exitTransition = exit,
        popEnterTransition = popEnter, popExitTransition = popExit
    ) {
        composable("home") {
            HomeScreen(
                onOpenProject = { id -> nav.navigate("editor/$id") },
                onAccount = { nav.navigate("account") }
            )
        }
        composable("editor/{id}") { entry ->
            EditorScreen(id = entry.arguments?.getString("id") ?: "", onBack = { nav.popBackStack() })
        }
        composable("account") { AccountScreen(onClose = { nav.popBackStack() }) }
    }
}
