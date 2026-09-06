package com.unspektrawesome.camera;

public enum LensRole {
    ULTRA_WIDE("Ultra wide"),
    MAIN("Main"),
    TELEPHOTO("Telephoto"),
    FRONT("Front"),
    EXTERNAL("External"),
    REAR("Rear"),
    UNKNOWN("Camera");

    public final String label;

    LensRole(String label) { this.label = label; }
}
