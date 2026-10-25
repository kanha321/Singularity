use std::ffi::CStr;
use std::os::raw::{c_char, c_int};

/// # Safety
/// Caller must ensure `argv` points to valid memory or is null.
pub unsafe fn parse_timeout(argc: c_int, argv: *const *const c_char, default_timeout: u32) -> u32 {
    if argc <= 0 || argv.is_null() {
        return default_timeout;
    }
    for i in 0..argc as isize {
        let arg_ptr = unsafe { *argv.offset(i) };
        if !arg_ptr.is_null() {
            let s = unsafe { CStr::from_ptr(arg_ptr) }.to_string_lossy();
            if let Some(val) = s.strip_prefix("timeout=") {
                if let Ok(num) = val.parse::<u32>() {
                    return num;
                }
            }
        }
    }
    default_timeout
}

#[cfg(test)]
mod tests {
    use super::*;
    use std::ffi::CString;

    #[test]
    fn parse_timeout_finds_value() {
        let arg = CString::new("timeout=15").unwrap();
        let argv = [arg.as_ptr()];
        assert_eq!(unsafe { parse_timeout(1, argv.as_ptr(), 10) }, 15);
    }

    #[test]
    fn parse_timeout_uses_default() {
        assert_eq!(unsafe { parse_timeout(0, std::ptr::null(), 10) }, 10);
    }
}
