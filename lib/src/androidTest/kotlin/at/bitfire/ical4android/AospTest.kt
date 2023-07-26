/***************************************************************************************************
 * Copyright © All Contributors. See LICENSE and AUTHORS in the root directory for details.
 **************************************************************************************************/

package at.bitfire.ical4android

import android.Manifest
import android.accounts.Account
import android.content.ContentUris
import android.content.ContentValues
import android.net.Uri
import android.provider.CalendarContract
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import at.bitfire.ical4android.util.InitCalendarProviderRule
import at.bitfire.ical4android.util.MiscUtils.ContentProviderClientHelper.closeCompat
import at.bitfire.ical4android.util.MiscUtils.CursorHelper.toValues
import org.junit.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull

class AospTest {

    companion object {

        @JvmField
        @ClassRule
        val initCalendarProviderRule = InitCalendarProviderRule.withPermissions

    }

    private val testAccount = Account("test@example.com", CalendarContract.ACCOUNT_TYPE_LOCAL)

    private val provider by lazy {
        InstrumentationRegistry.getInstrumentation().targetContext.contentResolver.acquireContentProviderClient(CalendarContract.AUTHORITY)!!
    }

    private lateinit var calendarUri: Uri

    @Before
    fun prepare() {
        calendarUri = provider.insert(
            CalendarContract.Calendars.CONTENT_URI.asSyncAdapter(), ContentValues().apply {
                put(CalendarContract.Calendars.ACCOUNT_NAME, testAccount.name)
                put(CalendarContract.Calendars.ACCOUNT_TYPE, testAccount.type)
                put(CalendarContract.Calendars.NAME, "Test Calendar")
            }
        )!!
    }

    @After
    fun shutdown() {
        provider.delete(calendarUri, null, null)
        provider.closeCompat()
    }

    private fun Uri.asSyncAdapter() =
        buildUpon()
            .appendQueryParameter(CalendarContract.CALLER_IS_SYNCADAPTER, "1")
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_NAME, testAccount.name)
            .appendQueryParameter(CalendarContract.Calendars.ACCOUNT_TYPE, testAccount.type)
            .build()


    @Test
    fun testInfiniteRRule() {
        assertNotNull(provider.insert(CalendarContract.Events.CONTENT_URI.asSyncAdapter(), ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, ContentUris.parseId(calendarUri))
            put(CalendarContract.Events.DTSTART, 1643192678000)
            put(CalendarContract.Events.DURATION, "P1H")
            put(CalendarContract.Events.RRULE, "FREQ=YEARLY")
            put(CalendarContract.Events.TITLE, "Test event with infinite RRULE")
        }))
    }

    @Test(expected = AssertionError::class)
    fun testInfiniteRRulePlusRDate() {
        // see https://issuetracker.google.com/issues/37116691

        assertNotNull(provider.insert(CalendarContract.Events.CONTENT_URI.asSyncAdapter(), ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, ContentUris.parseId(calendarUri))
            put(CalendarContract.Events.DTSTART, 1643192678000)
            put(CalendarContract.Events.DURATION, "PT1H")
            put(CalendarContract.Events.RRULE, "FREQ=YEARLY")
            put(CalendarContract.Events.RDATE, "20230101T000000Z")
            put(CalendarContract.Events.TITLE, "Test event with infinite RRULE and RDATE")
        }))

        /** FAILS:
            W RecurrenceProcessor: DateException with r=FREQ=YEARLY;WKST=MO rangeStart=135697573414 rangeEnd=9223372036854775807
            W CalendarProvider2: Could not calculate last date.
            W CalendarProvider2: com.android.calendarcommon2.DateException: No range end provided for a recurrence that has no UNTIL or COUNT.
            W CalendarProvider2: 	at com.android.calendarcommon2.RecurrenceProcessor.expand(RecurrenceProcessor.java:766)
            W CalendarProvider2: 	at com.android.calendarcommon2.RecurrenceProcessor.expand(RecurrenceProcessor.java:661)
            W CalendarProvider2: 	at com.android.calendarcommon2.RecurrenceProcessor.getLastOccurence(RecurrenceProcessor.java:130)
            W CalendarProvider2: 	at com.android.calendarcommon2.RecurrenceProcessor.getLastOccurence(RecurrenceProcessor.java:61)
         */
    }

    @Test
    fun testRdatesWithDifferentTimezones() {
        // tests whether Android supports multiple lines in RDATE with different time zones
        val uri = provider.insert(CalendarContract.Events.CONTENT_URI.asSyncAdapter(), ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, ContentUris.parseId(calendarUri))
            put(CalendarContract.Events.DTSTART, 1681379449000)     // Thu Apr 13 2023 09:50:49 GMT+0000 = Thu Apr 13 2023 11:50:49 Europe/Vienna
            put(CalendarContract.Events.DURATION, "P1H")
            // note that DTSTART must be contained in RDATE, too
            put(CalendarContract.Events.RDATE, "Europe/Vienna;20230413T095049Z,20230414T000000\nEurope/London;20230414T000000")
            put(CalendarContract.Events.TITLE, "Test event")
        })!!
        val eventId = ContentUris.parseId(uri)

        val instances = mutableListOf<ContentValues>()
        provider.query(CalendarContract.Instances.CONTENT_URI.buildUpon()
            .appendPath("1500000000000")
            .appendPath("2000000000000")
            .build(), null, null, null, null)!!.use { cursor ->
            while (cursor.moveToNext()) {
                val values = cursor.toValues()
                if (values.getAsLong(CalendarContract.Instances.EVENT_ID) == eventId)
                    instances += values
            }
        }
        assertEquals(3, instances.size)

        val times = instances.map { it.getAsLong(CalendarContract.Instances.BEGIN) }.toSet()
        assertEquals(setOf(1681379449000,1681423200000,1681426800000), times)
    }

}