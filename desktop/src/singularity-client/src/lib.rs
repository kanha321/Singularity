pub mod camera;
pub mod client;
pub mod device;
pub mod error;
pub mod framed;
pub mod notification;

pub use self::client::SingularityClient;
pub use self::error::{ClientError, Result};
pub use singularity_protocol::{AuthProgressEvent, DeviceEntry, FeatureState, FeatureStatus, IpcRequest, IpcResponse};
