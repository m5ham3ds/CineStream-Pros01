package com.example.ui.screens.profile
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.ArrowBack

import androidx.compose.ui.res.stringResource
import com.example.R

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(onBack: () -> Unit) {
    val bgColor = MaterialTheme.colorScheme.background
    val surfaceColor = MaterialTheme.colorScheme.surface
    val primaryRed = MaterialTheme.colorScheme.primary
    val restrictions by com.example.data.repository.UserSecurityManager.restrictionsFlow.collectAsState()
    val isVip = restrictions.isPremium

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.subscription), color = MaterialTheme.colorScheme.onBackground) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back), tint = MaterialTheme.colorScheme.onBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surfaceColor)
            )
        },
        containerColor = bgColor
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(stringResource(R.string.choose_plan), color = MaterialTheme.colorScheme.onBackground, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.upgrade_premium_desc), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(32.dp))
            
            // Basic Plan
            Card(
                colors = CardDefaults.cardColors(containerColor = surfaceColor),
                modifier = Modifier.fillMaxWidth().then(
                    if (!isVip) Modifier.border(1.5.dp, primaryRed.copy(alpha = 0.6f), RoundedCornerShape(16.dp)) else Modifier
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(stringResource(R.string.basic_plan), color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.per_month, "$0.00"), color = if (!isVip) primaryRed else MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    PlanFeature("Ad-supported streaming")
                    PlanFeature("Standard quality (720p)")
                    PlanFeature("Watch on 1 device")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isVip) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            if (!isVip) "✓ " + stringResource(R.string.current_plan) else "Free Plan",
                            color = MaterialTheme.colorScheme.onBackground,
                            fontWeight = if (!isVip) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Premium Plan
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.fillMaxWidth().border(2.dp, if (isVip) com.example.ui.theme.SuccessGreen else primaryRed, RoundedCornerShape(16.dp)),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(24.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.premium_crown), color = MaterialTheme.colorScheme.onBackground, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Box(
                            modifier = Modifier
                                .background(if (isVip) com.example.ui.theme.SuccessGreen else primaryRed, RoundedCornerShape(4.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (isVip) "ACTIVE VIP" else stringResource(R.string.popular_badge),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(stringResource(R.string.per_month, "$9.99"), color = primaryRed, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    PlanFeature("Ad-free experience")
                    PlanFeature("4K HDR & Dolby Atmos")
                    PlanFeature("Watch on 4 devices at once")
                    PlanFeature("Download for offline viewing")
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = if (isVip) com.example.ui.theme.SuccessGreen else primaryRed),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            if (isVip) "✓ " + stringResource(R.string.current_plan) else stringResource(R.string.upgrade_premium),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlanFeature(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, color = MaterialTheme.colorScheme.onBackground, fontSize = 14.sp)
    }
}