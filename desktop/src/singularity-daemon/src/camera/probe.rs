use std::collections::HashMap;
use std::sync::Mutex;
use singularity_protocol::CameraLens;

#[derive(Debug, Clone, PartialEq)]
pub struct DiscoveredCamera {
    pub id: u32,
    pub facing: String,
    pub resolution: (u32, u32),
    pub focal_length: f32,
    pub fov_degrees: f32,
    pub is_logical: bool,
}

static CAMERA_CACHE: Mutex<Option<HashMap<String, Vec<DiscoveredCamera>>>> = Mutex::new(None);

pub fn parse_dumpsys_cameras(output: &str) -> Vec<DiscoveredCamera> {
    let mut list = Vec::new();
    for sec in output.split("== Camera HAL device ").skip(1) {
        let Some((id_str, rest)) = sec.split_once(' ') else { continue; };
        let id_clean: String = id_str.rsplit('/').next().unwrap_or(id_str).chars().filter(|c| c.is_ascii_digit()).collect();
        let Ok(id) = id_clean.parse::<u32>() else { continue; };
        if list.iter().any(|c: &DiscoveredCamera| c.id == id) { continue; }

        let facing = if rest.contains("Facing: Front") { "front" } else { "back" }.to_string();
        let is_logical = rest.contains("LOGICAL_MULTI_CAMERA");
        let focal = extract_val_tokens(rest, "android.lens.info.availableFocalLengths").first().and_then(|x| x.parse().ok()).unwrap_or(0.0);
        let phys_tokens = extract_val_tokens(rest, "android.sensor.info.physicalSize");
        let phys_w = phys_tokens.first().and_then(|x| x.parse().ok()).unwrap_or(0.0f32);
        let pix_tokens = extract_val_tokens(rest, "android.sensor.info.pixelArraySize");
        let (pix_w, pix_h) = if pix_tokens.len() >= 2 { (pix_tokens[0].parse().unwrap_or(0), pix_tokens[1].parse().unwrap_or(0)) } else { (0, 0) };
        let act_tokens = extract_val_tokens(rest, "android.sensor.info.activeArraySize");
        let (act_l, act_w) = if act_tokens.len() >= 4 { (act_tokens[0].parse().unwrap_or(0), act_tokens[2].parse().unwrap_or(0)) } else { (0, 0) };

        let res = if pix_w > 0 && pix_h > 0 { (pix_w, pix_h) } else { (1920, 1080) };
        let mut fov = 0.0f32;
        if phys_w > 0.0 && act_w > act_l && pix_w > 0 && focal > 0.0 {
            let frac_x = (act_w - act_l) as f32 / pix_w as f32;
            fov = 2.0 * ((phys_w * frac_x) / (2.0 * focal)).atan().to_degrees();
        }
        list.push(DiscoveredCamera { id, facing, resolution: res, focal_length: focal, fov_degrees: fov, is_logical });
    }
    list
}

pub fn resolve_lens_id(cameras: &[DiscoveredCamera], lens: CameraLens) -> u32 {
    match lens {
        CameraLens::Custom(id) => id,
        CameraLens::Front => cameras.iter()
            .find(|c| c.facing == "front" && !c.is_logical)
            .or_else(|| cameras.iter().find(|c| c.facing == "front"))
            .map(|c| c.id).unwrap_or(1),
        CameraLens::Ultrawide => cameras.iter()
            .find(|c| c.facing == "back" && !c.is_logical && c.fov_degrees > 85.0)
            .or_else(|| cameras.iter().find(|c| c.facing == "back" && !c.is_logical && c.focal_length > 0.0 && c.focal_length <= 2.2))
            .or_else(|| cameras.iter().filter(|c| c.facing == "back" && !c.is_logical).nth(1))
            .map(|c| c.id)
            .unwrap_or_else(|| cameras.iter().find(|c| c.facing == "back").map(|c| c.id).unwrap_or(0)),
        CameraLens::Main => cameras.iter()
            .filter(|c| c.facing == "back" && !c.is_logical && (c.fov_degrees <= 85.0 || c.focal_length >= 2.5))
            .max_by_key(|c| c.resolution.0 * c.resolution.1)
            .or_else(|| cameras.iter().find(|c| c.facing == "back"))
            .map(|c| c.id).unwrap_or(0),
    }
}

fn extract_val_tokens<'a>(s: &'a str, tag: &str) -> Vec<&'a str> {
    let Some(i) = s.find(tag) else { return Vec::new(); };
    let Some(nl) = s[i..].find('\n') else { return Vec::new(); };
    let val_sub = &s[i + nl + 1..];
    let Some(b) = val_sub.find('[') else { return Vec::new(); };
    let Some(e) = val_sub[b + 1..].find(']') else { return Vec::new(); };
    val_sub[b + 1..b + 1 + e].split_whitespace().collect()
}

pub async fn probe_device_cameras(serial: &str) -> Vec<DiscoveredCamera> {
    if serial.is_empty() || serial == "auto" { return Vec::new(); }
    if let Ok(guard) = CAMERA_CACHE.lock() {
        if let Some(cached) = guard.as_ref().and_then(|m| m.get(serial)) { return cached.clone(); }
    }
    let res = if let Ok(out) = tokio::process::Command::new("adb")
        .args(["-s", serial, "shell", "dumpsys", "media.camera"]).output().await {
        parse_dumpsys_cameras(&String::from_utf8_lossy(&out.stdout))
    } else { Vec::new() };

    let cameras = if !res.is_empty() { res } else {
        let Ok(out) = tokio::process::Command::new("scrcpy")
            .args(["-s", serial, "--video-source=camera", "--list-cameras"]).output().await else { return Vec::new(); };
        parse_scrcpy_fallback(&String::from_utf8_lossy(&out.stdout))
    };
    if !cameras.is_empty() {
        if let Ok(mut guard) = CAMERA_CACHE.lock() { guard.get_or_insert_with(HashMap::new).insert(serial.to_string(), cameras.clone()); }
    }
    cameras
}

pub fn parse_scrcpy_fallback(output: &str) -> Vec<DiscoveredCamera> {
    output.lines().filter_map(|l| {
        let t = l.trim();
        if !t.starts_with("--camera-id=") { return None; }
        let id: u32 = t["--camera-id=".len()..].chars().take_while(|c| c.is_ascii_digit()).collect::<String>().parse().ok()?;
        let facing = if t.contains("front") { "front" } else { "back" }.to_string();
        let is_logical = t.contains("zoom-range=[0.");
        let res = t.split(|c: char| c == '(' || c == ')' || c == ',' || c == ' ')
            .find(|tok| tok.contains('x') && tok.replace('x', "").chars().all(|c| c.is_ascii_digit()))
            .and_then(|tok| {
                let (w, h) = tok.split_once('x')?;
                Some((w.parse().ok()?, h.parse().ok()?))
            }).unwrap_or((1920, 1080));
        Some(DiscoveredCamera { id, facing, resolution: res, focal_length: 0.0, fov_degrees: 0.0, is_logical })
    }).collect()
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_resolve_cameras_with_fov() {
        let cams = vec![
            DiscoveredCamera { id: 0, facing: "back".into(), resolution: (4624, 3472), focal_length: 4.68, fov_degrees: 69.5, is_logical: false },
            DiscoveredCamera { id: 1, facing: "front".into(), resolution: (2304, 1728), focal_length: 3.26, fov_degrees: 70.5, is_logical: false },
            DiscoveredCamera { id: 2, facing: "back".into(), resolution: (3280, 2464), focal_length: 1.65, fov_degrees: 96.1, is_logical: false },
            DiscoveredCamera { id: 4, facing: "back".into(), resolution: (4624, 3472), focal_length: 4.68, fov_degrees: 69.5, is_logical: true },
        ];
        assert_eq!(resolve_lens_id(&cams, CameraLens::Main), 0);
        assert_eq!(resolve_lens_id(&cams, CameraLens::Front), 1);
        assert_eq!(resolve_lens_id(&cams, CameraLens::Ultrawide), 2);
    }
}
