import sys

filepath = sys.argv[1]
lines_to_delete = sorted([int(x) - 1 for x in sys.argv[2:]], reverse=True)

with open(filepath, "r") as f:
    lines = f.readlines()

for idx in lines_to_delete:
    if 0 <= idx < len(lines):
        print(f"Deleting from {filepath} line {idx+1}: {lines[idx].strip()}")
        del lines[idx]

with open(filepath, "w") as f:
    f.writelines(lines)
