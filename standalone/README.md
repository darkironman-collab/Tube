# Extreme Tube Standalone v0.2

This branch builds an independent Android application. It does not patch or bundle the official YouTube Android APK and does not use Morphe code or Morphe patch bundles.

The CI recipe uses the GPLv3 NewPipe v0.29.0 source as an open-source standalone base, applies the auditable `patch_newpipe.py` changes, builds `com.extremetube.app`, signs the APK, verifies it, and publishes the APK plus corresponding patched source in the `extreme-tube-v0.2.0` GitHub release.

v0.2 quality display changes add codec/FPS data to stream rows and estimated size in the in-player quality list where bitrate/duration metadata is available. Exact fetched size remains available in the stream/download selector.
