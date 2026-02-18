package io.github.zyrouge.symphony.ui.view.home

import androidx.compose.runtime.Composable
import io.github.zyrouge.symphony.ui.helpers.ViewContext

interface HomePageState {
    @Composable fun DropdownItems()
    @Composable fun Dialogs(context: ViewContext)
}
