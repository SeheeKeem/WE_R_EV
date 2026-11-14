package com.example.rrr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.rrr.pleos.Source
import com.example.rrr.ui.theme.Hd
import kotlin.math.abs

/**
 * "차 기본 모드 대비 이번 트립 절약" 카드 (T6).
 * 차의 기본 에너지 관리는 주행 중 배터리/엔진을 어떻게 쓸지(효율)를 정하지만,
 * 시간대별 전기요금 · 주유소 가격 · 주차 시간은 모른다. We'r ev 는 그걸로 언제·어디서 채울지(돈)를 정한다.
 */
@Composable
fun TripCostCard(plan: Plan, cfg: RegionConfig, modifier: Modifier = Modifier) {
    val r = cfg.region
    val cost = plan.cost
    // 표시 단위(1원 / 1센트)보다 작은 차이는 '비슷함'
    val unit = if (r == Region.US) 0.01 else 1.0
    Column(
        modifier.fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Hd.SurfaceAlt)
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("이번 트립 비용", color = Hd.Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Text(cfg.tariffNote.substringBefore(" ·"), color = Hd.Faint, fontSize = 10.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Column {
                Text("기본 모드", color = Hd.Faint, fontSize = 11.sp)
                Text(
                    money(cost.baseline.total, r), color = Hd.Faint, fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold, textDecoration = TextDecoration.LineThrough,
                )
            }
            Text("→", color = Hd.Faint, fontSize = 16.sp, modifier = Modifier.padding(horizontal = 10.dp).padding(bottom = 2.dp))
            Column {
                Text("We'r ev", color = Hd.Navy, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Text(money(cost.planned.total, r), color = Hd.Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.weight(1f))
            val badge = when {
                plan.rule == Rule.TimeFirst -> "약 ${FAST_DETOUR_MIN}분 단축"
                cost.savings >= unit -> "${money(cost.savings, r)} 절약"
                else -> null
            }
            badge?.let {
                Text(
                    it, color = Hd.Navy, fontSize = 13.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(Hd.NavySoft)
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            when {
                plan.rule == Rule.TimeFirst -> "충전 정차 없음 · 오늘은 돈보다 시간을 아껴요"
                cost.savings >= unit -> "차가 모르는 것: 시간대별 전기요금 · 주유소 가격 · 주차 시간"
                abs(cost.savings) < unit -> "이번 트립은 기본 모드와 비슷해요"
                else -> "이번엔 조금 더 들지만 ${plan.benefit}"
            },
            color = Hd.Sub, fontSize = 11.sp, lineHeight = 15.sp,
        )
        Spacer(Modifier.height(4.dp))
        Text("기본 모드 = 배터리 먼저 쓰고 ${cost.baseline.note}", color = Hd.Faint, fontSize = 10.sp, lineHeight = 14.sp)
        Text("We'r ev = ${cost.planned.note}", color = Hd.Faint, fontSize = 10.sp, lineHeight = 14.sp)
    }
}

/** 데이터 출처 배지 — Pleos SDK 실제 값인지, 데모 값인지 */
@Composable
fun SourceBadge(source: Source, modifier: Modifier = Modifier) {
    val live = source == Source.PLEOS
    Text(
        if (live) "PLEOS 실시간" else "DEMO",
        color = if (live) Hd.Navy else Hd.Faint,
        fontSize = 10.sp, fontWeight = FontWeight.SemiBold,
        modifier = modifier
            .border(1.dp, if (live) Hd.Navy else Hd.Line, RoundedCornerShape(4.dp))
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/** 시연 도구에 보여줄 현재 상태 */
data class DemoToolsState(
    val tripActive: Boolean,
    val chargePending: Boolean,
    val vehicle: Source,
    val navi: Source,
    val voice: Source,
)

/**
 * 시연 도구 (개발자 메뉴) — 에뮬레이터에서 주행 없이 트립 흐름을 재현한다.
 * 실차/내비 연결 시에는 같은 일이 NaviHelper 이벤트(길안내 시작 · 도착)로 자동으로 일어난다.
 */
@Composable
fun DemoTools(
    state: DemoToolsState,
    onStartTrip: () -> Unit,
    onMoment: () -> Unit,
    onEndTrip: () -> Unit,
    onSkipToOffPeak: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxWidth()
            .border(1.dp, Hd.Line, RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Text("시연 도구", color = Hd.Sub, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "차량 ${state.vehicle} · 내비 ${state.navi} · 음성 ${state.voice}" + if (state.tripActive) " · 트립 진행 중" else "",
            color = Hd.Faint, fontSize = 10.sp,
        )
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            ToolButton(if (state.tripActive) "트립 진행 중" else "트립 시작", enabled = !state.tripActive, onClick = onStartTrip, modifier = Modifier.weight(1f))
            ToolButton("결정적 순간 음성", enabled = true, onClick = onMoment, modifier = Modifier.weight(1.2f))
            ToolButton("트립 종료", enabled = state.tripActive, onClick = onEndTrip, modifier = Modifier.weight(1f))
        }
        if (state.chargePending) {
            Spacer(Modifier.height(6.dp))
            ToolButton("싼 시간대로 건너뛰기 → 지금 충전 시작", enabled = true, onClick = onSkipToOffPeak, modifier = Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun ToolButton(label: String, enabled: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Text(
        label,
        color = if (enabled) Hd.Navy else Hd.Faint,
        fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, maxLines = 1,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (enabled) Hd.NavySoft else Hd.SurfaceAlt)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp),
    )
}

/** 시연용 배터리 −/+ (source 가 DEMO 일 때만 보여준다 — 실차에서는 Virtual Console 로 바꾼다) */
@Composable
fun DemoStepper(value: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        StepperButton("−") { onChange(value - 5) }
        Text(
            "$value%", color = Hd.Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            modifier = Modifier.width(50.dp),
        )
        StepperButton("+") { onChange(value + 5) }
    }
}

@Composable
private fun StepperButton(label: String, onClick: () -> Unit) {
    Text(
        label, color = Hd.Ink, fontSize = 16.sp, textAlign = TextAlign.Center,
        modifier = Modifier.width(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .border(1.dp, Hd.Line, RoundedCornerShape(15.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 3.dp),
    )
}
