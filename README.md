# RedMagic TGK Test

Minimal Android/Shizuku test app for reproducing the RedMagic shoulder-trigger input path through `/dev/uinput`.

The app creates two virtual input devices matching the observed RedMagic TGK devices:

- `nubia_tgk_aw_sar0_ch0` -> `KEY_F7` + `ABS_DISTANCE` (R candidate)
- `nubia_tgk_aw_sar1_ch0` -> `KEY_F8` + `ABS_DISTANCE` (L candidate)

It runs the uinput backend inside a Shizuku UserService (shell UID 2000), not in the normal app process.
