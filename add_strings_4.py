import re

with open("app/src/main/res/values/strings.xml", "r") as f:
    c = f.read()

def add_string(name, value):
    global c
    if f'name="{name}"' not in c:
        c = c.replace('</resources>', f'    <string name="{name}">{value}</string>\n</resources>')

add_string('unlock', 'Unlock')
add_string('menu', 'Menu')
add_string('rewind', 'Rewind')
add_string('forward', 'Forward')
add_string('play_pause', 'Play/Pause')
add_string('no_other_sites', 'No other sites')
add_string('no_other_sites_desc', 'Sorry, failed to search all available sites.')
add_string('cancel_op_title', 'Cancel Operation')
add_string('cancel_op_desc', 'Are you sure you want to cancel the operation?')

with open("app/src/main/res/values/strings.xml", "w") as f:
    f.write(c)

