package com.userhub.domain.time

import platform.Foundation.NSDate
import platform.Foundation.NSTimeZone
import platform.Foundation.localTimeZone
import platform.Foundation.timeIntervalSince1970

internal actual fun systemEpochMillis(): Long =
    (NSDate().timeIntervalSince1970 * 1000).toLong()

internal actual fun utcOffsetMillis(): Long =
    NSTimeZone.localTimeZone.secondsFromGMTForDate(NSDate()) * 1_000L
