package com.ovi.where.presentation.map.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ovi.where.core.theme.Dimens

/** Hard cap for note length — matches the use case's [com.ovi.where.domain.usecase.location.UpdateMeetupParticipantNoteUseCase.MAX_NOTE_CHARS]. */
private const val MAX_NOTE_CHARS = 80

/**
 * Pre-baked phrases that cover ~90% of "what's your status" intents,
 * so the user can update with one tap. Keeps the entry sheet from
 * feeling like a generic chat compose box.
 */
private val SUGGESTED_NOTES = listOf(
    "On my way",
    "Stuck in traffic",
    "Running 10 min late",
    "Just left",
    "Be there in 5",
    "Looking for parking",
    "Picking up coffee"
)

/**
 * Bottom sheet for entering / updating the user's free-form meetup
 * status note. Reuses the map-screen sheet vocabulary (38dp top corners,
 * 52dp/16dp CTAs, surfaceContainerHigh inline cards, primary-only accent).
 *
 *  • Multi-line text field, capped to 80 chars, with a live counter.
 *  • Suggested-phrase chip strip for one-tap entry.
 *  • Two CTAs: "Save" (disabled when nothing changed), and "Clear" when
 *    the user already has a note.
 */
@OptIn(
    ExperimentalMaterial3Api::class,
    androidx.compose.foundation.layout.ExperimentalLayoutApi::class
)
@Composable
fun MeetupStatusEditorSheet(
    initialNote: String,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var draft by remember { mutableStateOf(initialNote) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp),
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .navigationBarsPadding()
                .padding(horizontal = Dimens.spaceLarge)
        ) {
            // ── Title ────────────────────────────────────────────────────
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(Dimens.iconSizeXLarge)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Edit,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.width(Dimens.spaceMedium))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "YOUR MEETUP STATUS",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.2.sp
                    )
                    Text(
                        text = "Let the group know what's happening",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(Dimens.spaceLarge))

            // ── Text field ───────────────────────────────────────────────
            OutlinedTextField(
                value = draft,
                onValueChange = { input ->
                    if (input.length <= MAX_NOTE_CHARS) {
                        draft = input
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("e.g. Running 10 min late") },
                singleLine = false,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                shape = RoundedCornerShape(16.dp),
                supportingText = {
                    Text(
                        text = "${draft.length}/$MAX_NOTE_CHARS",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            )

            // ── Suggested chips ──────────────────────────────────────────
            Spacer(Modifier.height(Dimens.spaceLarge))
            Text(
                text = "QUICK PICKS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 1.2.sp
            )
            Spacer(Modifier.height(Dimens.spaceMedium))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium),
                verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
            ) {
                SUGGESTED_NOTES.forEach { phrase ->
                    SuggestionChip(
                        text = phrase,
                        selected = draft == phrase,
                        onClick = { draft = phrase }
                    )
                }
            }

            Spacer(Modifier.height(Dimens.spaceLarge))

            // ── Actions ──────────────────────────────────────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
            ) {
                if (initialNote.isNotBlank()) {
                    TextButton(
                        onClick = onClear,
                        modifier = Modifier.height(Dimens.buttonHeight)
                    ) {
                        Text(
                            "Clear",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
                Button(
                    onClick = {
                        onSave(draft.trim())
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(Dimens.buttonHeight),
                    enabled = draft.trim() != initialNote.trim(),
                    shape = RoundedCornerShape(Dimens.cornerMedium),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        if (draft.isBlank()) "Save (no status)" else "Save",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            Spacer(Modifier.height(Dimens.spaceMedium))
        }
    }
}

@Composable
private fun SuggestionChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.tertiary.copy(alpha = 0.16f)
        else MaterialTheme.colorScheme.surfaceContainerHigh,
        border = if (selected) androidx.compose.foundation.BorderStroke(
            1.dp, MaterialTheme.colorScheme.tertiary
        ) else null,
        tonalElevation = if (selected) 0.dp else 1.dp
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) MaterialTheme.colorScheme.tertiary
            else MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = Dimens.spaceLarge, vertical = Dimens.spaceMedium)
        )
    }
}
