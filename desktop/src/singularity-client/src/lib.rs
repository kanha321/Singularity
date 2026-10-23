pub mod client;
pub mod error;

pub use self::client::SingularityClient;
pub use self::error::{ClientError, Result};
pub use singularity_protocol::{FeatureState, FeatureStatus, IpcRequest, IpcResponse};
