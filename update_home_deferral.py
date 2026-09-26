import re

with open("app/src/main/java/com/example/ui/screens/home/HomeScreen.kt", "r") as f:
    c = f.read()

c = c.replace("kotlinx.coroutines.delay(350)", "kotlinx.coroutines.delay(700)")

with open("app/src/main/java/com/example/ui/screens/home/HomeScreen.kt", "w") as f:
    f.write(c)
