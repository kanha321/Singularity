pub mod caelestia;
pub mod dms;
pub mod hyprlock;
pub mod logind;

pub use self::caelestia::CaelestiaAdapter;
pub use self::dms::DmsAdapter;
pub use self::hyprlock::HyprlockAdapter;
pub use self::logind::LogindAdapter;

