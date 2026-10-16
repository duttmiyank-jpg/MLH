package com.krishivision.ai.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.krishivision.ai.data.model.CropAnalysisResult
import com.krishivision.ai.data.model.CropType
import com.krishivision.ai.data.model.UiState
import com.krishivision.ai.ui.theme.BorderOutline
import com.krishivision.ai.ui.theme.CardSurface
import com.krishivision.ai.ui.theme.DarkBackground
import com.krishivision.ai.ui.theme.EmeraldAccent
import com.krishivision.ai.ui.theme.GoldAccent
import com.krishivision.ai.ui.theme.PrimaryText
import com.krishivision.ai.ui.theme.SecondarySurface
import com.krishivision.ai.ui.theme.SecondaryText
import com.krishivision.ai.ui.viewmodel.KrishiVisionViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(viewModel: KrishiVisionViewModel) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val selectedCrop by viewModel.selectedCrop.collectAsState()
    val selectedImageUri by viewModel.selectedImageUri.collectAsState()
    val symptomsText by viewModel.symptomsText.collectAsState()
    val validationError by viewModel.validationError.collectAsState()

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        viewModel.setImageUri(uri)
    }

    val quickSuggestions = listOf(
        "Patte peele pad rahe hain",
        "Kala dhabba aa gaya hai",
        "Keede lag rahe hain",
        "Fasal sookh rahi hai"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // HEADER & BRANDING
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "KrishiVision AI 🌱",
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = PrimaryText
                )
                Text(
                    text = "Smart Farming, Better Future",
                    fontSize = 13.sp,
                    color = GoldAccent
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(SecondarySurface)
                    .border(1.dp, GoldAccent.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Gemma 4 AI",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = GoldAccent
                )
            }
        }

        // WELCOME CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(BorderOutline))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "Namaste Kisan! 🙏",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = EmeraldAccent
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Apni fasal ki photo chuniye ya samasya batayein. Gemma 4 AI aapko sateek upay batayega.",
                    fontSize = 13.sp,
                    color = SecondaryText
                )
            }
        }

        // CROP SELECTION
        Column {
            Text(
                text = "1. Fasal Ka Chunav Karein:",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryText
            )
            Spacer(modifier = Modifier.height(8.dp))

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CropType.values().forEach { crop ->
                    val isSelected = crop == selectedCrop
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) EmeraldAccent else SecondarySurface)
                            .border(
                                1.dp,
                                if (isSelected) GoldAccent else BorderOutline,
                                RoundedCornerShape(12.dp)
                            )
                            .clickable { viewModel.selectCrop(crop) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = "${crop.iconEmoji} ${crop.displayNameHinglish}",
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) DarkBackground else PrimaryText
                        )
                    }
                }
            }
        }

        // IMAGE PICKER / PREVIEW CARD
        Column {
            Text(
                text = "2. Fasal Ki Photo (Optional):",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryText
            )
            Spacer(modifier = Modifier.height(8.dp))

            if (selectedImageUri != null) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        AsyncImage(
                            model = selectedImageUri,
                            contentDescription = "Crop Photo Preview",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )

                        IconButton(
                            onClick = { viewModel.setImageUri(null) },
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(8.dp)
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.6f))
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Hataein",
                                tint = Color.White
                            )
                        }

                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomStart)
                                .padding(12.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.Black.copy(alpha = 0.7f))
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "✓ Photo Select Ho Gayi",
                                fontSize = 12.sp,
                                color = EmeraldAccent
                            )
                        }
                    }
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.dp, BorderOutline, RoundedCornerShape(16.dp))
                        .clickable {
                            photoPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddAPhoto,
                            contentDescription = "Photo Chuniye",
                            tint = EmeraldAccent,
                            modifier = Modifier.size(36.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Photo Gallery Se Chuniye",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = PrimaryText
                        )
                        Text(
                            text = "Ghar baithe sateek rog pehchanein",
                            fontSize = 12.sp,
                            color = SecondaryText
                        )
                    }
                }
            }
        }

        // SYMPTOMS TEXT FIELD & QUICK SUGGESTIONS
        Column {
            Text(
                text = "3. Samasya Ka Vivran (Symptoms):",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = PrimaryText
            )
            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = symptomsText,
                onValueChange = { viewModel.setSymptomsText(it) },
                placeholder = {
                    Text(
                        text = "Fasal me kya samasya aa rahi hai? Spasht likhein...",
                        color = SecondaryText,
                        fontSize = 13.sp
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                maxLines = 5,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = EmeraldAccent,
                    unfocusedBorderColor = BorderOutline,
                    focusedContainerColor = CardSurface,
                    unfocusedContainerColor = CardSurface,
                    focusedTextColor = PrimaryText,
                    unfocusedTextColor = PrimaryText
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Quick suggestion chips
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickSuggestions.forEach { suggestion ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(SecondarySurface)
                            .border(0.8.dp, BorderOutline, RoundedCornerShape(16.dp))
                            .clickable { viewModel.appendQuickSymptom(suggestion) }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = "+ $suggestion",
                            fontSize = 11.sp,
                            color = GoldAccent
                        )
                    }
                }
            }
        }

        // VALIDATION ERROR DISPLAY
        AnimatedVisibility(visible = validationError != null) {
            validationError?.let { err ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF2C1313)),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "Alert",
                            tint = Color(0xFFFF6B6B),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = err,
                            fontSize = 12.sp,
                            color = Color(0xFFFFD1D1)
                        )
                    }
                }
            }
        }

        // ANALYZE BUTTON
        Button(
            onClick = { viewModel.analyzeCrop() },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            enabled = uiState !is UiState.Loading,
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = EmeraldAccent,
                contentColor = DarkBackground,
                disabledContainerColor = SecondarySurface
            )
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AutoAwesome,
                    contentDescription = "Analyze",
                    tint = DarkBackground
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Fasal Ka Vishleshan Karein",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // STATE DISPLAY (LOADING / RESULT / ERROR)
        when (val state = uiState) {
            is UiState.Loading -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator(
                            color = GoldAccent,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(40.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Gemma 4 AI Vishleshan Kar Raha Hai...",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PrimaryText
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Aapki fasal ki rog pehchan aur upay tayyar ho rahe hain.",
                            fontSize = 12.sp,
                            color = SecondaryText
                        )
                    }
                }
            }

            is UiState.Success -> {
                ResultSection(
                    result = state.result,
                    onReset = { viewModel.resetAnalysis() },
                    onShare = {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                "🌱 KrishiVision AI Report:\n\n" +
                                        "Samasya: ${state.result.detectedIssue}\n\n" +
                                        "Karan:\n${state.result.possibleCauses.joinToString("\n• ")}\n\n" +
                                        "Upay:\n${state.result.remedies.joinToString("\n• ")}\n\n" +
                                        "Agla Kadam: ${state.result.nextSteps}"
                            )
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Report Share Karein"))
                    }
                )
            }

            is UiState.Error -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CardSurface),
                    border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(Color(0xFF8B2626)))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = "Error",
                                tint = Color(0xFFFF6B6B)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Vishleshan Me Samasya Aayi",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PrimaryText
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = state.message,
                            fontSize = 13.sp,
                            color = SecondaryText
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        OutlinedButton(
                            onClick = { viewModel.analyzeCrop() },
                            shape = RoundedCornerShape(10.dp),
                            border = ButtonDefaults.outlinedButtonBorder.copy(
                                brush = androidx.compose.ui.graphics.SolidColor(EmeraldAccent)
                            )
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Retry",
                                tint = EmeraldAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Dobara Koshish Karein",
                                color = EmeraldAccent,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            UiState.Idle -> {
                // Keep clean empty state
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun ResultSection(
    result: CropAnalysisResult,
    onReset: () -> Unit,
    onShare: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // SUCCESS HEADER CARD
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = CardSurface),
            border = CardDefaults.outlinedCardBorder().copy(brush = androidx.compose.ui.graphics.SolidColor(GoldAccent))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Success",
                        tint = EmeraldAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = "AI Vishleshan Poora Hua",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryText
                        )
                        Text(
                            text = "Gemma 4 AI Model Analysis",
                            fontSize = 11.sp,
                            color = GoldAccent
                        )
                    }
                }

                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = "Share",
                        tint = GoldAccent
                    )
                }
            }
        }

        // 1. PEHCHANI GAYI SAMASYA
        ResultCard(
            title = "1. Pehchani Gayi Samasya (Problem)",
            accentColor = EmeraldAccent
        ) {
            Text(
                text = result.detectedIssue,
                fontSize = 14.sp,
                color = PrimaryText,
                lineHeight = 20.sp
            )
        }

        // 2. SAMBHAVIT KARAN
        ResultCard(
            title = "2. Sambhavit Karan (Possible Causes)",
            accentColor = GoldAccent
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                result.possibleCauses.forEach { cause ->
                    Row {
                        Text(text = "• ", color = GoldAccent, fontWeight = FontWeight.Bold)
                        Text(
                            text = cause,
                            fontSize = 13.sp,
                            color = PrimaryText,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // 3. SURAKSHIT UPAY
        ResultCard(
            title = "3. Surakshit Upay (Safe Actions)",
            accentColor = EmeraldAccent
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                result.remedies.forEach { remedy ->
                    Row {
                        Text(text = "✓ ", color = EmeraldAccent, fontWeight = FontWeight.Bold)
                        Text(
                            text = remedy,
                            fontSize = 13.sp,
                            color = PrimaryText,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }

        // 4. AGLA KADAM
        ResultCard(
            title = "4. Agla Kadam (Recommended Next Step)",
            accentColor = GoldAccent
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(
                    imageVector = Icons.Default.Lightbulb,
                    contentDescription = "Tip",
                    tint = GoldAccent,
                    modifier = Modifier
                        .size(18.dp)
                        .padding(top = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = result.nextSteps,
                    fontSize = 13.sp,
                    color = PrimaryText,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // ACTION BUTTONS
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onReset,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                border = ButtonDefaults.outlinedButtonBorder.copy(
                    brush = androidx.compose.ui.graphics.SolidColor(BorderOutline)
                )
            ) {
                Text(
                    text = "Naya Vishleshan",
                    color = PrimaryText,
                    fontSize = 13.sp
                )
            }

            Button(
                onClick = onShare,
                modifier = Modifier
                    .weight(1f)
                    .height(46.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SecondarySurface,
                    contentColor = GoldAccent
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share",
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Report Share Karein",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}

@Composable
fun ResultCard(
    title: String,
    accentColor: Color,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = CardSurface),
        border = CardDefaults.outlinedCardBorder().copy(
            brush = androidx.compose.ui.graphics.SolidColor(BorderOutline)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = accentColor
            )
            Spacer(modifier = Modifier.height(10.dp))
            content()
        }
    }
}
