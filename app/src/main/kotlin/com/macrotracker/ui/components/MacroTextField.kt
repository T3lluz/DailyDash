package com.macrotracker.ui.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.macrotracker.ui.theme.Background
import com.macrotracker.ui.theme.Border
import com.macrotracker.ui.theme.Primary
import com.macrotracker.ui.theme.TextPrimary
import com.macrotracker.ui.theme.TextSecondary

@Composable
fun MacroTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    trailingIcon: @Composable (() -> Unit)? = null, // Added trailingIcon parameter
    textAlignment: TextAlign? = null,
    colors: TextFieldColors? = null,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val defaultColors = com.macrotracker.ui.screens.home.roomFieldColors()

    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        placeholder = { Text(placeholder, color = TextSecondary, textAlign = textAlignment ?: TextAlign.Start, modifier = Modifier.fillMaxWidth()) },
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = com.macrotracker.ui.screens.home.FieldShape,
        textStyle = LocalTextStyle.current.copy(textAlign = textAlignment ?: TextAlign.Start),
        colors = colors ?: defaultColors,
        visualTransformation = visualTransformation,
        trailingIcon = trailingIcon // Added trailingIcon to OutlinedTextField
    )
}
