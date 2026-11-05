pub mod client;
pub mod error;
pub mod framed;

pub use self::client::SingularityClient;
pub use self::error::{ClientError, Result};
pub use singularity_protocol::{AuthProgressEvent, FeatureState, FeatureStatus, IpcRequest, IpcResponse};
