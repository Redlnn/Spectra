@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package top.redlnn.spectra.presentation

import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import top.redlnn.spectra.BuildConfig
import top.redlnn.spectra.R

private const val ProjectLicenseUrl = "https://www.gnu.org/licenses/agpl-3.0.html"

private data class OpenSourceLink(val name: String, val license: String, val url: String)

@Composable
fun AboutPage(onBack: () -> Unit) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val projects =
        remember(context) {
            val data =
                context.assets.open("open_source_notices.json").bufferedReader().use {
                    JSONArray(it.readText())
                }
            List(data.length()) { index ->
                val item = data.getJSONObject(index)
                OpenSourceLink(
                    item.getString("name"),
                    item.getString("license"),
                    item.getString("url"),
                )
            }
        }
    val openLink: (String) -> Unit = { url ->
        runCatching { uriHandler.openUri(url) }
            .onFailure {
                Toast.makeText(context, "无法打开链接，请检查是否已安装浏览器", Toast.LENGTH_SHORT).show()
            }
    }
    val dark = LocalDarkMode.current
    BoxWithConstraints(Modifier.fillMaxSize().testTag("about-page")) {
        AboutBackdrop(Modifier.matchParentSize())
        Scaffold(
            containerColor = Color.Transparent,
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "返回")
                        }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            scrolledContainerColor = Color.Transparent,
                            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                        ),
                )
            },
        ) { padding ->
            val heroHeight =
                (maxHeight * .52f - padding.calculateTopPadding()).coerceIn(240.dp, 440.dp)
            Column(
                Modifier.fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(padding)
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AboutBrand(Modifier.fillMaxWidth().heightIn(min = heroHeight))
                Text(
                    "开放源代码许可",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = .8f),
                    modifier =
                        Modifier.widthIn(max = 620.dp)
                            .fillMaxWidth()
                            .padding(start = 12.dp, bottom = 12.dp),
                )
                Card(
                    modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors =
                        CardDefaults.cardColors(
                            containerColor =
                                MaterialTheme.colorScheme.surfaceContainer.copy(
                                    alpha = if (dark) .34f else .44f
                                )
                        ),
                ) {
                    projects.forEach { project ->
                        ListItem(
                            headlineContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        project.name,
                                        modifier = Modifier.weight(1f),
                                        fontWeight = FontWeight.Medium,
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        project.license,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            supportingContent = {
                                Text(
                                    project.url.removePrefix("https://").removeSuffix("/"),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            trailingContent = {
                                Icon(
                                    Icons.Rounded.OpenInNew,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier =
                                Modifier.testTag("source-link-${project.name}").clickable(
                                    onClickLabel = "打开 ${project.name} 网站"
                                ) {
                                    openLink(project.url)
                                },
                        )
                    }
                }
                TextButton(
                    onClick = { openLink(ProjectLicenseUrl) },
                    modifier = Modifier.padding(top = 16.dp).testTag("project-license"),
                ) {
                    Text("AGPL-3.0 开源许可")
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutBrand(modifier: Modifier) {
    Column(
        modifier.padding(vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Image(
            painter = painterResource(R.drawable.spectra_icon),
            contentDescription = null,
            modifier = Modifier.size(172.dp),
            colorFilter =
                if (LocalDarkMode.current) null
                else ColorFilter.tint(MaterialTheme.colorScheme.primary),
        )
        Text(
            "声镜",
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary,
        )
        Text(
            "SPECTRA",
            style = MaterialTheme.typography.labelMedium,
            letterSpacing = 4.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .65f),
            modifier = Modifier.padding(top = 4.dp),
        )
        Text(
            "v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = .7f),
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}
