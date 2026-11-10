package com.notegraph.app.ui.editor

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.notegraph.app.data.local.entity.ChecklistItem
import com.notegraph.app.data.local.entity.NoteBlock

/**
 * Interactive checklist / to-do block placed inside a note.
 * Supports toggling checkmarks (with strikethrough feedback), adding items,
 * editing tasks inline, and tracking live completion progress.
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

    var localTitle by remember(block.id) { mutableStateOf(block.title) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Header: Checklist Title + Live Progress Counter
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
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.weight(1f),
                decorationBox = { innerTextField ->
                    if (localTitle.isEmpty()) {
                        Text(
                            "Checklist Title",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    innerTextField()
                }
            )

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Text(
                    text = if (totalCount == 0) "0 tasks" else "$checkedCount / $totalCount done",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        if (totalCount > 0) {
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
            )
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

        // "+ Add item" button
        TextButton(
            onClick = onAddItem,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Text("+ Add to-do item", fontSize = 13.sp, fontWeight = FontWeight.Medium)
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
                uncheckedColor = MaterialTheme.colorScheme.onSurfaceVariant
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
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
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
                        "Write a task...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
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
                Icons.Default.Close,
                contentDescription = "Delete checklist item",
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
        }
    }
}
