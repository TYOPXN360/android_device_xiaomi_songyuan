//
// SPDX-FileCopyrightText: The LineageOS Project
// SPDX-License-Identifier: Apache-2.0
//

//! Light HAL for songyuan.
//!
//! The phone has a single AW21024 LED driver behind I2C that serves the
//! charging indicator, the notification pulse and the camera/microphone
//! privacy dots. The framework sends one state per logical light type, so this
//! HAL keeps the last state of each and decides which one the LED shows.
//!
//! The charging switch has no AOSP equivalent - BatteryService drives
//! LIGHT_ID_BATTERY unconditionally - so it is carried over as a property and
//! enforced here, which is the only place that can tell the light types apart.
//! The notification switch is not handled here: NotificationAttentionHelper
//! already reads Settings.System.NOTIFICATION_LIGHT_PULSE and stops sending
//! NOTIFICATIONS states when it is off.

use std::ffi::{c_char, c_int, CString};
use std::fs;
use std::sync::{Arc, Mutex};
use std::thread;
use std::time::Duration;

use binder::BinderFeatures;
use log::{error, info};

use android_hardware_light::aidl::android::hardware::light::FlashMode::FlashMode;
use android_hardware_light::aidl::android::hardware::light::HwLight::HwLight;
use android_hardware_light::aidl::android::hardware::light::HwLightEffect::HwLightEffect;
use android_hardware_light::aidl::android::hardware::light::HwLightState::HwLightState;
use android_hardware_light::aidl::android::hardware::light::ILights::{BnLights, ILights};
use android_hardware_light::aidl::android::hardware::light::LightType::LightType;

const LED_PATH: &str = "/sys/class/leds/aw21024_led/";

/// Largest period the driver accepts for any of the four breathing phases.
const MAX_PERIOD_MS: u32 = 8300;

/// Fallback breathing phase when the framework asks for a hardware effect
/// without giving timings.
const BREATH_PHASE_MS: u32 = 510;

const BATTERY_PROP: &str = "persist.sys.rear_light.battery";
const MASTER_PROP: &str = "persist.sys.rear_light.enable";

extern "C" {
    fn __system_property_get(name: *const c_char, value: *mut c_char) -> c_int;
}

fn get_bool_prop(key: &str, default: bool) -> bool {
    let mut buf = [0u8; 92];
    let Ok(c_key) = CString::new(key) else {
        return default;
    };
    // SAFETY: __system_property_get writes at most PROP_VALUE_MAX (92) bytes
    // plus a NUL into the buffer, which is exactly the size allocated above,
    // and c_key is a valid NUL-terminated string for the call's duration.
    let len = unsafe { __system_property_get(c_key.as_ptr(), buf.as_mut_ptr() as *mut c_char) };
    if len <= 0 {
        return default;
    }
    let value = String::from_utf8_lossy(&buf[..len as usize]);
    match value.trim() {
        "0" => false,
        "1" => true,
        _ => default,
    }
}

/// Which physical mode the driver is in. Re-entering the same mode would reset
/// the ramp, so the current one is remembered.
#[derive(Clone, Copy, PartialEq, Eq)]
enum Mode {
    Off,
    Solid,
    Breath,
}

#[derive(Clone, Copy, Default, PartialEq, Eq)]
enum Effect {
    #[default]
    Fixed,
    Timed { on_ms: u32, off_ms: u32 },
    Hardware,
}

/// A light state reduced to what this LED can actually do.
#[derive(Clone, Copy, Default)]
struct State {
    /// 0xRRGGBB, alpha dropped.
    rgb: u32,
    effect: Effect,
}

impl State {
    fn is_lit(&self) -> bool {
        self.rgb != 0
    }
}

fn write_node(name: &str, value: &str) -> bool {
    let path = format!("{LED_PATH}{name}");
    match fs::write(&path, format!("{value}\n")) {
        Ok(()) => true,
        Err(e) => {
            error!("failed to write {path}: {e}");
            false
        }
    }
}

/// The AW21024 has sixteen channels and the driver only switches them, it does
/// not interpret a colour. The segments are wired so that writing the same
/// value to all eight reaches every channel of both dice.
struct Led {
    mode: Mode,
}

impl Led {
    fn new() -> Self {
        Self { mode: Mode::Off }
    }

    fn available() -> bool {
        fs::metadata(format!("{LED_PATH}color")).is_ok()
    }

    fn set_mode(&mut self, want: Mode) -> bool {
        if self.mode == want {
            return true;
        }
        // Any transition has to go through the disabled state, the driver
        // latches the ramp otherwise.
        if self.mode != Mode::Off {
            write_node("run", "0");
            write_node("hwen", "0");
            thread::sleep(Duration::from_millis(10));
        }
        if want == Mode::Off {
            let ok = write_node("hwen", "0");
            self.mode = Mode::Off;
            return ok;
        }
        let mut ok = write_node("hwen", "1");
        ok &= write_node("run", if want == Mode::Solid { "1" } else { "2" });
        self.mode = want;
        ok
    }

    fn apply(&mut self, state: &State) -> bool {
        if !state.is_lit() {
            return self.set_mode(Mode::Off);
        }

        let color = format!("{:06X}", state.rgb & 0xFF_FFFF);

        let breath = match state.effect {
            Effect::Hardware => true,
            Effect::Timed { on_ms, off_ms } => on_ms > 0 && off_ms > 0,
            Effect::Fixed => false,
        };

        if breath {
            let (rise, on, fall, off) = match state.effect {
                // The framework packs the four phases into two ints when they
                // do not fit the plain on/off representation.
                Effect::Timed { on_ms, off_ms } if on_ms > 0xFFFF || off_ms > 0xFFFF => (
                    ((on_ms >> 16) & 0xFFFF).min(MAX_PERIOD_MS),
                    (on_ms & 0xFFFF).min(MAX_PERIOD_MS),
                    ((off_ms >> 16) & 0xFFFF).min(MAX_PERIOD_MS),
                    (off_ms & 0xFFFF).min(MAX_PERIOD_MS),
                ),
                Effect::Timed { on_ms, off_ms } => (
                    (on_ms / 2).min(MAX_PERIOD_MS),
                    (on_ms - on_ms / 2).min(MAX_PERIOD_MS),
                    (off_ms / 2).min(MAX_PERIOD_MS),
                    (off_ms - off_ms / 2).min(MAX_PERIOD_MS),
                ),
                _ => (
                    BREATH_PHASE_MS,
                    BREATH_PHASE_MS,
                    BREATH_PHASE_MS,
                    BREATH_PHASE_MS,
                ),
            };
            let mut ok = self.set_mode(Mode::Breath);
            ok &= write_node("color", &color);
            for segment in 0..8 {
                ok &= write_node("rgbcolor", &format!("{segment} {color}"));
            }
            ok &= write_node("period", &format!("{rise} {on} {fall} {off}"));
            ok &= write_node("repeat", "1");
            ok &= write_node("brightness", "255");
            ok
        } else {
            let mut ok = self.set_mode(Mode::Solid);
            ok &= write_node("color", &color);
            for segment in 0..8 {
                ok &= write_node("rgbcolor", &format!("{segment} {color}"));
            }
            ok &= write_node("brightness", "255");
            ok
        }
    }
}

struct LightsInner {
    led: Led,
    battery: State,
    notifications: State,
    attention: State,
    microphone: State,
    camera: State,
    master_enabled: bool,
    battery_enabled: bool,
}

impl LightsInner {
    /// The camera and microphone dots are privacy indicators, they always win.
    /// After that the framework's own ordering applies, with the charging light
    /// gated on the settings switches and everything gated on the master one.
    fn recompose(&mut self) {
        let mut state = State::default();
        if !self.master_enabled {
            self.led.apply(&state);
            return;
        }
        if self.camera.is_lit() {
            state = self.camera;
        } else if self.microphone.is_lit() {
            state = self.microphone;
        } else if self.notifications.is_lit() {
            state = self.notifications;
        } else if self.attention.is_lit() {
            state = self.attention;
        } else if self.battery_enabled && self.battery.is_lit() {
            state = self.battery;
        }
        self.led.apply(&state);
    }
}

struct Lights {
    inner: Arc<Mutex<LightsInner>>,
}

fn from_aidl(value: &HwLightState) -> State {
    let effect = match value.flashMode {
        FlashMode::NONE => Effect::Fixed,
        FlashMode::TIMED => Effect::Timed {
            on_ms: value.flashOnMs.max(0) as u32,
            off_ms: value.flashOffMs.max(0) as u32,
        },
        FlashMode::HARDWARE => Effect::Hardware,
        _ => Effect::Fixed,
    };
    State {
        rgb: value.color as u32 & 0xFF_FFFF,
        effect,
    }
}

impl binder::Interface for Lights {}

impl ILights for Lights {
    fn r#setLightState(&self, id: i32, state: &HwLightState) -> binder::Result<()> {
        let parsed = from_aidl(state);
        let mut inner = self.inner.lock().unwrap();
        match LightType(id as i8) {
            LightType::BATTERY => inner.battery = parsed,
            LightType::NOTIFICATIONS => inner.notifications = parsed,
            LightType::ATTENTION => inner.attention = parsed,
            LightType::MICROPHONE => inner.microphone = parsed,
            LightType::CAMERA => inner.camera = parsed,
            other => {
                error!("unsupported light type {other:?}");
                return Err(binder::ExceptionCode::UNSUPPORTED_OPERATION.into());
            }
        }
        inner.recompose();
        Ok(())
    }

    fn r#getLights(&self) -> binder::Result<Vec<HwLight>> {
        if !Led::available() {
            return Ok(Vec::new());
        }
        let light = |id: LightType| HwLight {
            id: id.0 as i32,
            ordinal: 0,
            r#type: id,
            minUpdatePeriodMillis: 0,
        };
        Ok(vec![
            light(LightType::BATTERY),
            light(LightType::NOTIFICATIONS),
            light(LightType::ATTENTION),
            light(LightType::MICROPHONE),
            light(LightType::CAMERA),
        ])
    }

    fn r#setLightEffects(&self, _effects: &[HwLightEffect]) -> binder::Result<()> {
        Err(binder::ExceptionCode::UNSUPPORTED_OPERATION.into())
    }
}

fn main() {
    android_logger::init_once(
        android_logger::Config::default().with_tag("Lights"),
    );

    // The LED class device is created by the driver probe, which can finish
    // after this service starts.
    for _ in 0..10 {
        if Led::available() {
            break;
        }
        thread::sleep(Duration::from_millis(500));
    }
    if !Led::available() {
        info!("AW21024 unavailable; registering empty Lights HAL");
    }

    let inner = Arc::new(Mutex::new(LightsInner {
        led: Led::new(),
        battery: State::default(),
        notifications: State::default(),
        attention: State::default(),
        microphone: State::default(),
        camera: State::default(),
        master_enabled: get_bool_prop(MASTER_PROP, true),
        battery_enabled: get_bool_prop(BATTERY_PROP, true),
    }));

    // Follow the settings switches. The properties are cheap to read and there
    // is nothing to block on, so a slow poll is the whole implementation.
    {
        let inner = Arc::clone(&inner);
        thread::spawn(move || {
            let mut last_master = get_bool_prop(MASTER_PROP, true);
            let mut last_battery = get_bool_prop(BATTERY_PROP, true);
            loop {
                thread::sleep(Duration::from_millis(1000));
                let master = get_bool_prop(MASTER_PROP, true);
                let battery = get_bool_prop(BATTERY_PROP, true);
                if master != last_master || battery != last_battery {
                    last_master = master;
                    last_battery = battery;
                    let mut guard = inner.lock().unwrap();
                    guard.master_enabled = master;
                    guard.battery_enabled = battery;
                    guard.recompose();
                }
            }
        });
    }

    binder::ProcessState::start_thread_pool();
    let service = Lights { inner };
    let binder = BnLights::new_binder(service, BinderFeatures::default());
    let name = "android.hardware.light.ILights/default";
    binder::add_service(name, binder.as_binder())
        .unwrap_or_else(|e| panic!("failed to register {name}: {e:?}"));
    info!("Lights HAL ready");
    binder::ProcessState::join_thread_pool();
}
