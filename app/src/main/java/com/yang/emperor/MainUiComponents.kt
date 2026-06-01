package com.yang.emperor

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DividerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.LaunchedEffect
import kotlinx.coroutines.delay
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppBottomSheetPanel(
    title: String,
    description: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        sheetMaxWidth = Dp.Unspecified,
        containerColor = Color(0xFFF4F6FF),
        contentColor = Color(0xFF111827),
        scrimColor = Color.Transparent,
        shape = RoundedCornerShape(topStart = 36.dp, topEnd = 36.dp),
        tonalElevation = 8.dp,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 10.dp, bottom = 6.dp)
                    .size(width = 64.dp, height = 7.dp)
                    .clip(RoundedCornerShape(99.dp))
                    .background(Color(0xFF9CA3AF).copy(alpha = 0.7f))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF4F6FF))
                .heightIn(max = 480.dp)
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 22.dp, end = 22.dp, top = 10.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF111827)
            )
            Text(
                text = description,
                color = Color(0xFF6B7280),
                style = MaterialTheme.typography.bodyMedium
            )
            content()
        }
    }
}

@Composable
fun HistoryStatsCard(
    successCount: Int,
    failedCount: Int,
    runningCount: Int
) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "处理概览",
                color = Color(0xFF111827),
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                HistoryStatItem(successCount, "成功", Color(0xFF16A34A))
                HistoryStatItem(failedCount, "失败", Color(0xFFDC2626))
                HistoryStatItem(runningCount, "处理中", Color(0xFFD97706))
            }
        }
    }
}
@Composable
fun HistoryStatItem(
    count: Int,
    label: String,
    color: Color
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = count.toString(),
            color = color,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = label,
            color = Color(0xFF6B7280),
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
fun BottomNavigationBar(
    currentRoute: ScreenRoute,
    onRouteSelected: (ScreenRoute) -> Unit
) {
    Surface(
        color = Color(0xFFF7F8FC),
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BottomNavButton(
                text = "创作",
                selected = currentRoute == ScreenRoute.MAIN,
                modifier = Modifier.weight(1f),
                onClick = { onRouteSelected(ScreenRoute.MAIN) }
            )
            BottomNavButton(
                text = "记录",
                selected = currentRoute == ScreenRoute.HISTORY,
                modifier = Modifier.weight(1f),
                onClick = { onRouteSelected(ScreenRoute.HISTORY) }
            )
            BottomNavButton(
                text = "设置",
                selected = currentRoute == ScreenRoute.SETTINGS,
                modifier = Modifier.weight(1f),
                onClick = { onRouteSelected(ScreenRoute.SETTINGS) }
            )
        }
    }
}

@Composable
fun BottomNavButton(
    text: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val content = if (selected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.58f)
    TextButton(
        onClick = onClick,
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
    ) {
        Text(text, color = content, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
    }
}

@Composable
fun ConfigEntryCard(
    title: String,
    primary: String,
    secondary: String,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = primary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = secondary,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF6B7280),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = "›",
                fontSize = 34.sp,
                color = accent,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
fun SectionTitle(title: String, desc: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        if (desc.isNotBlank()) {
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF6B7280)
            )
        }
    }
}

@Composable
fun InfoCard(title: String, content: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.labelLarge
            )
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
fun AppDropdownField(
    title: String,
    selected: String,
    options: List<String>,
    onSelected: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(options) { option ->
                        TextButton(
                            onClick = {
                                onSelected(option)
                                showDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = option,
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable { showDialog = true },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = selected,
                    modifier = Modifier.weight(1f),
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
fun AppEditableDropdownField(
    title: String,
    value: String,
    options: List<String>,
    placeholder: String,
    onValueChange: (String) -> Unit,
    onSelected: (String) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }
    var showCustomDialog by remember { mutableStateOf(false) }
    var customInput by remember { mutableStateOf("") }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(title) },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(options) { option ->
                        TextButton(
                            onClick = {
                                onSelected(option)
                                showDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = option,
                                modifier = Modifier.fillMaxWidth(),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    item {
                        TextButton(
                            onClick = {
                                showDialog = false
                                showCustomDialog = true
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "自定义输入",
                                modifier = Modifier.fillMaxWidth(),
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text("取消")
                }
            }
        )
    }

    if (showCustomDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("自定义模型") },
            text = {
                OutlinedTextField(
                    value = customInput,
                    onValueChange = { customInput = it },
                    placeholder = { Text("输入模型名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val trimmed = customInput.trim()
                        if (trimmed.isNotBlank()) {
                            onValueChange(trimmed)
                            onSelected(trimmed)
                        }
                        showCustomDialog = false
                        customInput = ""
                    }
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showCustomDialog = false
                        customInput = ""
                    }
                ) { Text("取消") }
            }
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .clickable { showDialog = true },
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 0.dp,
            border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 15.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = value.ifBlank { placeholder },
                    modifier = Modifier.weight(1f),
                    color = if (value.isBlank()) Color(0xFF9CA3AF) else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = null
                )
            }
        }
    }
}

@Composable
fun StatusCard(status: String) {
    val isError = isErrorNotice(status)
    val bg = if (isError) errorBg else successBg
    val fg = if (isError) errorText else successText

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(bg)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(fg)
        )
        Text(
            text = status,
            color = fg,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

/**
 * 显示一段时间后自动消失的提示卡片，用于说明性、非关键的引导文案。
 * 默认 3 秒后隐藏，避免长期占用预览区域。
 */
@Composable
fun AutoDismissStatusCard(status: String, durationMillis: Long = 3000L) {
    var visible by remember(status) { mutableStateOf(true) }
    LaunchedEffect(status) {
        visible = true
        delay(durationMillis)
        visible = false
    }
    if (visible) {
        StatusCard(status)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HistoryCard(
    item: HistoryItem,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onToggleSelected: () -> Unit = {},
    onLongPress: () -> Unit = {},
    onDelete: () -> Unit = {},
    onCopyError: () -> Unit = {},
    onPreview: () -> Unit = {},
    onOpen: () -> Unit = {},
    onSave: () -> Unit = {},
    onShare: () -> Unit,
    onCancelRunning: () -> Unit = {}
) {
    val borderColor = if (selected) accent else Color.Transparent

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(
            containerColor = if (selected) Color(0xFFEFF6FF) else Color(0xFFF5F6FF)
        ),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(BorderStroke(if (selected) 2.dp else 0.dp, borderColor), RoundedCornerShape(28.dp))
            .combinedClickable(
                onClick = {
                    if (selectionMode) {
                        onToggleSelected()
                    } else {
                        onPreview()
                    }
                },
                onLongClick = {
                    // 长按是历史记录唯一删除/选择入口：外层不再提供显式删除按钮。
                    onLongPress()
                }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 18.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            if (selectionMode) {
                Box(
                    modifier = Modifier
                        .padding(top = 4.dp, end = 10.dp)
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(if (selected) accent else Color.Transparent)
                        .border(1.dp, accent, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    if (selected) {
                        Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = item.model,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF111827),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    val pill = when (item.state) {
                        "running" -> Triple("处理中", Color(0xFFFFF3D6), Color(0xFFD97706))
                        "failed" -> Triple("处理失败", Color(0xFFFFE4E6), Color(0xFFE11D48))
                        else -> Triple("处理成功", Color(0xFFDFFBEA), Color(0xFF15803D))
                    }
                    StatusPill(
                        text = pill.first,
                        bg = pill.second,
                        fg = pill.third
                    )
                }

                Text(
                    text = "${item.time} · ${if (item.mode == "edit") "图生图" else "文生图"}",
                    color = Color(0xFF4B5563),
                    style = MaterialTheme.typography.bodyMedium
                )

                if (item.state == "failed" && item.error.isNotBlank()) {
                    val firstErrorLine = item.error.lines().firstOrNull { it.isNotBlank() } ?: item.error
                    Surface(
                        color = Color(0xFFFFF1F2),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "失败原因：$firstErrorLine",
                            modifier = Modifier.padding(12.dp),
                            color = Color(0xFF991B1B),
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                if (item.state == "running") {
                    Text(
                        text = "任务仍在后台处理，可直接取消生成。",
                        color = Color(0xFFD97706),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    TextButton(
                        onClick = onCancelRunning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("取消生成", color = Color(0xFFE11D48), fontWeight = FontWeight.Bold)
                    }
                }


            }
        }
    }
}
@Composable
fun EmptyHistoryCard() {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = Color.White),
        shape = RoundedCornerShape(28.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 34.dp, horizontal = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = Color(0xFFF3F4F6),
                shape = RoundedCornerShape(22.dp)
            ) {
                Text(
                    text = "暂无记录",
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    color = Color(0xFF6B7280),
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = "生成或编辑图片后，结果会自动收纳到这里。",
                color = Color(0xFF9CA3AF),
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                text = "长按记录可进入选择删除。",
                color = Color(0xFF9CA3AF),
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}

@Composable
fun StatusPill(
    text: String,
    bg: Color,
    fg: Color
) {
    Text(
        text = text,
        color = fg,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(99.dp))
            .background(bg)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}