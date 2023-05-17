package com.vitorpamplona.amethyst.ui.screen.loggedIn

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Divider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.patrykandpatrick.vico.compose.axis.horizontal.bottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.endAxis
import com.patrykandpatrick.vico.compose.axis.vertical.startAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.component.shape.shader.fromBrush
import com.patrykandpatrick.vico.compose.style.ProvideChartStyle
import com.patrykandpatrick.vico.core.DefaultAlpha
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.chart.composed.ComposedChartEntryModel
import com.patrykandpatrick.vico.core.chart.composed.plus
import com.patrykandpatrick.vico.core.chart.line.LineChart
import com.patrykandpatrick.vico.core.chart.values.ChartValues
import com.patrykandpatrick.vico.core.component.shape.shader.DynamicShaders
import com.patrykandpatrick.vico.core.entry.ChartEntryModel
import com.patrykandpatrick.vico.core.entry.ChartEntryModelProducer
import com.patrykandpatrick.vico.core.entry.composed.plus
import com.patrykandpatrick.vico.core.entry.entryOf
import com.vitorpamplona.amethyst.service.NostrAccountDataSource
import com.vitorpamplona.amethyst.ui.dal.NotificationFeedFilter
import com.vitorpamplona.amethyst.ui.navigation.Route
import com.vitorpamplona.amethyst.ui.note.UserReactionsRow
import com.vitorpamplona.amethyst.ui.note.UserReactionsViewModel
import com.vitorpamplona.amethyst.ui.note.showAmount
import com.vitorpamplona.amethyst.ui.note.showCount
import com.vitorpamplona.amethyst.ui.screen.CardFeedView
import com.vitorpamplona.amethyst.ui.screen.NotificationViewModel
import com.vitorpamplona.amethyst.ui.screen.ScrollStateKeys
import com.vitorpamplona.amethyst.ui.theme.BitcoinOrange
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@Composable
fun NotificationScreen(
    notifFeedViewModel: NotificationViewModel,
    accountViewModel: AccountViewModel,
    navController: NavController,
    scrollToTop: Boolean = false
) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val account = accountState?.account ?: return

    if (scrollToTop) {
        notifFeedViewModel.clear()
    }

    LaunchedEffect(account.userProfile().pubkeyHex, account.defaultNotificationFollowList) {
        NostrAccountDataSource.resetFilters()
        NotificationFeedFilter.account = account
        notifFeedViewModel.clear()
        notifFeedViewModel.refresh()
    }

    val lifeCycleOwner = LocalLifecycleOwner.current
    DisposableEffect(accountViewModel) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                NotificationFeedFilter.account = account
                notifFeedViewModel.invalidateData()
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
            SummaryBar(accountViewModel, navController)
            CardFeedView(
                viewModel = notifFeedViewModel,
                accountViewModel = accountViewModel,
                navController = navController,
                routeForLastRead = Route.Notification.base,
                scrollStateKey = ScrollStateKeys.NOTIFICATION_SCREEN,
                scrollToTop = scrollToTop
            )
        }
    }
}

@Composable
fun SummaryBar(accountViewModel: AccountViewModel, navController: NavController) {
    val accountState by accountViewModel.accountLiveData.observeAsState()
    val accountUser = remember(accountState) { accountState?.account?.userProfile() } ?: return

    val model: UserReactionsViewModel = viewModel()

    var chartModel by remember(accountState) { mutableStateOf<ComposedChartEntryModel<ChartEntryModel>?>(null) }
    var axisLabels by remember(accountState) { mutableStateOf<List<String>>(emptyList()) }

    val scope = rememberCoroutineScope()

    var showChart by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(accountUser.pubkeyHex) {
        scope.launch {
            model.load(accountUser)
            model.refreshSuspended()
            val day = 24 * 60 * 60L
            val now = LocalDateTime.now()
            val displayAxisFormatter = DateTimeFormatter.ofPattern("EEE")

            val dataAxisLabels = listOf(6, 5, 4, 3, 2, 1, 0).map { model.sdf.format(now.minusSeconds(day * it)) }
            axisLabels = listOf(6, 5, 4, 3, 2, 1, 0).map { displayAxisFormatter.format(now.minusSeconds(day * it)) }

            val listOfCountCurves = listOf(
                dataAxisLabels.mapIndexed { index, dateStr -> entryOf(index, model.replies[dateStr]?.toFloat() ?: 0f) },
                dataAxisLabels.mapIndexed { index, dateStr -> entryOf(index, model.boosts[dateStr]?.toFloat() ?: 0f) },
                dataAxisLabels.mapIndexed { index, dateStr -> entryOf(index, model.reactions[dateStr]?.toFloat() ?: 0f) }
            )

            val listOfValueCurves = listOf(
                dataAxisLabels.mapIndexed { index, dateStr -> entryOf(index, model.zaps[dateStr]?.toFloat() ?: 0f) }
            )

            val chartEntryModelProducer1 = ChartEntryModelProducer(listOfCountCurves).getModel()
            val chartEntryModelProducer2 = ChartEntryModelProducer(listOfValueCurves).getModel()

            chartModel = chartEntryModelProducer1.plus(chartEntryModelProducer2)
        }
    }

    UserReactionsRow(model, accountViewModel, navController) {
        showChart = !showChart
    }

    val lineChartCount =
        lineChart(
            lines = listOf(Color.Cyan, Color.Green, Color.Red).map { lineChartColor ->
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

    chartModel?.let {
        if (showChart) {
            Row(modifier = Modifier.padding(vertical = 10.dp, horizontal = 20.dp).clickable(onClick = { showChart = !showChart })) {
                ProvideChartStyle() {
                    Chart(
                        chart = remember(lineChartCount, lineChartZaps) {
                            lineChartCount.plus(lineChartZaps)
                        },
                        model = it,
                        startAxis = startAxis(
                            valueFormatter = CountAxisValueFormatter()
                        ),
                        endAxis = endAxis(
                            valueFormatter = AmountAxisValueFormatter()
                        ),
                        bottomAxis = bottomAxis(
                            valueFormatter = LabelValueFormatter(axisLabels)
                        )
                    )
                }
            }
        }
    }

    Divider(
        thickness = 0.25.dp
    )
}

class LabelValueFormatter(val axisLabels: List<String>) : AxisValueFormatter<AxisPosition.Horizontal.Bottom> {
    override fun formatValue(
        value: Float,
        chartValues: ChartValues
    ): String {
        return axisLabels[value.roundToInt()]
    }
}

class CountAxisValueFormatter() : AxisValueFormatter<AxisPosition.Vertical.Start> {
    override fun formatValue(
        value: Float,
        chartValues: ChartValues
    ): String {
        return showCount(value.roundToInt())
    }
}

class AmountAxisValueFormatter() : AxisValueFormatter<AxisPosition.Vertical.End> {
    override fun formatValue(
        value: Float,
        chartValues: ChartValues
    ): String {
        return showAmount(value.toBigDecimal())
    }
}
