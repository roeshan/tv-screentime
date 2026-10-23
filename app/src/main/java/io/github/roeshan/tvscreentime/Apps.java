package io.github.roeshan.tvscreentime;

import java.util.Set;

/**
 * Which apps count, which need the PIN, and which windows to look past. These are package names; list the ones on
 * a TV with `adb shell pm list packages`. Edit here, rebuild, reinstall.
 */
final class Apps {
    private Apps() {}

    /** Time on screen in these apps counts against the daily quota. */
    static final Set<String> TRACKED = Set.of(
            // Streaming
            "com.google.android.youtube.tv",            // YouTube
            "com.google.android.youtube.tvkids",        // YouTube Kids
            "com.netflix.ninja",                        // Netflix
            "com.amazon.amazonvideo.livingroom",        // Prime Video
            "com.disney.disneyplus",                    // Disney+
            "in.startv.hotstar.dplus.tv",               // Disney+ Hotstar
            "org.jellyfin.androidtv",                   // Jellyfin
            "com.plexapp.android",                      // Plex
            "org.xbmc.kodi",                            // Kodi
            "com.vidio.android.tv",                     // Vidio
            // Live TV and HDMI, by vendor
            "com.google.android.tv",                    // Google Live Channels
            "com.cltv.hybrid",                          // iWedia CanaliTV (Skyworth, Motorola)
            "com.cltv.mal",
            "com.mediatek.wwtv.tvcenter",               // MediaTek Live TV
            "com.tcl.tvinput",                          // TCL
            "com.sony.dtv.tvx",                         // Sony
            "com.xiaomi.mitv.tvplayer");                // Xiaomi

    /** Opening these needs the PIN, so the limiter can't be switched off, cleared or uninstalled. */
    static final Set<String> GATED = Set.of(
            "com.android.tv.settings",
            "com.android.settings",
            "com.android.vending");

    /** Windows that are not "what's being watched": system UI and keyboards. */
    static final Set<String> IGNORED = Set.of(
            "android",
            "com.android.systemui",
            "com.google.android.inputmethod.latin",
            "com.google.android.leanback.ime",
            "org.liskovsoft.androidtv.rukeyboard");
}
