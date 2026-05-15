package com.ovi.where.presentation.onboarding

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.ovi.where.R

/**
 * Data class representing a single onboarding page.
 * Each page has a full-bleed illustration and text content.
 */
data class OnboardingPageData(
    @DrawableRes val illustrationRes: Int,
    @StringRes val headlineRes: Int,
    @StringRes val subtitleRes: Int,
    val illustrationDescription: String
)

/**
 * Static list of onboarding pages.
 * Replace drawable resources with your actual illustrations.
 */
internal val onboardingPages = listOf(
    OnboardingPageData(
        illustrationRes = R.drawable.img_onboarding_1,
        headlineRes = R.string.onboarding_headline_1,
        subtitleRes = R.string.onboarding_subtitle_1,
        illustrationDescription = "People sharing their location on a map"
    ),
    OnboardingPageData(
        illustrationRes = R.drawable.img_onboarding_2,
        headlineRes = R.string.onboarding_headline_2,
        subtitleRes = R.string.onboarding_subtitle_2,
        illustrationDescription = "Friends creating and joining groups"
    ),
    OnboardingPageData(
        illustrationRes = R.drawable.img_onboarding_3,
        headlineRes = R.string.onboarding_headline_3,
        subtitleRes = R.string.onboarding_subtitle_3,
        illustrationDescription = "People staying connected across the globe"
    )
)
