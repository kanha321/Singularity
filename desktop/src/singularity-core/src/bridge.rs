pub mod app;
pub mod device;
pub mod root;
pub mod shell;

pub use self::app::AppBridge;
pub use self::device::DeviceBridge;
pub use self::root::RootBridge;
pub use self::shell::ShellBridge;

use async_trait::async_trait;
use crate::error::Result;

pub use singularity_protocol::DeviceInfo;

/// Common lifecycle trait for all specialized child bridges.
#[async_trait]
pub trait Bridge: Send + Sync {
    /// Human-readable identifier for logging and diagnostics (e.g. "RootBridge")
    fn name(&self) -> &'static str;

    /// Verifies whether the underlying transport, process, or subsystem is operational
    async fn is_healthy(&self) -> bool;

    /// Gracefully tears down connections, forwarders, or spawned processes
    async fn shutdown(&self) -> Result<()>;
}
