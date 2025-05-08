#!/bin/bash

# =============================================================================
# Amethyst Build Script
# =============================================================================

# Logcat filter
LOGCAT_FILTER="DVM"

# Function to print section headers
print_header() {
    echo -e "\n\033[1;36m=== $1 ===\033[0m"
}

# Ensure we have sudo privileges
check_sudo() {
    if ! sudo -n true 2>/dev/null; then
        echo "This operation requires sudo privileges."
        echo "Please enter your password when prompted."
        sudo true || {
            echo "Failed to obtain sudo privileges. Exiting."
            exit 1
        }
    fi
}

# Check Java setup
print_header "CHECKING JAVA SETUP"

# In WSL, if JAVA_HOME points to a Windows path, use the system Java
if [[ "$JAVA_HOME" == *"/mnt/c/"* ]] || [[ "$JAVA_HOME" == "" ]]; then
  # Use the system Java in WSL
  export JAVA_HOME=/usr/lib/jvm/java-17-openjdk-amd64
  echo "Using WSL Java: $JAVA_HOME"
fi

# Check if JAVA_HOME is set
if [ -z "$JAVA_HOME" ]; then
  echo "Error: JAVA_HOME is not set"
  echo "Please set JAVA_HOME to the path of your Java installation"
  exit 1
fi

# Verify Java installation
if [ ! -f "$JAVA_HOME/bin/javac" ]; then
  echo "Error: Java compiler not found at $JAVA_HOME/bin/javac"
  exit 1
fi

# Add Java bin to path
export PATH=$JAVA_HOME/bin:$PATH

# Process command and arguments
COMMAND=""
AUTO_LAUNCH=false
SHOW_LOGS=false
NEED_DEVICE=false
WAIT_FOR_DEBUGGER=false
GRADLE_ARGS=()

# Parse arguments
if [ "$#" -gt 0 ]; then
    COMMAND="$1"
    shift
    
    # Process remaining arguments
    while [ "$#" -gt 0 ]; do
        case "$1" in
            --launch|-l)
                AUTO_LAUNCH=true
                shift
                ;;
            --debug-launch|-dl)
                AUTO_LAUNCH=true
                WAIT_FOR_DEBUGGER=true
                shift
                ;;
            --logs|-lg)
                SHOW_LOGS=true
                shift
                ;;
            *)
                # Check if this is an install task that would need a device
                if [[ "$1" == *"install"* ]]; then
                    NEED_DEVICE=true
                fi
                # Add any non-script flags to gradle arguments
                GRADLE_ARGS+=("$1")
                shift
                ;;
        esac
    done
fi

# Function to check for properly authorized device
check_device_connection() {
    # Start ADB server if it's not already running
    if ! adb get-state >/dev/null 2>&1; then
        adb start-server >/dev/null 2>&1
    fi
    
    # Check if a device is connected and properly authorized
    if adb devices | grep -q "device$"; then
        echo "Device is connected and authorized."
        return 0
    elif adb devices | grep -q "unauthorized"; then
        echo "Device is connected but NOT AUTHORIZED."
        echo "Please check your device screen and accept the USB debugging authorization prompt."
        echo "If you don't see a prompt, try disconnecting and reconnecting the USB cable."
        
        # Wait for authorization
        ATTEMPTS=0
        MAX_ATTEMPTS=5
        
        while adb devices | grep -q "unauthorized" && [ $ATTEMPTS -lt $MAX_ATTEMPTS ]; do
            ATTEMPTS=$((ATTEMPTS+1))
            echo "Waiting for device authorization (attempt $ATTEMPTS of $MAX_ATTEMPTS)..."
            echo "Please check your device for the authorization prompt."
            sleep 5
        done
        
        if adb devices | grep -q "device$"; then
            echo "Device successfully authorized!"
            return 0
        else
            echo "Failed to authorize device. Please ensure USB debugging is enabled and authorized."
            return 1
        fi
    fi
    
    # No device detected
    return 2
}

# Function to set up device connection
setup_device() {
    print_header "SETTING UP USB DEBUGGING FROM WSL"
    
    # Check if a device is already properly connected before starting the setup
    echo "Checking for already connected devices..."
    check_device_connection
    DEVICE_STATUS=$?
    
    if [ $DEVICE_STATUS -eq 0 ]; then
        echo "Device is already connected and authorized. No setup needed."
        return 0
    elif [ $DEVICE_STATUS -eq 1 ]; then
        echo "Device is connected but unauthorized. Please authorize it on your device."
        echo "Do you want to continue with the setup process anyway? (y/n)"
        read continue_setup
        if [[ "$continue_setup" != "y" ]]; then
            echo "Setup aborted."
            return 1
        fi
    fi
    
    # Step 1: Check for connected devices in PowerShell
    echo "Checking for USB devices..."
    powershell.exe "usbipd list" || {
        echo "Error: Could not list USB devices. Make sure usbipd is installed in Windows."
        echo "Install with: winget install usbipd"
        exit 1
    }
    
    # Check if device is already attached to WSL
    DEVICE_ALREADY_ATTACHED=false
    if powershell.exe "usbipd list" | grep -q "WSL"; then
        echo "Device(s) already attached to WSL detected."
        
        # Check if ADB already sees a connected device
        adb devices > /dev/null 2>&1
        if adb devices | grep -q "device$"; then
            echo "Android device already connected to ADB. Skipping USB setup."
            DEVICE_ALREADY_ATTACHED=true
            return 0
        fi
        
        echo "Would you like to skip USB setup and use already attached device(s)? (y/n)"
        read skip_setup
        
        if [[ "$skip_setup" == "y" ]]; then
            DEVICE_ALREADY_ATTACHED=true
            
            # Just restart ADB to make sure it can see the device
            echo "Restarting ADB server..."
            adb kill-server && adb start-server
            
            echo "Checking for connected devices..."
            adb devices
            
            if adb devices | grep -q "device$"; then
                echo "Device connected successfully!"
                return 0
            else
                echo "No Android devices detected by ADB despite USB devices being attached."
                echo "Would you like to continue with manual USB setup? (y/n)"
                read continue_setup
                
                if [[ "$continue_setup" != "y" ]]; then
                    echo "Setup aborted."
                    return 1
                fi
                
                DEVICE_ALREADY_ATTACHED=false
            fi
        fi
    fi
    
    if [ "$DEVICE_ALREADY_ATTACHED" = false ]; then
        # Prompt for busid
        echo ""
        echo "From the list above, identify your Android device's BUSID (e.g., 1-4)"
        read -p "Enter your device's BUSID: " busid
        
        if [ -z "$busid" ]; then
            echo "Error: No BUSID provided"
            exit 1
        fi
        
        # Step 2: Bind device - requires admin in Windows
        echo "Binding device with BUSID $busid..."
        echo "This may require Windows administrator privileges..."
        
        # Try binding with standard PowerShell first
        BIND_OUTPUT=$(powershell.exe "usbipd bind --busid $busid" 2>&1)
        
        # Check if permission was denied
        if echo "$BIND_OUTPUT" | grep -qi "Access is denied\|requires elevation\|administrator"; then
            echo "Binding requires Windows administrator privileges."
            echo "Attempting to run with elevated privileges..."
            # Try with elevated privileges
            powershell.exe "Start-Process powershell -Verb RunAs -WindowStyle Hidden -ArgumentList 'usbipd bind --busid $busid'" || {
                echo "Failed to run with elevated privileges."
                echo "Please manually run this command in an Administrator PowerShell window:"
                echo "usbipd bind --busid $busid"
                echo ""
                echo "Have you manually run the bind command? (y/n)"
                read manual_bind
                if [[ "$manual_bind" != "y" ]]; then
                    echo "Setup aborted."
                    exit 1
                fi
            }
        elif echo "$BIND_OUTPUT" | grep -qi "error\|failed"; then
            # Only show output if there's a real error (not already shared)
            if ! echo "$BIND_OUTPUT" | grep -qi "already shared"; then
                echo "$BIND_OUTPUT"
                echo "Failed to bind device. Make sure the BUSID is correct."
                exit 1
            fi
        fi
        
        echo "Attaching device to WSL..."
        ATTACH_OUTPUT=$(powershell.exe "usbipd attach --wsl --busid $busid" 2>&1)
        
        # Check if the error is because it's already attached
        if echo "$ATTACH_OUTPUT" | grep -q "already attached"; then
            echo "Device is already attached to WSL, continuing..."
        elif echo "$ATTACH_OUTPUT" | grep -q "error"; then
            echo "$ATTACH_OUTPUT"
            echo "Failed to attach device to WSL."
            exit 1
        fi
    fi
    
    # Step 3: Set up ADB in WSL (requires sudo)
    print_header "CONFIGURING ADB CONNECTION"
    
    # Ensure we have sudo privileges for USB device access
    check_sudo
    
    echo "Waiting for device to be recognized in WSL..."
    # Add a delay to let the device be recognized
    sleep 3
    
    echo "Looking for your device in WSL..."
    lsusb
    
    # If lsusb doesn't show the device, wait a bit longer and try again
    if ! lsusb | grep -qi "android\|google\|motorola\|samsung\|oneplus\|xiaomi\|oppo\|vivo\|huawei\|sony\|htc\|lg\|asus\|realme"; then
        echo "Device not immediately detected. Waiting a few more seconds..."
        sleep 5
        echo "Checking again..."
        lsusb
    fi
    
    if [ "$DEVICE_ALREADY_ATTACHED" = true ]; then
        # Skip the manual device path configuration if already attached and working
        echo "Skipping manual device path configuration as device is already attached."
    else
        # Find device path
        echo ""
        echo "From the list above, identify your device's bus and device numbers"
        echo "Example: If you see 'Bus 001 Device 004: ID 22b8:XXXX DEVICE_NAME...'"
        read -p "Enter bus number (e.g., 001): " bus
        read -p "Enter device number (e.g., 004): " device
        
        if [ -z "$bus" ] || [ -z "$device" ]; then
            echo "Error: Bus or device number not provided"
            exit 1
        fi
        
        # Set device permissions
        echo "Setting device permissions..."
        sudo chmod 666 /dev/bus/usb/$bus/$device || {
            echo "Failed to set permissions. Make sure the bus and device numbers are correct."
            exit 1
        }
    fi
    
    # Restart ADB
    echo "Restarting ADB server..."
    adb kill-server
    
    # Start ADB with sudo if needed
    if ! which adb > /dev/null; then
        echo "Error: ADB not found. Please install Android SDK Platform Tools."
        exit 1
    fi
    
    if ! adb start-server; then
        echo "Starting ADB server with sudo..."
        sudo adb start-server
    fi
    
    # Check for device with retries
    echo "Checking for connected devices..."
    adb devices
    
    # Check for unauthorized devices and prompt for authorization
    if adb devices | grep -q "unauthorized"; then
        echo "========================================================================"
        echo "DEVICE AUTHORIZATION REQUIRED"
        echo "========================================================================"
        echo "Your device is showing as 'unauthorized'. Please check your device screen"
        echo "and approve the USB debugging connection."
        echo ""
        echo "If you don't see a prompt on your device:"
        echo "1. Make sure the screen is unlocked"
        echo "2. Check notification area for an authorization request"
        echo "3. Try unplugging and reconnecting the USB cable"
        echo "4. Revoke USB debugging authorizations in Developer Options and try again"
        echo "========================================================================"
        
        # Wait for authorization
        ATTEMPTS=0
        MAX_ATTEMPTS=5
        
        while adb devices | grep -q "unauthorized" && [ $ATTEMPTS -lt $MAX_ATTEMPTS ]; do
            ATTEMPTS=$((ATTEMPTS+1))
            echo "Waiting for device authorization (attempt $ATTEMPTS of $MAX_ATTEMPTS)..."
            sleep 5
            adb devices
        done
    fi
    
    # Try a few times if device isn't detected properly
    ATTEMPTS=0
    MAX_ATTEMPTS=3
    
    while ! adb devices | grep -q "device$" && [ $ATTEMPTS -lt $MAX_ATTEMPTS ]; do
        ATTEMPTS=$((ATTEMPTS+1))
        echo "No properly authorized devices detected yet. Waiting (attempt $ATTEMPTS of $MAX_ATTEMPTS)..."
        sleep 5
        adb devices
    done
    
    if ! adb devices | grep -q "device$"; then
        echo "Warning: No authorized devices detected by ADB. Debugging may not work."
        echo "Make sure USB debugging is enabled on your device and it's set to PTP usb mode."
        echo "Also ensure you've approved the USB debugging authorization prompt on your device."
        return 1
    else
        echo "Device connected successfully!"
        return 0
    fi
}

# Function to launch app with debugging
launch_app() {
    if [ "$AUTO_LAUNCH" = true ]; then
        if [ "$WAIT_FOR_DEBUGGER" = true ] || [ "$COMMAND" = "debug" ]; then
            # Launch with debugger waiting (debug mode)
            echo "Auto-launching app with debugging enabled (will wait for debugger)..."
            adb shell am start -D -n com.vitorpamplona.amethyst.debug/com.vitorpamplona.amethyst.ui.MainActivity
            echo "App launched with debugging enabled. Connect your debugger now."
        else
            # Launch normally without waiting for debugger (build mode)
            echo "Auto-launching app normally (no debugging)..."
            adb shell am start -n com.vitorpamplona.amethyst.debug/com.vitorpamplona.amethyst.ui.MainActivity
            echo "App launched successfully."
        fi
    else
        # Interactive mode
        if [ "$COMMAND" = "debug" ]; then
            # In debug command, always ask about debugging
            echo "Would you like to launch the app with debugging enabled? (y/n)"
            read launch_debug
            
            if [[ "$launch_debug" == "y" ]]; then
                echo "Launching app with debugging enabled..."
                adb shell am start -D -n com.vitorpamplona.amethyst.debug/com.vitorpamplona.amethyst.ui.MainActivity
                echo "App launched with debugging enabled. Connect your debugger now."
            fi
        else
            # In build command, offer both options
            echo "Would you like to launch the app? (n/y/d)"
            echo "  n - no, don't launch"
            echo "  y - yes, launch normally"
            echo "  d - yes, launch with debugger"
            read launch_option
            
            if [[ "$launch_option" == "y" ]]; then
                echo "Launching app normally..."
                adb shell am start -n com.vitorpamplona.amethyst.debug/com.vitorpamplona.amethyst.ui.MainActivity
                echo "App launched successfully."
            elif [[ "$launch_option" == "d" ]]; then
                echo "Launching app with debugging enabled..."
                adb shell am start -D -n com.vitorpamplona.amethyst.debug/com.vitorpamplona.amethyst.ui.MainActivity
                echo "App launched with debugging enabled. Connect your debugger now."
            fi
        fi
    fi
}

# Function to show device logs
show_logs() {
    if [ "$SHOW_LOGS" = true ]; then
        echo "Automatically showing logs (press Ctrl+C to stop)..."
        adb logcat *:D | grep -i -E "$LOGCAT_FILTER"
    else
        echo "Would you like to view device logs? (y/n)"
        read logs
        
        if [[ "$logs" == "y" ]]; then
            echo "Showing logs (press Ctrl+C to stop)..."
            adb logcat *:D | grep -i -E "$LOGCAT_FILTER"
        fi
    fi
}

# Check for build command
if [[ "$COMMAND" == "build" ]]; then
    # If install task is included and device connection is needed, set it up first
    if [ "$NEED_DEVICE" = true ]; then
        echo "Install task detected. Device connection needed."
        setup_device
        DEVICE_CONNECTED=$?
        
        if [ $DEVICE_CONNECTED -ne 0 ]; then
            echo "Warning: Proceeding with build but installation may fail without a connected device."
        fi
    fi
    
    print_header "BUILDING AMETHYST"
    
    # Run Gradle with system properties
    ./gradlew \
      -Dorg.gradle.java.home=$JAVA_HOME \
      -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g" \
      "${GRADLE_ARGS[@]}"
    
    BUILD_SUCCESS=$?
    
    if [ $BUILD_SUCCESS -eq 0 ] && [ "$NEED_DEVICE" = true ] && [ "$DEVICE_CONNECTED" -eq 0 ]; then
        # If build was successful and we have a connected device, offer to launch app and show logs
        launch_app
        show_logs
    fi
    
    if [ $BUILD_SUCCESS -eq 0 ]; then
        echo "Build completed successfully!"
    else
        echo "Build failed with errors."
        exit 1
    fi
    
    exit 0
fi

# Check for debug command
if [[ "$COMMAND" == "debug" ]]; then
    # Set up device connection
    setup_device
    
    # Step 4: Install and debug
    print_header "INSTALLING DEBUG BUILD"
    
    echo "Installing debug build..."
    ./gradlew installFdroidDebug || {
        echo "Failed to install debug build."
        exit 1
    }
    
    # Launch app and show logs
    launch_app
    show_logs
    
    print_header "DEBUGGING SETUP COMPLETE"
    echo "Your device is now connected for debugging."
    echo ""
    echo "Quick reference:"
    echo "- Launch with debugging: adb shell am start -D -n com.vitorpamplona.amethyst.debug/com.vitorpamplona.amethyst.ui.MainActivity"
    echo "- Launch normally: adb shell am start -n com.vitorpamplona.amethyst.debug/com.vitorpamplona.amethyst.ui.MainActivity"
    echo "- View logs: adb logcat *:D | grep -i -E '$LOGCAT_FILTER'"
    echo "- Reinstall app: ./gradlew installFdroidDebug"
    
    exit 0
fi

# If no specific command was given, show help
print_header "AMETHYST BUILD SCRIPT"
echo "Usage:"
echo "  $0 [command] [options]"
echo ""
echo "Commands:"
echo "  build [gradle-options]  Build the project with specified Gradle options"
echo "  debug [options]         Set up USB debugging from WSL"
echo ""
echo "Debug options:"
echo "  --launch, -l            Automatically launch app (normal mode in build, debug mode in debug)"
echo "  --debug-launch, -dl     Automatically launch app with debugger (always waits for debugger)"
echo "  --logs, -lg             Automatically show device logs"
echo ""
echo "Examples:"
echo "  $0 build                                Run a default build"
echo "  $0 build assembleDebug                  Build debug APK"
echo "  $0 build installFdroidDebug             Build and install debug APK"
echo "  $0 debug                                Set up device for debugging"
echo "  $0 debug --launch --logs                Set up device, auto-launch with debugging and show logs"
echo "  $0 build installFdroidDebug -l -lg      Install APK, launch it normally and show logs"
echo "  $0 build installFdroidDebug -dl -lg     Install APK, launch with debugger and show logs"
echo ""
echo "For a plain build with no additional features, use:"
echo "  $0 build"

exit 0
