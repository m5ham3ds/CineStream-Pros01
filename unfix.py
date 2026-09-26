import re

files_to_fix = [
    "app/src/main/java/com/example/ui/screens/home/HomeScreen.kt",
    "app/src/main/java/com/example/ui/screens/movies/MoviesScreen.kt",
    "app/src/main/java/com/example/ui/screens/series/SeriesScreen.kt",
    "app/src/main/java/com/example/ui/screens/anime/AnimeScreen.kt"
]

for filepath in files_to_fix:
    with open(filepath, "r") as f:
        content = f.read()
        
    # Find LazyColumn(modifier = Modifier.fillMaxSize()) {
    lazy_header = "LazyColumn(\n        modifier = Modifier.fillMaxSize()\n    ) {"
    if lazy_header not in content:
        continue
        
    start_idx = content.find(lazy_header) + len(lazy_header)
    
    # find matching brace for LazyColumn
    brace_count = 1
    idx = start_idx
    while idx < len(content) and brace_count > 0:
        if content[idx] == '{':
            brace_count += 1
        elif content[idx] == '}':
            brace_count -= 1
        idx += 1
        
    end_idx = idx - 1
    
    lazy_body = content[start_idx:end_idx]
    
    # unwrap
    # lazy_body is composed of: `item {\n    X\n}` or `item {\n    {...}\n}`
    # Wait, the string was just `.split('\n')` and indented.
    # So every line inside `item {\n ... \n}` has 4 spaces added if it's not empty.
    
    # Let's use regex to find all `item {\n(.*?)\n}` (non-greedy, with re.DOTALL)
    # BUT wait! A block `{ ... }` inside `item { ... }` might contain `\n}\n` which would break a simple regex!
    
    # Better way: iterate through `lazy_body`.
    unwrapped = ""
    
    # We can split by `item {\n`
    parts = lazy_body.split("item {\n")
    unwrapped += parts[0] # leading whitespace
    for part in parts[1:]:
        # part ends with `\n}` optionally followed by whitespace.
        # find the last `\n}`
        last_brace = part.rfind("\n}")
        if last_brace != -1:
            inner = part[:last_brace]
            after = part[last_brace+2:]
            
            # un-indent inner
            un_indented = []
            for line in inner.split("\n"):
                if line.startswith("    "):
                    un_indented.append(line[4:])
                else:
                    un_indented.append(line)
            
            unwrapped += "\n".join(un_indented) + after
        else:
            unwrapped += "item {\n" + part
            
    # Reconstruct Column instead of LazyColumn
    new_header = "val scrollState = rememberScrollState()\n    Column(\n        modifier = Modifier\n            .fillMaxSize()\n            .verticalScroll(scrollState)\n    ) {"
    
    new_content = content[:content.find(lazy_header)] + new_header + unwrapped + content[end_idx:]
    with open(filepath, "w") as f:
        f.write(new_content)
        
    print(f"Restored {filepath}")

