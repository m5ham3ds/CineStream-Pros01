import re

with open("app/src/main/res/values/strings.xml", "r") as f:
    c = f.read()

def add_string(name, value):
    global c
    if f'name="{name}"' not in c:
        c = c.replace('</resources>', f'    <string name="{name}">{value}</string>\n</resources>')

add_string('share', 'Share')
add_string('gallery', 'Gallery')
add_string('verified', 'Verified')
add_string('profile_picture', 'Profile Picture')
add_string('edit_photo', 'Edit Photo')
add_string('avatar', 'Avatar')
add_string('options', 'Options')
add_string('attach', 'Attach')
add_string('emoji', 'Emoji')
add_string('help_support_desc', "We're here to help you")
add_string('no', 'No')

with open("app/src/main/res/values/strings.xml", "w") as f:
    f.write(c)

