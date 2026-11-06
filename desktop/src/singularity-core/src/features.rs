pub mod biometric;
pub mod browser;
pub mod calling;
pub mod camera;
pub mod clipboard;
pub mod storage;

pub use self::biometric::BiometricFeature;
pub use self::calling::CallingFeature;
pub use self::camera::CameraFeature;
pub use self::clipboard::ClipboardFeature;
pub use self::storage::StorageFeature;
