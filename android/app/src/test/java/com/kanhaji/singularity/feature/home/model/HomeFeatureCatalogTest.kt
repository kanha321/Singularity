package com.kanhaji.singularity.feature.home.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class HomeFeatureCatalogTest {

    @Test
    fun featureCards_whenCameraInactive_allCardsMatchStandardHighlightAndNoAlert() {
        val cards = HomeFeatureCatalog.featureCards(
            onOpenAuth = {},
            onOpenPower = {},
            onOpenFiles = {},
            onOpenCamera = {},
            isCameraActive = false
        )

        assertThat(cards).hasSize(5)
        val audioCard = cards.first { it.id == "audio_feature" }
        assertThat(audioCard.title).isEqualTo("Audio Output")
        assertThat(audioCard.isHighlighted).isTrue()

        val cameraCard = cards.first { it.id == "camera_feature" }

        assertThat(cameraCard.isHighlighted).isTrue()
        assertThat(cameraCard.isAlert).isFalse()
        assertThat(cameraCard.statusText).isEqualTo("Camera")

        for (card in cards) {
            assertThat(card.isHighlighted).isTrue()
            assertThat(card.isAlert).isFalse()
        }
    }

    @Test
    fun featureCards_whenCameraActive_cameraCardHasAlertAndLiveStatus() {
        val cards = HomeFeatureCatalog.featureCards(
            onOpenAuth = {},
            onOpenPower = {},
            onOpenFiles = {},
            onOpenCamera = {},
            isCameraActive = true
        )

        val cameraCard = cards.first { it.id == "camera_feature" }
        assertThat(cameraCard.isHighlighted).isTrue()
        assertThat(cameraCard.isAlert).isTrue()
        assertThat(cameraCard.statusText).isEqualTo("Live")
        assertThat(cameraCard.subtitle).contains("Live streaming")

        val otherCards = cards.filter { it.id != "camera_feature" }
        for (card in otherCards) {
            assertThat(card.isAlert).isFalse()
        }
    }
}

