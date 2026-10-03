package com.example.bamachat.ui.screen

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Handyman
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String
)

private val pages = listOf(
    OnboardingPage(
        icon = Icons.Filled.AutoAwesome,
        title = "AI Workspace OS",
        description = "BamaFlow ist ein autonomer KI-Arbeitsraum, kein Chat-Fenster. Plattform\u00fcbergreifend, erweiterbar und bereit f\u00fcr deine Produktivit\u00e4t."
    ),
    OnboardingPage(
        icon = Icons.Filled.Handyman,
        title = "Agenten & Tools",
        description = "MCP-Protokoll, Dateizugriff, Terminal, Git und Web-Recherche. Deine KI-Agenten navigieren die Tools, die du brauchst."
    ),
    OnboardingPage(
        icon = Icons.Filled.Groups,
        title = "Kollaboration",
        description = "Echtzeit-Sessions mit Rollenmodell und intelligenter Workspace-Konflikterkennung. Gemeinsam arbeiten, ohne Datenverlust."
    ),
    OnboardingPage(
        icon = Icons.Filled.Widgets,
        title = "Workspace & Automation",
        description = "Mini-Apps, Extensions und Workflow-Automation direkt im Workspace. Passe deinen Arbeitsraum an deine Prozesse an."
    ),
    OnboardingPage(
        icon = Icons.Filled.Flag,
        title = "Los geht\u2019s!",
        description = "Starte noch heute und entdecke die volle Kraft deines KI-gest\u00fctzten Arbeitsraums. Kein Konto n\u00f6tig \u2013 einfach loslegen."
    )
)

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    onSkip: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val currentPage by remember { derivedStateOf { pagerState.currentPage } }
    val isLastPage = currentPage == pages.lastIndex

    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        Color(0xFF08111F),
                        Color(0xFF132844),
                        Color(0xFF18385E)
                    )
                )
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(top = 12.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onSkip) {
                    Text(
                        text = "\u00dcberspringen",
                        color = Color(0xFFD8E4FF).copy(alpha = 0.7f),
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }

            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) { pageIndex ->
                OnboardingPageContent(page = pages[pageIndex])
            }

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                pages.indices.forEach { index ->
                    val isSelected = index == currentPage
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .size(if (isSelected) 10.dp else 8.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) Color(0xFFB9CCFF)
                                else Color(0xFFB9CCFF).copy(alpha = 0.3f)
                            )
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Button(
                    onClick = {
                        if (isLastPage) onComplete()
                        else {
                            scope.launch {
                                pagerState.animateScrollToPage(currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(18.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFB9CCFF),
                        contentColor = Color(0xFF10233F)
                    )
                ) {
                    Text(
                        text = if (isLastPage) "Starten" else "Weiter",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 16.sp
                    )
                }

                if (!isLastPage) {
                    Spacer(Modifier.height(12.dp))
                    TextButton(onClick = onSkip) {
                        Text(
                            text = "Starten \u00fcberspringen",
                            color = Color(0xFFD8E4FF).copy(alpha = 0.5f),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    AnimatedContent(
        targetState = page,
        transitionSpec = {
            (slideInVertically(
                animationSpec = tween(380, easing = FastOutSlowInEasing),
                initialOffsetY = { it / 4 }
            ) + fadeIn(animationSpec = tween(380)))
                .togetherWith(
                    slideOutVertically(
                        animationSpec = tween(280),
                        targetOffsetY = { -it / 4 }
                    ) + fadeOut(animationSpec = tween(280))
                )
        },
        label = "onboarding_page"
    ) { currentPage ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(
                                Color(0xFFB9CCFF).copy(alpha = 0.2f),
                                Color.Transparent
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = currentPage.icon,
                    contentDescription = null,
                    modifier = Modifier.size(52.dp),
                    tint = Color(0xFFB9CCFF)
                )
            }

            Spacer(Modifier.height(32.dp))

            Text(
                text = currentPage.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = currentPage.description,
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFFD8E4FF),
                textAlign = TextAlign.Center,
                lineHeight = 24.sp
            )
        }
    }
}
