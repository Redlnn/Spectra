package top.redlnn.spectra.presentation.home

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleStartEffect
import java.util.Date

@Composable
internal fun AnalyzerDeviceStatus(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var time by remember { mutableStateOf(DateFormat.getTimeFormat(context).format(Date())) }
    var battery by remember { mutableStateOf<Int?>(null) }
    var charging by remember { mutableStateOf(false) }
    LifecycleStartEffect(context) {
        time = DateFormat.getTimeFormat(context).format(Date())
        val receiver =
            object : BroadcastReceiver() {
                override fun onReceive(context: Context, intent: Intent) {
                    if (intent.action == Intent.ACTION_BATTERY_CHANGED) {
                        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
                        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
                        battery =
                            if (level >= 0 && scale > 0) (level * 100 / scale).coerceIn(0, 100)
                            else null
                        charging =
                            intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1) in
                                listOf(
                                    BatteryManager.BATTERY_STATUS_CHARGING,
                                    BatteryManager.BATTERY_STATUS_FULL,
                                )
                    } else {
                        time = DateFormat.getTimeFormat(context).format(Date())
                    }
                }
            }
        val filter =
            IntentFilter().apply {
                addAction(Intent.ACTION_BATTERY_CHANGED)
                addAction(Intent.ACTION_TIME_TICK)
                addAction(Intent.ACTION_TIME_CHANGED)
                addAction(Intent.ACTION_TIMEZONE_CHANGED)
            }
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        onStopOrDispose { context.unregisterReceiver(receiver) }
    }
    val batteryColor =
        when {
            battery != null && battery!! <= 15 && !charging -> MaterialTheme.colorScheme.error
            charging -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
    Row(
        modifier.testTag("landscape-device-status"),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            time,
            modifier = Modifier.testTag("landscape-time"),
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                when {
                    charging -> Icons.Rounded.BatteryChargingFull
                    battery == null -> Icons.Rounded.BatteryUnknown
                    battery!! <= 15 -> Icons.Rounded.BatteryAlert
                    battery!! < 50 -> Icons.Rounded.Battery3Bar
                    battery!! < 85 -> Icons.Rounded.Battery5Bar
                    else -> Icons.Rounded.BatteryFull
                },
                contentDescription = if (charging) "正在充电" else "电量",
                modifier = Modifier.size(16.dp),
                tint = batteryColor,
            )
            Text(
                battery?.let { "$it%" } ?: "—",
                modifier = Modifier.testTag("landscape-battery"),
                fontSize = 11.sp,
                color = batteryColor,
                maxLines = 1,
            )
        }
    }
}
