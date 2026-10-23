use thiserror::Error;

#[derive(Error, Debug)]
pub enum ProtocolError {
    #[error("Buffer too short: expected at least {expected} bytes, found {found}")]
    BufferTooShort { expected: usize, found: usize },

    #[error("Unknown packet opcode: 0x{0:02x}")]
    UnknownOpcode(u8),

    #[error("Invalid UTF-8 payload: {0}")]
    InvalidUtf8(#[from] std::string::FromUtf8Error),

    #[error("Serialization failure: {0}")]
    Serialization(#[from] serde_json::Error),

    #[error("I/O error during packet decoding: {0}")]
    Io(#[from] std::io::Error),
}

pub type Result<T> = std::result::Result<T, ProtocolError>;

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn buffer_too_short_displays_proper_counts() {
        let err = ProtocolError::BufferTooShort { expected: 10, found: 3 };
        assert_eq!(err.to_string(), "Buffer too short: expected at least 10 bytes, found 3");
    }

    #[test]
    fn unknown_opcode_displays_hex() {
        let err = ProtocolError::UnknownOpcode(0xfe);
        assert_eq!(err.to_string(), "Unknown packet opcode: 0xfe");
    }
}
