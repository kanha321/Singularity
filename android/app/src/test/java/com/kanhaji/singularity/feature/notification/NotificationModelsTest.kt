package com.kanhaji.singularity.feature.notification

import com.kanhaji.singularity.feature.notification.model.DesktopNotification
import com.kanhaji.singularity.feature.notification.model.NotificationButton
import com.kanhaji.singularity.feature.notification.model.NotificationDismiss
import com.kanhaji.singularity.feature.notification.model.PhoneNotification
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationModelsTest {

    @Test
    fun testPhoneNotificationSerialization() {
        val notif = PhoneNotification(
            id = "0|com.whatsapp|42|null|1000",
            packageName = "com.whatsapp",
            appName = "WhatsApp",
            title = "Alice",
            text = "Dinner at 7?",
            timestamp = 1730000000L,
            isClearable = true,
            canReply = true,
            actions = listOf(
                NotificationButton("action_0", "Reply"),
                NotificationButton("action_1", "Mark Read")
            )
        )

        val json = notif.toJson()
        val parsed = PhoneNotification.fromJson(json)

        assertEquals(notif.id, parsed.id)
        assertEquals(notif.packageName, parsed.packageName)
        assertEquals(notif.appName, parsed.appName)
        assertEquals(notif.title, parsed.title)
        assertEquals(notif.text, parsed.text)
        assertEquals(notif.timestamp, parsed.timestamp)
        assertEquals(notif.isClearable, parsed.isClearable)
        assertEquals(notif.canReply, parsed.canReply)
        assertEquals(2, parsed.actions.size)
        assertEquals("Reply", parsed.actions[0].label)
        assertEquals("action_0", parsed.actions[0].actionId)
    }

    @Test
    fun testDesktopNotificationParsing() {
        val json = org.json.JSONObject().apply {
            put("id", 101)
            put("app_name", "Terminal")
            put("summary", "Tests Passed")
            put("body", "Cargo test succeeded in 1.2s")
            put("urgency", 1)
            put("timeout_ms", 4000)
        }

        val notif = DesktopNotification.fromJson(json)
        assertEquals(101, notif.id)
        assertEquals("Terminal", notif.appName)
        assertEquals("Tests Passed", notif.summary)
        assertEquals("Cargo test succeeded in 1.2s", notif.body)
        assertEquals(1, notif.urgency)
        assertEquals(4000, notif.timeoutMs)
    }

    @Test
    fun testNotificationDismissSerialization() {
        val dismiss = NotificationDismiss(id = "101", source = "desktop")
        val json = dismiss.toJson()
        val parsed = NotificationDismiss.fromJson(json)

        assertEquals("101", parsed.id)
        assertEquals("desktop", parsed.source)
    }

    @Test
    fun testPhoneNotificationTrackerQueueDrain() {
        val dismiss = NotificationDismiss(id = "sbn_key_1", source = "phone")
        PhoneNotificationTracker.enqueueDismissal(dismiss)

        val (notifs, dismissals) = PhoneNotificationTracker.drainPending()
        assertEquals(0, notifs.size)
        assertEquals(1, dismissals.size)
        assertEquals("sbn_key_1", dismissals[0].id)

        // Ensure drained queue is now empty
        val (emptyNotifs, emptyDismissals) = PhoneNotificationTracker.drainPending()
        assertTrue(emptyNotifs.isEmpty())
        assertTrue(emptyDismissals.isEmpty())
    }

    @Test
    fun testCleanPackageFallback() {
        assertEquals("Linkedin", PhoneNotificationTracker.cleanPackageFallback("com.linkedin.android"))
        assertEquals("Amazon", PhoneNotificationTracker.cleanPackageFallback("in.amazon.mShop.android.shopping"))
        assertEquals("Shell", PhoneNotificationTracker.cleanPackageFallback("com.android.shell"))
    }
}

