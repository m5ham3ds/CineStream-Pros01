import re
import os

files_step_3 = [
    "app/src/main/java/com/example/ui/screens/player/PlayerScreen.kt",
    "app/src/main/java/com/example/ui/screens/player/ServerSelectionDialog.kt",
    "app/src/main/java/com/example/ui/screens/details/DetailsScreens.kt",
    "app/src/main/java/com/example/ui/screens/details/PersonDetailsScreen.kt",
    "app/src/main/java/com/example/ui/screens/profile/EditProfileScreen.kt",
    "app/src/main/java/com/example/ui/screens/profile/SubscriptionScreen.kt",
    "app/src/main/java/com/example/ui/screens/profile/PublicProfileScreen.kt",
    "app/src/main/java/com/example/ui/screens/profile/SecurityScreen.kt",
    "app/src/main/java/com/example/ui/screens/profile/HelpSupportScreen.kt",
    "app/src/main/java/com/example/ui/screens/crash/CrashActivity.kt"
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
            
for fp in files_step_3:
    check_file(fp)

