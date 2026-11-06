use super::hub::PhoneMediaHub;
use super::player::{PhonePlayerInterface, PhoneRootInterface};
use zbus::connection::Connection;

pub struct PhoneMediaServer {
    _conn: Connection,
    hub: PhoneMediaHub,
}

impl PhoneMediaServer {
    pub async fn start(hub: PhoneMediaHub) -> Result<Self, zbus::Error> {
        let root = PhoneRootInterface;
        let player = PhonePlayerInterface { hub: hub.clone() };

        let conn = zbus::connection::Builder::session()?
            .name("org.mpris.MediaPlayer2.singularity")?
            .serve_at("/org/mpris/MediaPlayer2", root)?
            .serve_at("/org/mpris/MediaPlayer2", player)?
            .build()
            .await?;

        Ok(Self { _conn: conn, hub })
    }

    pub async fn notify_changed(&self) -> Result<(), zbus::Error> {
        let iface = self._conn.object_server().interface::<_, PhonePlayerInterface>("/org/mpris/MediaPlayer2").await?;
        let emitter = iface.signal_emitter();
        let player = iface.get().await;
        player.playback_status_changed(emitter).await?;
        player.metadata_changed(emitter).await?;
        player.can_play_changed(emitter).await?;
        player.can_pause_changed(emitter).await?;
        player.can_go_next_changed(emitter).await?;
        player.can_go_previous_changed(emitter).await?;
        player.can_seek_changed(emitter).await?;
        Ok(())
    }

    pub async fn update_and_notify(&self, media: Option<singularity_protocol::MediaState>) {
        self.hub.update_media(media).await;
        let _ = self.notify_changed().await;
    }

    #[allow(dead_code)]
    pub fn hub(&self) -> &PhoneMediaHub {
        &self.hub
    }
}
