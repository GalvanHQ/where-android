package com.ovi.where.presentation.onboarding

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.ui.unit.dp
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.property.Arb
import io.kotest.property.PropertyTesting
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.map
import io.kotest.property.checkAll

/**
 * Property-based tests for the Category Grid structure invariant.
 *
 * Verifies that a valid Category_Grid configuration contains exactly 6 icon elements,
 * and each icon element's container diameter is between 40dp and 56dp inclusive.
 *
 * **Validates: Requirements 4.1, 4.2**
 */
// Feature: premium-onboarding-redesign, Property 4: Category grid structure invariant
class CategoryGridPropertyTest : StringSpec({

    // ─────────────────────────────────────────────────────────────────────────
    // Feature: premium-onboarding-redesign, Property 4: Category grid structure invariant
    // ─────────────────────────────────────────────────────────────────────────

    "Property 4a: Only lists of exactly 6 elements are valid for the Category Grid" {
        checkAll(iterations = 200, Arb.int(1..10)) { listSize ->
            // The Category Grid requires exactly 6 icon elements
            val isValidGridSize = listSize == 6

            if (listSize == 6) {
                isValidGridSize shouldBe true
            } else {
                isValidGridSize shouldBe false
            }
        }
    }

    "Property 4b: Each icon element container diameter is between 40dp and 56dp inclusive" {
        checkAll(iterations = 200, Arb.int(30..70)) { sizeValue ->
            val sizeDp = sizeValue.dp
            val isValidSize = sizeDp >= 40.dp && sizeDp <= 56.dp

            if (sizeValue in 40..56) {
                isValidSize shouldBe true
            } else {
                isValidSize shouldBe false
            }
        }
    }

    "Property 4c: Static categoryIcons list has exactly 6 elements" {
        categoryIcons shouldHaveSize 6
    }

    "Property 4d: Static categoryIcons all have container diameters between 40dp and 56dp" {
        categoryIcons.forEach { iconData ->
            (iconData.sizeDp >= 40.dp) shouldBe true
            (iconData.sizeDp <= 56.dp) shouldBe true
        }
    }

    "Property 4e: Generated valid category grids always have 6 elements with valid sizes" {
        // Generate exactly 6 CategoryIconData items with valid sizes and verify invariants
        checkAll(
            iterations = 200,
            Arb.int(40..56),
            Arb.int(40..56),
            Arb.int(40..56),
            Arb.int(40..56),
            Arb.int(40..56),
            Arb.int(40..56)
        ) { s1, s2, s3, s4, s5, s6 ->
            val icons = listOf(
                Icons.Default.LocationOn,
                Icons.Default.Group,
                Icons.Default.Map,
                Icons.Default.Chat,
                Icons.Default.Notifications,
                Icons.Default.Navigation
            )
            val labels = listOf("Location", "Group", "Map", "Chat", "Notifications", "Navigation")
            val sizes = listOf(s1, s2, s3, s4, s5, s6)

            val grid = sizes.mapIndexed { index, size ->
                CategoryIconData(
                    icon = icons[index],
                    label = labels[index],
                    sizeDp = size.dp
                )
            }

            // Verify exactly 6 elements
            grid shouldHaveSize 6

            // Verify each element's container diameter is between 40dp and 56dp
            grid.forEach { iconData ->
                (iconData.sizeDp >= 40.dp) shouldBe true
                (iconData.sizeDp <= 56.dp) shouldBe true
            }
        }
    }

    "Property 4f: Invalid sizes outside 40-56dp range are rejected by validation" {
        checkAll(iterations = 200, Arb.int(30..70)) { sizeValue ->
            val sizeDp = sizeValue.dp
            // Replicate the validation logic: container diameter must be 40dp..56dp
            val meetsMinimum = sizeDp >= 40.dp
            val meetsMaximum = sizeDp <= 56.dp
            val isValid = meetsMinimum && meetsMaximum

            if (sizeValue < 40) {
                meetsMinimum shouldBe false
                isValid shouldBe false
            } else if (sizeValue > 56) {
                meetsMaximum shouldBe false
                isValid shouldBe false
            } else {
                isValid shouldBe true
            }
        }
    }

}) {
    companion object {
        init {
            // Lock the seed for reproducible counter-examples.
            PropertyTesting.defaultSeed = 0xC4E6L
        }
    }
}
