import re

with open("app/src/main/res/values/strings.xml", "r") as f:
    c = f.read()

def add_string(name, value):
    global c
    if f'name="{name}"' not in c:
        c = c.replace('</resources>', f'    <string name="{name}">{value}</string>\n</resources>')

add_string('back', 'Back')
add_string('receive', 'Receive')

with open("app/src/main/res/values/strings.xml", "w") as f:
    f.write(c)

