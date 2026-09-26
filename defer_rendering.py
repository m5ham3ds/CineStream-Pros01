import re
import glob

def apply_deferral(file_path, skeleton_call):
    with open(file_path, "r") as f:
        c = f.read()

    # If it already has some launched effect for loading, skip it
    if "transitionFinished" in c:
        return

    # Pattern for screens that have uiState.isLoading check
    target1 = """    if (uiState.isLoading) {
        """ + skeleton_call + """()
        return
    }"""
    
    replacement1 = """    var transitionFinished by remember { androidx.compose.runtime.mutableStateOf(false) }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        kotlinx.coroutines.delay(350)
        transitionFinished = true
    }

    if (uiState.isLoading || !transitionFinished) {
        """ + skeleton_call + """()
        return
    }"""
    
    if target1 in c:
        c = c.replace(target1, replacement1)
        with open(file_path, "w") as f:
            f.write(c)

apply_deferral("app/src/main/java/com/example/ui/screens/home/HomeScreen.kt", "MediaScreenSkeleton")
apply_deferral("app/src/main/java/com/example/ui/screens/movies/MoviesScreen.kt", "MediaScreenSkeleton")
apply_deferral("app/src/main/java/com/example/ui/screens/series/SeriesScreen.kt", "MediaScreenSkeleton")
apply_deferral("app/src/main/java/com/example/ui/screens/anime/AnimeScreen.kt", "MediaScreenSkeleton")

