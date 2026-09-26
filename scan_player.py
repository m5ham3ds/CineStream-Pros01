import re
import os

files_step_4 = [
    "app/src/main/java/com/example/ui/screens/player/PlayerScreen.kt",
    "app/src/main/java/com/example/ui/screens/player/ServerSelectionDialog.kt"
]

def check_file(filepath):
    if not os.path.exists(filepath):
        print(f"File not found: {filepath}")
        return
        
    print(f"--- {filepath} ---")
    with open(filepath, "r") as f:
        lines = f.readlines()
        
    for i, line in enumerate(lines):
        if re.search(r'Text\s*\(\s*"([^"]+)"', line):
            print(f"Line {i+1} (Text String): {line.strip()}")
        if re.search(r'contentDescription\s*=\s*"([^"]+)"', line):
            print(f"Line {i+1} (ContentDesc): {line.strip()}")
        if re.search(r'Color\([^)]+\)', line) and 'MaterialTheme.colorScheme' not in line:
            print(f"Line {i+1} (Color): {line.strip()}")
            
for fp in files_step_4:
    check_file(fp)

