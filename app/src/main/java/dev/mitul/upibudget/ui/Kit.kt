package dev.mitul.upibudget.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

@Composable fun Hairline(modifier: Modifier = Modifier) = Box(modifier.fillMaxWidth().height(1.dp).background(pal.mist))

@Composable fun Dot(color: Color, size: Int = 8) = Box(Modifier.size(size.dp).clip(CircleShape).background(color))

@Composable fun Soft(text: String, modifier: Modifier = Modifier, align: TextAlign? = null) =
    Text(text, modifier, color = pal.inkSoft, style = MaterialTheme.typography.bodySmall, textAlign = align)

@Composable fun Heading(text: String, modifier: Modifier = Modifier) =
    Text(text, modifier.padding(top = 28.dp, bottom = 8.dp), style = MaterialTheme.typography.titleMedium)

/** Full-width moss button: the one obvious next action on a screen. */
@Composable
fun Primary(text: String, modifier: Modifier = Modifier, enabled: Boolean = true, onClick: () -> Unit) =
    Button(onClick, modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = enabled, shape = MaterialTheme.shapes.medium,
        colors = ButtonDefaults.buttonColors(containerColor = pal.moss, contentColor = pal.onMoss,
            disabledContainerColor = pal.mist, disabledContentColor = pal.inkSoft)) { Text(text, style = MaterialTheme.typography.labelLarge) }

/** Text-only action in moss. */
@Composable
fun Quiet(text: String, modifier: Modifier = Modifier, color: Color = pal.moss, onClick: () -> Unit) =
    TextButton(onClick, modifier, colors = ButtonDefaults.textButtonColors(contentColor = color)) { Text(text, style = MaterialTheme.typography.labelLarge) }

/** A tappable settings/list row: title, optional detail on the second line, optional value on the right. */
@Composable
fun ListRow(title: String, detail: String? = null, value: String? = null, valueColor: Color = pal.inkSoft, onClick: (() -> Unit)? = null) {
    Column {
        androidx.compose.foundation.layout.Row(
            Modifier.fillMaxWidth().then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier).padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (detail != null) Soft(detail)
            }
            if (value != null) Text(value, color = valueColor, style = MaterialTheme.typography.labelLarge)
        }
        Hairline()
    }
}

/**
 * A text field that takes focus as soon as its dialog opens. The keyboard can cover a dialog's buttons,
 * so the keyboard's Done key runs [onDone] (the dialog's main action).
 */
@Composable
fun FocusedField(value: String, onChange: (String) -> Unit, modifier: Modifier = Modifier, label: String? = null, onDone: () -> Unit = {}) {
    val fr = androidx.compose.runtime.remember { androidx.compose.ui.focus.FocusRequester() }
    androidx.compose.runtime.LaunchedEffect(Unit) { fr.requestFocus() }
    OutlinedTextField(value, onChange, modifier.focusRequester(fr), label = label?.let { { Text(it) } }, singleLine = true, shape = MaterialTheme.shapes.medium,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Done),
        keyboardActions = androidx.compose.foundation.text.KeyboardActions(onDone = { onDone() }))
}
