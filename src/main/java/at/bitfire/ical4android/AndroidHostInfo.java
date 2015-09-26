/*
 * Copyright (c) 2013 – 2015 Ricki Hirner (bitfire web engineering).
 *
 * This program is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 * without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR
 * PURPOSE.  See the GNU General Public License for more details.
 */

package at.bitfire.ical4android;

import android.content.ContentResolver;
import android.provider.Settings;

import net.fortuna.ical4j.util.HostInfo;

public class AndroidHostInfo implements HostInfo {

    final ContentResolver resolver;

    public AndroidHostInfo(ContentResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public String getHostName() {
        return Settings.Secure.getString(resolver, Settings.Secure.ANDROID_ID) + ".android";
    }

}
