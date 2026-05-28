package com.ovi.where.presentation.common

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.ovi.where.R
import com.ovi.where.core.theme.Dimens


@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary
) {
    Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(Dimens.buttonHeight)
            .pressAnimation(),
        enabled = enabled && !isLoading,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor   = contentColor
        ),
        shape = MaterialTheme.shapes.medium
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier  = Modifier.size(Dimens.iconSizeMedium),
                color     = contentColor,
                strokeWidth = Dimens.strokeWidthThin
            )
        } else {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false
) {
    ElevatedButton(
        onClick   = onClick,
        modifier  = modifier.fillMaxWidth().height(Dimens.buttonHeight).pressAnimation(),
        enabled   = enabled && !isLoading,
        shape     = MaterialTheme.shapes.medium
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier    = Modifier.size(Dimens.iconSizeMedium),
                strokeWidth = Dimens.strokeWidthThin
            )
        } else {
            Text(text = text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun GoogleSignInButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isLoading: Boolean = false,
    enabled: Boolean = true
) {
    Button(
        onClick = onClick,
        modifier = modifier.fillMaxWidth().height(Dimens.buttonHeight),
        enabled = enabled && !isLoading,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color.Transparent,
            contentColor   = MaterialTheme.colorScheme.primary
        )
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(Dimens.iconSizeMedium),
                strokeWidth = Dimens.strokeWidthThin
            )
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    imageVector = ImageVector.vectorResource(id = R.drawable.google_logo),
                    contentDescription = null,
                    tint = Color.Unspecified,
                    modifier = Modifier.size(Dimens.iconSizeSmall)
                )
                Spacer(modifier = Modifier.width(Dimens.spaceMedium))
                Text(
                    text = stringResource(R.string.action_continue_with_google),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

// ── Text fields ───────────────────────────────────────────────────────────────

@Composable
fun WhereTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    isPassword: Boolean = false,
    enabled: Boolean = true,
    readOnly: Boolean = false,
    singleLine: Boolean = true,
    maxLines: Int = if (singleLine) 1 else Int.MAX_VALUE,
    minLines: Int = 1,
    isError: Boolean = false,
    errorMessage: String? = null,
    leadingIcon: @Composable (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    imeAction: ImeAction = ImeAction.Next,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None
) {
    var passwordVisible by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        OutlinedTextField(
            value         = value,
            onValueChange = onValueChange,
            label         = { Text(label) },
            modifier      = Modifier.fillMaxWidth().heightIn(min = Dimens.buttonHeight),
            keyboardOptions = KeyboardOptions(
                keyboardType   = keyboardType,
                imeAction      = imeAction,
                capitalization = capitalization
            ),
            keyboardActions = keyboardActions,
            visualTransformation = if (isPassword && !passwordVisible)
                PasswordVisualTransformation()
            else
                VisualTransformation.None,
            leadingIcon = leadingIcon,
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible)
                                Icons.Default.VisibilityOff
                            else
                                Icons.Default.Visibility,
                            contentDescription = if (passwordVisible)
                                stringResource(R.string.cd_hide_password)
                            else
                                stringResource(R.string.cd_show_password)
                        )
                    }
                }
            } else trailingIcon,
            enabled   = enabled && !readOnly,
            readOnly  = readOnly,
            singleLine = singleLine,
            maxLines  = maxLines,
            minLines  = minLines,
            isError   = isError || errorMessage != null,
            shape     = MaterialTheme.shapes.medium,
            colors    = OutlinedTextFieldDefaults.colors(
                focusedBorderColor   = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                errorBorderColor     = MaterialTheme.colorScheme.error
            )
        )
        errorMessage?.let {
            Text(
                text  = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = Dimens.spaceLarge, top = Dimens.spaceSmall)
            )
        }
    }
}

@Composable
fun EmailTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorMessage: String? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    imeAction: ImeAction = ImeAction.Next
) {
    WhereTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = stringResource(R.string.label_email),
        modifier      = modifier,
        keyboardType  = KeyboardType.Email,
        enabled       = enabled,
        isError       = isError,
        errorMessage  = errorMessage,
        keyboardActions = keyboardActions,
        imeAction     = imeAction,
        leadingIcon   = {
            Icon(Icons.Default.Email, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    )
}

@Composable
fun PasswordTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String,
    enabled: Boolean = true,
    isError: Boolean = false,
    errorMessage: String? = null,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    imeAction: ImeAction = ImeAction.Done
) {
    WhereTextField(
        value         = value,
        onValueChange = onValueChange,
        label         = label,
        modifier      = modifier,
        keyboardType  = KeyboardType.Password,
        isPassword    = true,
        enabled       = enabled,
        isError       = isError,
        errorMessage  = errorMessage,
        keyboardActions = keyboardActions,
        imeAction     = imeAction,
        leadingIcon   = {
            Icon(Icons.Default.Lock, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    )
}

@Composable
fun DividerText(
    text: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(Dimens.dividerThickness)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
        Text(
            text     = text,
            modifier = Modifier.padding(horizontal = Dimens.spaceLarge),
            style    = MaterialTheme.typography.bodySmall,
            color    = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Box(
            modifier = Modifier
                .weight(1f)
                .height(Dimens.dividerThickness)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    }
}

// ── Annotated clickable text ──────────────────────────────────────────────────

@Composable
fun AnnotatedClickableText(
    prefix: String,
    clickableText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = buildAnnotatedString {
                append(prefix)
                append(" ")
                withStyle(
                    style = SpanStyle(
                        color          = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                        fontWeight     = FontWeight.SemiBold
                    )
                ) { append(clickableText) }
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Shimmer loading placeholders ──────────────────────────────────────────────

/**
 * Creates a shimmer brush with a 900ms linear gradient sweep that repeats
 * until loading completes. Used for skeleton placeholder content.
 *
 * @param showShimmer Whether to show the shimmer animation or a transparent brush.
 * @param targetValue The end offset for the gradient sweep animation.
 */
@Composable
fun shimmerBrush(showShimmer: Boolean = true, targetValue: Float = 1000f): Brush {
    return if (showShimmer) {
        val shimmerColors = listOf(
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f),
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
        )
        val transition = rememberInfiniteTransition(label = "shimmer")
        val translateAnimation by transition.animateFloat(
            initialValue = 0f,
            targetValue  = targetValue,
            animationSpec = infiniteRepeatable(
                animation  = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "shimmer_translate"
        )
        Brush.linearGradient(
            colors = shimmerColors,
            start  = Offset.Zero,
            end    = Offset(x = translateAnimation, y = translateAnimation)
        )
    } else {
        Brush.linearGradient(colors = listOf(Color.Transparent, Color.Transparent))
    }
}

@Composable
fun ShimmerGroupList(modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Dimens.spaceLarge),
        verticalArrangement = Arrangement.spacedBy(Dimens.spaceMedium)
    ) {
        Spacer(Modifier.height(Dimens.spaceSmall))
        repeat(4) { ShimmerGroupCard() }
    }
}

@Composable
private fun ShimmerGroupCard() {
    val brush = shimmerBrush()
    Card(
        modifier  = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(Dimens.cardElevation),
        shape     = MaterialTheme.shapes.large,
        colors    = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier          = Modifier.fillMaxWidth().padding(Dimens.spaceLarge),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(Dimens.avatarSizeMedium)
                    .clip(CircleShape)
                    .background(brush)
            )
            Spacer(Modifier.width(Dimens.spaceMedium))
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.6f)
                        .height(Dimens.shimmerBarHeightL)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(brush)
                )
                Spacer(Modifier.height(Dimens.spaceSmall))
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.4f)
                        .height(Dimens.shimmerBarHeightS)
                        .clip(MaterialTheme.shapes.extraSmall)
                        .background(brush)
                )
            }
        }
    }
}

// ── Consistent top app bar ─────────────────────────────────────────────────────
// All screens share the same surface-coloured bar with correct content colours.

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhereTopAppBar(
    title: String,
    onNavigateBack: (() -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    scrollBehavior: TopAppBarScrollBehavior? = null
) {
    TopAppBar(
        title = {
            Text(
                text  = title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        navigationIcon = {
            if (onNavigateBack != null) {
                IconButton(onClick = onNavigateBack) {
                    Icon(
                        imageVector        = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint               = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        actions = { actions() },
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor              = MaterialTheme.colorScheme.background,
            titleContentColor           = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor  = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor      = MaterialTheme.colorScheme.onBackground
        )
    )
}