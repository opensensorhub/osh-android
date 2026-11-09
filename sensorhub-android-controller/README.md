# Android Controller Driver

OpenSensorHub driver for Android gamepad controllers. Captures real-time input from any connected gamepad via USB.

## Captured Inputs

- **Buttons**: A, B, X, Y, L1, R1, L3 (left stick click), R3 (right stick click), Mode, Start, Select
- **Triggers**: Left trigger, Right trigger (analog 0.0 - 1.0)
- **Joysticks**: Left stick X/Y, Right stick X/Y (analog -1.0 to 1.0)
- **D-Pad**: 8-directional (UP, DOWN, LEFT, RIGHT, and diagonals) plus NONE

## Setup

1. Connect a gamepad controller to the Android device (USB)
2. Enable the controller sensor in the osh-android app sensors tab
3. The driver auto-detects connected gamepads and listens for events
