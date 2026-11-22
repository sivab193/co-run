package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.ui.theme.CarbonOutline
import com.example.ui.theme.CarbonSurface
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.InkWhite
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.RunnerBlue
import com.example.ui.theme.RunnerOrange

@Composable
fun OnboardingNameScreen(
    onSaveName: (String) -> Unit
) {
    var name by rememberSaveable { mutableStateOf("") }
    val trimmed = name.trim()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBg)
            .safeDrawingPadding()
            .imePadding(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(RunnerOrange)
                )
                Box(
                    modifier = Modifier
                        .height(6.dp)
                        .weight(1f)
                        .clip(RoundedCornerShape(3.dp))
                        .background(RunnerBlue)
                )
            }

            Text(
                text = stringResource(R.string.onboarding_title),
                style = MaterialTheme.typography.displayLarge,
                color = InkWhite
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.onboarding_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = InkSecondary
            )

            Spacer(modifier = Modifier.height(36.dp))

            Text(
                text = stringResource(R.string.onboarding_name_label),
                style = MaterialTheme.typography.labelLarge,
                color = InkWhite
            )
            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 40) name = it },
                placeholder = {
                    Text(
                        text = stringResource(R.string.onboarding_name_placeholder),
                        color = InkSecondary
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = CarbonSurface,
                    unfocusedContainerColor = CarbonSurface,
                    focusedBorderColor = RunnerOrange,
                    unfocusedBorderColor = CarbonOutline,
                    focusedTextColor = InkWhite,
                    unfocusedTextColor = InkWhite,
                    cursorColor = RunnerOrange
                ),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (trimmed.isNotEmpty()) onSaveName(trimmed) }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_name_input")
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { if (trimmed.isNotEmpty()) onSaveName(trimmed) },
                enabled = trimmed.isNotEmpty(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = RunnerOrange,
                    contentColor = ObsidianBg,
                    disabledContainerColor = CarbonOutline,
                    disabledContentColor = InkSecondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("onboarding_continue_button")
            ) {
                Text(
                    text = stringResource(R.string.onboarding_continue),
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
