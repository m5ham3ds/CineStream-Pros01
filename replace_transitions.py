import re

with open("app/src/main/java/com/example/navigation/AppNavigation.kt", "r") as f:
    content = f.read()

target_transitions = """                enterTransition = { 
                    androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(400))
                },
                exitTransition = { 
                    androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(400))
                },
                popEnterTransition = { 
                    androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(400))
                },
                popExitTransition = { 
                    androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(400))
                }"""

replacement = """                enterTransition = { 
                    val route = targetState.destination.route ?: ""
                    val initialRoute = initialState.destination.route ?: ""
                    if (initialRoute == Screen.Splash.route && topLevelRoutes.any { route.startsWith(it) }) {
                        androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(700))
                    } else if (topLevelRoutes.any { route.startsWith(it) }) {
                        androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) 
                    } else {
                        slideIntoContainer(
                            towards = androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Start, 
                            animationSpec = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        ) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(400))
                    }
                },
                exitTransition = { 
                    val route = targetState.destination.route ?: ""
                    val initialRoute = initialState.destination.route ?: ""
                    if (initialRoute == Screen.Splash.route) {
                        androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(700))
                    } else if (topLevelRoutes.any { route.startsWith(it) }) {
                        androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300)) 
                    } else {
                        slideOutOfContainer(
                            towards = androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.Start, 
                            animationSpec = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        ) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(400))
                    }
                },
                popEnterTransition = { 
                    val route = targetState.destination.route ?: ""
                    if (topLevelRoutes.any { route.startsWith(it) }) {
                        androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(300)) 
                    } else {
                        slideIntoContainer(
                            towards = androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.End, 
                            animationSpec = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        ) + androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(400))
                    }
                },
                popExitTransition = { 
                    val route = targetState.destination.route ?: ""
                    if (topLevelRoutes.any { route.startsWith(it) }) {
                        androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(300)) 
                    } else {
                        slideOutOfContainer(
                            towards = androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection.End, 
                            animationSpec = androidx.compose.animation.core.tween(400, easing = androidx.compose.animation.core.FastOutSlowInEasing)
                        ) + androidx.compose.animation.fadeOut(animationSpec = androidx.compose.animation.core.tween(400))
                    }
                }"""

content = content.replace(target_transitions, replacement)
with open("app/src/main/java/com/example/navigation/AppNavigation.kt", "w") as f:
    f.write(content)

