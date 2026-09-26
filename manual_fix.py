import re

def fix(filepath, func_name, braces_count):
    with open(filepath, "r") as f: c = f.read()
    # replace all braces right before @Composable\nfun func_name
    braces_str = "\n" + ("    }\n" * braces_count)
    c = re.sub(r'(\s+\})+\n@Composable\nfun ' + func_name, braces_str + '@Composable\nfun ' + func_name, c)
    with open(filepath, "w") as f: f.write(c)
    
fix("app/src/main/java/com/example/ui/screens/about/AboutScreen.kt", "FeatureCard", 3)
fix("app/src/main/java/com/example/ui/screens/share/ShareScreen.kt", "RowScope.ContentTypeCard", 3)
fix("app/src/main/java/com/example/ui/screens/social/SocialScreen.kt", "CustomFilterChip", 3)
fix("app/src/main/java/com/example/ui/screens/details/DetailsScreens.kt", "SeriesDetailsScreen", 3)
fix("app/src/main/java/com/example/ui/screens/details/DetailsScreens.kt", "AnimatedDownloadIcon", 4)
