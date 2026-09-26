import re
import os

files_step_2 = [
    "app/src/main/java/com/example/ui/screens/settings/SettingsScreen.kt",
    "app/src/main/java/com/example/ui/screens/downloads/DownloadsScreen.kt",
    "app/src/main/java/com/example/ui/screens/about/AboutScreen.kt",
    "app/src/main/java/com/example/ui/screens/notifications/NotificationsScreen.kt",
    "app/src/main/java/com/example/ui/screens/search/SearchScreen.kt",
    "app/src/main/java/com/example/ui/screens/share/ShareScreen.kt",
    "app/src/main/java/com/example/ui/screens/social/SocialScreen.kt",
    "app/src/main/java/com/example/ui/screens/auth/AuthScreen.kt",
    "app/src/main/java/com/example/ui/screens/onboarding/OnboardingScreen.kt",
    "app/src/main/java/com/example/ui/screens/splash/SplashScreen.kt",
    "app/src/main/java/com/example/ui/screens/extensions/ExtensionsScreen.kt",
    "app/src/main/java/com/example/ui/screens/extensions/NoExtensionsDialog.kt",
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
            
for fp in files_step_2:
    check_file(fp)

