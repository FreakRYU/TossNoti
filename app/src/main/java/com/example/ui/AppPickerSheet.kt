package com.example.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.TN
import com.example.apps.InstalledApp
import com.example.apps.InstalledAppsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext

/**
 * Galaxy-folder style picker, restyled to the light TossNoti theme.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPickerSheet(
    initialSelected: Set<String>,
    onDismiss: () -> Unit,
    onConfirm: (Set<String>) -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var apps by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var query by remember { mutableStateOf("") }
    val selectedFlow = remember { MutableStateFlow(initialSelected) }
    val selected by selectedFlow.collectAsState()

    LaunchedEffect(Unit) {
        val list = withContext(Dispatchers.IO) {
            InstalledAppsRepository(context).queryLaunchableApps()
        }
        apps = list
        loading = false
    }

    val filtered by remember(apps, query) {
        derivedStateOf {
            val q = query.trim().lowercase()
            val base = if (q.isEmpty()) apps else apps.filter {
                it.label.lowercase().contains(q) || it.packageName.lowercase().contains(q)
            }
            base.sortedByDescending { selectedFlow.value.contains(it.packageName) }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = TN.Bg,
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "가로챌 앱 선택",
                        color = TN.TextPrimary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp
                    )
                    Text(
                        text = "${selected.size}개 선택됨 · 총 ${apps.size}개 앱",
                        color = TN.TextSecondary,
                        fontSize = 12.sp
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(TN.Surface)
                        .clickable { onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "닫기",
                        tint = TN.TextSecondary,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("앱 검색...", color = TN.TextMuted, fontSize = 14.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, null, tint = TN.TextSecondary)
                },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = TN.Surface,
                    unfocusedContainerColor = TN.Surface,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = TN.TextPrimary,
                    unfocusedTextColor = TN.TextPrimary,
                    cursorColor = TN.Primary
                ),
                shape = RoundedCornerShape(16.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                when {
                    loading -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = TN.Primary)
                    }

                    filtered.isEmpty() -> Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (query.isBlank()) "설치된 앱을 불러오지 못했습니다." else "검색 결과가 없습니다.",
                            color = TN.TextMuted,
                            fontSize = 13.sp
                        )
                    }

                    else -> LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 84.dp),
                        contentPadding = PaddingValues(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(filtered, key = { it.packageName }) { app ->
                            AppIconCell(
                                app = app,
                                selected = selected.contains(app.packageName),
                                onClick = {
                                    val cur = selectedFlow.value.toMutableSet()
                                    if (cur.contains(app.packageName)) cur.remove(app.packageName)
                                    else cur.add(app.packageName)
                                    selectedFlow.value = cur
                                }
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TN.Surface,
                        contentColor = TN.TextPrimary
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp)
                ) {
                    Text("취소", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                }
                Button(
                    onClick = { onConfirm(selected) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = TN.Primary,
                        contentColor = TN.OnPrimary
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .weight(2f)
                        .height(56.dp)
                ) {
                    Text(
                        "완료 (${selected.size}개)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 15.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun AppIconCell(
    app: InstalledApp,
    selected: Boolean,
    onClick: () -> Unit
) {
    val ringColor = if (selected) TN.Primary else Color.Transparent
    val bgColor = if (selected) Color(0xFFE6EEFF) else TN.Surface
    val bitmap = rememberDrawableBitmap(app.icon)

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(if (selected) 2.dp else 0.dp, ringColor, RoundedCornerShape(16.dp))
            .clickable { onClick() }
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Image(
                bitmap = bitmap,
                contentDescription = app.label,
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(TN.Primary)
                        .border(2.dp, TN.Bg, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = app.label,
            color = TN.TextPrimary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            lineHeight = 13.sp
        )
    }
}

/**
 * Compact display of currently selected apps in the sender screen.
 */
@Composable
fun SelectedAppsStrip(
    selected: Set<String>,
    repo: InstalledAppsRepository,
    onRemove: (String) -> Unit
) {
    var resolved by remember { mutableStateOf<List<InstalledApp>>(emptyList()) }

    LaunchedEffect(selected) {
        if (selected.isEmpty()) {
            resolved = emptyList()
            return@LaunchedEffect
        }
        val all = withContext(Dispatchers.IO) { repo.queryLaunchableApps() }
        resolved = all.filter { selected.contains(it.packageName) }
    }

    if (selected.isEmpty()) {
        Text(
            text = "No apps selected yet — tap \"Choose\" to pick which apps to relay.",
            color = TN.TextMuted,
            fontSize = 12.sp
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        resolved.forEach { app ->
            SelectedAppRow(
                icon = app.icon,
                label = app.label,
                packageName = app.packageName,
                onRemove = { onRemove(app.packageName) }
            )
        }
        val unresolvedPkgs = selected - resolved.map { it.packageName }.toSet()
        unresolvedPkgs.forEach { pkg ->
            SelectedAppRow(
                icon = null,
                label = pkg,
                packageName = "(현재 설치되어 있지 않음)",
                onRemove = { onRemove(pkg) }
            )
        }
    }
}

@Composable
private fun SelectedAppRow(
    icon: android.graphics.drawable.Drawable?,
    label: String,
    packageName: String,
    onRemove: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color.White)
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(TN.Surface),
            contentAlignment = Alignment.Center
        ) {
            if (icon != null) {
                Image(
                    bitmap = rememberDrawableBitmap(icon, sizePx = 72),
                    contentDescription = label,
                    modifier = Modifier.size(28.dp)
                )
            } else {
                Text("?", color = TN.TextSecondary, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = TN.TextPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(packageName, color = TN.TextMuted, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(CircleShape)
                .background(TN.Surface)
                .clickable { onRemove() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "제거",
                tint = TN.TextSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
