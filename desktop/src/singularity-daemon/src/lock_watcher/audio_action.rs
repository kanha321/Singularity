use singularity_protocol::{ActiveAudioRouteInfo, AudioRouteCommand, AudioSinkInfo};

pub async fn handle_audio_action(
    cmd: &AudioRouteCommand,
    audio_worker: &mut crate::audio::AudioWorker,
    serial: &str,
    cached_sinks: &mut [AudioSinkInfo],
) {
    tracing::info!(target: "lock_watcher", ?cmd, "Executing audio command from phone");
    match cmd.action.as_str() {
        "set_volume" => {
            if let (Some(ref sink), Some(vol)) = (&cmd.target_sink, cmd.volume) {
                let _ = crate::audio::set_sink_volume(sink, vol).await;
                if let Some(s) = cached_sinks.iter_mut().find(|s| s.name == *sink) {
                    s.volume = vol;
                }
            }
        }
        "set_mute" => {
            if let (Some(ref sink), Some(mute)) = (&cmd.target_sink, cmd.mute) {
                let _ = crate::audio::set_sink_mute(sink, mute).await;
                if let Some(s) = cached_sinks.iter_mut().find(|s| s.name == *sink) {
                    s.mute = mute;
                }
            }
        }
        "set_route" => {
            match cmd.target_sink.as_deref() {
                Some("phone") | None => {
                    audio_worker.stop().await;
                    crate::audio::worker::cleanup_remote_audio_server(serial).await;
                    let def_sink = crate::audio::router::get_default_hardware_sink().await;
                    let _ = tokio::process::Command::new("pactl").args(["set-default-sink", &def_sink]).status().await;
                    let _ = crate::audio::migrate_sink_inputs("singularity_phone_sink", &def_sink).await;
                }
                Some("singularity_phone_sink") => {
                    audio_worker.stop().await;
                    let _ = crate::audio::ensure_phone_sink().await;
                    let _ = tokio::process::Command::new("pactl").args(["set-default-sink", "singularity_phone_sink"]).status().await;
                }
                Some(target_sink) => {
                    let _ = tokio::process::Command::new("pactl").args(["set-default-sink", target_sink]).status().await;
                    let _ = crate::audio::migrate_sink_inputs("singularity_phone_sink", target_sink).await;
                    let _ = audio_worker.set_target_sink(serial, target_sink).await;
                }
            }
        }
        "start_laptop_stream" => {
            audio_worker.stop().await;
            let _ = crate::audio::ensure_phone_sink().await;
            let _ = tokio::process::Command::new("pactl").args(["set-default-sink", "singularity_phone_sink"]).status().await;
        }
        _ => {}
    }
}

pub async fn compute_active_audio_route(
    audio_worker_sink: Option<&str>,
    cached_sinks: &[AudioSinkInfo],
    phone_media: Option<&singularity_protocol::MediaState>,
) -> Option<ActiveAudioRouteInfo> {
    if let Some((sink_name, app_name)) = crate::audio::router::query_active_desktop_stream().await {
        if sink_name == "singularity_phone_sink" {
            return Some(ActiveAudioRouteInfo {
                output_device_name: "singularity_phone_sink".into(),
                output_device_label: "Phone Speaker (Laptop Audio)".into(),
                source_app: Some(app_name),
                source_origin: "laptop".into(),
                is_playing: true,
            });
        }
        let label = cached_sinks.iter().find(|s| s.name == sink_name)
            .map(|s| format!("Laptop: {}", s.nick))
            .unwrap_or_else(|| format!("Laptop: {}", sink_name));
        return Some(ActiveAudioRouteInfo {
            output_device_name: sink_name,
            output_device_label: label,
            source_app: Some(app_name),
            source_origin: "laptop".into(),
            is_playing: true,
        });
    }

    if let Some(target_sink) = audio_worker_sink {
        let label = cached_sinks.iter().find(|s| s.name == target_sink)
            .map(|s| format!("Laptop: {}", s.nick))
            .unwrap_or_else(|| format!("Laptop: {}", target_sink));
        let app = phone_media.map(|m| m.player.clone()).filter(|p| !p.is_empty());
        return Some(ActiveAudioRouteInfo {
            output_device_name: target_sink.to_string(),
            output_device_label: label,
            source_app: app,
            source_origin: "phone".into(),
            is_playing: true,
        });
    }

    if let Some(pm) = phone_media {
        if pm.status.eq_ignore_ascii_case("playing") {
            let app = if pm.player.is_empty() { None } else { Some(pm.player.clone()) };
            return Some(ActiveAudioRouteInfo {
                output_device_name: "phone".into(),
                output_device_label: "This Phone".into(),
                source_app: app,
                source_origin: "phone".into(),
                is_playing: true,
            });
        }
    }

    None
}
