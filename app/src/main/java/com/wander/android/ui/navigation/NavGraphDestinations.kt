package com.wander.android.ui.navigation

import androidx.compose.animation.AnimatedContentScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MotionScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.compose.composable
import com.wander.android.ui.components.LocalDestinationScope
import com.wander.android.ui.components.LocalSharedKeyClaims
import com.wander.android.ui.components.SharedKeyClaims
import com.wander.android.ui.components.enteringBlur
import com.wander.android.ui.screens.settings.SettingsCategory

/** Where "Open Settings" goes when the thing that is missing is an Agro server. */
internal val SYNC_SETTINGS = Routes.settingsCategory(SettingsCategory.SYNC.name)

internal fun NavBackStackEntry.route(): String? = destination.route

/**
 * A top-level tab: peers, so they slide along X in the direction of the bar. A tab's root also
 * hears its button being tapped again — see [ProvideTabReselect].
 */
internal fun NavGraphBuilder.tabDestination(
    motion: MotionScheme,
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) = composable(
    route = route,
    arguments = arguments,
    enterTransition = { tabEnter(initialState.route(), targetState.route(), motion) },
    exitTransition = { tabExit(initialState.route(), targetState.route(), motion) },
    popEnterTransition = { tabEnter(initialState.route(), targetState.route(), motion) },
    popExitTransition = { tabExit(initialState.route(), targetState.route(), motion) },
    content = { entry -> BlurredWhileEntering(entry) { ProvideTabReselect(route) { content(entry) } } }
)

/** A screen opened on top of another: shared-axis Z. */
internal fun NavGraphBuilder.detailDestination(
    motion: MotionScheme,
    route: String,
    arguments: List<NamedNavArgument> = emptyList(),
    content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit
) = composable(
    route = route,
    arguments = arguments,
    enterTransition = { detailEnter(motion) },
    exitTransition = { detailExit(motion) },
    popEnterTransition = { detailPopEnter(motion) },
    popExitTransition = { detailPopExit(motion) },
    content = { entry -> BlurredWhileEntering(entry) { content(entry) } }
)

/**
 * The screen a back swipe reveals starts blurred and sharpens with the swipe — see
 * [enteringBlur]. Wraps every destination, so it covers every back in the graph. It also hands
 * the destination's own transition to [LocalDestinationScope], which covers travel on, and notes
 * the touches that land on it for `navigateSettled` ([recordsTouchOrigin]).
 */
@Composable
internal fun AnimatedContentScope.BlurredWhileEntering(entry: NavBackStackEntry, content: @Composable () -> Unit) {
    val claims = remember { SharedKeyClaims() }
    CompositionLocalProvider(LocalDestinationScope provides this, LocalSharedKeyClaims provides claims) {
        Box(
            modifier = Modifier.fillMaxSize().recordsTouchOrigin(entry).inertWhileLeaving(this).enteringBlur(this)
        ) { content() }
    }
}
