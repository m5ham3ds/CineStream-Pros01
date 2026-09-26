import re
with open("app/src/main/java/com/example/ui/screens/details/DetailsScreens.kt", "r") as f: c = f.read()
c = re.sub(r'(\s+\})+\n@Composable\nfun SeriesDetailsScreen', '\n    }\n    }\n    }\n}\n@Composable\nfun SeriesDetailsScreen', c)
with open("app/src/main/java/com/example/ui/screens/details/DetailsScreens.kt", "w") as f: f.write(c)
