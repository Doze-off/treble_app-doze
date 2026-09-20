package me.phh.treble.app

import android.content.ComponentName
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log

// Notification-access-gated companion to RogEvents: a NotificationListener
// is the only way for a regular (non-privileged-audio) app to enumerate
// active MediaSessions, so this one service backs both the "Notification"
// and "Music" event lighting triggers. Needs the user to grant Notification
// access (Settings) - see LightingTriggersActivity's permission hint.
class RogNotificationListenerService : NotificationListenerService() {
    private val controllers = mutableMapOf<MediaController, MediaController.Callback>()

    private fun anyPlaying(): Boolean =
        controllers.keys.any { it.playbackState?.state == PlaybackState.STATE_PLAYING }

    private fun trackSessions(sessions: List<MediaController>) {
        val current = sessions.toSet()
        val toRemove = controllers.keys.filter { it !in current }
        for (c in toRemove) {
            c.unregisterCallback(controllers.getValue(c))
            controllers.remove(c)
        }
        for (c in sessions) {
            if (controllers.containsKey(c)) continue
            val callback = object : MediaController.Callback() {
                override fun onPlaybackStateChanged(state: PlaybackState?) {
                    RogEvents.onMusicPlaybackActiveChanged(anyPlaying())
                }
                override fun onSessionDestroyed() {
                    controllers.remove(c)
                    RogEvents.onMusicPlaybackActiveChanged(anyPlaying())
                }
            }
            c.registerCallback(callback)
            controllers[c] = callback
        }
        RogEvents.onMusicPlaybackActiveChanged(anyPlaying())
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (!RogSettings.enabled(this)) return
        try {
            val msm = getSystemService(MediaSessionManager::class.java)
            val component = ComponentName(this, RogNotificationListenerService::class.java)
            msm.addOnActiveSessionsChangedListener({ sessions -> trackSessions(sessions ?: emptyList()) }, component)
            trackSessions(msm.getActiveSessions(component))
        } catch (t: Throwable) {
            Log.d("PHH", "RogNotificationListenerService: failed reading media sessions", t)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        for ((c, cb) in controllers) c.unregisterCallback(cb)
        controllers.clear()
        RogEvents.onMusicPlaybackActiveChanged(false)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (!RogSettings.enabled(this)) return
        if (sbn.packageName == packageName) return
        // Ongoing notifications (media playback controls, download progress,
        // this app's own foreground-service notification, etc.) aren't a
        // discrete "new notification" event - they'd otherwise retrigger
        // the flash indefinitely.
        if (sbn.notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0) return
        RogEvents.onNotificationPosted()
    }
}
