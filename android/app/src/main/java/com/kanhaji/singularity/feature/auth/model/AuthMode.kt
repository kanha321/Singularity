package com.kanhaji.singularity.feature.auth.model

enum class AuthMode(val label: String, val description: String) {
    DENY("Deny", "Bypass phone and go straight to laptop password"),
    NOTIFY("Notify", "Show notification; tap to verify fingerprint"),
    PROMPT("Prompt", "Immediately display fingerprint dialog on phone");

    companion object {
        fun fromString(value: String): AuthMode = when (value.lowercase()) {
            "deny" -> DENY
            "notify" -> NOTIFY
            else -> PROMPT
        }
    }
}
