/// Deterministic canonical payload builder matching Android CanonicalPayload.
pub fn build_canonical_payload(
    nonce: &[u8],
    timestamp_secs: u64,
    counter: u64,
    service: &str,
    user: &str,
) -> Vec<u8> {
    let mut buf = Vec::with_capacity(64 + nonce.len() + service.len() + user.len());

    buf.extend_from_slice(b"SINGULARITY_AUTH_V1");
    buf.extend_from_slice(&(nonce.len() as u32).to_be_bytes());
    buf.extend_from_slice(nonce);
    buf.extend_from_slice(&timestamp_secs.to_be_bytes());
    buf.extend_from_slice(&counter.to_be_bytes());

    let s_bytes = service.as_bytes();
    buf.extend_from_slice(&(s_bytes.len() as u16).to_be_bytes());
    buf.extend_from_slice(s_bytes);

    let u_bytes = user.as_bytes();
    buf.extend_from_slice(&(u_bytes.len() as u16).to_be_bytes());
    buf.extend_from_slice(u_bytes);

    buf
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn canonical_payload_has_correct_prefix_and_structure() {
        let nonce = [7u8; 32];
        let payload = build_canonical_payload(&nonce, 1731411200, 1, "sudo", "kanhaji");
        assert!(payload.starts_with(b"SINGULARITY_AUTH_V1"));
        assert_eq!(payload.len(), 19 + 4 + 32 + 8 + 8 + 2 + 4 + 2 + 7);
    }
}

