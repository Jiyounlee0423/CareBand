package com.example.careband.ui.screens

import android.app.DatePickerDialog
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.careband.data.model.VitalSignsRecord
import com.example.careband.viewmodel.VitalSignsViewModel
import com.example.careband.viewmodel.VitalSignsViewModelFactory
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.core.axis.formatter.AxisValueFormatter
import com.patrykandpatrick.vico.core.axis.AxisPosition
import com.patrykandpatrick.vico.core.entry.ChartEntry
import com.patrykandpatrick.vico.core.entry.entryModelOf
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.*
import java.util.Locale

@Composable
fun VitalSignsChartScreen(
    userId: String,
    viewModel: VitalSignsViewModel = viewModel(factory = VitalSignsViewModelFactory(userId))
) {
    val selectedTab = remember { mutableStateOf(0) }
    val tabTitles = listOf("전체 보기", "하루 보기")

    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = selectedTab.value) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab.value == index,
                    onClick = { selectedTab.value = index },
                    text = { Text(title) }
                )
            }
        }

        when (selectedTab.value) {
            0 -> VitalSignsRangeTab(userId, viewModel)
            1 -> VitalSignsDailyTab(viewModel)
        }
    }
}

@Composable
fun VitalSignsRangeTab(userId: String, viewModel: VitalSignsViewModel) {
    val records by viewModel.records.collectAsState()
    val healthRecords by viewModel.healthRecords.collectAsState()
    val selectedRange = remember { mutableStateOf(7) }
    val today = LocalDate.now()
    val listState = rememberLazyListState()

    LaunchedEffect(selectedRange.value) {
        val fromDate = today.minusDays(selectedRange.value.toLong())
        viewModel.loadVitalRecords(fromDate)
        viewModel.loadHealthRecords(fromDate)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { selectedRange.value = 7 }) { Text("7일간") }
                Button(onClick = { selectedRange.value = 30 }) { Text("1개월") }
            }
        }

        // ✅ 날짜별 평균 계산 함수
        fun averageByDate(
            records: List<VitalSignsRecord>,
            valueSelector: (VitalSignsRecord) -> Number?
        ): List<Pair<String, Int>> {
            return records
                .filter { valueSelector(it) != null && valueSelector(it)!!.toFloat() > 0f }
                .groupBy { it.date }
                .mapValues { (_, values) ->
                    values.mapNotNull { valueSelector(it)?.toFloat() }.average().toInt()
                }
                .toSortedMap()
                .map { (date, avg) ->
                    val label = try {
                        LocalDate.parse(date).format(DateTimeFormatter.ofPattern("MM/dd"))
                    } catch (e: Exception) {
                        date
                    }
                    label to avg
                }
        }

        // ✅ 평균 그래프 출력
        item {
            Text("심박수 (BPM)", style = MaterialTheme.typography.titleMedium)
            val bpmAvg = averageByDate(records) { it.heartRate }
            VitalLineChart(
                values = bpmAvg.map { it.second },
                labels = bpmAvg.map { it.first }
            )
        }

        item {
            Text("산소포화도 (%)", style = MaterialTheme.typography.titleMedium)
            val spo2Avg = averageByDate(records) { it.spo2 }
            VitalLineChart(
                values = spo2Avg.map { it.second },
                labels = spo2Avg.map { it.first }
            )
        }

        item {
            Text("체온 (°C)", style = MaterialTheme.typography.titleMedium)
            val tempAvg = averageByDate(records) { it.bodyTemp.toInt() }
            VitalLineChart(
                values = tempAvg.map { it.second },
                labels = tempAvg.map { it.first }
            )
        }

        // ✅ 기존 건강 기록 유지 (체중, 혈압, 혈당)
        val formatLabel: (String) -> String = {
            try {
                LocalDate.parse(it).format(DateTimeFormatter.ofPattern("MM/dd"))
            } catch (e: Exception) { it }
        }

        item {
            Text("체중 (kg)", style = MaterialTheme.typography.titleMedium)
            VitalLineChart(
                values = healthRecords.map { it.weight },
                labels = healthRecords.map { formatLabel(it.date) }
            )
        }

        item {
            Text("수축기 혈압", style = MaterialTheme.typography.titleMedium)
            VitalLineChart(
                values = healthRecords.map { it.systolic },
                labels = healthRecords.map { formatLabel(it.date) }
            )
        }

        item {
            Text("이완기 혈압", style = MaterialTheme.typography.titleMedium)
            VitalLineChart(
                values = healthRecords.map { it.diastolic },
                labels = healthRecords.map { formatLabel(it.date) }
            )
        }

        item {
            Text("식후 혈당", style = MaterialTheme.typography.titleMedium)
            VitalLineChart(
                values = healthRecords.map { it.glucosePost },
                labels = healthRecords.map { formatLabel(it.date) }
            )
        }

        item {
            Text("공복 혈당", style = MaterialTheme.typography.titleMedium)
            VitalLineChart(
                values = healthRecords.map { it.glucoseFasting },
                labels = healthRecords.map { formatLabel(it.date) }
            )
        }
    }
}

@Composable
fun VitalSignsDailyTab(viewModel: VitalSignsViewModel) {
    val context = LocalContext.current
    val records by viewModel.records.collectAsState()
    val selectedDate = remember { mutableStateOf(LocalDate.now()) }

    val datePicker = DatePickerDialog(
        context,
        { _, y, m, d -> selectedDate.value = LocalDate.of(y, m + 1, d) },
        selectedDate.value.year,
        selectedDate.value.monthValue - 1,
        selectedDate.value.dayOfMonth
    )

    // ✅ 날짜 기준 필터링
    val filtered = records.filter { it.date == selectedDate.value.toString() }

    // ✅ 디버깅용 로그 출력
    LaunchedEffect(records) {
        println("✅ 전체 레코드 수: ${records.size}")
        records.forEach {
            println("📄 timestamp=${it.timestamp}, date=${it.date}, HR=${it.heartRate}, SpO2=${it.spo2}, Temp=${it.bodyTemp}")
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("선택 날짜: ${selectedDate.value}", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { datePicker.show() }) {
                    Text("날짜 선택")
                }
            }
        }

        item {
            Text("심박수 (BPM)", style = MaterialTheme.typography.titleMedium)
            VitalLineChart(
                values = filtered.mapNotNull { it.heartRate?.takeIf { hr -> hr > 0 } },
                labels = filtered.filter { it.heartRate != null && it.heartRate > 0 }.map {
                    it.timestamp.substringAfter("T").substring(0, 5)
                }
            )
        }

        item {
            Text("산소포화도 (%)", style = MaterialTheme.typography.titleMedium)
            VitalLineChart(
                values = filtered.mapNotNull { it.spo2.takeIf { s -> s > 0 } },
                labels = filtered.filter { it.spo2 > 0 }.map {
                    it.timestamp.substringAfter("T").substring(0, 5)
                }
            )
        }

        item {
            Text("체온 (°C)", style = MaterialTheme.typography.titleMedium)
            VitalLineChart(
                values = filtered.mapNotNull { it.bodyTemp.takeIf { t -> t > 0 }?.toInt() },
                labels = filtered.filter { it.bodyTemp > 0 }.map {
                    it.timestamp.substringAfter("T").substring(0, 5)
                }
            )
        }
    }
}

@Composable
fun VitalLineChart(values: List<Int>, labels: List<String>) {
    if (values.isEmpty() || labels.isEmpty()) {
        Text("데이터 없음", style = MaterialTheme.typography.bodyMedium)
        return
    }

    val entries = entryModelOf(*values.mapIndexed { i, v -> i to v }.toTypedArray())
    val labelMap = labels.mapIndexed { i, label -> i.toFloat() to label }.toMap()

    val customFormatter = AxisValueFormatter<AxisPosition.Horizontal.Bottom> { value, _ ->
        labelMap[value] ?: ""
    }

    Chart(
        chart = lineChart(), // ✅ chartModelProducer 아님
        model = entries,
        startAxis = rememberStartAxis(),
        bottomAxis = rememberBottomAxis(valueFormatter = customFormatter),
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
    )
}