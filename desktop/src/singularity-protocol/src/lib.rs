pub mod camera;
pub mod device;
pub mod error;
pub mod event;
pub mod ipc;
pub mod media;
pub mod scrcpy;
pub mod status;

pub use self::camera::{CameraConfig, CameraLens, CameraResolution, CameraStatus};
pub use self::device::{DeviceEntry, DeviceInfo};
pub use self::error::{ProtocolError, Result};
pub use self::event::DeviceEvent;
pub use self::ipc::{AuthProgressEvent, IpcEvent, IpcRequest, IpcResponse};
pub use self::media::{MediaAction, MediaState};
pub use self::scrcpy::{
    decode_device_msg, encode_set_clipboard, ScrcpyDeviceMsg,
    TYPE_ACK_CLIPBOARD, TYPE_CLIPBOARD_EVENT, TYPE_SET_CLIPBOARD,
};
pub use self::status::{
    CommandResponse, FeatureCommand, FeatureRequirements, FeatureState, FeatureStatus,
    RequirementLevel,
};
