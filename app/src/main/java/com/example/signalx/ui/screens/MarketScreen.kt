package com.example.signalx.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.signalx.data.model.MarketType
import com.example.signalx.data.model.Ticker
import com.example.signalx.ui.viewmodel.UiState
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

enum class ScannerFilter {
    ALL,
    BULLISH,
    BEARISH,
    HIGH_VOLUME,
    FAVORITES
}

@Composable
fun MarketScreen(
    state: UiState,
    onSelectSymbol: (String, MarketType) -> Unit,
    onToggleFavorite: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(ScannerFilter.ALL) }
    var searchQuery by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf(if (state.activeMarketType == MarketType.SPOT) 0 else 1) }

    val rawTickers = if (activeTab == 0) state.spotTickers else state.futuresTickers
    val currentMarketType = if (activeTab == 0) MarketType.SPOT else MarketType.FUTURES

    // Filter tickers
    val filteredTickers = remember(rawTickers, selectedFilter, searchQuery, state.favorites) {
        var list = rawTickers

        if (searchQuery.isNotBlank()) {
            list = list.filter { it.symbol.contains(searchQuery.trim().uppercase(Locale.US)) }
        }

        when (selectedFilter) {
            ScannerFilter.ALL -> list.sortedByDescending { it.quoteVolume24h }
            ScannerFilter.BULLISH -> list.filter { it.change24h > 0 }.sortedByDescending { it.change24h }
            ScannerFilter.BEARISH -> list.filter { it.change24h < 0 }.sortedBy { it.change24h }
            ScannerFilter.HIGH_VOLUME -> list.sortedByDescending { it.quoteVolume24h }
            ScannerFilter.FAVORITES -> list.filter { state.favorites.contains(it.symbol) }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF0B0E11))
            .padding(horizontal = 12.dp)
    ) {
        // Top Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Market Scanner",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(DarkCard)
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${filteredTickers.size} Pairs",
                    color = AccentYellow,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Spot vs Futures Tabs
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
                text = { Text("Spot Markets", fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal) }
            )
            Tab(
                selected = activeTab == 1,
                onClick = { activeTab = 1 },
                text = { Text("USDT Futures", fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Normal) }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search coin (e.g. BTC, ETH, SOL)", color = TextMuted, fontSize = 13.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = TextSecondary) },
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = DarkCard,
                unfocusedContainerColor = DarkCard,
                focusedBorderColor = AccentYellow,
                unfocusedBorderColor = DarkBorder,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            ),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter Chips Row
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(ScannerFilter.entries) { filter ->
                val isSelected = selectedFilter == filter
                FilterChip(
                    selected = isSelected,
                    onClick = { selectedFilter = filter },
                    label = {
                        Text(
                            text = when (filter) {
                                ScannerFilter.ALL -> "All Pairs"
                                ScannerFilter.BULLISH -> "Top Gainers"
                                ScannerFilter.BEARISH -> "Top Losers"
                                ScannerFilter.HIGH_VOLUME -> "24h Volume"
                                ScannerFilter.FAVORITES -> "Favorites"
                            },
                            fontSize = 12.sp,
                            color = if (isSelected) Color(0xFF0B0E11) else TextSecondary
                        )
                    },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = AccentYellow,
                        containerColor = DarkCard
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = isSelected,
                        borderColor = DarkBorder,
                        selectedBorderColor = AccentYellow
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Coin / Vol", fontSize = 11.sp, color = TextMuted, modifier = Modifier.weight(1.3f))
            Text("Last Price", fontSize = 11.sp, color = TextMuted, modifier = Modifier.weight(1.2f))
            Text("24h Change", fontSize = 11.sp, color = TextMuted, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(36.dp))
        }

        // Ticker List
        if (filteredTickers.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("No markets match your criteria", color = TextSecondary, fontSize = 14.sp)
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredTickers, key = { it.symbol }) { ticker ->
                    TickerItemCard(
                        ticker = ticker,
                        isFavorite = state.favorites.contains(ticker.symbol),
                        onSelect = { onSelectSymbol(ticker.symbol, currentMarketType) },
                        onToggleFavorite = { onToggleFavorite(ticker.symbol) }
                    )
                }
            }
        }
    }
}

@Composable
fun TickerItemCard(
    ticker: Ticker,
    isFavorite: Boolean,
    onSelect: () -> Unit,
    onToggleFavorite: () -> Unit
) {
    val isBull = ticker.isBullish
    val changeColor = if (isBull) BullishGreen else BearishRed
    val changeBg = if (isBull) Color(0x220ECB81) else Color(0x22F6465D)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(DarkCard)
            .clickable { onSelect() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Coin Name & 24h Vol
        Column(modifier = Modifier.weight(1.3f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = ticker.symbol.replace("USDT", ""),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Text(
                    text = "/USDT",
                    fontSize = 11.sp,
                    color = TextMuted
                )
            }
            Text(
                text = "Vol: ${formatVolume(ticker.quoteVolume24h)}",
                fontSize = 10.sp,
                color = TextSecondary
            )
        }

        // Last Price
        Column(modifier = Modifier.weight(1.2f)) {
            val priceDisplay = if (ticker.rawLastPrice.isNotBlank()) {
                PriceFormatter.formatPrice(ticker.symbol, ticker.marketType, ticker.rawLastPrice)
            } else {
                PriceFormatter.formatPrice(ticker.symbol, ticker.marketType, ticker.lastPrice)
            }
            Text(
                text = priceDisplay,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            if (ticker.marketType == MarketType.FUTURES && ticker.fundingRate != 0.0) {
                Text(
                    text = "FR: ${String.format(Locale.US, "%.4f", ticker.fundingRate * 100)}%",
                    fontSize = 9.sp,
                    color = TextMuted
                )
            }
        }

        // 24h Change badge
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(4.dp))
                .background(changeBg)
                .padding(horizontal = 6.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "${if (isBull) "+" else ""}${String.format(Locale.US, "%.2f", ticker.change24hPercent)}%",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = changeColor
            )
        }

        // Favorite Star
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier.size(36.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Outlined.StarBorder,
                contentDescription = "Favorite",
                tint = if (isFavorite) AccentYellow else TextMuted,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun formatVolume(volume: Double): String {
    return if (volume >= 1_000_000.0) {
        String.format(Locale.US, "%.2fM", volume / 1_000_000.0)
    } else if (volume >= 1000.0) {
        String.format(Locale.US, "%.1fK", volume / 1000.0)
    } else {
        String.format(Locale.US, "%.0f", volume)
    }
}
