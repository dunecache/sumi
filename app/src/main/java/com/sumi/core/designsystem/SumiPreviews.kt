package com.sumi.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.sumi.core.designsystem.component.EmptyState
import com.sumi.core.designsystem.component.LoadingState
import com.sumi.core.designsystem.theme.SumiSpacing
import com.sumi.core.designsystem.theme.SumiTheme

@Composable
private fun PreviewScaffold(content: @Composable () -> Unit) {
    Column {
        Text(text = "Your day, in perspective.", style = MaterialTheme.typography.displayLarge)
        Spacer(Modifier.height(SumiSpacing.md))
        content()
    }
}

@Preview(showBackground = true, name = "Light")
@Composable
private fun PreviewLight() {
    SumiTheme(darkTheme = false) { PreviewScaffold { EmptyState("No usage yet", "Come back later.") } }
}

@Preview(showBackground = true, name = "Dark")
@Composable
private fun PreviewDark() {
    SumiTheme(darkTheme = true) { PreviewScaffold { EmptyState("No usage yet", "Come back later.") } }
}

@Preview(showBackground = true, name = "Large font", fontScale = 1.5f)
@Composable
private fun PreviewLargeFont() {
    SumiTheme(darkTheme = false) { PreviewScaffold { LoadingState() } }
}
