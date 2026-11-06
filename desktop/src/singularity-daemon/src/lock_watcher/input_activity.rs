use std::fs;
use std::os::unix::ffi::OsStrExt;
use std::sync::atomic::{AtomicU64, Ordering};
use std::sync::Arc;
use std::time::{Duration, Instant};

const EV_KEY: u16 = 1;
const EV_REL: u16 = 2;
const EV_ABS: u16 = 3;

#[repr(C)]
struct RawInputEvent {
    time: libc::timeval,
    type_: u16,
    code: u16,
    value: i32,
}

#[derive(Clone)]
pub struct InputActivityTracker {
    last_activity_millis: Arc<AtomicU64>,
    start_instant: Instant,
}

impl InputActivityTracker {
    pub fn start() -> Self {
        let tracker = Self {
            last_activity_millis: Arc::new(AtomicU64::new(0)),
            start_instant: Instant::now(),
        };
        let t_clone = tracker.clone();
        std::thread::Builder::new()
            .name("input-activity-tracker".into())
            .spawn(move || run_poll_loop(t_clone))
            .ok();
        tracker
    }

    pub fn idle_duration(&self) -> Duration {
        let last_ms = self.last_activity_millis.load(Ordering::Relaxed);
        let now_ms = self.start_instant.elapsed().as_millis() as u64;
        Duration::from_millis(now_ms.saturating_sub(last_ms))
    }

    pub fn record_activity(&self) {
        let now_ms = self.start_instant.elapsed().as_millis() as u64;
        self.last_activity_millis.store(now_ms, Ordering::Relaxed);
    }
}

fn run_poll_loop(tracker: InputActivityTracker) {
    let mut fds = open_input_fds();
    let mut last_scan = Instant::now();
    let ev_size = std::mem::size_of::<RawInputEvent>();
    let mut buf = [0u8; std::mem::size_of::<RawInputEvent>()];

    loop {
        if fds.is_empty() || last_scan.elapsed() >= Duration::from_secs(30) {
            close_fds(&fds);
            fds = open_input_fds();
            last_scan = Instant::now();
            if fds.is_empty() {
                std::thread::sleep(Duration::from_secs(5));
                continue;
            }
        }

        let mut p_fds: Vec<libc::pollfd> = fds
            .iter()
            .map(|&fd| libc::pollfd { fd, events: libc::POLLIN, revents: 0 })
            .collect();

        let ret = unsafe { libc::poll(p_fds.as_mut_ptr(), p_fds.len() as libc::nfds_t, 1000) };
        if ret > 0 {
            for pfd in &p_fds {
                if (pfd.revents & libc::POLLIN) != 0 {
                    let n = unsafe { libc::read(pfd.fd, buf.as_mut_ptr() as *mut libc::c_void, ev_size) };
                    if n == ev_size as isize {
                        let ev: RawInputEvent = unsafe { std::ptr::read(buf.as_ptr() as *const _) };
                        if ev.type_ == EV_KEY || ev.type_ == EV_REL || ev.type_ == EV_ABS {
                            tracker.record_activity();
                        }
                    }
                }
            }
        }
    }
}

fn open_input_fds() -> Vec<i32> {
    let mut fds = Vec::new();
    let entries = match fs::read_dir("/dev/input") {
        Ok(e) => e,
        Err(_) => return fds,
    };
    for entry in entries.flatten() {
        let name = entry.file_name();
        if name.to_string_lossy().starts_with("event") {
            let path = entry.path();
            let mut c_path = path.as_os_str().as_bytes().to_vec();
            c_path.push(0);
            let fd = unsafe { libc::open(c_path.as_ptr() as *const libc::c_char, libc::O_RDONLY | libc::O_NONBLOCK) };
            if fd >= 0 {
                fds.push(fd);
            }
        }
    }
    fds
}

fn close_fds(fds: &[i32]) {
    for &fd in fds {
        unsafe { libc::close(fd); }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_record_activity_resets_idle_duration() {
        let tracker = InputActivityTracker {
            last_activity_millis: Arc::new(AtomicU64::new(0)),
            start_instant: Instant::now(),
        };
        std::thread::sleep(Duration::from_millis(20));
        assert!(tracker.idle_duration().as_millis() >= 15);
        tracker.record_activity();
        assert!(tracker.idle_duration().as_millis() < 10);
    }
}

