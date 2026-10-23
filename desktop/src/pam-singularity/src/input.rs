use std::collections::HashSet;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::Duration;

const EV_KEY: u16 = 1;
const KEY_ESC: u16 = 1;
const KEY_ENTER: u16 = 28;
const KEY_KPENTER: u16 = 96;

/// Watches for user cancel key (Enter/Esc) via /dev/tty (CLI) or evdev (GUI greeter).
pub struct KeyboardWatcher {
    stop: Arc<AtomicBool>,
}

impl KeyboardWatcher {
    pub fn new() -> Self {
        Self {
            stop: Arc::new(AtomicBool::new(false)),
        }
    }

    pub async fn wait_for_key(&self) {
        let stop = Arc::clone(&self.stop);
        let _ = tokio::task::spawn_blocking(move || {
            let is_cli = unsafe { libc::isatty(libc::STDIN_FILENO) } == 1;
            let mut raw_fds = Vec::new();

            if is_cli {
                let tty_fd = unsafe {
                    libc::open(b"/dev/tty\0".as_ptr() as *const libc::c_char, libc::O_RDONLY | libc::O_NONBLOCK)
                };
                if tty_fd >= 0 {
                    raw_fds.push((tty_fd, true));
                }
            } else if let Ok(entries) = std::fs::read_dir("/dev/input/by-path") {
                let mut seen = HashSet::new();
                for entry in entries.flatten() {
                    let path = entry.path();
                    let name = path.file_name().and_then(|n| n.to_str()).unwrap_or("");
                    if name.ends_with("-event-kbd") {
                        if let Ok(real) = std::fs::canonicalize(&path) {
                            if seen.insert(real.clone()) {
                                if let Ok(c_path) = std::ffi::CString::new(real.to_string_lossy().as_bytes()) {
                                    let fd = unsafe {
                                        libc::open(c_path.as_ptr(), libc::O_RDONLY | libc::O_NONBLOCK)
                                    };
                                    if fd >= 0 {
                                        drain_evdev(fd);
                                        raw_fds.push((fd, false));
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if raw_fds.is_empty() {
                while !stop.load(Ordering::Relaxed) {
                    std::thread::sleep(Duration::from_millis(150));
                }
                return;
            }

            let mut pollfds: Vec<libc::pollfd> = raw_fds
                .iter()
                .map(|&(fd, _)| libc::pollfd { fd, events: libc::POLLIN, revents: 0 })
                .collect();

            while !stop.load(Ordering::Relaxed) {
                let ret = unsafe { libc::poll(pollfds.as_mut_ptr(), pollfds.len() as libc::nfds_t, 150) };
                if ret > 0 {
                    for (i, pfd) in pollfds.iter().enumerate() {
                        if (pfd.revents & libc::POLLIN) != 0 {
                            let (fd, is_tty) = raw_fds[i];
                            if is_tty {
                                let mut buf = [0u8; 1];
                                if unsafe { libc::read(fd, buf.as_mut_ptr() as *mut libc::c_void, 1) } > 0 {
                                    cleanup_fds(&raw_fds);
                                    return;
                                }
                            } else if check_evdev_cancel_key(fd) {
                                cleanup_fds(&raw_fds);
                                return;
                            }
                        }
                    }
                }
            }

            cleanup_fds(&raw_fds);
        })
        .await;
    }
}

fn drain_evdev(fd: libc::c_int) {
    let mut buf = [0u8; 24];
    while unsafe { libc::read(fd, buf.as_mut_ptr() as *mut libc::c_void, 24) } == 24 {}
}

fn check_evdev_cancel_key(fd: libc::c_int) -> bool {
    let mut buf = [0u8; 24];
    while unsafe { libc::read(fd, buf.as_mut_ptr() as *mut libc::c_void, 24) } == 24 {
        let type_ = u16::from_ne_bytes([buf[16], buf[17]]);
        let code = u16::from_ne_bytes([buf[18], buf[19]]);
        let val = i32::from_ne_bytes([buf[20], buf[21], buf[22], buf[23]]);
        if type_ == EV_KEY && val == 1 && (code == KEY_ENTER || code == KEY_KPENTER || code == KEY_ESC) {
            return true;
        }
    }
    false
}

fn cleanup_fds(fds: &[(libc::c_int, bool)]) {
    for &(fd, _) in fds {
        unsafe { libc::close(fd); }
    }
}

impl Drop for KeyboardWatcher {
    fn drop(&mut self) {
        self.stop.store(true, Ordering::Relaxed);
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[tokio::test]
    async fn watcher_stops_cleanly_on_drop() {
        let watcher = KeyboardWatcher::new();
        assert!(!watcher.stop.load(Ordering::Relaxed));
        drop(watcher);
    }
}
