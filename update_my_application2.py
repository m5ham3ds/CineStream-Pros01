import re

with open("app/src/main/java/com/example/MyApplication.kt", "r") as f:
    c = f.read()

# Restore memory cache to maxSizePercent(0.25)
old_memory = """            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizeBytes(500L * 1024 * 1024)
                    .build()
            }"""

new_memory = """            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }"""

c = c.replace(old_memory, new_memory)

with open("app/src/main/java/com/example/MyApplication.kt", "w") as f:
    f.write(c)

