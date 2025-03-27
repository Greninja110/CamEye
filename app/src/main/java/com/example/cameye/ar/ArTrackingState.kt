package com.example.cameye.ar

enum class ArTrackingState {
    INITIALIZING,
    TRACKING,
    NOT_TRACKING,
    UNSUPPORTED, // ARCore not supported or installed
    ERROR
}