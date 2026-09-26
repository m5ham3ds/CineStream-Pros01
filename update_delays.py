import re
import glob

# Update artificial delays to make it snappier (300ms matches transition duration)
for file in glob.glob("app/src/main/java/com/example/ui/screens/**/*.kt", recursive=True):
    with open(file, "r") as f:
        c = f.read()
    
    # We added artificial delays in some files
    new_c = c.replace("kotlinx.coroutines.delay(600)", "kotlinx.coroutines.delay(350)")
    new_c = new_c.replace("kotlinx.coroutines.delay(800)", "kotlinx.coroutines.delay(350)")
    
    if new_c != c:
        with open(file, "w") as f:
            f.write(new_c)

