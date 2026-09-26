import re
import os

files_step_1 = [
    "app/src/main/java/com/example/ui/screens/home/HomeScreen.kt",
    "app/src/main/java/com/example/ui/screens/movies/MoviesScreen.kt",
    "app/src/main/java/com/example/ui/screens/series/SeriesScreen.kt",
    "app/src/main/java/com/example/ui/screens/anime/AnimeScreen.kt",
    "app/src/main/java/com/example/ui/screens/library/LibraryScreen.kt",
    "app/src/main/java/com/example/ui/screens/profile/ProfileScreen.kt"
]

def check_file(filepath):
    print(f"--- {filepath} ---")
    with open(filepath, "r") as f:
        lines = f.readlines()
        
    for i, line in enumerate(lines):
        # Look for Text("Something")
        if re.search(r'Text\s*\(\s*"([^"]+)"', line):
            print(f"Line {i+1} (Text String): {line.strip()}")
        # Look for contentDescription = "Something"
        if re.search(r'contentDescription\s*=\s*"([^"]+)"', line):
            print(f"Line {i+1} (ContentDesc): {line.strip()}")
        # Look for hardcoded Color(0x...) or Color(...) if not using theme
        if re.search(r'Color\([^)]+\)', line) and 'MaterialTheme.colorScheme' not in line:
            print(f"Line {i+1} (Color): {line.strip()}")
            
for fp in files_step_1:
    check_file(fp)

