use std::io::Read;
use std::time::Duration;
use wl_clipboard_rs::paste::{get_contents, ClipboardType, MimeType, Seat};
use wl_clipboard_rs::watch::Offer;

const PREFERRED_CANDIDATES: &[&str] = &[
    "text/plain;charset=utf-8",
    "text/plain",
    "UTF8_STRING",
    "TEXT",
    "STRING",
];

/// Collects available plain-text MIME candidates in priority order.
pub fn candidate_mime_types<'a>(mime_types: &'a [String]) -> Vec<&'a str> {
    let mut candidates = Vec::new();
    for preferred in PREFERRED_CANDIDATES {
        if let Some(found) = mime_types.iter().find(|m| m.as_str() == *preferred) {
            candidates.push(found.as_str());
        }
    }
    for m in mime_types {
        if m.starts_with("text/") && !candidates.contains(&m.as_str()) {
            candidates.push(m.as_str());
        }
    }
    candidates
}

/// Fallback helper that fetches clipboard text via `get_contents` with compositor roundtrip.
pub fn read_clipboard_fallback() -> Option<String> {
    match get_contents(ClipboardType::Regular, Seat::Unspecified, MimeType::Text) {
        Ok((mut reader, _)) => {
            let mut text = String::new();
            if reader.read_to_string(&mut text).is_ok() && !text.is_empty() {
                Some(text)
            } else {
                None
            }
        }
        Err(e) => {
            tracing::debug!(target: "wayland_reader", error = %e, "Fallback get_contents failed");
            None
        }
    }
}

/// Attempts to read text from an active Wayland offer across all candidate MIME types,
/// falling back to roundtrip-backed retrieval if the offer pipe was empty or racy.
pub fn extract_text_from_offer(offer: &mut Offer<'_>, mime_types: &[String]) -> Option<String> {
    let candidates = candidate_mime_types(mime_types);
    for mime in candidates {
        match offer.receive(mime) {
            Ok(mut reader) => {
                let mut content = String::new();
                if reader.read_to_string(&mut content).is_ok() && !content.is_empty() {
                    return Some(content);
                }
            }
            Err(e) => {
                tracing::debug!(target: "wayland_reader", mime = mime, error = %e, "Offer receive failed");
            }
        }
    }

    // If offer-pipe was empty or racy (e.g. Chromium async IPC delay), fall back to roundtrip fetch
    std::thread::sleep(Duration::from_millis(25));
    read_clipboard_fallback()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn candidate_mime_types_prioritizes_utf8_charset() {
        let types = vec![
            "text/html".into(),
            "text/plain".into(),
            "text/plain;charset=utf-8".into(),
        ];
        let candidates = candidate_mime_types(&types);
        assert_eq!(candidates[0], "text/plain;charset=utf-8");
        assert_eq!(candidates[1], "text/plain");
        assert_eq!(candidates[2], "text/html");
    }

    #[test]
    fn candidate_mime_types_includes_legacy_string_tokens() {
        let types = vec!["STRING".into(), "random/binary".into(), "UTF8_STRING".into()];
        let candidates = candidate_mime_types(&types);
        assert_eq!(candidates, vec!["UTF8_STRING", "STRING"]);
    }

    #[test]
    fn candidate_mime_types_empty_when_no_text() {
        let types = vec!["image/png".into(), "application/octet-stream".into()];
        assert!(candidate_mime_types(&types).is_empty());
    }
}

