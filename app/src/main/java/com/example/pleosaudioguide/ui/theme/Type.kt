package com.example.pleosaudioguide.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import com.example.pleosaudioguide.R

/** Bundled Asta Sans: Korean/Latin, SIL OFL (assets/licenses/asta_sans_ofl.txt). */
@OptIn(ExperimentalTextApi::class)
val AstaSans = FontFamily(
    *listOf(300, 400, 500, 600, 700, 800).map { weight ->
        Font(R.font.asta_sans, weight = FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)))
    }.toTypedArray()
)

private val defaults = Typography()
val Typography = Typography(
    displayLarge = defaults.displayLarge.copy(fontFamily = AstaSans),
    displayMedium = defaults.displayMedium.copy(fontFamily = AstaSans),
    displaySmall = defaults.displaySmall.copy(fontFamily = AstaSans),
    headlineLarge = defaults.headlineLarge.copy(fontFamily = AstaSans),
    headlineMedium = defaults.headlineMedium.copy(fontFamily = AstaSans),
    headlineSmall = defaults.headlineSmall.copy(fontFamily = AstaSans),
    titleLarge = defaults.titleLarge.copy(fontFamily = AstaSans),
    titleMedium = defaults.titleMedium.copy(fontFamily = AstaSans),
    titleSmall = defaults.titleSmall.copy(fontFamily = AstaSans),
    bodyLarge = defaults.bodyLarge.copy(fontFamily = AstaSans),
    bodyMedium = defaults.bodyMedium.copy(fontFamily = AstaSans),
    bodySmall = defaults.bodySmall.copy(fontFamily = AstaSans),
    labelLarge = defaults.labelLarge.copy(fontFamily = AstaSans),
    labelMedium = defaults.labelMedium.copy(fontFamily = AstaSans),
    labelSmall = defaults.labelSmall.copy(fontFamily = AstaSans)
)
