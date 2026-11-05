use tokio::net::UnixStream;
use tokio::io::{AsyncReadExt, AsyncWriteExt};
use singularity_protocol::{IpcRequest, IpcResponse};
use crate::error::Result;

pub async fn read_framed(stream: &mut UnixStream) -> Result<IpcResponse> {
    let mut len_buf = [0u8; 4];
    stream.read_exact(&mut len_buf).await?;
    let len = u32::from_be_bytes(len_buf) as usize;
    let mut buf = vec![0u8; len];
    stream.read_exact(&mut buf).await?;
    Ok(serde_json::from_slice(&buf)?)
}

pub async fn write_framed(stream: &mut UnixStream, req: &IpcRequest) -> Result<()> {
    let payload = serde_json::to_vec(req)?;
    stream.write_all(&(payload.len() as u32).to_be_bytes()).await?;
    stream.write_all(&payload).await?;
    stream.flush().await?;
    Ok(())
}

