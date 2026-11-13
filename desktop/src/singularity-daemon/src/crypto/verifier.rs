use base64::engine::general_purpose::STANDARD as BASE64;
use base64::Engine;
use p256::ecdsa::signature::Verifier;
use p256::ecdsa::{Signature, VerifyingKey};
use p256::pkcs8::DecodePublicKey;

#[derive(Debug, thiserror::Error)]
pub enum CryptoVerifyError {
    #[error("Base64 decode error: {0}")]
    Base64(#[from] base64::DecodeError),
    #[error("Public key parse error: {0}")]
    PublicKey(String),
    #[error("Signature parse error: {0}")]
    Signature(String),
    #[error("Cryptographic signature verification failed")]
    VerificationFailed,
}

pub fn verify_signature(
    public_key_base64: &str,
    data: &[u8],
    signature_base64: &str,
) -> Result<(), CryptoVerifyError> {
    let pub_bytes = BASE64.decode(public_key_base64)?;
    let verifying_key = VerifyingKey::from_public_key_der(&pub_bytes)
        .map_err(|e| CryptoVerifyError::PublicKey(e.to_string()))?;

    let sig_bytes = BASE64.decode(signature_base64)?;
    let signature = Signature::from_der(&sig_bytes)
        .map_err(|e| CryptoVerifyError::Signature(e.to_string()))?;

    verifying_key
        .verify(data, &signature)
        .map_err(|_| CryptoVerifyError::VerificationFailed)
}

#[cfg(test)]
mod tests {
    use super::*;
    use p256::ecdsa::signature::Signer;
    use p256::ecdsa::SigningKey;
    use p256::pkcs8::EncodePublicKey;
    use rand::rngs::OsRng;

    #[test]
    fn verify_signature_succeeds_for_valid_keypair() {
        let signing_key = SigningKey::random(&mut OsRng);
        let verifying_key = signing_key.verifying_key();

        let pub_doc = verifying_key.to_public_key_der().unwrap();
        let pub_base64 = BASE64.encode(pub_doc.as_bytes());

        let message = b"test biometric challenge message";
        let signature: Signature = signing_key.sign(message);
        let sig_der = signature.to_der();
        let sig_base64 = BASE64.encode(sig_der.as_bytes());

        assert!(verify_signature(&pub_base64, message, &sig_base64).is_ok());

        // Mismatched data fails verification
        assert!(verify_signature(&pub_base64, b"tampered message", &sig_base64).is_err());
    }
}

