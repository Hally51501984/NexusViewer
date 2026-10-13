#!/bin/bash
# Android開発用 環境変数設定スクリプト
export JAVA_HOME="/home/hally/tools/jdk-17"
export ANDROID_HOME="/home/hally/Android/Sdk"
export ANDROID_SDK_ROOT="/home/hally/Android/Sdk"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"

echo "Environment initialized:"
echo "  JAVA_HOME: $JAVA_HOME"
echo "  ANDROID_HOME: $ANDROID_HOME"
