package com.macrotracker.ui.components

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.macrotracker.ui.theme.AppIcons
import com.macrotracker.util.toDecimalOrNull
import kotlin.math.roundToInt

/**
 * The one way to log food by hand, on Home's Food card and Health's Food today alike:
 * a name, calories and protein, and Add. The caller keeps the text so it survives the
 * card scrolling away.
 */
@Composable
fun FoodLogForm(
    name: String,
    onNameChange: (String) -> Unit,
    calories: String,
    onCaloriesChange: (String) -> Unit,
    protein: String,
    onProteinChange: (String) -> Unit,
    onAdd: (name: String, calories: Int, protein: Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    Column(modifier = modifier.fillMaxWidth()) {
        MacroTextField(
            value = name,
            onValueChange = onNameChange,
            placeholder = "Food name (optional)",
            trailingIcon = clearIcon(name) { onNameChange("") },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MacroTextField(
                value = calories,
                onValueChange = { onCaloriesChange(it.numberInput()) },
                placeholder = "Calories",
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                trailingIcon = clearIcon(calories) { onCaloriesChange("") },
            )
            MacroTextField(
                value = protein,
                onValueChange = { onProteinChange(it.numberInput()) },
                placeholder = "Protein (g)",
                modifier = Modifier.weight(1f),
                keyboardType = KeyboardType.Number,
                trailingIcon = clearIcon(protein) { onProteinChange("") },
            )
        }
        MacroButton(
            text = "Add",
            // MacroButton gives the tap its own haptic; a second one here buzzed twice.
            onClick = {
                val cal = calories.toDecimalOrNull()?.roundToInt() ?: 0
                val prot = protein.toDecimalOrNull()?.roundToInt() ?: 0
                if (cal > 0 || prot > 0) {
                    onAdd(name, cal, prot)
                    onNameChange("")
                    onCaloriesChange("")
                    onProteinChange("")
                    Toast.makeText(context, "Logged", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Enter calories or protein first", Toast.LENGTH_SHORT).show()
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/** Digits and one decimal mark: no minus, so a typo can't log -100 kcal. */
private fun String.numberInput(): String {
    val kept = filter { it.isDigit() || it == '.' || it == ',' }
    val mark = kept.indexOfFirst { it == '.' || it == ',' }
    return if (mark < 0) kept else kept.substring(0, mark + 1) + kept.substring(mark + 1).filter(Char::isDigit)
}

private fun clearIcon(value: String, onClear: () -> Unit): (@Composable () -> Unit)? =
    if (value.isEmpty()) {
        null
    } else {
        {
            IconButton(onClick = onClear) {
                Icon(imageVector = AppIcons.Close, contentDescription = "Clear")
            }
        }
    }
