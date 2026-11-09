package org.sensorhub.android.ui.features.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Help
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import org.sensorhub.android.R
import org.sensorhub.android.ui.components.OSHAlertDialogWithoutDismiss
import org.sensorhub.android.ui.components.OSHCard
import org.sensorhub.android.ui.components.OSHClickableRowWithIcon
import org.sensorhub.android.ui.components.OSHInfoRow
import org.sensorhub.android.ui.components.OSHSingleChoiceDialog
import org.sensorhub.android.ui.components.OSHTopAppBarWithBack
import org.sensorhub.android.ui.theme.OSHTheme
import org.sensorhub.android.ui.theme.Dimensions

@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onNavigateToHelpFaq: () -> Unit,
    onNavigateToRepo: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    var showLanguageDialog by rememberSaveable { mutableStateOf(false) }
    if (showLanguageDialog) {
        OSHSingleChoiceDialog(
            onDismissRequest = { showLanguageDialog = false },
            dialogTitle = stringResource(R.string.title_language_dialog),
            icon = Icons.Filled.Language,
            options = LANGUAGE_LABELS,
            selectedIndex = state.selectedLanguageIndex,
            onOptionSelected = { index -> viewModel.selectLanguage(index) }
        )
    }

    var showThemeDialog by rememberSaveable { mutableStateOf(false) }
    if (showThemeDialog) {
        OSHSingleChoiceDialog(
            onDismissRequest = { showThemeDialog = false },
            dialogTitle = stringResource(R.string.title_theme),
            icon = Icons.Filled.Palette,
            options = listOf(
                stringResource(R.string.theme_system_default),
                stringResource(R.string.theme_light),
                stringResource(R.string.theme_dark),
            ),
            selectedIndex = state.selectedThemeIndex,
            onOptionSelected = viewModel::selectTheme,
        )
    }

    var showAbout by rememberSaveable { mutableStateOf(false) }
    if (showAbout) {
        OSHAlertDialogWithoutDismiss(
            onConfirmation = { showAbout = false },
            dialogText = stringResource(R.string.about_description),
            dialogTitle = stringResource(R.string.app_name),
            icon = Icons.Filled.Info
        )
    }

    Scaffold(
        topBar = {
            OSHTopAppBarWithBack(
                stringResource(R.string.tab_settings),
                onBackClick = onBackClick
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            OSHCard(
                Modifier
                    .padding(horizontal = Dimensions.screenHorizontal)
            ) {
                OSHClickableRowWithIcon(
                    title = stringResource(R.string.title_theme),
                    imageVector = Icons.Default.Palette,
                    contentDescription = stringResource(R.string.title_theme),
                    onClick = { showThemeDialog = true },
                )
                OSHClickableRowWithIcon(
                    title = stringResource(R.string.title_language),
                    imageVector = Icons.Default.Language,
                    contentDescription = stringResource(R.string.title_language),
                    onClick = { showLanguageDialog = true }
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            OSHCard(
                Modifier
                    .padding(horizontal = Dimensions.screenHorizontal)
            ) {
                OSHClickableRowWithIcon(
                    title = stringResource(R.string.title_help_faq),
                    imageVector = Icons.AutoMirrored.Filled.Help,
                    contentDescription = stringResource(R.string.title_help_faq),
                    onClick = onNavigateToHelpFaq
                )
                OSHClickableRowWithIcon(
                    title = stringResource(R.string.title_about),
                    imageVector = Icons.Default.Info,
                    contentDescription = stringResource(R.string.title_about),
                    onClick = { showAbout = true }
                )
                OSHClickableRowWithIcon(
                    title = stringResource(R.string.github_repo),
                    imageVector = Icons.Default.Link,
                    contentDescription = stringResource(R.string.github_repo),
                    onClick = onNavigateToRepo
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            OSHCard(
                Modifier
                    .padding(horizontal = Dimensions.screenHorizontal)
            ) {
                OSHInfoRow(
                    label = stringResource(R.string.title_version),
                    value = state.appVersion
                )
            }
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121212)
@Composable
private fun SettingsScreenPreview() {
    OSHTheme {
        SettingsScreen({}, {}, {})
    }
}
