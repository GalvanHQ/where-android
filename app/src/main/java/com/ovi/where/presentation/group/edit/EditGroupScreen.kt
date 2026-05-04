package com.ovi.where.presentation.group.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.hilt.navigation.compose.hiltViewModel
import com.ovi.where.R
import com.ovi.where.core.common.UiEvent
import com.ovi.where.core.theme.Dimens
import com.ovi.where.presentation.common.PrimaryButton
import com.ovi.where.presentation.common.WhereTextField
import com.ovi.where.presentation.common.WhereTopAppBar

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditGroupScreen(
    groupId: String,
    onNavigateBack: () -> Unit,
    viewModel: EditGroupViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(groupId) { viewModel.loadGroup(groupId) }

    LaunchedEffect(Unit) {
        viewModel.uiEvent.collect { event ->
            when (event) {
                is UiEvent.ShowSnackbar -> snackbarHostState.showSnackbar(event.message.asString(context))
                is UiEvent.NavigateBack -> onNavigateBack()
                else -> Unit
            }
        }
    }

    Scaffold(
        topBar = {
            WhereTopAppBar(
                title = stringResource(R.string.title_edit_group),
                onNavigateBack = onNavigateBack
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Surface(
            modifier = Modifier.fillMaxSize().padding(paddingValues),
            color = MaterialTheme.colorScheme.background
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(Dimens.spaceXLarge),
                verticalArrangement = Arrangement.Top
            ) {
                Spacer(Modifier.height(Dimens.spaceMedium))

                WhereTextField(
                    value = uiState.name,
                    onValueChange = viewModel::onNameChange,
                    label = stringResource(R.string.label_group_name),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading,
                    errorMessage = uiState.nameError,
                    singleLine = true
                )

                Spacer(Modifier.height(Dimens.spaceMedium))

                WhereTextField(
                    value = uiState.description,
                    onValueChange = viewModel::onDescriptionChange,
                    label = stringResource(R.string.label_description_optional),
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !uiState.isLoading,
                    singleLine = false,
                    maxLines = 4
                )

                Spacer(Modifier.height(Dimens.spaceXLarge))

                PrimaryButton(
                    text = stringResource(R.string.action_save),
                    onClick = { viewModel.onSave(groupId) },
                    modifier = Modifier.fillMaxWidth(),
                    isLoading = uiState.isLoading
                )
            }
        }
    }
}
