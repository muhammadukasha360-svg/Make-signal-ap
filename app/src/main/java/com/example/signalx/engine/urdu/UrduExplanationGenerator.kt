package com.example.signalx.engine.urdu

import com.example.signalx.data.api.PriceFormatter
import com.example.signalx.data.model.Direction
import com.example.signalx.data.model.ForecastResult
import com.example.signalx.data.model.MarketType
import com.example.signalx.data.model.SignalType
import com.example.signalx.data.model.TradingDecision
import com.example.signalx.data.model.UrduExplanation
import com.example.signalx.engine.analysis.MarketAnalysisReport
import java.util.Locale
import kotlin.math.roundToInt

object UrduExplanationGenerator {

    fun generateExplanation(
        report: MarketAnalysisReport,
        forecast: ForecastResult,
        decision: TradingDecision,
        symbol: String,
        marketType: MarketType = MarketType.SPOT
    ): UrduExplanation {
        val priceStr = PriceFormatter.formatPrice(symbol, marketType, report.currentPrice)
        val rsiInt = report.rsi14.roundToInt()
        val ema7Str = PriceFormatter.formatPrice(symbol, marketType, report.ema7)
        val ema25Str = PriceFormatter.formatPrice(symbol, marketType, report.ema25)
        val ema99Str = PriceFormatter.formatPrice(symbol, marketType, report.ema99)
        val supportStr = PriceFormatter.formatPrice(symbol, marketType, report.keySupport)
        val resistStr = PriceFormatter.formatPrice(symbol, marketType, report.keyResistance)
        val slStr = PriceFormatter.formatPrice(symbol, marketType, decision.stopLoss)
        val tp1Str = PriceFormatter.formatPrice(symbol, marketType, decision.takeProfit1)
        val tp2Str = PriceFormatter.formatPrice(symbol, marketType, decision.takeProfit2)
        val confInt = forecast.confidence.roundToInt()
        val upPct = forecast.probUp.roundToInt()
        val downPct = forecast.probDown.roundToInt()
        val sidePct = forecast.probSideways.roundToInt()
        val buyers = report.buyersPressurePct
        val sellers = report.sellersPressurePct

        // 1. Next candle expectation sentence
        val urduExpectation = when (forecast.direction) {
            Direction.UP -> "اگلی کینڈل میں قیمت $priceStr سے اوپر جانے کا %$upPct امکان ہے، کیونکہ بائنگ پریشر غالب ہے۔"
            Direction.DOWN -> "اگلی کینڈل میں قیمت $priceStr سے نیچے گرنے کا %$downPct امکان ہے، کیونکہ سیلرز دباؤ بڑھا رہے ہیں۔"
            Direction.SIDEWAYS -> "اگلی کینڈل میں مارکیٹ سائیڈ ویز (%$sidePct امکان) رہنے کا خدشہ ہے اور واضح سمت کا فقدان ہے۔"
        }

        val romanExpectation = when (forecast.direction) {
            Direction.UP -> "Agli candle mein price $priceStr se oopar janay ka $upPct% chance hai kyun ke buyers active hain."
            Direction.DOWN -> "Agli candle mein price $priceStr se neechay girnay ka $downPct% chance hai kyun ke sellers ka pressure zyada hai."
            Direction.SIDEWAYS -> "Agli candle mein market sideways ($sidePct% imkan) reh sakti hai, breakout ka intezar karein."
        }

        val englishExpectation = when (forecast.direction) {
            Direction.UP -> "The next candle has a $upPct% probability of moving UP from $priceStr due to strong buyer momentum."
            Direction.DOWN -> "The next candle has a $downPct% probability of moving DOWN from $priceStr due to seller volume pressure."
            Direction.SIDEWAYS -> "The next candle shows sideways consolidation ($sidePct% probability) lacking clear breakout directional bias."
        }

        // 2. Buyer vs Seller pressure sentence
        val urduPressure = if (buyers > sellers) {
            "مارکیٹ میں خریداروں کا غلبہ $buyers% ہے جبکہ فروخت کنندگان $sellers% پر ہیں۔"
        } else {
            "سیلرز کا دباؤ $sellers% تک پہنچ چکا ہے جبکہ بائرز صرف $buyers% ہیں۔"
        }

        val romanPressure = if (buyers > sellers) {
            "Order flow mein buyers ka ghalba $buyers% hai aur sellers sirf $sellers% hain."
        } else {
            "Sellers ka dabao $sellers% par hai jab ke buyers $buyers% tak mehdood hain."
        }

        val englishPressure = "Current market volume delta shows buyers at $buyers% versus sellers at $sellers%."

        // 3. Technical indicators sentence
        val rsiTextUrdu = when {
            rsiInt > 70 -> "RSI ($rsiInt) اوور باٹ زون میں ہے"
            rsiInt < 30 -> "RSI ($rsiInt) اوور سولڈ زون میں ہے"
            report.rsiSlope > 0 -> "RSI ($rsiInt) مسلسل اوپر کی جانب رخ کیے ہوئے ہے"
            else -> "RSI ($rsiInt) نیچے کی طرف جھکاؤ دکھا رہا ہے"
        }

        val rsiTextRoman = when {
            rsiInt > 70 -> "RSI ($rsiInt) overbought zone mein hai"
            rsiInt < 30 -> "RSI ($rsiInt) oversold zone mein hai"
            report.rsiSlope > 0 -> "RSI ($rsiInt) oopar ki simt barh raha hai"
            else -> "RSI ($rsiInt) neechay ki taraf jhuka hua hai"
        }

        val emaTextUrdu = if (report.ema7 > report.ema25 && report.ema25 > report.ema99) {
            "اور EMA 7 ($ema7Str) > EMA 25 ($ema25Str) > EMA 99 ($ema99Str) کا بولش الائنمنٹ موجود ہے۔"
        } else if (report.ema7 < report.ema25 && report.ema25 < report.ema99) {
            "اور EMAs کا رخ بیئرش ٹرینڈ (7 < 25 < 99) کی تصدیق کر رہا ہے۔"
        } else {
            "جبکہ موونگ ایوریجز فلیٹ رینج میں الجھے ہوئے ہیں۔"
        }

        val emaTextRoman = if (report.ema7 > report.ema25 && report.ema25 > report.ema99) {
            "aur EMAs bullish alignment (7=$ema7Str > 25=$ema25Str > 99=$ema99Str) mein hain."
        } else if (report.ema7 < report.ema25 && report.ema25 < report.ema99) {
            "aur EMAs bearish trend (7 < 25 < 99) show kar rahay hain."
        } else {
            "jab ke EMAs flat range mein intertwine hain."
        }

        val englishIndicators = "Technical Indicators: RSI is at $rsiInt with EMA 7 at $ema7Str, EMA 25 at $ema25Str, and EMA 99 at $ema99Str."

        // 4. Key levels
        val urduLevels = "اہم لیولز: قریبی سپورٹ $supportStr پر اور ریزسٹنس $resistStr پر موجود ہے۔"
        val romanLevels = "Key Levels: Qareebi support $supportStr par hai aur resistance $resistStr par hai."
        val englishLevels = "Key Levels: Critical support at $supportStr and resistance at $resistStr."

        // 5. Signal and Risk management
        val urduSignalAction = when (decision.signal) {
            SignalType.STRONG_LONG, SignalType.LONG -> "ٹریڈنگ فیصلہ: لانگ پوزیشن - انٹری زون: $priceStr، سٹاپ لاس: $slStr، ٹیک پرافٹ TP1: $tp1Str، TP2: $tp2Str۔"
            SignalType.STRONG_SHORT, SignalType.SHORT -> "ٹریڈنگ فیصلہ: شارٹ پوزیشن - انٹری زون: $priceStr، سٹاپ لاس: $slStr، ٹیک پرافٹ TP1: $tp1Str، TP2: $tp2Str۔"
            SignalType.WAIT -> "ٹریڈنگ فیصلہ: انتظار (WAIT) - وجہ: ${decision.waitReason ?: "تصدیق ناکافی ہے"}۔"
        }

        val romanSignalAction = when (decision.signal) {
            SignalType.STRONG_LONG, SignalType.LONG -> "Signal: LONG Position - Entry: $priceStr, Stop Loss: $slStr, Take Profit TP1: $tp1Str, TP2: $tp2Str."
            SignalType.STRONG_SHORT, SignalType.SHORT -> "Signal: SHORT Position - Entry: $priceStr, Stop Loss: $slStr, Take Profit TP1: $tp1Str, TP2: $tp2Str."
            SignalType.WAIT -> "Signal: WAIT (Intezar) - Reason: ${decision.waitReason ?: "Tasdeeq mukammal nahi hui"}."
        }

        val englishSignalAction = when (decision.signal) {
            SignalType.STRONG_LONG, SignalType.LONG -> "Action: ${decision.signal} - Entry near $priceStr, Stop Loss: $slStr, TP1: $tp1Str, TP2: $tp2Str."
            SignalType.STRONG_SHORT, SignalType.SHORT -> "Action: ${decision.signal} - Entry near $priceStr, Stop Loss: $slStr, TP1: $tp1Str, TP2: $tp2Str."
            SignalType.WAIT -> "Action: WAIT - Reason: ${decision.waitReason ?: "Insufficient confirmations"}."
        }

        // 6. Invalidation and warnings
        val urduWarning = if (report.isFalseBreakout || report.isLiquidityTrap) {
            "انتباہ: مارکیٹ میں فیک بریک آؤٹ یا لیکویڈیٹی ٹریپ کا خطرہ ہے! سخت رسک مینجمنٹ اپنائیں۔"
        } else {
            "انویلیڈیشن: اگر قیمت $slStr کو چھو لے تو یہ ٹریڈ تھیسس مکمل منسوخ ہو جائے گا۔"
        }

        val romanWarning = if (report.isFalseBreakout || report.isLiquidityTrap) {
            "Warning: Fake breakout ya trap ka khatra hai! Strictly Stop Loss use karein."
        } else {
            "Invalidation: Agar price $slStr ko hit karti hai toh yeh signal cancel samjha jayega."
        }

        val englishWarning = "Invalidation Level: If price breaks $slStr, this trading setup is strictly invalidated."

        val urduDisclaimer = "⚠️ نوٹ: یہ ریاضیاتی اور تکنیکی تجزیے پر مبنی اندازہ ہے، کوئی فنانشل ایڈوائس یا پرافٹ گارنٹی نہیں ہے۔"
        val romanDisclaimer = "⚠️ Note: Yeh andaza hai, guarantee nahi. Hamesha apna risk manage karein."
        val englishDisclaimer = "⚠️ Educational analysis only. Not financial advice. Markets involve risk."

        val fullUrdu = """
            $urduExpectation
            $urduPressure
            $rsiTextUrdu $emaTextUrdu
            $urduLevels
            $urduSignalAction
            $urduWarning
            
            $urduDisclaimer
        """.trimIndent()

        val fullRoman = """
            $romanExpectation
            $romanPressure
            $rsiTextRoman, $emaTextRoman
            $romanLevels
            $romanSignalAction
            $romanWarning
            
            $romanDisclaimer
        """.trimIndent()

        val fullEnglish = """
            $englishExpectation
            $englishPressure
            $englishIndicators
            $englishLevels
            $englishSignalAction
            $englishWarning
            
            $englishDisclaimer
        """.trimIndent()

        val bullets = listOf(
            "Symbol: $symbol @ $priceStr",
            "Next Direction: ${forecast.direction} (Confidence: $confInt%)",
            "Buyers vs Sellers: $buyers% vs $sellers%",
            "RSI: $rsiInt | ATR: ${PriceFormatter.formatPrice(symbol, marketType, report.atr)}",
            "Decision: ${decision.signal}",
            "Stop Loss: $slStr | TP1: $tp1Str"
        )

        return UrduExplanation(
            urduScript = fullUrdu,
            romanUrdu = fullRoman,
            english = fullEnglish,
            bulletPoints = bullets
        )
    }
}
