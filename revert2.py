import re

def revert(filepath, target, replacement):
    with open(filepath, "r") as f: c = f.read()
    c = c.replace(target, replacement)
    with open(filepath, "w") as f: f.write(c)

revert("app/src/main/java/com/example/ui/screens/about/AboutScreen.kt",
       '    androidx.compose.animation.Crossfade(targetState = !transitionFinished, animationSpec = androidx.compose.animation.core.tween(800), label = "fade") { loading ->\n        if (loading) {\n            com.example.ui.components.AboutScreenSkeleton()\n        } else {',
       '    if (!transitionFinished) {\n        com.example.ui.components.AboutScreenSkeleton()\n        return\n    }')

revert("app/src/main/java/com/example/ui/screens/social/SocialScreen.kt",
       '    androidx.compose.animation.Crossfade(targetState = isLoading || !transitionFinished, animationSpec = androidx.compose.animation.core.tween(800), label = "fade") { loading ->\n        if (loading) {\n            com.example.ui.components.SocialScreenSkeleton()\n        } else {',
       '    if (isLoading || !transitionFinished) {\n        com.example.ui.components.SocialScreenSkeleton()\n        return\n    }')

revert("app/src/main/java/com/example/ui/screens/share/ShareScreen.kt",
       '    androidx.compose.animation.Crossfade(targetState = !transitionFinished, animationSpec = androidx.compose.animation.core.tween(800), label = "fade") { loading ->\n        if (loading) {\n            com.example.ui.components.ShareScreenSkeleton()\n        } else {',
       '    if (!transitionFinished) {\n        com.example.ui.components.ShareScreenSkeleton()\n        return\n    }')

with open("app/src/main/java/com/example/ui/screens/details/DetailsScreens.kt", "r") as f: c = f.read()
target_movie_cond = '''        val showSkeleton = uiState.isLoading || !transitionFinished || uiState.movie == null
        androidx.compose.animation.Crossfade(targetState = showSkeleton, animationSpec = androidx.compose.animation.core.tween(800), label = "fade") { loading ->
            if (loading) {
                DetailsSkeleton()
            } else {'''
replacement_movie_cond = '''        if (uiState.isLoading || !transitionFinished || uiState.movie == null) {
            DetailsSkeleton()
        } else if (uiState.movie != null) {'''
c = c.replace(target_movie_cond, replacement_movie_cond)

target_series_cond = '''        val series = uiState.series
        val showSkeleton = uiState.isLoading || !transitionFinished || series == null
        androidx.compose.animation.Crossfade(targetState = showSkeleton, animationSpec = androidx.compose.animation.core.tween(800), label = "fade") { loading ->
            if (loading) {
                DetailsSkeleton()
            } else {'''
replacement_series_cond = '''        val series = uiState.series
        if ((uiState.isLoading || !transitionFinished) && series == null) {
            DetailsSkeleton()
            return@PullToRefreshBox
        }
        if (series == null && !uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(uiState.error ?: "Failed to load details", color = MaterialTheme.colorScheme.error)
            }
            return@PullToRefreshBox
        }
        if (series != null) {'''
c = c.replace(target_series_cond, replacement_series_cond)
with open("app/src/main/java/com/example/ui/screens/details/DetailsScreens.kt", "w") as f: f.write(c)

