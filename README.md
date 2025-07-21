A collection of client-side tweaks, primarily useful for developers and creators.

Note: Most features are disabled by default. Run /moulberrystweaks to access the config and enable the features that you want.

# Features
- Gameplay
    - Confirm Disconnect Button
        - Adds a confirmation when disconnecting from a server to avoid accidental disconnects
    - Attack Indicator Fix (MC-255058)
        - Makes the attack indicator bar show the correct value visually
    - Prevent Server Closing Pause Screen
        - Prevents the server from closing your pause screen and other sub-screens
- Resource Loading Overlay
    - Fast Loading Overlay
        - Speeds up the resource loading overlay by removing the fade out animation
    - Transparent Loading Overlay
        - Removes the background from the resource loading overlay
- Resource Pack
    - Automatic Pack Reload
        - Automatically reloads resourcepacks when a file in the folder is changed
    - Disable Warnings
        - Disables the warning screen when toggling resourcepacks with the wrong version
- Debugging
    - Log Packet Exceptions
        - Prints the stacktrace of a packet exception, useful for debugging
    - Ignore Narrator Error
        - If the narrator can't be loaded, stop the game from complaining about it
    - Send Debug Movement
        - Sends debug movement data to the server every tick. Useful for anticheat developers (moulberrystweaks:debug_movement_data)
    - Item Component Widget
        - Keybind to open a widget showing item component data
    - Packet Debug Widget
        - Keybind to open a widget showing incoming and outgoing inventory packets. Useful for server developers
- Commands
    - Auto Vanish Players
        - Command to automatically vanish players that are nearby. Useful for parkour servers so you can see where to go
    - Dump Held Item Json
        - Prints the serialized json of the item you are holding. Useful for developers
    - Generate Font Width Table
        - Writes a file containing the width of every font character. Useful for developers of external tools/software
    - Dump Player Attributes
        - Dumps the entity attributes of the player entity. Useful for developers
    - Debug Render
        - Allows hiding/clearing debug renders sent by the server using the debug_render protocol. Useful for developers
    - Dump Bossbar Json
        - Prints the serialized json of the bossbar's Name, Useful for developers

# Additional protocols
See GitHub repository for implementations

## Debug Render
This protocol allows servers to render custom shapes on the client
- debugrender:add
- debugrender:clear_namespace
- debugrender:clear
- debugrender:remove
## Debug Movement
This protocol sends debug movement data to the server. Useful for anticheat developers
- moulberrystweaks:debug_movement_data
