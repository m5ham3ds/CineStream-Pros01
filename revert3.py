import re

# For these files, I need to remove "        }\n    }\n}" and restore it to "    }\n}" if it exists.
files = [
    "app/src/main/java/com/example/ui/screens/about/AboutScreen.kt",
    "app/src/main/java/com/example/ui/screens/social/SocialScreen.kt",
    "app/src/main/java/com/example/ui/screens/share/ShareScreen.kt"
]
for fp in files:
    with open(fp, "r") as f: c = f.read()
    c = c.replace("        }\n    }\n}", "    }\n}", 1)
    with open(fp, "w") as f: f.write(c)

# For DetailsScreens.kt, I need to replace "            }\n        }\n    }\n}" with "    }\n}"
with open("app/src/main/java/com/example/ui/screens/details/DetailsScreens.kt", "r") as f: c = f.read()
c = c.replace("            }\n        }\n    }\n}", "    }\n}", 1)
with open("app/src/main/java/com/example/ui/screens/details/DetailsScreens.kt", "w") as f: f.write(c)

