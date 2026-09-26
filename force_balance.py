import re
import sys

def balance_file(filepath, breakpoints):
    with open(filepath, "r") as f:
        c = f.read()

    chunks = []
    current_idx = 0
    for bp in breakpoints:
        match = re.search(r'\n@Composable\s*\nfun ' + bp, c[current_idx:])
        if not match:
            # fallback
            match = re.search(r'\n(private )?fun ' + bp, c[current_idx:])
            
        if match:
            idx = current_idx + match.start()
            chunk = c[current_idx:idx]
            
            # calculate open vs close in chunk
            open_count = chunk.count('{')
            close_count = chunk.count('}')
            diff = open_count - close_count
            
            if diff > 0:
                # append missing closing braces
                chunk += "\n" + ("    }\n" * diff)
            elif diff < 0:
                # remove extra closing braces
                for _ in range(-diff):
                    chunk = chunk[::-1].replace("}", "", 1)[::-1]
                    
            chunks.append(chunk)
            current_idx = current_idx + match.end()
            # Wait, the match itself contains the function definition, we should append it to the NEXT chunk
            # Actually, we can just split the file string using regex
            
    # let's do this simpler:
    with open(filepath, "r") as f:
        content = f.read()
    
    parts = re.split(r'(?=\n@Composable\s*\nfun |\nprivate fun )', content)
    
    new_content = ""
    for i, part in enumerate(parts):
        open_count = part.count('{')
        close_count = part.count('}')
        if i < len(parts) - 1:
            diff = open_count - close_count
            if diff > 0:
                part += ("}\n" * diff)
            elif diff < 0:
                for _ in range(-diff):
                    part = part[::-1].replace("}", "", 1)[::-1]
        else:
            # For the last part, balance it to 0
            diff = open_count - close_count
            if diff > 0:
                part += ("}\n" * diff)
            elif diff < 0:
                for _ in range(-diff):
                    part = part[::-1].replace("}", "", 1)[::-1]
                    
        new_content += part

    with open(filepath, "w") as f:
        f.write(new_content)

balance_file("app/src/main/java/com/example/ui/screens/details/DetailsScreens.kt", [])
balance_file("app/src/main/java/com/example/ui/screens/share/ShareScreen.kt", [])
balance_file("app/src/main/java/com/example/ui/screens/social/SocialScreen.kt", [])
balance_file("app/src/main/java/com/example/ui/screens/about/AboutScreen.kt", [])

