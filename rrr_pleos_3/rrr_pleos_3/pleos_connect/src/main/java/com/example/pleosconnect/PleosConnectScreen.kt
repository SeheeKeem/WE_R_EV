package com.example.pleosconnect

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

// ───────────────────────── 스타일 ─────────────────────────

private val Pretendard = FontFamily(
    Font(R.font.pretendard_regular, FontWeight.Normal),
    Font(R.font.pretendard_medium, FontWeight.Medium),
    Font(R.font.pretendard_semibold, FontWeight.SemiBold),
    Font(R.font.pretendard_bold, FontWeight.Bold),
    Font(R.font.pretendard_extrabold, FontWeight.ExtraBold),
)
private val Ink = Color(0xFF1C1F26)
private val Sub = Color(0xFF4B5060)
private val Faint = Color(0xFF8A8F9C)
private val Panel = Color(0xFFF3F4F7)
private val Soft = Color(0xFFF4F5F8)
private val Green = Color(0xFF16A34A)
private val Ev = Color(0xFF22C55E)
private val Amber = Color(0xFFF59E0B)
private val Red = Color(0xFFE5484D)
private val Blue = Color(0xFF3B82F6)

@Composable
private fun T(
    text: String, size: TextUnit, weight: FontWeight = FontWeight.Normal, color: Color = Ink,
    modifier: Modifier = Modifier, spacing: TextUnit = TextUnit.Unspecified,
) = Text(text, modifier, color = color, fontSize = size, fontWeight = weight, fontFamily = Pretendard,
    letterSpacing = spacing, lineHeight = size * 1.15f, maxLines = 1, softWrap = false)

private fun Modifier.card(r: Int = 22) =
    shadow(3.dp, RoundedCornerShape(r.dp), ambientColor = Color(0x14000000), spotColor = Color(0x14000000))
        .clip(RoundedCornerShape(r.dp)).background(Color.White)

// ───────────────────────── 화면 상태 ─────────────────────────

private data class Hud(
    val gen: Boolean, val battery: Int, val ratio: Int, val speed: Int, val range: Int, val odo: String,
    val clock: String, val eta: String, val remain: String,
    val turnIc: String, val turnDist: String, val street: String, val nextIc: String, val nextDist: String,
    val road: String, val lanes: List<Boolean>,
)

private val ClockFmt = DateTimeFormatter.ofPattern("h:mm a", Locale.US)

private fun fmtDist(m: Float) = if (m >= 1000) "%.1fkm".format(m / 1000f) else "${(m / 10f).roundToInt() * 10}m"
private fun arrow(dir: Int) = if (dir > 0) "↱" else "↰"

private fun hudAt(simT: Float): Hud {
    val sc = Scenario.at(simT % Scenario.LOOP)
    val s = simT * Scenario.SIM_MPS
    val turns = DriveRoute.upcoming(s)
    val (i1, d1) = turns[0]; val (i2, d2) = turns[1]
    val dir1 = DriveRoute.turnDir(i1)
    val ic = if (d1 > 700) "↑" else arrow(dir1)
    val remainKm = 31.2f - (simT * 0.11f) % 26f
    val mins = (remainKm / 1.15f).roundToInt()
    val now = LocalTime.now()
    return Hud(
        gen = sc.gen, battery = sc.battery, ratio = sc.ratio,
        speed = (60 + 3 * sin(simT * 0.7f) + 1.5f * sin(simT * 1.9f)).roundToInt(),
        range = 323 + sc.battery, odo = "%.1fkm".format(5035.1f + simT * 0.11f),
        clock = now.format(ClockFmt), eta = now.plusMinutes(mins.toLong()).format(ClockFmt),
        remain = "${mins}분 · ${"%.1f".format(remainKm)}km",
        turnIc = ic, turnDist = fmtDist(d1), street = DriveRoute.SEG[i1].first,
        nextIc = arrow(DriveRoute.turnDir(i2)), nextDist = fmtDist(d2 - d1),
        road = DriveRoute.SEG[DriveRoute.segmentAt(s)].second,
        lanes = List(6) { i -> if (ic == "↑") i in 1..3 else if (dir1 > 0) i >= 4 else i == 0 },
    )
}

private val AnimText = mapOf("auto" to "자동 전환", "gen" to "발전 애니메이션 고정", "ev" to "EV 애니메이션 고정")
private val MotionText = mapOf("normal" to "기본 움직임", "calm" to "차분하게", "off" to "멈춤")
fun settingsToastText(s: WidgetSettings) = "르르링 위젯 설정 · ${AnimText[s.anim]} · ${MotionText[s.motion]}"

// ───────────────────────── 화면 ─────────────────────────

/**
 * @param appToast 르르링에서 설정이 바뀌었을 때 띄울 문구 (key 가 바뀔 때마다 표시)
 */
@Composable
fun PleosConnectScreen(settings: WidgetSettings, appToastKey: Int = 0, appToast: String? = null) {
    // 주행 시계 (초). 지도/도로는 그리기 단계에서만 읽고, 글자 정보는 0.1초 단위로만 갱신
    var simT by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var last = -1L
        while (true) withFrameNanos { n ->
            if (last >= 0) simT += ((n - last) / 1e9f).coerceAtMost(0.1f)
            last = n
        }
    }
    val tick by remember { derivedStateOf { (simT * 10).toInt() } }
    val hud = remember(tick) { hudAt(tick / 10f) }

    // 토스트 (모드 전환 / 르르링 설정 변경)
    var toast by remember { mutableStateOf<String?>(null) }
    var toastKey by remember { mutableIntStateOf(0) }
    var firstMode by remember { mutableStateOf(true) }
    LaunchedEffect(hud.gen) {
        if (firstMode) { firstMode = false; return@LaunchedEffect }
        if (settings.toast) {
            toast = if (hud.gen) "⚡ 배터리 20% · 발전 모드로 전환했어요" else "🔋 배터리로 조용히 주행 중"
            toastKey++
        }
    }
    LaunchedEffect(appToastKey) { if (appToastKey > 0 && appToast != null) { toast = appToast; toastKey++ } }
    LaunchedEffect(toastKey) { if (toastKey > 0) { delay(4000); toast = null } }

    val widgetMode = when (settings.anim) { "gen" -> "gen"; "ev" -> "ev"; else -> if (hud.gen) "gen" else "ev" }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFDFE2E8)), contentAlignment = Alignment.Center) {
        val scale = min(maxWidth / 1600.dp, maxHeight / 880.dp)
        Box(Modifier.requiredSize(1600.dp, 880.dp).graphicsLayer { scaleX = scale; scaleY = scale }) {
            Column(
                Modifier.fillMaxSize().padding(22.dp)
                    .shadow(18.dp, RoundedCornerShape(36.dp)).clip(RoundedCornerShape(36.dp)).background(Panel)
                    .padding(horizontal = 28.dp, vertical = 26.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Cluster(hud, { simT }, Modifier.width(450.dp).fillMaxHeight())
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        StatusBar(hud.clock, Modifier.fillMaxWidth().height(44.dp))
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            MapCard(hud, { simT }, toast, Modifier.weight(1f).fillMaxHeight())
                            WidgetCard(hud, widgetMode, settings.motion, Modifier.width(340.dp).fillMaxHeight())
                        }
                    }
                }
                Dock(Modifier.fillMaxWidth().height(84.dp))
            }
        }
    }
}

// ───────────────────────── 계기판 ─────────────────────────

@Composable
private fun Cluster(hud: Hud, simT: () -> Float, modifier: Modifier) {
    Box(modifier.card()) {
        Column(Modifier.fillMaxSize().padding(start = 26.dp, end = 26.dp, top = 22.dp)) {
            Row {
                Column(Modifier.width(120.dp)) {
                    T("${hud.speed}", 76.sp, FontWeight.Bold, spacing = (-2).sp)
                    T("km/h", 15.sp, color = Sub, modifier = Modifier.padding(top = 4.dp))
                }
                T("D", 30.sp, FontWeight.Bold, modifier = Modifier.padding(start = 54.dp, top = 4.dp))
                Spacer(Modifier.weight(1f))
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        T("${hud.range}km", 17.sp, FontWeight.SemiBold)
                        T("🔌", 12.sp)
                        T("${hud.battery}", 12.sp, FontWeight.Bold, Color.White,
                            Modifier.clip(RoundedCornerShape(5.dp)).background(Ink).padding(horizontal = 7.dp, vertical = 1.dp))
                    }
                    T("READY", 12.sp, FontWeight.Bold, Green, Modifier.padding(top = 8.dp), spacing = 0.5.sp)
                }
            }
            Row(Modifier.padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(52.dp).border(5.dp, Red, CircleShape), contentAlignment = Alignment.Center) {
                    T("60", 19.sp, FontWeight.ExtraBold)
                }
                Spacer(Modifier.width(22.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        T(hud.turnIc, 28.sp, FontWeight.Bold); Spacer(Modifier.width(6.dp))
                        T(hud.turnDist, 30.sp, FontWeight.Bold, spacing = (-0.5).sp)
                    }
                    T(hud.street, 11.sp, color = Faint)
                }
                Spacer(Modifier.width(22.dp))
                T("${hud.nextIc} ${hud.nextDist}", 14.sp, FontWeight.SemiBold, Color(0xFF2D313B), Modifier.align(Alignment.Top).padding(top = 6.dp))
            }
            RoadScene(simT, Modifier.fillMaxWidth().weight(1f).padding(top = 6.dp, bottom = 100.dp).offset(x = 0.dp))
        }
        T(hud.odo, 15.sp, FontWeight.SemiBold, modifier = Modifier.align(Alignment.BottomStart).padding(start = 26.dp, bottom = 112.dp))
        Column(Modifier.align(Alignment.BottomEnd).padding(end = 26.dp, bottom = 108.dp), horizontalAlignment = Alignment.End) {
            T("Lv 1", 14.sp, FontWeight.Bold); T("26°C", 12.sp, FontWeight.Medium, Sub)
        }
        Row(
            Modifier.align(Alignment.BottomCenter).padding(14.dp).fillMaxWidth().height(78.dp)
                .clip(RoundedCornerShape(16.dp)).background(Soft).padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            T("⏮ ⏸ ⏭", 11.sp, color = Color(0xFF2D313B), spacing = 3.sp)
            Column(Modifier.weight(1f)) {
                T("차량용 인포테인먼트 시스템", 15.sp, FontWeight.Bold)
                T("현대자동차그룹(HYUNDAI)", 11.sp, color = Faint)
            }
            Box(Modifier.size(46.dp).clip(RoundedCornerShape(10.dp))
                .background(Brush.linearGradient(listOf(Color(0xFFA855F7), Color(0xFFEC4899), Color(0xFFF59E0B)))))
        }
    }
}

private fun sp(d: String): Path = PathParser().parsePathString(d).toPath()

private object CarPaths {
    val road = sp("M 196 0 L 254 0 L 430 330 L 20 330 Z")
    val body = sp("M 14 34 Q 18 8 40 6 L 110 6 Q 132 8 136 34 L 142 58 L 142 100 Q 142 108 134 108 L 16 108 Q 8 108 8 100 L 8 58 Z")
    val window = sp("M 28 14 L 122 14 Q 128 16 130 30 L 132 44 L 18 44 L 20 30 Q 22 16 28 14 Z")
    val glare = sp("M 34 18 L 70 18 L 54 40 L 24 40 Z")
}

private val platePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
    textAlign = android.graphics.Paint.Align.CENTER; textSize = 7.5f
    typeface = android.graphics.Typeface.DEFAULT_BOLD; color = Ink.toArgb()
}

/** 원근 도로 + 차선 이동 + 내 차 후면 (viewBox 450x330, 아래 정렬) */
@Composable
private fun RoadScene(simT: () -> Float, modifier: Modifier) {
    Canvas(modifier) {
        val s = min(size.width / 450f, size.height / 330f)
        withTransform({
            translate((size.width - 450f * s) / 2f, size.height - 330f * s)
            scale(s, s, Offset.Zero)
        }) {
            drawPath(CarPaths.road, Brush.verticalGradient(
                0f to Color(0x00F3F4F7), 0.35f to Color(0xFFEEF0F3), 1f to Color(0xFFE4E7EC), startY = 0f, endY = 330f))
            val phase = 40f - ((simT() / 0.55f) * 40f) % 40f
            val dash = PathEffect.dashPathEffect(floatArrayOf(18f, 22f), phase)
            drawLine(Color(0xFFC4C8D0), Offset(214f, 0f), Offset(150f, 330f), 3f, pathEffect = dash)
            drawLine(Color(0xFFC4C8D0), Offset(236f, 0f), Offset(300f, 330f), 3f, pathEffect = dash)
            drawLine(Color(0xFFD5D8DE), Offset(200f, 0f), Offset(60f, 330f), 2f)
            drawLine(Color(0xFFD5D8DE), Offset(250f, 0f), Offset(390f, 330f), 2f)
            drawRoundRect(Color(0xFFD8DBE1), Offset(210f, 66f), Size(18f, 12f), CornerRadius(3f))
            drawRoundRect(Color(0xFFD8DBE1), Offset(250f, 128f), Size(26f, 17f), CornerRadius(4f))
            withTransform({ translate(150f, 178f) }) {
                drawOval(Color.Black, Offset(-7f, 103f), Size(164f, 18f), alpha = 0.12f)
                drawPath(CarPaths.body, Color(0xFF17181C))
                drawPath(CarPaths.window, Color(0xFF3B3F48))
                drawPath(CarPaths.glare, Color(0xFF565B66), alpha = 0.5f)
                drawRoundRect(Red, Offset(8f, 56f), Size(134f, 6f), CornerRadius(3f))
                drawRoundRect(Color(0xFFFF5A5F), Offset(8f, 54f), Size(22f, 10f), CornerRadius(3f))
                drawRoundRect(Color(0xFFFF5A5F), Offset(120f, 54f), Size(22f, 10f), CornerRadius(3f))
                drawOval(Color(0xFF9AA0AB), Offset(66f, 67f), Size(18f, 10f), style = Stroke(2f))
                drawRoundRect(Color(0xFFF5F6F8), Offset(52f, 80f), Size(46f, 13f), CornerRadius(2f))
                drawContext.canvas.nativeCanvas.drawText("12가 3456", 75f, 90f, platePaint)
                drawRoundRect(Color(0xFF0D0E10), Offset(14f, 98f), Size(28f, 14f), CornerRadius(3f))
                drawRoundRect(Color(0xFF0D0E10), Offset(108f, 98f), Size(28f, 14f), CornerRadius(3f))
            }
        }
    }
}

// ───────────────────────── 상태바 ─────────────────────────

private object Icons {
    val lock = sp("M7 10V7a5 5 0 0 1 10 0v3h1a1 1 0 0 1 1 1v9a1 1 0 0 1-1 1H6a1 1 0 0 1-1-1v-9a1 1 0 0 1 1-1zm2 0h6V7a3 3 0 0 0-6 0z")
    val bell = sp("M12 3a6 6 0 0 0-6 6v4l-2 3h16l-2-3V9a6 6 0 0 0-6-6zm-2 15a2 2 0 0 0 4 0z")
    val speaker = sp("M4 9h4l5-4v14l-5-4H4z")
}

@Composable
private fun Icon24(modifier: Modifier = Modifier, draw: DrawScope.() -> Unit) {
    Canvas(modifier.size(20.dp)) { withTransform({ scale(size.width / 24f, size.height / 24f, Offset.Zero) }) { draw() } }
}

@Composable
private fun StatusBar(clock: String, modifier: Modifier) {
    Row(modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(22.dp)) {
        Icon24 { drawPath(Icons.lock, Ink) }
        Icon24 {
            drawCircle(Ink, 6f, Offset(10.5f, 10.5f), style = Stroke(2.6f))
            drawLine(Ink, Offset(15f, 15f), Offset(20f, 20f), 2.6f, StrokeCap.Round)
        }
        Icon24 { drawPath(Icons.bell, Ink); drawCircle(Red, 3.2f, Offset(18f, 5f)) }
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            T("LTE", 13.sp, FontWeight.Bold)
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.padding(bottom = 3.dp)) {
                listOf(4, 6, 8, 10, 12).forEach { Box(Modifier.width(4.dp).height(it.dp).background(Ink)) }
            }
        }
        Icon24 {
            drawPath(Icons.speaker, Ink)
            drawArc(Ink, -50f, 100f, false, Offset(11f, 7f), Size(10f, 10f), style = Stroke(2f, cap = StrokeCap.Round))
        }
        T("✱", 18.sp, color = Blue)
        Box(Modifier.size(26.dp).clip(CircleShape).background(Color(0xFFE3E5EA)))
        T(clock, 20.sp, FontWeight.Bold, spacing = (-0.3).sp)
    }
}

// ───────────────────────── 지도 ─────────────────────────

private fun rand(bx: Int, by: Int, n: Int): Float {
    var h = bx * 374761393 + by * 668265263 + n * 2147483647
    h = (h xor (h ushr 13)) * 1274126177
    return ((h xor (h ushr 16)).toUInt().toDouble() / 4294967296.0).toFloat()
}

private val Poi = listOf(Color(0xFF22C55E), Color(0xFFF59E0B), Color(0xFFEF4444), Color(0xFF8B5CF6), Color(0xFF3B82F6))
private const val PPM = 1.15f // dp per meter

private class Camera { var heading = Float.NaN; var lastT = 0f }

@Composable
private fun MapCard(hud: Hud, simT: () -> Float, toast: String?, modifier: Modifier) {
    val cam = remember { Camera() }
    Box(modifier.card()) {
        Canvas(Modifier.fillMaxSize()) { drawMap(simT(), cam) }

        // 길안내 카드
        Column(Modifier.padding(14.dp).width(270.dp).shadow(8.dp, RoundedCornerShape(14.dp)).clip(RoundedCornerShape(14.dp)).background(Color.White)) {
            Row(Modifier.padding(start = 18.dp, end = 18.dp, top = 14.dp, bottom = 8.dp)) {
                T(hud.turnIc, 34.sp, FontWeight.Bold, modifier = Modifier.width(34.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    T(hud.turnDist, 30.sp, FontWeight.ExtraBold, spacing = (-0.5).sp)
                    T(hud.street, 11.sp, color = Faint, modifier = Modifier.padding(top = 2.dp))
                }
            }
            Row(Modifier.fillMaxWidth().padding(start = 22.dp, end = 22.dp, bottom = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                listOf("↰", "↑", "↑", "↑", "↱", "↱").forEachIndexed { i, a ->
                    T(a, 15.sp, FontWeight.Bold, if (hud.lanes[i]) Ink else Color(0xFFC9CDD6))
                }
            }
            Row(Modifier.fillMaxWidth().background(Soft).padding(horizontal = 18.dp, vertical = 9.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                T(hud.nextIc, 16.sp, FontWeight.Bold); T(hud.nextDist, 16.sp, FontWeight.Bold)
            }
        }
        Column(Modifier.align(Alignment.TopEnd).padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MapButton("2D"); MapButton("≡")
        }
        MapButton("⚙", Modifier.align(Alignment.BottomEnd).padding(end = 14.dp, bottom = 16.dp))

        // 도착 예정
        Column(
            Modifier.align(Alignment.BottomStart).padding(14.dp).width(270.dp).shadow(8.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp)).background(Color.White).padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp)
        ) {
            Row { T(hud.eta, 14.sp, FontWeight.Bold); Spacer(Modifier.weight(1f)); T(hud.remain, 14.sp, FontWeight.SemiBold) }
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(9.dp).border(2.dp, Blue, CircleShape)); Spacer(Modifier.width(5.dp)); T("회사", 11.sp, color = Sub)
            }
            Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("✕" to 1.6f, "↻" to 1f, "⌕" to 1f).forEach { (g, w) ->
                    Box(Modifier.weight(w).height(30.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFFF1F2F5)), contentAlignment = Alignment.Center) {
                        T(g, 13.sp, color = Sub)
                    }
                }
            }
        }
        T(hud.road, 12.sp, FontWeight.SemiBold, modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 16.dp).offset(x = 40.dp)
            .shadow(4.dp, CircleShape).clip(CircleShape).background(Color.White).padding(horizontal = 14.dp, vertical = 6.dp))

        // 토스트
        val a by animateFloatAsState(if (toast != null) 1f else 0f, tween(350), label = "toast")
        var lastToast by remember { mutableStateOf("") }
        if (toast != null) lastToast = toast
        if (a > 0.01f) {
            T(lastToast, 13.sp, FontWeight.SemiBold, Color.White,
                Modifier.align(Alignment.TopCenter).padding(top = 14.dp).graphicsLayer { alpha = a; translationY = (1 - a) * -12f * density }
                    .clip(CircleShape).background(Color(0xE61C1F26)).padding(horizontal = 16.dp, vertical = 8.dp))
        }
    }
}

@Composable
private fun MapButton(label: String, modifier: Modifier = Modifier) {
    Box(modifier.size(34.dp).shadow(4.dp, CircleShape).clip(CircleShape).background(Color.White), contentAlignment = Alignment.Center) {
        T(label, if (label == "⚙") 16.sp else 11.sp, FontWeight.ExtraBold)
    }
}

private fun DrawScope.drawMap(simT: Float, cam: Camera) {
    val s = simT * Scenario.SIM_MPS
    val car = DriveRoute.locate(s)
    val dt = (simT - cam.lastT).coerceIn(0f, 0.1f); cam.lastT = simT
    if (cam.heading.isNaN()) cam.heading = car.heading
    var dh = car.heading - cam.heading
    while (dh > PI) dh -= (2 * PI).toFloat()
    while (dh < -PI) dh += (2 * PI).toFloat()
    cam.heading += dh * min(1f, dt * 5f)

    drawRect(Color(0xFFFBFBFC))
    val ppm = PPM * density            // px per meter
    val u = 1f / PPM                    // 1dp 를 m 로
    val cx = size.width / 2f; val cy = size.height * 0.73f

    withTransform({
        translate(cx, cy)
        rotate((-cam.heading * 180f / PI).toFloat(), Offset.Zero)
        scale(ppm, ppm, Offset.Zero)
        translate(-car.x, -car.y)
    }) {
        val rad = hypot(size.width, size.height) / ppm
        val bx0 = floor((car.x - rad) / 100f).toInt(); val bx1 = floor((car.x + rad) / 100f).toInt()
        val by0 = floor((car.y - rad) / 100f).toInt(); val by1 = floor((car.y + rad) / 100f).toInt()

        for (bx in bx0..bx1) for (by in by0..by1) {
            val x0 = bx * 100f + 8f; val y0 = by * 100f + 8f; val sz = 84f
            val park = rand(bx, by, 1) < 0.12f
            drawRect(if (park) Color(0xFFDFF2E2) else Color(0xFFEEF0F3), Offset(x0, y0), Size(sz, sz))
            drawRect(if (park) Color(0xFFCFE8D4) else Color(0xFFE2E5EA), Offset(x0, y0), Size(sz, sz), style = Stroke(u))
            if (!park) {
                val nb = 1 + (rand(bx, by, 2) * 3).toInt()
                for (b in 0 until nb) {
                    val w = 18 + rand(bx, by, 10 + b) * 26; val h = 14 + rand(bx, by, 20 + b) * 24
                    val px = x0 + 6 + rand(bx, by, 30 + b) * (sz - 12 - w); val py = y0 + 6 + rand(bx, by, 40 + b) * (sz - 12 - h)
                    drawRect(Color(0xFFE1E4E9), Offset(px, py), Size(w, h))
                    drawRect(Color(0xFFD3D7DE), Offset(px, py), Size(w, h), style = Stroke(u))
                }
            }
            if (rand(bx, by, 3) < 0.28f) {
                drawCircle(Poi[(rand(bx, by, 4) * Poi.size).toInt()], 3.6f * u,
                    Offset(x0 + 12 + rand(bx, by, 5) * 60, y0 + 12 + rand(bx, by, 6) * 60))
            }
        }

        // 큰길 중앙 점선
        val dash = PathEffect.dashPathEffect(floatArrayOf(6 * u, 6 * u))
        val lineC = Color(0x8CEAC478)
        var gx = Math.floorDiv(bx0 + 2, 3) * 3
        while (gx <= bx1) { drawLine(lineC, Offset(gx * 100f, by0 * 100f), Offset(gx * 100f, (by1 + 1) * 100f), 1.4f * u, pathEffect = dash); gx += 3 }
        var gy = Math.floorDiv(by0 + 2, 3) * 3
        while (gy <= by1) { drawLine(lineC, Offset(bx0 * 100f, gy * 100f), Offset((bx1 + 1) * 100f, gy * 100f), 1.4f * u, pathEffect = dash); gy += 3 }

        // 안내 경로 (차 앞쪽만)
        val route = Path().apply {
            moveTo(car.x, car.y)
            var d = 4f
            while (d < 1300f) { val p = DriveRoute.locate(s + d); lineTo(p.x, p.y); d += 4f }
        }
        drawPath(route, Color(0x8C15803D), style = Stroke(15f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
        drawPath(route, Ev, style = Stroke(10f * u, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }

    // 내 차 마커
    val d = density
    drawCircle(Color(0x332563EB), 20f * d, Offset(cx, cy))
    drawCircle(Color.White, 14f * d, Offset(cx, cy))
    drawCircle(Color(0xFF2563EB), 11f * d, Offset(cx, cy))
    drawPath(Path().apply {
        moveTo(cx, cy - 6.5f * d); lineTo(cx + 5f * d, cy + 5f * d); lineTo(cx, cy + 2.5f * d); lineTo(cx - 5f * d, cy + 5f * d); close()
    }, Color.White)
}

// ───────────────────────── 르르르 위젯 (We'r ev) ─────────────────────────

@Composable
private fun WidgetCard(hud: Hud, widgetMode: String, motion: String, modifier: Modifier) {
    val gen = hud.gen
    val barColor by animateColorAsState(if (gen) Amber else Ev, tween(600), label = "bar")
    val barW by animateFloatAsState(hud.battery / 100f, tween(800), label = "barW")
    Column(
        modifier.card().border(1.5.dp, Color(0xFFEBE7FB), RoundedCornerShape(22.dp))
            .padding(start = 18.dp, end = 18.dp, top = 18.dp, bottom = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("We'r ", 17.sp, FontWeight.ExtraBold, spacing = (-0.3).sp)
            T("ev", 17.sp, FontWeight.ExtraBold, Ev)
            Spacer(Modifier.weight(1f))
            T(if (gen) "발전 중" else "EV 주행", 11.sp, FontWeight.Bold, if (gen) Color(0xFFD97706) else Green,
                Modifier.clip(CircleShape).background(if (gen) Color(0xFFFEF3C7) else Color(0xFFDCFCE7)).padding(horizontal = 10.dp, vertical = 4.dp))
        }
        RrrWidgetAnimation(widgetMode, motion, Modifier.padding(top = 26.dp, bottom = 8.dp).size(300.dp, 262.dp).align(Alignment.CenterHorizontally))
        Row(
            Modifier.align(Alignment.CenterHorizontally).padding(top = 4.dp).clip(CircleShape).background(Color.White)
                .border(1.dp, if (gen) Color(0x66F5A623) else Color(0x5900C49A), CircleShape)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (gen) { T("⚡", 13.sp, color = Amber); T("발전 중 · 연료 → 전기", 13.sp, FontWeight.SemiBold) }
            else { T("🔋", 13.sp); T("EV 주행 · 배터리 전기 사용 중", 13.sp, FontWeight.SemiBold) }
        }
        Spacer(Modifier.weight(1f))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFECEEF2)))
        Spacer(Modifier.height(14.dp))
        Row { T("배터리", 12.sp, color = Sub); Spacer(Modifier.weight(1f)); T("${hud.battery}%", 12.sp, FontWeight.Bold) }
        Box(Modifier.padding(top = 8.dp, bottom = 18.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(Color(0xFFECEEF2))) {
            Box(Modifier.fillMaxWidth(barW).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(barColor))
        }
        Row { T("오늘 전기 주행 비율", 12.sp, color = Sub); Spacer(Modifier.weight(1f)); T("${hud.ratio}%", 12.sp, FontWeight.Bold) }
    }
}

// ───────────────────────── 하단 독 ─────────────────────────

@Composable
private fun Dock(modifier: Modifier) {
    Row(modifier.card().padding(horizontal = 30.dp), verticalAlignment = Alignment.CenterVertically) {
        Canvas(Modifier.size(34.dp, 22.dp)) {
            val k = size.width / 34f
            withTransform({ scale(k, k, Offset.Zero) }) {
                drawPath(sp("M4 14 L7 6 Q8 3 12 3 L22 3 Q26 3 27 6 L30 14 L30 19 L4 19 Z"), Red)
                drawRoundRect(Color.White, Offset(9f, 6f), Size(16f, 5f), CornerRadius(1.5f), alpha = 0.85f)
                drawCircle(Ink, 2.6f, Offset(9f, 19f)); drawCircle(Ink, 2.6f, Offset(25f, 19f))
            }
        }
        Spacer(Modifier.width(14.dp))
        Temp("A/C")
        Spacer(Modifier.width(14.dp))
        T("⦚", 18.sp, color = Sub); Spacer(Modifier.width(14.dp)); T("▦", 18.sp, color = Sub)
        Spacer(Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            AppIcon("✥", Color(0xFF5B6170)); AppIcon("♪", Color(0xFFA855F7)); AppIcon("✆", Color(0xFF16A34A)); AppIcon("➤", Color(0xFF3B82F6))
            T("⁘", 19.sp, color = Sub)
            Box(Modifier.padding(horizontal = 4.dp).width(1.dp).height(26.dp).background(Color(0xFFDFE2E8)))
            AppIcon("ev", Ev, 13.sp); AppIcon("◉", Color(0xFFEC4899))
        }
        Spacer(Modifier.weight(1f))
        Column(horizontalAlignment = Alignment.CenterHorizontally) { T("❄3", 13.sp, FontWeight.Bold); T("AUTO", 9.sp, FontWeight.Bold) }
        Spacer(Modifier.width(26.dp))
        Temp("SYNC")
    }
}

@Composable
private fun Temp(label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            T("‹", 20.sp, color = Blue); Spacer(Modifier.width(8.dp))
            T("23.5°", 28.sp, FontWeight.Bold, spacing = (-0.5).sp)
            Spacer(Modifier.width(8.dp)); T("›", 20.sp, color = Red)
        }
        T(label, 9.sp, FontWeight.Bold, Green)
    }
}

@Composable
private fun AppIcon(glyph: String, bg: Color, size: TextUnit = 19.sp) {
    Box(Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(bg), contentAlignment = Alignment.Center) {
        T(glyph, size, FontWeight.ExtraBold, Color.White)
    }
}

@Preview(widthDp = 1600, heightDp = 880)
@Composable
private fun PleosConnectPreview() {
    PleosConnectScreen(WidgetSettings())
}
