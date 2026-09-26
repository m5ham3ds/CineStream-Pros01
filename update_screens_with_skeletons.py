import re

def insert_skeleton(file_path, skeleton_name):
    try:
        with open(file_path, "r") as f:
            c = f.read()
    except FileNotFoundError:
        return
        
    if skeleton_name in c:
        return
        
    # We want to insert it right after the declarations of variables at the top of the composable.
    # Usually around `val context = LocalContext.current` or `val scope = rememberCoroutineScope()`
    
    deferral_code = f"""
    var transitionFinished by remember {{ androidx.compose.runtime.mutableStateOf(false) }}
    androidx.compose.runtime.LaunchedEffect(Unit) {{
        kotlinx.coroutines.delay(400)
        transitionFinished = true
    }}
    if (!transitionFinished) {{
        com.example.ui.components.{skeleton_name}()
        return
    }}
"""

    # For DownloadsScreen
    if "DownloadsScreen(" in c:
        target = "    val scope = rememberCoroutineScope()"
        if target in c:
            c = c.replace(target, target + deferral_code)
    
    # For ShareScreen
    elif "ShareScreen(" in c:
        target = "    val scope = rememberCoroutineScope()"
        if target in c:
            c = c.replace(target, target + deferral_code)
            
    # For AboutScreen
    elif "AboutScreen(" in c:
        target = "    val context = LocalContext.current"
        if target in c:
            c = c.replace(target, target + deferral_code)
            
    with open(file_path, "w") as f:
        f.write(c)

insert_skeleton("app/src/main/java/com/example/ui/screens/downloads/DownloadsScreen.kt", "DownloadsScreenSkeleton")
insert_skeleton("app/src/main/java/com/example/ui/screens/share/ShareScreen.kt", "ShareScreenSkeleton")
insert_skeleton("app/src/main/java/com/example/ui/screens/about/AboutScreen.kt", "AboutScreenSkeleton")

