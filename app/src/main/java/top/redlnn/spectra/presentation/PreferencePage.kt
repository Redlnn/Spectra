@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package top.redlnn.spectra.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import top.yukonga.miuix.kmp.basic.MiuixScrollBehavior
import top.yukonga.miuix.kmp.basic.Scaffold as MiScaffold
import top.yukonga.miuix.kmp.basic.TopAppBar as MiTopAppBar

@Composable
internal fun PreferencePage(
    title: String,
    miuix: Boolean,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val back: @Composable () -> Unit = {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Rounded.ArrowBack, if (title == "设置") "关闭设置" else "返回")
        }
    }
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onBackground) {
        if (miuix) {
            val behavior = MiuixScrollBehavior()
            MiScaffold(
                modifier = Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection),
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    MiTopAppBar(title = title, navigationIcon = back, scrollBehavior = behavior)
                },
                content = content,
            )
        } else {
            val behavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
            Scaffold(
                modifier = Modifier.fillMaxSize().nestedScroll(behavior.nestedScrollConnection),
                containerColor = MaterialTheme.colorScheme.background,
                topBar = {
                    LargeTopAppBar(
                        title = { Text(title) },
                        navigationIcon = back,
                        scrollBehavior = behavior,
                    )
                },
                content = content,
            )
        }
    }
}
