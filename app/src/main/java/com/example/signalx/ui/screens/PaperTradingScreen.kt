package com.example.signalx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.signalx.data.api.PriceFormatter
import com.example.signalx.data.db.PaperTradeEntity
import com.example.signalx.data.model.MarketType
import com.example.signalx.ui.viewmodel.UiState
import com.example.ui.theme.AccentCyan
import com.example.ui.theme.AccentYellow
import com.example.ui.theme.BearishRed
import com.example.ui.theme.BullishGreen
import com.example.ui.theme.DarkBorder
import com.example.ui.theme.DarkCard
import com.example.ui.theme.DarkCardElevated
import com.example.ui.theme.TextMuted
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import java.util.Locale

@Composable
fun PaperTradingScreen(
    state: UiState,
    onExecuteTrade: (String, Double, Double, Int, Double, Double) -> Unit,
    onCloseTrade: (PaperTradeEntity, Double) -> Unit,
    onResetBalance: () -> Unit,
    modifier: Modifier = Modifier
) {
    var activeTab by remember { mutableStateOf(0) } // 0: Open Positions, 1: Order Form, 2: Trade History

    val livePrice = state.latestTicker?.lastPrice ?: state.candles.lastOrNull()?.close ?: 0.0

    // Calculate total unrealized PnL
    val totalUnrealizedPnl = state.openTrades.sumOf { trade ->
        val isBuy = trade.side == "BUY"
        val currentP = if (trade.symbol == state.activeSymbol && livePrice > 0) livePrice else trade.entryPrice
        val diff = if (isBuy) (currentP - trade.entryPrice) else (trade.entryPrice - currentP)
        diff * trade.quantity * trade.leverage
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E11))
            .padding(12.dp)
    ) {
        // Balance Header Card
        Card(
            colors = CardDefaults.cardColors(containerColor = DarkCard),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("SIMULATED VIRTUAL BALANCE", fontSize = 10.sp, color = TextMuted)
                        Text(
                            text = "${PriceFormatter.formatClean(state.paperBalance, 2)} USDT",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    IconButton(
                        onClick = onResetBalance,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = AccentYellow)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Unrealized PnL: ${if (totalUnrealizedPnl >= 0) "+" else ""}${PriceFormatter.formatClean(totalUnrealizedPnl, 2)} USDT",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (totalUnrealizedPnl >= 0) BullishGreen else BearishRed
                    )
                    Text(
                        text = "Active: ${state.openTrades.size} | History: ${state.tradeHistory.size}",
                        fontSize = 11.sp,
                        color = TextSecondary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Navigation Tabs
        TabRow(
            selectedTabIndex = activeTab,
            containerColor = Color(0xFF161A1E),
            contentColor = AccentYellow,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[activeTab]),
                    color = AccentYellow
                )
            }
        ) {
            Tab(
                selected = activeTab == 0,
                onClick = { activeTab = 0 },
                text = { Text("Positions (${state.openTrades.size})", fontSize = 12.sp) }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("New Order", fontSize = 12.sp) }
            )
            Tab(
                selected = activeTab == 2,
                onClick = { activeTab = 2 },
                text = { Text("History", fontSize = 12.sp) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        when (activeTab) {
            0 -> OpenPositionsList(
                trades = state.openTrades,
                currentSymbol = state.activeSymbol,
                currentPrice = livePrice,
                onClose = { trade -> onCloseTrade(trade, livePrice) },
                onNewOrderClick = { activeTab = 1 }
            )
            1 -> PaperOrderForm(
                state = state,
                currentPrice = livePrice,
                onPlaceTrade = { side, entry, qty, lev, sl, tp ->
                    onExecuteTrade(side, entry, qty, lev, sl, tp)
                    activeTab = 0
                }
            )
            2 -> TradeHistoryList(trades = state.tradeHistory)
        }
    }
}

@Composable
fun OpenPositionsList(
    trades: List<PaperTradeEntity>,
    currentSymbol: String,
    currentPrice: Double,
    onClose: (PaperTradeEntity) -> Unit,
    onNewOrderClick: () -> Unit
) {
    if (trades.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("No open positions", color = TextSecondary, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onNewOrderClick,
                    colors = ButtonDefaults.buttonColors(containerColor = AccentYellow)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color(0xFF0B0E11))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Place Paper Order", color = Color(0xFF0B0E11), fontWeight = FontWeight.Bold)
                }
            }
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(trades, key = { it.id }) { trade ->
                val isBuy = trade.side == "BUY"
                val currP = if (trade.symbol == currentSymbol && currentPrice > 0) currentPrice else trade.entryPrice
                val diff = if (isBuy) (currP - trade.entryPrice) else (trade.entryPrice - currP)
                val rawPnl = diff * trade.quantity * trade.leverage
                val pnlPct = if (trade.entryPrice > 0) ((diff / trade.entryPrice) * trade.leverage * 100.0) else 0.0

                // Simulated liquidation price (approx for isolated margin)
                val liqPrice = if (isBuy) {
                    trade.entryPrice * (1.0 - (1.0 / trade.leverage.toDouble()))
                } else {
                    trade.entryPrice * (1.0 + (1.0 / trade.leverage.toDouble()))
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isBuy) Color(0x220ECB81) else Color(0x22F6465D))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "${trade.side} ${trade.leverage}X",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isBuy) BullishGreen else BearishRed
                                )
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            Text(
                                text = trade.symbol,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )

                            Spacer(modifier = Modifier.weight(1f))

                            Button(
                                onClick = { onClose(trade) },
                                colors = ButtonDefaults.buttonColors(containerColor = DarkCardElevated),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Close", fontSize = 10.sp, color = TextPrimary)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        val mType = try { MarketType.valueOf(trade.marketType) } catch (e: Exception) { MarketType.SPOT }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Entry: ${PriceFormatter.formatPrice(trade.symbol, mType, trade.entryPrice)}", fontSize = 11.sp, color = TextSecondary)
                                Text("Mark: ${PriceFormatter.formatPrice(trade.symbol, mType, currP)}", fontSize = 11.sp, color = TextPrimary)
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("SL: ${if (trade.stopLoss > 0) PriceFormatter.formatPrice(trade.symbol, mType, trade.stopLoss) else "--"}", fontSize = 10.sp, color = BearishRed)
                                Text("TP: ${if (trade.takeProfit > 0) PriceFormatter.formatPrice(trade.symbol, mType, trade.takeProfit) else "--"}", fontSize = 10.sp, color = BullishGreen)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${if (rawPnl >= 0) "+" else ""}${PriceFormatter.formatClean(rawPnl, 2)} USDT",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (rawPnl >= 0) BullishGreen else BearishRed
                                )
                                Text(
                                    text = "${if (pnlPct >= 0) "+" else ""}${String.format(Locale.US, "%.2f", pnlPct)}%",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (pnlPct >= 0) BullishGreen else BearishRed
                                )
                            }
                        }

                        if (trade.leverage > 1) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Est. Liquidation Price: ${PriceFormatter.formatPrice(trade.symbol, mType, liqPrice)} (simulated)",
                                fontSize = 9.sp,
                                color = TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PaperOrderForm(
    state: UiState,
    currentPrice: Double,
    onPlaceTrade: (String, Double, Double, Int, Double, Double) -> Unit
) {
    var side by remember { mutableStateOf("BUY") }
    var leverage by remember { mutableIntStateOf(1) }
    var amountUsdt by remember { mutableStateOf("100") }
    var slInput by remember(state.decision) { mutableStateOf(state.decision?.stopLoss?.let { PriceFormatter.formatPrice(state.activeSymbol, state.activeMarketType, it) } ?: "") }
    var tpInput by remember(state.decision) { mutableStateOf(state.decision?.takeProfit1?.let { PriceFormatter.formatPrice(state.activeSymbol, state.activeMarketType, it) } ?: "") }

    val isFutures = state.activeMarketType == MarketType.FUTURES
    val priceToUse = if (currentPrice > 0) currentPrice else 1.0

    Card(
        colors = CardDefaults.cardColors(containerColor = DarkCard),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = "Simulated Order for ${state.activeSymbol}",
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Buy / Sell Selector
            Row(modifier = Modifier.fillMaxWidth()) {
                Button(
                    onClick = { side = "BUY" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (side == "BUY") BullishGreen else DarkCardElevated
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isFutures) "Open Long (BUY)" else "Buy (SPOT)", fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.width(8.dp))

                Button(
                    onClick = { side = "SELL" },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (side == "SELL") BearishRed else DarkCardElevated
                    ),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(if (isFutures) "Open Short (SELL)" else "Sell (SPOT)", fontWeight = FontWeight.Bold)
                }
            }

            // Leverage (Futures only)
            if (isFutures) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Leverage", fontSize = 12.sp, color = TextSecondary)
                    Text("${leverage}X", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = AccentYellow)
                }
                Slider(
                    value = leverage.toFloat(),
                    onValueChange = { leverage = it.toInt() },
                    valueRange = 1f..50f,
                    steps = 49,
                    colors = SliderDefaults.colors(
                        thumbColor = AccentYellow,
                        activeTrackColor = AccentYellow
                    )
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Amount Input
            OutlinedTextField(
                value = amountUsdt,
                onValueChange = { amountUsdt = it },
                label = { Text("Position Margin (USDT)") },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentYellow
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Stop Loss & Take Profit
            Row(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = slInput,
                    onValueChange = { slInput = it },
                    label = { Text("Stop Loss") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = BearishRed
                    ),
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = tpInput,
                    onValueChange = { tpInput = it },
                    label = { Text("Take Profit") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        focusedBorderColor = BullishGreen
                    ),
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = {
                    val margin = amountUsdt.toDoubleOrNull() ?: 100.0
                    val sl = slInput.toDoubleOrNull() ?: 0.0
                    val tp = tpInput.toDoubleOrNull() ?: 0.0
                    val qty = margin / priceToUse
                    onPlaceTrade(side, priceToUse, qty, leverage, sl, tp)
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (side == "BUY") BullishGreen else BearishRed
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(44.dp)
            ) {
                Text("Confirm Simulated $side Order", fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}

@Composable
fun TradeHistoryList(trades: List<PaperTradeEntity>) {
    val closedTrades = trades.filter { it.status == "CLOSED" }

    if (closedTrades.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("No closed trades yet", color = TextSecondary, fontSize = 14.sp)
        }
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(closedTrades) { trade ->
                val isWin = trade.pnl > 0
                Card(
                    colors = CardDefaults.cardColors(containerColor = DarkCard),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    val mType = try { MarketType.valueOf(trade.marketType) } catch (e: Exception) { MarketType.SPOT }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${trade.symbol} (${trade.side} ${trade.leverage}X)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Entry: ${PriceFormatter.formatPrice(trade.symbol, mType, trade.entryPrice)} → Exit: ${PriceFormatter.formatPrice(trade.symbol, mType, trade.exitPrice)}", fontSize = 10.sp, color = TextSecondary)
                            Text("Fee Paid: ${PriceFormatter.formatClean(trade.fee, 4)} USDT", fontSize = 9.sp, color = TextMuted)
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "${if (isWin) "+" else ""}${PriceFormatter.formatClean(trade.pnl, 2)} USDT",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isWin) BullishGreen else BearishRed
                            )
                            Text(
                                text = if (isWin) "WIN" else "LOSS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isWin) BullishGreen else BearishRed
                            )
                        }
                    }
                }
            }
        }
    }
}
