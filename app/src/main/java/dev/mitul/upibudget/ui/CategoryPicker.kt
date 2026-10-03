package dev.mitul.upibudget.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.mitul.upibudget.data.CategoryEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategoryPickerSheet(categories: List<CategoryEntity>, onPick: (Long) -> Unit, onDismiss: () -> Unit) {
    var q by remember { mutableStateOf("") }
    val groups = categories.filter { it.parentId == null }
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = pal.paper, shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.padding(horizontal = 20.dp).fillMaxHeight(0.85f)) {
            OutlinedTextField(q, { q = it }, placeholder = { Text("Search categories") }, singleLine = true,
                shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = pal.moss, unfocusedBorderColor = pal.mist))
            LazyColumn(contentPadding = PaddingValues(bottom = 32.dp)) {
                for (g in groups) {
                    val subs = categories.filter { it.parentId == g.id && (q.isBlank() || it.name.contains(q, true) || g.name.contains(q, true)) }
                    if (subs.isEmpty()) continue
                    item(key = "g${g.id}") { Text(g.name, color = pal.inkSoft, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 20.dp, bottom = 4.dp)) }
                    items(subs, key = { it.id }) { c ->
                        Row(Modifier.fillMaxWidth().clickable { onPick(c.id) }.padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Dot(Color(c.color)); Spacer(Modifier.width(12.dp)); Text(c.name, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        }
    }
}
