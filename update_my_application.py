import re

with open("app/src/main/java/com/example/MyApplication.kt", "r") as f:
    c = f.read()

# Add imports for work manager
if "androidx.work" not in c:
    c = c.replace("import android.app.Application", "import android.app.Application\nimport androidx.work.PeriodicWorkRequestBuilder\nimport androidx.work.WorkManager\nimport androidx.work.Constraints\nimport androidx.work.NetworkType\nimport androidx.work.ExistingPeriodicWorkPolicy\nimport com.example.workers.CacheCleanupWorker\nimport java.util.concurrent.TimeUnit")

# Limit cache to 500 MB (500L * 1024 * 1024)
# Currently it uses .maxSizePercent(0.05) or something similar.
c = re.sub(r'maxSizePercent\(0\.[0-9]+\)', 'maxSizeBytes(500L * 1024 * 1024)', c)

# Add WorkManager scheduling to onCreate
schedule_code = """
        // Schedule automatic cache cleanup every 12 hours (only when connected to internet)
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
            
        val cacheCleanupRequest = PeriodicWorkRequestBuilder<CacheCleanupWorker>(12, TimeUnit.HOURS)
            .setConstraints(constraints)
            .build()
            
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "CacheCleanupWork",
            ExistingPeriodicWorkPolicy.KEEP,
            cacheCleanupRequest
        )
"""

if "WorkManager.getInstance" not in c:
    c = c.replace("super.onCreate()", "super.onCreate()\n" + schedule_code)

with open("app/src/main/java/com/example/MyApplication.kt", "w") as f:
    f.write(c)

