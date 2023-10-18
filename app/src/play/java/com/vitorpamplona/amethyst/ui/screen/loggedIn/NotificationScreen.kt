package com.vitorpamplona.amethyst.ui.screen.loggedIn

import android.Manifest
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.patrykandpatrick.vico.compose.axis.axisLabelComponent
import com.patrykandpatrick.vico.compose.axis.horizontal.bottomAxis
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.endAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberEndAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.axis.vertical.startAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.component.shape.shader.fromBrush
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.DefaultAlpha
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.chart.composed.plus
import com.patrykandpatrick.vico.core.chart.line.LineChart
import com.patrykandpatrick.vico.core.chart.values.ChartValues
import com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders
import com.vitorpamplona.amethyst.service.NostrAccountDataSource
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.note.OneGiga
import com.vitorpamplona.amethyst.ui.note.OneKilo
import com.vitorpamplona.amethyst.ui.note.OneMega
import com.vitorpamplona.amethyst.ui.note.UserReactionsRow
import com.vitorpamplona.amethyst.ui.note.UserReactionsViewModel
import com.vitorpamplona.amethyst.ui.note.showCount
import com.vitorpamplona.amethyst.ui.screen.NotificationViewModel
import com.vitorpamplona.amethyst.ui.screen.RefresheableCardView
import com.vitorpamplona.amethyst.ui.screen.ScrollStateKeys
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import com.vitorpamplona.amethyst.ui.theme.RoyalBlue
import com.vitorpamplona.amethyst.ui.theme.chartStyle
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import kotlin.math.roundToInt

@Composable
fun NotificationScreen(
    notifFeedViewModel: NotificationViewModel,
    userReactionsStatsModel: UserReactionsViewModel,
    accountViewModel: AccountViewModel,
    nav: (String) -> Unit
) {
    WatchAccountForNotifications(notifFeedViewModel, accountViewModel)

    CheckifItNeedsToRequestNotificationPermission()

    val lifeCycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifeCycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                NostrAccountDataSource.invalidateFilters()
            }
        }

        lifeCycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifeCycleOwner.lifecycle.removeObserver(observer)
        }
    }

    Column(Modifier.fillMaxHeight()) {
        Column(
            modifier = Modifier.padding(vertical = 0.dp)
        ) {
            SummaryBar(
                model = userReactionsStatsModel
            )

            RefresheableCardView(
                viewModel = notifFeedViewModel,
                accountViewModel = accountViewModel,
                nav = nav,
                routeForLastRead = Route.Notification.base,
                scrollStateKey = ScrollStateKeys.NOTIFICATION_SCREEN
            )
        }
    }
}

// TODO: Turn this into an Account flag
var hasAlreadyAskedNotificationPermissions = false

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CheckifItNeedsToRequestNotificationPermission() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !hasAlreadyAskedNotificationPermissions) {
        val notificationPermissionState = rememberPermissionState(
            Manifest.permission.POST_NOTIFICATIONS
        )

        if (!notificationPermissionState.status.isGranted) {
            hasAlreadyAskedNotificationPermissions = true

            // This will pause the APP, including the connection with relays.
            LaunchedEffect(notificationPermissionState) {
                notificationPermissionState.launchPermissionRequest()
            }
        }
    }
}

@Composable
fun WatchAccountForNotifications(
    notifFeedViewModel: NotificationViewModel,
    accountViewModel: AccountViewModel
) {
    val accountState by accountViewModel.accountLiveData.observeAsState()

    LaunchedEffect(accountViewModel, accountState?.account?.defaultNotificationFollowList) {
        NostrAccountDataSource.invalidateFilters()
        notifFeedViewModel.checkKeysInvalidateDataAndSendToTop()
    }
}

@Composable
fun SummaryBar(model: UserReactionsViewModel) {
    var showChart by remember {
        mutableStateOf(false)
    }

    UserReactionsRow(model) {
        showChart = !showChart
    }

    if (showChart) {
        val lineChartCount =
            lineChart(
                lines = listOf(RoyalBlue, Color.Green, Color.Red).map { lineChartColor ->
                    LineChart.LineSpec(
                        lineColor = lineChartColor.toArgb(),
                        lineBackgroundShader = DynamicShaders.fromBrush(
                            Brush.verticalGradient(
                                listOf(
                                    lineChartColor.copy(DefaultAlpha.LINE_BACKGROUND_SHADER_START),
                                    lineChartColor.copy(DefaultAlpha.LINE_BACKGROUND_SHADER_END)
                                )
                            )
                        )
                    )
                },
                targetVerticalAxisPosition = AxisPosition.Vertical.Start
            )

        val lineChartZaps =
            lineChart(
                lines = listOf(BitcoinOrange).map { lineChartColor ->
                    LineChart.LineSpec(
                        lineColor = lineChartColor.toArgb(),
                        lineBackgroundShader = DynamicShaders.fromBrush(
                            Brush.verticalGradient(
                                listOf(
                                    lineChartColor.copy(DefaultAlpha.LINE_BACKGROUND_SHADER_START),
                                    lineChartColor.copy(DefaultAlpha.LINE_BACKGROUND_SHADER_END)
                                )
                            )
                        )
                    )
                },
                targetVerticalAxisPosition = AxisPosition.Vertical.End
            )

        Row(
            modifier = Modifier
                .padding(vertical = 10.dp, horizontal = 20.dp)
                .clickable(onClick = { showChart = !showChart })
        ) {
            ProvideChartStyle(
                chartStyle = MaterialTheme.colorScheme.chartStyle
            ) {
                ObserveAndShowChart(model, lineChartCount, lineChartZaps)
            }
        }
    }

    Divider(
        thickness = 0.25.dp
    )
}

@Composable
private fun ObserveAndShowChart(
    model: UserReactionsViewModel,
    lineChartCount: LineChart,
    lineChartZaps: LineChart
) {
    val axisModel = model.axisLabels.collectAsState()
    val chartModel by model.chartModel.collectAsState()
    chartModel?.let {
        Chart(
            chart = remember(lineChartCount, lineChartZaps) {
                lineChartCount.plus(lineChartZaps)
            },
            model = it,
            startAxis = rememberStartAxis(
                valueFormatter = CountAxisValueFormatter()
            ),
            endAxis = rememberEndAxis(
                label = axisLabelComponent(color = BitcoinOrange),
                valueFormatter = AmountAxisValueFormatter()
            ),
            bottomAxis = rememberBottomAxis(
                valueFormatter = LabelValueFormatter(axisModel)
            )
        )
    }
}

@Stable
class LabelValueFormatter(val axisLabels: State<List<String>>) : AxisValueFormatter<AxisPosition.Horizontal.Bottom> {
    override fun formatValue(
        value: Float,
        chartValues: ChartValues
    ): String {
        return axisLabels.value[value.roundToInt()]
    }
}

@Stable
class CountAxisValueFormatter() : AxisValueFormatter<AxisPosition.Vertical.Start> {
    override fun formatValue(
        value: Float,
        chartValues: ChartValues
    ): String {
        return showCount(value.roundToInt())
    }
}

@Stable
class AmountAxisValueFormatter() : AxisValueFormatter<AxisPosition.Vertical.End> {
    override fun formatValue(
        value: Float,
        chartValues: ChartValues
    ): String {
        return showAmountAxis(value.toBigDecimal())
    }
}

var dfG: DecimalFormat = DecimalFormat("#G")
var dfM: DecimalFormat = DecimalFormat("#M")
var dfK: DecimalFormat = DecimalFormat("#k")
var dfN: DecimalFormat = DecimalFormat("#")

fun showAmountAxis(amount: BigDecimal?): String {
    if (amount == null) return ""
    if (amount.abs() < BigDecimal(0.01)) return ""

    return when {
        amount >= OneGiga -> dfG.format(amount.div(OneGiga).setScale(0, RoundingMode.HALF_UP))
        amount >= OneMega -> dfM.format(amount.div(OneMega).setScale(0, RoundingMode.HALF_UP))
        amount >= OneKilo -> dfK.format(amount.div(OneKilo).setScale(0, RoundingMode.HALF_UP))
        else -> dfN.format(amount)
    }
}
