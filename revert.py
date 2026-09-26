import re

files_to_fix = [
    ("app/src/main/java/com/example/ui/screens/home/HomeScreen.kt", "MediaScreenSkeleton", "uiState.trendingMovies.isEmpty()"),
    ("app/src/main/java/com/example/ui/screens/movies/MoviesScreen.kt", "MediaScreenSkeleton", "uiState.movies.isEmpty()"),
    ("app/src/main/java/com/example/ui/screens/series/SeriesScreen.kt", "MediaScreenSkeleton", "uiState.series.isEmpty()"),
    ("app/src/main/java/com/example/ui/screens/anime/AnimeScreen.kt", "MediaScreenSkeleton", "uiState.series.isEmpty()"),
]

for filepath, skeleton, empty_check in files_to_fix:
    with open(filepath, "r") as f: c = f.read()
    
    # Revert the if statements
    target = f'    val showSkeleton = uiState.isLoading || !transitionFinished || (uiState.error != null && {empty_check})\n    androidx.compose.animation.Crossfade(targetState = showSkeleton, animationSpec = androidx.compose.animation.core.tween(800), label = "skeleton_fade") {{ loading ->\n        if (loading) {{\n            {skeleton}()\n        }} else {{'
    replacement = f'    if (uiState.isLoading || !transitionFinished) {{\n        {skeleton}()\n        return\n    }}\n    if (uiState.error != null && {empty_check}) {{\n        {skeleton}()\n        return\n    }}'
    c = c.replace(target, replacement)
    
    with open(filepath, "w") as f: f.write(c)

# Remove the incorrectly added braces. Since auto_brace.py balanced them by removing/adding, 
# and then we manipulated them, let's just let auto_brace balance them again after we removed the Crossfade!
# Actually, if we just run auto_brace after reverting the Crossfade, the file will be perfectly restored to original!

