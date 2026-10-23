use byteorder::{BigEndian, ByteOrder};
use crate::error::{ProtocolError, Result};

pub const TYPE_CLIPBOARD_EVENT: u8 = 0x00;
pub const TYPE_ACK_CLIPBOARD: u8 = 0x01;
pub const TYPE_SET_CLIPBOARD: u8 = 0x09;

/// Encodes a `SET_CLIPBOARD` (0x09) packet for scrcpy-server.
pub fn encode_set_clipboard(sequence: u64, paste: bool, text: &str) -> Vec<u8> {
    let text_bytes = text.as_bytes();
    let mut buf = vec![0u8; 1 + 8 + 1 + 4 + text_bytes.len()];

    buf[0] = TYPE_SET_CLIPBOARD;
    BigEndian::write_u64(&mut buf[1..9], sequence);
    buf[9] = if paste { 1 } else { 0 };
    BigEndian::write_u32(&mut buf[10..14], text_bytes.len() as u32);
    buf[14..].copy_from_slice(text_bytes);

    buf
}

/// Incoming event packet from scrcpy-server
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum ScrcpyDeviceMsg {
    Clipboard(String),
    AckClipboard(u64),
}

/// Decodes an incoming packet from scrcpy-server.
pub fn decode_device_msg(buf: &[u8]) -> Result<(ScrcpyDeviceMsg, usize)> {
    if buf.is_empty() {
        return Err(ProtocolError::BufferTooShort { expected: 1, found: 0 });
    }

    match buf[0] {
        TYPE_CLIPBOARD_EVENT => {
            if buf.len() < 5 {
                return Err(ProtocolError::BufferTooShort { expected: 5, found: buf.len() });
            }
            let len = BigEndian::read_u32(&buf[1..5]) as usize;
            let total = 5 + len;
            if buf.len() < total {
                return Err(ProtocolError::BufferTooShort { expected: total, found: buf.len() });
            }
            let text = String::from_utf8(buf[5..total].to_vec())?;
            Ok((ScrcpyDeviceMsg::Clipboard(text), total))
        }
        TYPE_ACK_CLIPBOARD => {
            if buf.len() < 9 {
                return Err(ProtocolError::BufferTooShort { expected: 9, found: buf.len() });
            }
            let seq = BigEndian::read_u64(&buf[1..9]);
            Ok((ScrcpyDeviceMsg::AckClipboard(seq), 9))
        }
        opcode => Err(ProtocolError::UnknownOpcode(opcode)),
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn encode_set_clipboard_produces_correct_wire_layout() {
        let text = "test";
        let packet = encode_set_clipboard(42, true, text);
        assert_eq!(packet[0], TYPE_SET_CLIPBOARD);
        assert_eq!(BigEndian::read_u64(&packet[1..9]), 42);
        assert_eq!(packet[9], 1);
        assert_eq!(BigEndian::read_u32(&packet[10..14]), 4);
        assert_eq!(&packet[14..], text.as_bytes());
    }

    #[test]
    fn decode_device_clipboard_roundtrip() {
        let text = "Hello Wayland!";
        let mut raw = vec![TYPE_CLIPBOARD_EVENT];
        raw.extend_from_slice(&(text.len() as u32).to_be_bytes());
        raw.extend_from_slice(text.as_bytes());

        let (msg, consumed) = decode_device_msg(&raw).unwrap();
        assert_eq!(consumed, raw.len());
        assert_eq!(msg, ScrcpyDeviceMsg::Clipboard(text.into()));
    }
}
