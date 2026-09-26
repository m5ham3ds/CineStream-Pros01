import re
with open("app/src/main/java/com/example/ui/screens/social/SocialScreen.kt", "r") as f: c = f.read()
c = re.sub(r'(\s+\})+\nprivate fun formatTime', '\n    }\n}\n}\nprivate fun formatTime', c)
with open("app/src/main/java/com/example/ui/screens/social/SocialScreen.kt", "w") as f: f.write(c)

with open("app/src/main/java/com/example/navigation/AppNavigation.kt", "r") as f: c = f.read()
# Let's see the error in AppNavigation
