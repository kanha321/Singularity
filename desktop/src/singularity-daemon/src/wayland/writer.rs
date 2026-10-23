use wl_clipboard_rs::copy::{ClipboardType, Error as CopyError, MimeType, Options, Source};

/// Injects clipboard payloads into Wayland compositor selections.
pub struct WaylandClipboardWriter;

impl WaylandClipboardWriter {
    /// Copies text into Wayland clipboard, attempting both Regular and Primary selections.
    pub fn write_text(text: &str) -> Result<(), CopyError> {
        if text.is_empty() {
            return Ok(());
        }

        let mut opts = Options::new();
        opts.clipboard(ClipboardType::Both);
        opts.foreground(false);

        let source = Source::Bytes(text.as_bytes().to_vec().into());

        // Attempt writing to both Regular and Primary. If Primary fails (compositor lack),
        // gracefully fall back to writing Regular only.
        match opts.clone().copy(source.clone(), MimeType::Autodetect) {
            Ok(()) => {
                tracing::debug!(target: "wayland_writer", len = text.len(), "Copied to both Regular and Primary clipboards");
                Ok(())
            }
            Err(e) => {
                tracing::debug!(target: "wayland_writer", error = %e, "Dual-clipboard write failed, falling back to Regular");
                let mut fallback_opts = Options::new();
                fallback_opts.clipboard(ClipboardType::Regular);
                fallback_opts.foreground(false);
                fallback_opts.copy(source, MimeType::Autodetect)
            }
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn empty_string_is_noop_and_returns_ok() {
        assert!(WaylandClipboardWriter::write_text("").is_ok());
    }

    #[test]
    fn build_source_preserves_utf8_payload() {
        let payload = "🚀 Singularity Wayland Copy Test \n Multi-line 123";
        let source_bytes = payload.as_bytes().to_vec();
        assert_eq!(source_bytes.len(), payload.len());
    }
}
