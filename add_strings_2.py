import re

with open("app/src/main/res/values/strings.xml", "r") as f:
    c = f.read()

def add_string(name, value):
    global c
    if f'name="{name}"' not in c:
        c = c.replace('</resources>', f'    <string name="{name}">{value}</string>\n</resources>')

add_string('system', 'System')
add_string('light', 'Light')
add_string('dark', 'Dark')
add_string('pause_resume', 'Pause/Resume')
add_string('logo', 'Logo')
add_string('mark_all_read', 'Mark all as read')
add_string('filter', 'Filter')
add_string('folder', 'Folder')
add_string('open', 'Open')
add_string('qr_code', 'QR Code')
add_string('signal', 'Signal')
add_string('add_story', 'Add Story')
add_string('more', 'More')
add_string('close', 'Close')

with open("app/src/main/res/values/strings.xml", "w") as f:
    f.write(c)

