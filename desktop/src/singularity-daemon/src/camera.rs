pub mod audio;
pub mod process;
pub mod supervisor;
pub mod v4l2;

#[allow(unused_imports)]
pub use self::audio::{setup_virtual_microphone, teardown_virtual_microphone, VirtualMicHandles};
#[allow(unused_imports)]
pub use self::process::{build_scrcpy_args, spawn_scrcpy_stream};
pub use self::supervisor::CameraSupervisor;
#[allow(unused_imports)]
pub use self::v4l2::{check_device_exists, count_device_readers, count_external_readers};
