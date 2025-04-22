#!/bin/bash

# =============================================================================
# Amethyst Build Script
# =============================================================================
#
# DEBUGGING ON PHYSICAL DEVICE FROM WSL:
#
# 1. Connect your phone via USB to your Windows PC, make sure developer mode and USB debugging are enabled, and usb mode is set to PTP
#
# 2. Run these commands in PowerShell:
#    usbipd bind --busid X-Y  # Replace X-Y with your device's BUSID from 'usbipd list'
#    usbipd attach --wsl --busid X-Y
#
# 3. In WSL, run these commands:
#    lsusb  # Find your device (e.g., Bus 001 Device 004: ID 22b8:XXXX Motorola...)
#    sudo chmod 666 /dev/bus/usb/001/XXX  # Replace XXX with the device number from lsusb
#    adb kill-server && adb start-server
#    adb devices  # Verify connection
#    ./gradlew installFdroidDebug  # Install the debug build
#
# 4. To launch with debugging enabled:
#    adb shell am start -D -n com.vitorpamplona.amethyst.debug/com.vitorpamplona.amethyst.ui.MainActivity

# 5. To view device app logs:
#    adb logcat *:D | grep -i "com..*\.amethyst"
#
# =============================================================================

# Ensure the script stops on first error
set -e

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

# Run Gradle with system properties
./gradlew \
  -Dorg.gradle.java.home=$JAVA_HOME \
  -Dorg.gradle.jvmargs="-Xmx4g -XX:MaxMetaspaceSize=1g" \
  $@

echo "Build completed!"
