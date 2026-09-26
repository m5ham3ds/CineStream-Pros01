import re
import os

files_to_fix = [
    "app/src/main/java/com/example/ui/screens/home/HomeScreen.kt",
    "app/src/main/java/com/example/ui/screens/movies/MoviesScreen.kt",
    "app/src/main/java/com/example/ui/screens/series/SeriesScreen.kt",
    "app/src/main/java/com/example/ui/screens/anime/AnimeScreen.kt",
    "app/src/main/java/com/example/ui/screens/details/DetailsScreens.kt",
    "app/src/main/java/com/example/ui/screens/about/AboutScreen.kt",
    "app/src/main/java/com/example/ui/screens/social/SocialScreen.kt",
    "app/src/main/java/com/example/ui/screens/share/ShareScreen.kt"
]

for filepath in files_to_fix:
    if not os.path.exists(filepath):
        continue
    with open(filepath, "r") as f:
        c = f.read()

    open_braces = c.count('{')
    close_braces = c.count('}')
    
    diff = close_braces - open_braces
    if diff > 0:
        # We need to remove `diff` number of closing braces from the file
        # We'll remove them from the end where we likely appended them
        for _ in range(diff):
            c = c[::-1].replace("}", "", 1)[::-1]
        
        with open(filepath, "w") as f:
            f.write(c)
        print(f"Fixed extra braces in {filepath}")
    elif diff < 0:
        # We need to add braces at the end
        c += "\n" + "}\n" * (-diff)
        with open(filepath, "w") as f:
            f.write(c)
        print(f"Added missing braces in {filepath}")
