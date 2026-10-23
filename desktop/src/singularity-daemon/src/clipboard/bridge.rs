// singularity: allow-large-file (test suite)
use std::sync::{Arc, Mutex};
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use tokio::net::tcp::{OwnedReadHalf, OwnedWriteHalf};
use tokio::sync::broadcast::{error::RecvError, Receiver};
use singularity_core::features::clipboard::{compute_hash, EchoRingBuffer};
use singularity_protocol::{encode_set_clipboard, TYPE_ACK_CLIPBOARD, TYPE_CLIPBOARD_EVENT};
use crate::error::Result;
use crate::wayland::WaylandClipboardWriter;

pub struct ClipboardBridge {
    echo_buffer: Arc<Mutex<EchoRingBuffer>>,
}

impl ClipboardBridge {
    pub fn new() -> Self {
        Self {
            echo_buffer: Arc::new(Mutex::new(EchoRingBuffer::default())),
        }
    }

    /// Runs bidirectional clipboard synchronization between Wayland and scrcpy-server TCP stream.
    pub async fn run(
        &self,
        local_rx: Receiver<String>,
        read_half: OwnedReadHalf,
        write_half: OwnedWriteHalf,
    ) -> Result<()> {
        let echo_tx = Arc::clone(&self.echo_buffer);
        let echo_rx = Arc::clone(&self.echo_buffer);

        let tx_task = tokio::spawn(async move {
            Self::pc_to_phone_loop(local_rx, write_half, echo_tx).await
        });

        let rx_task = tokio::spawn(async move {
            Self::phone_to_pc_loop(read_half, echo_rx).await
        });

        tokio::select! {
            res = tx_task => tracing::warn!(target: "clipboard_bridge", res = ?res, "PC->Phone task ended"),
            res = rx_task => tracing::warn!(target: "clipboard_bridge", res = ?res, "Phone->PC task ended"),
        }

        Ok(())
    }

    async fn pc_to_phone_loop(
        mut local_rx: Receiver<String>,
        mut writer: OwnedWriteHalf,
        echo_buf: Arc<Mutex<EchoRingBuffer>>,
    ) -> Result<()> {
        let mut seq = 1u64;
        loop {
            let text = match local_rx.recv().await {
                Ok(t) => t,
                Err(RecvError::Lagged(skipped)) => {
                    tracing::debug!(target: "clipboard_bridge", skipped = skipped, "Broadcast receiver lagged; continuing");
                    continue;
                }
                Err(RecvError::Closed) => break,
            };

            let hash = compute_hash(&text);
            {
                let mut buf = echo_buf.lock().unwrap();
                if buf.is_echo(hash) {
                    tracing::debug!(target: "clipboard_bridge", hash = hash, "Echo suppressed: local copy originated from phone");
                    continue;
                }
                buf.record(hash);
            }

            let packet = encode_set_clipboard(seq, false, &text);
            writer.write_all(&packet).await?;
            writer.flush().await?;
            tracing::info!(target: "clipboard_bridge", seq = seq, len = text.len(), "Synced: Wayland -> Android");
            seq = seq.wrapping_add(1);
        }
        Ok(())
    }

    async fn phone_to_pc_loop(
        mut reader: OwnedReadHalf,
        echo_buf: Arc<Mutex<EchoRingBuffer>>,
    ) -> Result<()> {
        let mut type_buf = [0u8; 1];
        while reader.read_exact(&mut type_buf).await.is_ok() {
            match type_buf[0] {
                TYPE_CLIPBOARD_EVENT => {
                    let mut len_buf = [0u8; 4];
                    reader.read_exact(&mut len_buf).await?;
                    let len = u32::from_be_bytes(len_buf) as usize;

                    let mut text_buf = vec![0u8; len];
                    reader.read_exact(&mut text_buf).await?;
                    let text = String::from_utf8_lossy(&text_buf).into_owned();

                    let hash = compute_hash(&text);
                    {
                        let mut buf = echo_buf.lock().unwrap();
                        if buf.is_echo(hash) {
                            tracing::debug!(target: "clipboard_bridge", hash = hash, "Echo suppressed: remote clip originated from PC");
                            continue;
                        }
                        buf.record(hash);
                    }

                    if let Err(e) = WaylandClipboardWriter::write_text(&text) {
                        tracing::warn!(target: "clipboard_bridge", error = %e, "Failed to inject clip into Wayland");
                    } else {
                        tracing::info!(target: "clipboard_bridge", len = text.len(), "Synced: Android -> Wayland");
                    }
                }
                TYPE_ACK_CLIPBOARD => {
                    let mut seq_buf = [0u8; 8];
                    reader.read_exact(&mut seq_buf).await?;
                    let seq = u64::from_be_bytes(seq_buf);
                    tracing::trace!(target: "clipboard_bridge", seq = seq, "Received clipboard ack from phone");
                }
                other => {
                    tracing::trace!(target: "clipboard_bridge", opcode = other, "Received other scrcpy device message");
                }
            }
        }
        Ok(())
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn bridge_initializes_with_empty_echo_buffer() {
        let bridge = ClipboardBridge::new();
        let buf = bridge.echo_buffer.lock().unwrap();
        assert!(!buf.is_echo(999));
    }

    #[tokio::test]
    async fn pc_to_phone_suppresses_recorded_echo_hash() {
        let bridge = ClipboardBridge::new();
        let text = "Duplicate payload";
        let hash = compute_hash(text);
        bridge.echo_buffer.lock().unwrap().record(hash);

        let (tx, rx) = tokio::sync::broadcast::channel(4);
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await.unwrap();
        let addr = listener.local_addr().unwrap();

        let client = tokio::net::TcpStream::connect(addr).await.unwrap();
        let (server, _) = listener.accept().await.unwrap();

        let (_, writer) = client.into_split();
        let (mut server_reader, _) = server.into_split();

        tx.send(text.to_string()).unwrap();
        drop(tx);

        let echo_buf = Arc::clone(&bridge.echo_buffer);
        let _ = ClipboardBridge::pc_to_phone_loop(rx, writer, echo_buf).await;

        // Verify zero bytes were written to server stream because echo was suppressed
        let mut buf = Vec::new();
        server_reader.read_to_end(&mut buf).await.unwrap();
        assert!(buf.is_empty(), "Server should not receive any data when echo is suppressed");
    }
}
