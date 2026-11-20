package com.macrotracker.ui.screens.health

import androidx.compose.runtime.Composable
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModelStoreOwner
import com.macrotracker.ui.screens.home.HomePage
import com.macrotracker.ui.screens.home.HomeTone
import com.macrotracker.ui.viewmodel.HealthViewModel

/**
 * The full pages the Health tab opens, as Home's tiles open theirs: the sleep ("SLEEP") and
 * a workout ("WORKOUT:<id>"). They read the tab's own view-model ([owner] is the tab's
 * back-stack entry), so nothing is read twice and a page opens on what the tab showed.
 */
@Composable
fun HealthPageHost(id: String, owner: ViewModelStoreOwner, onBack: () -> Unit) {
    val vm: HealthViewModel = hiltViewModel(owner)
    when {
        id == "SLEEP" -> SleepPage(vm, onBack)
        id.startsWith("WORKOUT:") -> WorkoutPage(vm, id.removePrefix("WORKOUT:"), onBack)
        else -> HomePage(title = "Health", tone = HomeTone.Body, onBack = onBack) { }
    }
}
