import os

skeleton_code = """package com.example.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp

@Composable
fun MediaScreenSkeleton() {
    Column(modifier = Modifier.fillMaxSize()) {
        // Hero Banner Skeleton (16:9)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .shimmerEffect()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Tab Row Skeleton (Not equal weight, like real tabs)
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false
        ) {
            items(4) {
                Box(
                    modifier = Modifier
                        .width(90.dp)
                        .height(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .shimmerEffect()
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Sections
        repeat(2) {
            // Section Title
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(140.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerEffect()
                )
                Box(
                    modifier = Modifier
                        .size(24.dp)
                        .clip(CircleShape)
                        .shimmerEffect()
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            
            // Section Items (Media Cards 140x210)
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                userScrollEnabled = false
            ) {
                items(5) {
                    Box(
                        modifier = Modifier
                            .width(140.dp)
                            .aspectRatio(3f / 4f)
                            .clip(RoundedCornerShape(12.dp))
                            .shimmerEffect()
                    )
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
fun GridScreenSkeleton() {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 100.dp, top = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        userScrollEnabled = false
    ) {
        items(15) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
                    .clip(RoundedCornerShape(12.dp))
                    .shimmerEffect()
            )
        }
    }
}

@Composable
fun SocialScreenSkeleton() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(120.dp).height(32.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
            Box(modifier = Modifier.size(40.dp).clip(CircleShape).shimmerEffect())
        }
        Spacer(modifier = Modifier.height(24.dp))
        
        // Stories Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            userScrollEnabled = false
        ) {
            items(6) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(70.dp).clip(CircleShape).shimmerEffect())
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.width(50.dp).height(12.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                }
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
        
        // Conversations List
        repeat(5) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(56.dp).clip(CircleShape).shimmerEffect())
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.width(150.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                    Spacer(modifier = Modifier.height(8.dp))
                    Box(modifier = Modifier.fillMaxWidth(0.6f).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                }
            }
        }
    }
}

@Composable
fun SearchScreenSkeleton() {
    Column(modifier = Modifier.fillMaxSize()) {
        // Search Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(50))
                .shimmerEffect()
        )
        
        // Filter Row
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false
        ) {
            items(5) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .height(32.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .shimmerEffect()
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Results Grid
        GridScreenSkeleton()
    }
}

@Composable
fun ProfileScreenSkeleton() {
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(48.dp))
        // Avatar
        Box(modifier = Modifier.size(120.dp).clip(CircleShape).shimmerEffect())
        Spacer(modifier = Modifier.height(16.dp))
        // Name
        Box(modifier = Modifier.width(180.dp).height(28.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
        Spacer(modifier = Modifier.height(8.dp))
        // Email
        Box(modifier = Modifier.width(140.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
        
        Spacer(modifier = Modifier.height(32.dp))
        
        // Options List
        repeat(5) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(24.dp).clip(CircleShape).shimmerEffect())
                Spacer(modifier = Modifier.width(16.dp))
                Box(modifier = Modifier.width(150.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                Spacer(modifier = Modifier.weight(1f))
                Box(modifier = Modifier.size(24.dp).clip(CircleShape).shimmerEffect())
            }
        }
    }
}

@Composable
fun LibraryScreenSkeleton() {
    Column(modifier = Modifier.fillMaxSize()) {
        // Title
        Box(
            modifier = Modifier
                .padding(16.dp)
                .width(120.dp)
                .height(32.dp)
                .clip(RoundedCornerShape(4.dp))
                .shimmerEffect()
        )
        
        // Tab Row
        LazyRow(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            userScrollEnabled = false
        ) {
            items(3) {
                Box(
                    modifier = Modifier
                        .width(110.dp)
                        .height(40.dp)
                        .clip(RoundedCornerShape(50))
                        .shimmerEffect()
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Grid
        GridScreenSkeleton()
    }
}

@Composable
fun ExtensionsScreenSkeleton() {
    Column(modifier = Modifier.fillMaxSize()) {
        // Top Bar
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.size(24.dp).clip(CircleShape).shimmerEffect())
            Spacer(modifier = Modifier.width(16.dp))
            Box(modifier = Modifier.width(120.dp).height(24.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        // List items
        repeat(6) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(modifier = Modifier.size(48.dp).clip(RoundedCornerShape(8.dp)).shimmerEffect())
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Box(modifier = Modifier.width(120.dp).height(20.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                    Spacer(modifier = Modifier.height(6.dp))
                    Box(modifier = Modifier.width(80.dp).height(16.dp).clip(RoundedCornerShape(4.dp)).shimmerEffect())
                }
                Spacer(modifier = Modifier.width(16.dp))
                Box(modifier = Modifier.width(48.dp).height(24.dp).clip(RoundedCornerShape(12.dp)).shimmerEffect())
            }
        }
    }
}
"""

with open("app/src/main/java/com/example/ui/components/SkeletonScreens.kt", "w") as f:
    f.write(skeleton_code)

