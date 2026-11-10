package com.notegraph.app.ui.editor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notegraph.app.data.local.entity.ChecklistItem
import com.notegraph.app.data.local.entity.NoteBlock
import com.notegraph.app.ui.theme.ThemeManager

/**
 * Minimalist interactive checklist / to-do block with gradient progress indicator.
 */
@Composable
fun ChecklistBlockUi(
    block: NoteBlock.ChecklistBlock,
    onTitleChanged: (String) -> Unit,
    onToggleItem: (itemId: String) -> Unit,
    onItemTextChanged: (itemId: String, newText: String) -> Unit,
    onAddItem: () -> Unit,
    onRemoveItem: (itemId: String) -> Unit
) {
    val totalCount = block.items.size
    val checkedCount = block.items.count { it.isChecked }
    val progress = if (totalCount > 0) checkedCount.toFloat() / totalCount.toFloat() else 0f
    val activePreset = ThemeManager.currentGradient
    val isLuxury = ThemeManager.isLuxuryUi
    val lux = ThemeManager.luxuryTokens
    val accent = if (isLuxury) lux.accent else activePreset.accentStart

    var localTitle by remember(block.id) { mutableStateOf(block.title) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        // Header: Checklist Title + Progress Badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            BasicTextField(
                value = localTitle,
                onValueChange = {
                    localTitle = it
                    onTitleChanged(it)
                },
                singleLine = true,
                textStyle = MaterialTheme.typography.titleSmall.copy(
                    color = if (isLuxury) lux.textPrimary else accent,
                    fontWeight = FontWeight.SemiBold
                ),
                cursorBrush = SolidColor(accent),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (localTitle.isEmpty()) {
                        Text(
                            "Checklist Title",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
                        )
                    }
                    innerTextField()
                }
            )

            Surface(
                shape = RoundedCornerShape(if (isLuxury) 6.dp else 10.dp),
                color = if (isLuxury) lux.recessedSurface else accent.copy(alpha = 0.14f),
                border = if (isLuxury) BorderStroke(1.dp, lux.borderHairline) else null
            ) {
                Text(
                    text = if (totalCount == 0) "0 TASKS" else "$checkedCount / $totalCount",
                    fontFamily = if (isLuxury) FontFamily.Monospace else FontFamily.Default,
                    style = MaterialTheme.typography.labelSmall,
                    color = accent,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        if (totalCount > 0) {
            Spacer(Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(if (isLuxury) 2.dp else 4.dp)
                    .clip(CircleShape)
                    .background(if (isLuxury) lux.dividerRule else ThemeManager.cardBorderColor)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .fillMaxHeight()
                        .background(if (isLuxury) SolidColor(accent) else ThemeManager.activeAccentBrush)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // Checklist Items
        block.items.forEach { item ->
            key(item.id) {
                ChecklistItemRow(
                    item = item,
                    onToggle = { onToggleItem(item.id) },
                    onTextChanged = { newText -> onItemTextChanged(item.id, newText) },
                    onSubmitNext = onAddItem,
                    onRemove = { onRemoveItem(item.id) }
                )
            }
        }

        // "+ Add task" button
        TextButton(
            onClick = onAddItem,
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = null,
                modifier = Modifier.size(15.dp)
            )
            Spacer(Modifier.width(4.dp))
            Text("Add task", fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ChecklistItemRow(
    item: ChecklistItem,
    onToggle: () -> Unit,
    onTextChanged: (String) -> Unit,
    onSubmitNext: () -> Unit,
    onRemove: () -> Unit
) {
    var localText by remember(item.id) { mutableStateOf(item.text) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = { onToggle() },
            colors = CheckboxDefaults.colors(
                checkedColor = MaterialTheme.colorScheme.primary,
                checkmarkColor = MaterialTheme.colorScheme.onPrimary,
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.65f)
            )
        )

        BasicTextField(
            value = localText,
            onValueChange = {
                localText = it
                onTextChanged(it)
            },
            textStyle = MaterialTheme.typography.bodyMedium.copy(
                color = if (item.isChecked) {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                textDecoration = if (item.isChecked) TextDecoration.LineThrough else TextDecoration.None
            ),
            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
            keyboardActions = KeyboardActions(onNext = { onSubmitNext() }),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
            decorationBox = { innerTextField ->
                if (localText.isEmpty()) {
                    Text(
                        "Write a task…",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.48f)
                    )
                }
                innerTextField()
            }
        )

        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(28.dp)
        ) {
            Icon(
                Icons.Rounded.Close,
                contentDescription = "Delete checklist item",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
            )
        }
    }
}
