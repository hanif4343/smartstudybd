package com.hanif.smartstudy.ui.typing

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.hanif.smartstudy.ui.theme.NotoSansBengali

/**
 * Phase ৩: আঙুল-পজিশন রেফারেন্স কীবোর্ড — Neonlipi-এর "প্রথমে আঙুলগুলো বসিয়ে নাও"
 * স্ক্রিনের সমতুল্য। এটা একটা physical QWERTY কী-পজিশনের ডায়াগ্রাম (ভাষা-নিরপেক্ষ —
 * আঙুলের দায়িত্ব শারীরিক কী-এর অবস্থানের উপর নির্ভর করে, বাংলা/ইংরেজি কোন অক্ষর
 * ছাপা আছে তার উপর না), তাই বিজয়/ফোনেটিক/জাতীয় — যেকোনো লেআউটের জন্যই এই একই
 * ডায়াগ্রাম কাজ করে। একটা static reference কার্ড — লাইভ টাইপিং ট্র্যাকিং না,
 * শুধু শেখার শুরুতে "কোন আঙুল কোন কী চাপবে" বোঝানোর জন্য।
 */

// ── ৮টা আঙুল (বাম/ডান পিঙ্কি→ইনডেক্স) + থাম্ব (স্পেস) — প্রতিটার একটা রঙ ──
private enum class Finger(val label: String, val color: Color) {
    L_PINKY("বাম কনিষ্ঠা",  Color(0xFFEF4444)),
    L_RING("বাম অনামিকা",   Color(0xFFF97316)),
    L_MIDDLE("বাম মধ্যমা",  Color(0xFFEAB308)),
    L_INDEX("বাম তর্জনী",   Color(0xFF22C55E)),
    R_INDEX("ডান তর্জনী",   Color(0xFF06B6D4)),
    R_MIDDLE("ডান মধ্যমা",  Color(0xFF3B82F6)),
    R_RING("ডান অনামিকা",   Color(0xFF8B5CF6)),
    R_PINKY("ডান কনিষ্ঠা",  Color(0xFFEC4899)),
    THUMB("থাম্ব",         Color(0xFF9CA3AF))
}

private data class Key(val label: String, val finger: Finger, val weight: Float = 1f)

private val ROW_NUM = listOf(
    Key("`", Finger.L_PINKY), Key("1", Finger.L_PINKY), Key("2", Finger.L_RING),
    Key("3", Finger.L_MIDDLE), Key("4", Finger.L_INDEX), Key("5", Finger.L_INDEX),
    Key("6", Finger.R_INDEX), Key("7", Finger.R_INDEX), Key("8", Finger.R_MIDDLE),
    Key("9", Finger.R_RING), Key("0", Finger.R_PINKY), Key("-", Finger.R_PINKY),
    Key("=", Finger.R_PINKY), Key("⌫", Finger.R_PINKY, 1.6f)
)
private val ROW_QWERTY = listOf(
    Key("Tab", Finger.L_PINKY, 1.4f), Key("Q", Finger.L_PINKY), Key("W", Finger.L_RING),
    Key("E", Finger.L_MIDDLE), Key("R", Finger.L_INDEX), Key("T", Finger.L_INDEX),
    Key("Y", Finger.R_INDEX), Key("U", Finger.R_INDEX), Key("I", Finger.R_MIDDLE),
    Key("O", Finger.R_RING), Key("P", Finger.R_PINKY), Key("[", Finger.R_PINKY),
    Key("]", Finger.R_PINKY)
)
private val ROW_ASDF = listOf(
    Key("Caps", Finger.L_PINKY, 1.6f), Key("A", Finger.L_PINKY), Key("S", Finger.L_RING),
    Key("D", Finger.L_MIDDLE), Key("F", Finger.L_INDEX), Key("G", Finger.L_INDEX),
    Key("H", Finger.R_INDEX), Key("J", Finger.R_INDEX), Key("K", Finger.R_MIDDLE),
    Key("L", Finger.R_RING), Key(";", Finger.R_PINKY), Key("'", Finger.R_PINKY),
    Key("↵", Finger.R_PINKY, 1.6f)
)
private val ROW_ZXCV = listOf(
    Key("Shift", Finger.L_PINKY, 2f), Key("Z", Finger.L_PINKY), Key("X", Finger.L_RING),
    Key("C", Finger.L_MIDDLE), Key("V", Finger.L_INDEX), Key("B", Finger.L_INDEX),
    Key("N", Finger.R_INDEX), Key("M", Finger.R_INDEX), Key(",", Finger.R_MIDDLE),
    Key(".", Finger.R_RING), Key("/", Finger.R_PINKY), Key("Shift", Finger.R_PINKY, 2f)
)

/** Smart Typing: Live next-key হাইলাইট কীবোর্ড — TypingPracticeScreen থেকে টাইপিং
 *  চলাকালীন কল হয়, ঠিক এখন যেই ক্যারেক্টার টাইপ করার কথা (nextChar) সেই কী-টা
 *  হলুদ বর্ডার দিয়ে হাইলাইট হয় দেখানোর জন্য। FingerKeyboardDiagram()-এর সাথে একই
 *  ROW_*/Key/Finger স্ট্রাকচার শেয়ার করে (physical QWERTY পজিশন — ভাষা-নিরপেক্ষ,
 *  তাই বাংলা অক্ষরের জন্য মিল না পেলে কোনো কী হাইলাইট হবে না, শুধু স্বাভাবিক রঙে দেখাবে)। */
@Composable
fun LiveKeyHighlightKeyboard(nextChar: Char?) {
    val highlightChar = nextChar?.takeIf { it != ' ' }?.uppercaseChar()
    val highlightSpace = nextChar == ' '
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        HighlightKeyboardRow(ROW_NUM, highlightChar)
        HighlightKeyboardRow(ROW_QWERTY, highlightChar)
        HighlightKeyboardRow(ROW_ASDF, highlightChar)
        HighlightKeyboardRow(ROW_ZXCV, highlightChar)
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Surface(
                modifier = Modifier.weight(1f).height(30.dp),
                shape = RoundedCornerShape(5.dp),
                color = if (highlightSpace) Color(0xFFFACC15) else Finger.THUMB.color.copy(alpha = 0.85f),
                border = if (highlightSpace) BorderStroke(2.dp, Color(0xFFB45309)) else null
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Space", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun HighlightKeyboardRow(keys: List<Key>, highlightChar: Char?) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        keys.forEach { k ->
            val isHighlighted = highlightChar != null && k.label.length == 1 && k.label[0] == highlightChar
            Surface(
                modifier = Modifier.weight(k.weight).height(34.dp),
                shape = RoundedCornerShape(5.dp),
                color = if (isHighlighted) Color(0xFFFACC15) else k.finger.color.copy(alpha = 0.85f),
                border = if (isHighlighted) BorderStroke(2.dp, Color(0xFFB45309)) else null
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(k.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun KeyboardRow(keys: List<Key>) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        keys.forEach { k ->
            Surface(
                modifier = Modifier.weight(k.weight).height(34.dp),
                shape = RoundedCornerShape(5.dp),
                color = k.finger.color.copy(alpha = 0.85f)
            ) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(k.label, fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

@Composable
fun FingerKeyboardDiagram() {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        KeyboardRow(ROW_NUM)
        KeyboardRow(ROW_QWERTY)
        KeyboardRow(ROW_ASDF)
        KeyboardRow(ROW_ZXCV)
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Surface(Modifier.weight(1f).height(30.dp), shape = RoundedCornerShape(5.dp),
                color = Finger.THUMB.color.copy(alpha = 0.85f)) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Space", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color.White)
                }
            }
        }
    }
}

/** ছোট legend — কোন রঙ কোন আঙুল বোঝায় (দুই কলামে সাজানো, স্ক্রিনে জায়গা বাঁচাতে) */
@Composable
private fun FingerLegend() {
    val leftFingers = Finger.values().filter { it.name.startsWith("L_") }
    val rightFingers = Finger.values().filter { it.name.startsWith("R_") }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            leftFingers.forEach { f -> LegendRow(f) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            rightFingers.forEach { f -> LegendRow(f) }
        }
    }
}

@Composable
private fun LegendRow(f: Finger) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier.size(12.dp)
                .background(f.color, RoundedCornerShape(3.dp))
        )
        Text(f.label, fontSize = 10.sp, fontFamily = NotoSansBengali)
    }
}

/** পুরো ফিচারের এন্ট্রি পয়েন্ট — TypingPracticeScreen থেকে "✋ আঙুলের পজিশন" বাটনে
 *  ট্যাপ করলে এই ডায়ালগ খোলে। */
@Composable
fun FingerPositionDialog(onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 8.dp) {
            Column(Modifier.padding(18.dp).fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("✋ প্রথমে আঙুলগুলো বসিয়ে নাও", fontSize = 14.sp, fontFamily = NotoSansBengali,
                        fontWeight = FontWeight.Bold)
                    TextButton(onClick = onDismiss) { Text("✕") }
                }
                Text(
                    "প্রতিটা আঙুল নির্দিষ্ট কী-এর দায়িত্বে থাকে — শুরুতেই এই অভ্যাস গড়ে তুললে টাইপিং অনেক দ্রুত ও সহজ হয়ে যায়।",
                    fontSize = 11.sp, fontFamily = NotoSansBengali,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp, bottom = 14.dp)
                )
                FingerKeyboardDiagram()
                Spacer(Modifier.height(14.dp))
                FingerLegend()
                Spacer(Modifier.height(8.dp))
                Text(
                    "নোট: এটা physical কী-বোর্ডের অবস্থান দেখায় — বিজয়/ফোনেটিক/জাতীয়, যেকোনো লেআউটেই একই আঙুল একই জায়গায় থাকে।",
                    fontSize = 9.sp, fontFamily = NotoSansBengali,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
