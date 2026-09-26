import re

with open("app/src/main/java/com/example/navigation/AppNavigation.kt", "r") as f:
    content = f.read()

# We need to replace the enterTransition, exitTransition, popEnterTransition, popExitTransition
# inside the NavHost

target_nav_host = """            enterTransition = { 
                val route = targetState.destination.route ?: ""
                if (topLevelRoutes.any { route.startsWith(it) }) {
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
                if (topLevelRoutes.any { route.startsWith(it) }) {
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
                    androidx.compose.animation.fadeIn(animationSpec = androidx.compose.animation.core.tween(400))
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

replacement_nav_host = """            enterTransition = { 
                val route = targetState.destination.route ?: ""
                val initialRoute = initialState.destination.route ?: ""
                if (initialRoute == Screen.Splash.route && route == Screen.Home.route) {
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

if target_nav_host in content:
    content = content.replace(target_nav_host, replacement_nav_host)
    with open("app/src/main/java/com/example/navigation/AppNavigation.kt", "w") as f:
        f.write(content)
    print("Replaced successfully!")
else:
    print("Target not found.")
    
