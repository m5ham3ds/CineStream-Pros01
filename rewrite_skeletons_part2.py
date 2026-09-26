import re

with open("app/src/main/java/com/example/ui/components/SkeletonScreens.kt", "r") as f:
    c = f.read()

# Fix SearchScreenSkeleton (It was using GridScreenSkeleton, now it should use a List style)
target_search = """@Composable
fun SearchScreenSkeleton() {
    Column(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.height(16.dp))
        GridScreenSkeleton()
    }
}"""

replacement_search = """@Composable
fun SearchScreenSkeleton() {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(16.dp))
        // Fake "Results for..." text
        Box(
            modifier = Modifier
                .width(180.dp)
                .height(24.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmerEffect()
        )
        Spacer(modifier = Modifier.height(16.dp))
        
        repeat(8) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Image
                Box(
                    modifier = Modifier
                        .size(60.dp, 80.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .shimmerEffect()
                )
                Spacer(modifier = Modifier.width(16.dp))
                // Texts
                Column(modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.width(150.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.width(80.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                }
            }
        }
    }
}"""

c = c.replace(target_search, replacement_search)

# Add DownloadsScreenSkeleton, ShareScreenSkeleton, AboutScreenSkeleton
additional_skeletons = """

@Composable
fun DownloadsScreenSkeleton() {
    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(modifier = Modifier.height(48.dp))
        
        // Storage Header
        Box(modifier = Modifier.fillMaxWidth().height(120.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect())
        Spacer(modifier = Modifier.height(24.dp))
        
        // Tabs
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) {
                Box(modifier = Modifier.width(80.dp).height(32.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect())
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        
        // Items
        repeat(5) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(80.dp, 120.dp).clip(RoundedCornerShape(8.dp)).shimmerEffect())
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.width(140.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(modifier = Modifier.fillMaxWidth(0.8f).height(6.dp).clip(RoundedCornerShape(3.dp)).shimmerEffect())
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.width(60.dp).height(14.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                }
            }
        }
    }
}

@Composable
fun ShareScreenSkeleton() {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(64.dp))
        // Center huge radar/qr area
        Box(modifier = Modifier.size(250.dp).clip(CircleShape).align(Alignment.CenterHorizontally).shimmerEffect())
        Spacer(modifier = Modifier.height(48.dp))
        
        // Buttons
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            Box(modifier = Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect())
            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.weight(1f).height(60.dp).clip(RoundedCornerShape(16.dp)).shimmerEffect())
        }
    }
}

@Composable
fun AboutScreenSkeleton() {
    Column(modifier = Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(modifier = Modifier.height(64.dp))
        // Logo
        Box(modifier = Modifier.size(100.dp).clip(CircleShape).shimmerEffect())
        Spacer(modifier = Modifier.height(24.dp))
        // Title
        Box(modifier = Modifier.width(150.dp).height(28.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
        Spacer(modifier = Modifier.height(16.dp))
        // Version
        Box(modifier = Modifier.width(80.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Text blocks
        repeat(3) {
            Box(modifier = Modifier.fillMaxWidth().height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
            Spacer(modifier = Modifier.height(8.dp))
        }
        Box(modifier = Modifier.fillMaxWidth(0.7f).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
        
        Spacer(modifier = Modifier.height(48.dp))
        
        // Links
        repeat(3) {
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(24.dp).clip(CircleShape).shimmerEffect())
                Spacer(modifier = Modifier.width(16.dp))
                Box(modifier = Modifier.width(120.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
            }
        }
    }
}
"""

c += additional_skeletons

with open("app/src/main/java/com/example/ui/components/SkeletonScreens.kt", "w") as f:
    f.write(c)

