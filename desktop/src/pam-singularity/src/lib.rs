use std::ffi::CStr;
use std::io::Write;
use std::os::raw::{c_char, c_int};
use singularity_client::SingularityClient;

mod args;
mod input;
pub use args::parse_timeout;
pub use input::KeyboardWatcher;

pub const PAM_SUCCESS: c_int = 0;
pub const PAM_AUTH_ERR: c_int = 7;
pub const PAM_IGNORE: c_int = 25;

#[repr(C)]
pub struct PamHandle {
    _opaque: [u8; 0],
}

pub const PAM_SERVICE: c_int = 1;

extern "C" {
    fn pam_get_user(
        pamh: *const PamHandle,
        user: *mut *const c_char,
        prompt: *const c_char,
    ) -> c_int;

    fn pam_get_item(
        pamh: *const PamHandle,
        item_type: c_int,
        item: *mut *const std::ffi::c_void,
    ) -> c_int;
}

/// Authenticates the user using Singularity proximity biometrics or keyboard fallback.
///
/// # Safety
///
/// Must be called with a valid `PamHandle` pointer provided by the Linux PAM library.
#[no_mangle]
pub unsafe extern "C" fn pam_sm_authenticate(
    pamh: *mut PamHandle,
    _flags: c_int,
    argc: c_int,
    argv: *const *const c_char,
) -> c_int {
    let mut user_ptr: *const c_char = std::ptr::null();
    let res = pam_get_user(pamh, &mut user_ptr, std::ptr::null());
    if res != PAM_SUCCESS || user_ptr.is_null() {
        return PAM_IGNORE;
    }

    let mut service_ptr: *const std::ffi::c_void = std::ptr::null();
    let s_res = pam_get_item(pamh, PAM_SERVICE, &mut service_ptr);
    let service = if s_res == PAM_SUCCESS && !service_ptr.is_null() {
        CStr::from_ptr(service_ptr as *const c_char).to_string_lossy().to_string()
    } else {
        "sudo".to_string()
    };

    let username = CStr::from_ptr(user_ptr).to_string_lossy().to_string();
    let timeout = parse_timeout(argc, argv, 15).max(10);

    let rt = match tokio::runtime::Builder::new_current_thread()
        .enable_all()
        .build()
    {
        Ok(rt) => rt,
        Err(_) => return PAM_IGNORE,
    };

    rt.block_on(async move {
        authenticate_user(&username, &service, timeout).await
    })
}

async fn authenticate_user(username: &str, service: &str, timeout_secs: u32) -> c_int {
    let client = SingularityClient::new();
    let reason = format!("{}: {}", service, username);
    let watcher = KeyboardWatcher::new();

    eprint!("\r📱 Touch fingerprint on POCO F5 (or press Enter for password)... ");
    let _ = std::io::stderr().flush();

    tokio::select! {
        auth_res = client.authenticate_biometric_streaming(&reason, timeout_secs, |event| {
            match event {
                singularity_client::AuthProgressEvent::Ready => {
                    eprint!("\r\x1b[2K📱 Touch fingerprint on POCO F5 (or press Enter for password)... ");
                    let _ = std::io::stderr().flush();
                }
                singularity_client::AuthProgressEvent::FingerMismatch { attempt } => {
                    eprint!("\r\x1b[2K📱 Touch fingerprint on POCO F5... \x1b[33m❌ Try again (Attempt {}/5)\x1b[0m", attempt);
                    let _ = std::io::stderr().flush();
                }
                singularity_client::AuthProgressEvent::SensorLockout { duration_secs, .. } => {
                    eprintln!("\r\x1b[2K📱 Sensor locked out for {}s. Switching to password...", duration_secs);
                }
                singularity_client::AuthProgressEvent::Cancelled => {
                    eprintln!("\r\x1b[2K📱 Biometric authentication canceled on phone.");
                }
            }
        }) => {
            if let Ok(true) = auth_res {
                eprintln!("\r\x1b[2K📱 Touch fingerprint on POCO F5... \x1b[32m✔ Approved!\x1b[0m");
                PAM_SUCCESS
            } else {
                eprintln!("\r\x1b[2K📱 Biometric authentication canceled or timed out.");
                PAM_IGNORE
            }
        }
        _ = watcher.wait_for_key() => {
            eprintln!("\r\x1b[2K📱 Switched to password prompt.");
            let _ = client.cancel_biometric().await;
            PAM_IGNORE
        }
    }
}

/// Sets PAM credentials (no-op for Singularity biometric module).
///
/// # Safety
///
/// Must be called with a valid `PamHandle` pointer provided by the Linux PAM library.
#[no_mangle]
pub unsafe extern "C" fn pam_sm_setcred(
    _pamh: *mut PamHandle,
    _flags: c_int,
    _argc: c_int,
    _argv: *const *const c_char,
) -> c_int {
    PAM_SUCCESS
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn pam_constants_are_standard() {
        assert_eq!(PAM_SUCCESS, 0);
        assert_eq!(PAM_AUTH_ERR, 7);
        assert_eq!(PAM_IGNORE, 25);
    }
}
