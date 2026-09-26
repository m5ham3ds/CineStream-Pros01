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

    # The issue with unfix.py was that our fix script was very broken.
    # Let's just wrap everything inside Column() in a SINGLE `item { Column { ... } }` for now?
    # NO, that doesn't fix lazy loading!
    
    # We must properly wrap EACH section in an `item`.
    # Let's write a python parser that parses brackets.
    
    # Find Column(modifier = Modifier.fillMaxSize().verticalScroll(scrollState)) {
    match = re.search(r'val scrollState = rememberScrollState\(\)\s*Column\(\s*modifier\s*=\s*Modifier[\s\S]*?\.verticalScroll\(scrollState\)[\s\S]*?\)\s*\{', content)
    if not match:
        continue
        
    start_idx = match.start()
    end_idx = match.end()
    
    new_header = "LazyColumn(\n        modifier = Modifier.fillMaxSize()\n    ) {"
    
    brace_count = 1
    idx = end_idx
    while idx < len(content) and brace_count > 0:
        if content[idx] == '{':
            brace_count += 1
        elif content[idx] == '}':
            brace_count -= 1
        idx += 1
        
    col_end_idx = idx - 1
    
    col_body = content[end_idx:col_end_idx]
    
    # Let's tokenize by looking for `\n` followed by 8 spaces or so.
    # Actually, a simple approach:
    # We find all statements that start at depth = 0.
    
    parsed = []
    i = 0
    depth = 0
    in_string = False
    in_comment = False
    
    current_node = ""
    
    while i < len(col_body):
        c = col_body[i]
        
        if c == '"' and not in_comment and (i == 0 or col_body[i-1] != '\\'):
            in_string = not in_string
            
        if not in_string:
            if c == '/' and i+1 < len(col_body) and col_body[i+1] == '/':
                # line comment
                eol = col_body.find('\n', i)
                if eol == -1: eol = len(col_body)
                current_node += col_body[i:eol]
                i = eol
                continue
                
            if c == '{': depth += 1
            if c == '}': depth -= 1
            
        current_node += c
        i += 1
        
        # We complete a node when depth == 0 and we hit a blank line or a comment or a known top-level construct.
        # But wait, Kotlin functions can span multiple lines.
        # Let's just say a node completes when depth == 0 AND we hit a newline followed by `        if` or `        Spacer` or `        SectionTitle` or `        LazyRow` or `        val` or `        //`
        if depth == 0 and c == '\n':
            # look at next non-ws token
            temp = i
            while temp < len(col_body) and col_body[temp].isspace():
                temp += 1
            
            if temp == len(col_body):
                parsed.append(current_node)
                current_node = ""
                break
                
            next_word_match = re.match(r'([a-zA-Z_]+|//)', col_body[temp:])
            if next_word_match:
                word = next_word_match.group(1)
                if word in ['if', 'Spacer', 'SectionTitle', 'LazyRow', 'val', 'Row', 'Column', 'Box', 'Text', 'HeroCarousel', 'var', 'data', 'PullToRefreshBox', 'else', '//']:
                    # except if it's 'else', 'else' belongs to previous 'if'
                    if word != 'else':
                        parsed.append(current_node)
                        current_node = ""

    if current_node:
        parsed.append(current_node)
        
    wrapped = ""
    for p in parsed:
        if p.strip() == "":
            wrapped += p
        elif p.strip().startswith("//") and "\n" not in p.strip():
            wrapped += p
        else:
            # indent it
            lines = p.split('\n')
            indented = "\n".join("    " + line if line.strip() else line for line in lines)
            # wait, if p is just a comment, don't wrap? We should wrap it if it has code.
            if re.search(r'\w', p.replace("//", "")): # rough check
                wrapped += f"\n        item {{\n{indented}\n        }}"
            else:
                wrapped += p

    # Fix imports
    if "import androidx.compose.foundation.lazy.LazyColumn" not in content:
        content = content.replace("import androidx.compose.foundation.layout.*", "import androidx.compose.foundation.layout.*\nimport androidx.compose.foundation.lazy.LazyColumn")
        
    new_content = content[:start_idx] + new_header + wrapped + content[col_end_idx:]
    with open(filepath, "w") as f:
        f.write(new_content)
        
