# Batch N — full wired modem

`n1_wired_modem_full.lua` automates the wired-modem peripheral surface if a wall or full wired
modem is attached to the computer. The six-face hub behaviour needs blocks placed in the world, so
the script stages those checks on screen and in `/batch-n-results.txt`.

## Setup

1. Place a computer next to any wired modem (wall or full).
2. Copy `n1_wired_modem_full.lua` into the computer and run `n1_wired_modem_full`.
3. For the staged checks, place a **Full Wired Modem** with chests/inventories on several faces,
   plus at least one computer on the cable network.

## What cannot be automated

- The texture states (`wiredModemFace`, `wiredModemFaceOn`, `wiredModemFacePeripheral`,
  `wiredModemFacePeripheralOn`) and the full-cube shape.
- Sorted, combined chat messages when toggling all six sides.
- Per-face `getNameLocal` and the shared channel state across faces.
- Neighbour removal, drop, and save/reload persistence.
