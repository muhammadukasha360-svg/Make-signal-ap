# SIGNALX AI — MASTER FEATURE CHECKLIST & SPECIFICATION

## System Overview
- **App Name:** SignalX AI
- **Application ID:** `com.aistudio.signalx.ai`
- **Architecture:** Android Native (Kotlin + Jetpack Compose M3 + StateFlow + Clean Architecture)
- **Data Engine:** Real Bitget Exchange REST v2 + WebSockets (Spot & Futures), Zero Mock Data
- **Price Precision:** Strict `BigDecimal` representation and per-symbol `pricePrecision`/`pricePlace`

---

## 1. Smart Money Concepts (SMC) Engine

| Feature Module | Sub-Feature / Indicator | Mathematical & Algorithmic Logic | Status |
| :--- | :--- | :--- | :--- |
| **Market Momentum & Zones** | Bullish / Bearish Momentum | Candle body to total range ratio >= 60%, consecutive directional bars | **IMPLEMENTED** |
| | Bullish / Bearish Displacement | Candle size >= 1.8x ATR(14) with strong directional expansion | **IMPLEMENTED** |
| | Strong Rejection Zones | Long wick >= 60% of total candle height rejecting extreme levels | **IMPLEMENTED** |
| | Momentum Shift | Fast directional transition opposing dominant short-term trend | **IMPLEMENTED** |
| | Buying / Selling Pressure | High volume bull/bear candles with close in top/bottom 20% | **IMPLEMENTED** |
| | Consolidation / Expansion | Range compression (< 0.7x ATR) followed by volatility explosion | **IMPLEMENTED** |
| **Fair Value Gap (FVG)** | Bullish FVG | Candle 0 High < Candle 2 Low gap created by displacement Candle 1 | **IMPLEMENTED** |
| | Bearish FVG | Candle 0 Low > Candle 2 High gap created by displacement Candle 1 | **IMPLEMENTED** |
| | Inverse FVG (IFVG) | FVG fully closed through and flipped to opposite polarity support/resistance | **IMPLEMENTED** |
| | Balanced Price Range (BPR) | Overlapping bullish and bearish FVGs creating immediate equilibrium | **IMPLEMENTED** |
| | FVG Lifecycle Metrics | Exact Upper/Lower boundary, Midpoint (50%), Fill %, Mitigation status, Age | **IMPLEMENTED** |
| **Order Block (OB) Engine** | Bullish Order Block | Last down candle prior to explosive upward displacement breaking structure | **IMPLEMENTED** |
| | Bearish Order Block | Last up candle prior to explosive downward displacement breaking structure | **IMPLEMENTED** |
| | Breaker Block | Violated order block converted into inverted support/resistance | **IMPLEMENTED** |
| | Mitigation Block | Order block that failed to take liquidity but still offers mitigation bounce | **IMPLEMENTED** |
| | Rejection Block | High-volume wick extremes where institutional supply/demand stepped in | **IMPLEMENTED** |
| | OB Lifecycle Metrics | Displacement strength (multiple of ATR), Volume ratio, Retested / Invalidated | **IMPLEMENTED** |
| **Market Structure Analysis** | Break of Structure (BOS) | Trend continuation break beyond established swing high/low | **IMPLEMENTED** |
| | Change of Character (CHoCH) | Counter-trend break signalling early structural trend reversal | **IMPLEMENTED** |
| | Market Structure Shift (MSS) | Confirmed multi-swing transition from bearish to bullish or vice-versa | **IMPLEMENTED** |
| | Close vs Wick Confirmation | Distinction between confirmed close beyond level vs liquidity wick sweep | **IMPLEMENTED** |
| | Internal vs External Structure | Multi-timeframe sub-structure swings vs major dealing range swings | **IMPLEMENTED** |
| **Liquidity Analysis** | Liquidity Sweeps | Price wicks past key swing extreme and closes back inside the range | **IMPLEMENTED** |
| | Stop Hunt Detection | Aggressive expansion to trigger stop orders followed by swift reversal | **IMPLEMENTED** |
| | Equal Highs / Lows (EQH/EQL)| Price levels within 0.1% of each other forming engineered liquidity pools | **IMPLEMENTED** |
| | Buy-Side Liquidity (BSL) | Clustered stop orders resting above major swing highs | **IMPLEMENTED** |
| | Sell-Side Liquidity (SSL) | Clustered stop orders resting below major swing lows | **IMPLEMENTED** |
| | Sweep Reclaim Confirmation | Candle close confirming failure to sustain beyond liquidity level | **IMPLEMENTED** |
| **Support & Resistance** | Dynamic Key Levels | Volume-weighted dynamic reaction levels and ATR bands | **IMPLEMENTED** |
| | Static Multi-Touch Zones | Horizontal reaction zones with 2+ validated touches within tolerance | **IMPLEMENTED** |
| **Premium & Discount Range** | 50% Equilibrium Line | Exact median `(RangeHigh + RangeLow) / 2` of active dealing range | **IMPLEMENTED** |
| | Discount Zone | Bottom 50% of dealing range (favored for institutional longs) | **IMPLEMENTED** |
| | Premium Zone | Top 50% of dealing range (favored for institutional shorts) | **IMPLEMENTED** |
| | Optimal Trade Entry (OTE) | Fibonacci 61.8% to 78.6% retracement zone for maximum R:R entries | **IMPLEMENTED** |
| **Volume Profile & POC** | Point of Control (POC) | Price bin containing highest traded volume over active window | **IMPLEMENTED** |
| | Value Area High (VAH) | Upper boundary enclosing 70% of total traded volume distribution | **IMPLEMENTED** |
| | Value Area Low (VAL) | Lower boundary enclosing 70% of total traded volume distribution | **IMPLEMENTED** |
| **SMT Divergence Engine** | Bullish SMT Divergence | Active coin makes Lower Low while Benchmark BTC makes Higher Low | **IMPLEMENTED** |
| | Bearish SMT Divergence | Active coin makes Higher High while Benchmark BTC makes Lower High | **IMPLEMENTED** |
| | Cross-Asset Correlation | Real-time correlation tracking against BTC Spot benchmark | **IMPLEMENTED** |
| **Intelligent SL & Invalidation** | Structural Stop-Loss | Invalidation level placed safely behind invalidating OB or swing extreme | **IMPLEMENTED** |
| | Dynamic ATR Buffer | Volatility-adjusted buffer preventing premature stop-out by wick | **IMPLEMENTED** |
| | Multi-Tier Take-Profit (TP1-3)| TP1 (1:1.5 - 1:2), TP2 (Key liquidity/FVG target), TP3 (Macro expansion)| **IMPLEMENTED** |

---

## 2. Professional 34 Chart Visual Markings

All 34 visual markers are calculated and rendered in `CandlestickChart.kt`, anchored to candle timestamps and price coordinates, scaling seamlessly with zoom, scroll, and viewport resize:

1. **Bullish Fair Value Gap Box:** Green shaded translucent zone (`#220ECB81`) with border.
2. **Bearish Fair Value Gap Box:** Red shaded translucent zone (`#22F6465D`) with border.
3. **FVG 50% Midpoint Dotted Line:** Consequent encroachment line (`AccentYellow`).
4. **Inverse Fair Value Gap (IFVG):** Inverted dashed boundary box with polarity label.
5. **Balanced Price Range (BPR):** Overlapping dual-color boundary marking equilibrium.
6. **Bullish Order Block Box:** Cyan shaded zone (`#2200F0FF`) with solid left anchor.
7. **Bearish Order Block Box:** Orange shaded zone (`#22FF9800`) with solid left anchor.
8. **Breaker Block Box:** Dashed cyan/orange border box highlighting inverted role.
9. **Mitigation Block Box:** Dimmed order block marking mitigated level.
10. **Rejection Block Wick Shading:** Highlighted wick zone where heavy absorption occurred.
11. **Break of Structure (BOS) Line:** Horizontal ray across candles with `"BOS"` tag.
12. **Change of Character (CHoCH) Line:** Prominent horizontal ray with `"CHoCH"` tag.
13. **Market Structure Shift (MSS) Label:** Accent colored callout banner at structural shift candle.
14. **Confirmed Close vs Wick Break Badge:** Visual indicator showing `"CLOSE"` or `"WICK"` marker.
15. **Liquidity Sweep Wick Indicator:** Curved arrow / chevron marking purged liquidity.
16. **Stop Hunt Highlight:** Flashing accent callout tag indicating trapped breakout traders.
17. **Equal Highs (EQH) Dotted Line:** Double horizontal tick line labeled `"EQH x.xx"`.
18. **Equal Lows (EQL) Dotted Line:** Double horizontal tick line labeled `"EQL x.xx"`.
19. **Buy-Side Liquidity (BSL) Pool Line:** Red dashed boundary line marking resting stops above highs.
20. **Sell-Side Liquidity (SSL) Pool Line:** Green dashed boundary line marking resting stops below lows.
21. **Sweep Reclaim Confirmation Marker:** Checkmark badge at candle close back inside range.
22. **50% Equilibrium Dealing Range Line:** Prominent gold dashed center line labeled `"EQ 50%"`.
23. **Premium Zone Overlay:** Subtle purple upper-half background tint labeled `"PREMIUM"`.
24. **Discount Zone Overlay:** Subtle cyan lower-half background tint labeled `"DISCOUNT"`.
25. **Bullish Optimal Trade Entry (OTE) Zone:** Gold shaded bracket from 61.8% to 78.6% retracement.
26. **Bearish Optimal Trade Entry (OTE) Zone:** Orange shaded bracket from 61.8% to 78.6% retracement.
27. **Point of Control (POC) Horizontal Line:** Solid red horizontal price line labeled `"POC"`.
28. **Value Area High (VAH) Line:** Dashed blue line labeled `"VAH (70%)"`.
29. **Value Area Low (VAL) Line:** Dashed blue line labeled `"VAL (70%)"`.
30. **SMT Divergence Indicator:** Warning badge at divergence candle comparing against BTC.
31. **Multi-Touch Support Zones:** Green translucent horizontal ribbons with touch-count badges.
32. **Multi-Touch Resistance Zones:** Red translucent horizontal ribbons with touch-count badges.
33. **Intelligent Stop-Loss Level Line:** Dotted crimson price line with `"SL @ Price"` tag.
34. **Take-Profit Multi-Tier Lines:** Green horizontal target lines labeled `"TP1"`, `"TP2"`, `"TP3"`.

---

## 3. Interactive Chart & Layer Visibility System

- **Tap Detection:** Precise gesture detector mapping screen coordinates to price & time windows of SMC elements.
- **Element Detail Modal (`SmcElementDetailModal.kt`):**
  - Pattern Name & Direction badge (`BULLISH` / `BEARISH`).
  - Pakistan Standard Time (`PKT`) formation timestamp.
  - Price Range (Upper, Lower, Midpoint / 50% Consequent Encroachment).
  - Status (Active, Retested, Mitigated, Invalidation criteria).
  - Technical Confirmation requirements (e.g. Volume > 1.2x ATR, confirmed close).
  - **Roman Urdu Explanation:** Plain-language institutional rationale (e.g. *"Institutions ne yahan aggressive buying ki thi..."*).
  - Supporting Market Evidence (RSI, CVD, Volume, Confluence).
- **Layer Visibility Controls Strip:** Independent toggle chips for:
  - FVG
  - Order Blocks (OB)
  - BOS / CHoCH
  - Liquidity Pools & Sweeps
  - Equilibrium & OTE Zones
  - Volume Profile (POC/VAH/VAL)
  - SMT Divergences
  - Trade Levels (SL/TP)
  - Reset All Layers

---

## 4. Timeframe Architecture & Transition Safety

- **Supported Timeframes (13 total):**
  - Short-Term: `1m`, `3m`, `5m`, `15m`, `30m`
  - Medium-Term: `1h`, `2h`, `4h`, `6h`, `12h`
  - Long-Term: `1D`, `1W`, `1M`
- **Transition Safety & Zero-Crash Architecture:**
  - `candleLoadJob?.cancel()` ensures any previous network fetch is cancelled upon switching timeframe.
  - Prevents race conditions where a slow 15m response overwrites a newly requested 1m dataset.
  - `CandlestickChart` canvas is guarded with `.coerceAtLeast(0f)` on dimensions and early exits when chart dimensions $\le 10f$.
  - Clean WebSocket re-subscription to the exact timeframe channel on Bitget.

---

## 5. Verification & Build Artifacts

- **Local Unit Tests:** `gradle :app:testDebugUnitTest` — Verified All Pass.
- **Compilation Check:** `compile_applet` — Verified Clean Build.
- **APK Assembly:** `gradle :app:assembleDebug` — Generates `app/build/outputs/apk/debug/app-debug.apk`.
- **CI/CD Automation:** `.github/workflows/android-build.yml` — Automated GitHub Actions workflow on Ubuntu runner.
