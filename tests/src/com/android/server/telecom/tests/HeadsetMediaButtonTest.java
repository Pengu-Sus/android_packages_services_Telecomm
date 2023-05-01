/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.server.telecom.tests;

import android.content.Intent;
import android.media.session.MediaSession;
import android.telecom.CallEndpoint;
import android.test.suitebuilder.annotation.MediumTest;
import android.test.suitebuilder.annotation.SmallTest;
import android.view.KeyEvent;

import com.android.server.telecom.Call;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.HeadsetMediaButton;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.HeadsetMediaButton.MediaSessionWrapper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(JUnit4.class)
public class HeadsetMediaButtonTest extends TelecomTestCase {
    private static final int TEST_TIMEOUT_MILLIS = 1000;

    private HeadsetMediaButton mHeadsetMediaButton;
    private MediaSession.Callback mSessionCallback;

    @Mock private CallsManager mMockCallsManager;
    @Mock private HeadsetMediaButton.MediaSessionAdapter mMediaSessionAdapter;
    private TelecomSystem.SyncRoot mLock = new TelecomSystem.SyncRoot() {};

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        ArgumentCaptor<MediaSession.Callback> sessionCallbackArgument =
                ArgumentCaptor.forClass(MediaSession.Callback.class);

        mHeadsetMediaButton = new HeadsetMediaButton(mContext, mMockCallsManager, mLock,
                mMediaSessionAdapter);

        verify(mMediaSessionAdapter).setCallback(sessionCallbackArgument.capture());
        mSessionCallback = sessionCallbackArgument.getValue();
    }

    @Override
    @After
    public void tearDown() throws Exception {
        mHeadsetMediaButton = null;
        super.tearDown();
    }

    /**
     * Nominal case; just add a call and remove it; this happens when the audio state is earpiece.
     */
    @SmallTest
    @Test
    public void testAddCall() {
        Call regularCall = getRegularCall();

        when(mMockCallsManager.hasAnyCalls()).thenReturn(true);
        mHeadsetMediaButton.onCallAdded(regularCall);
        waitForHandlerAction(mHeadsetMediaButton.getHandler(), TEST_TIMEOUT_MILLIS);
        verify(mMediaSessionAdapter, never()).setActive(eq(true));

        // Report that the endpoint is earpiece and other routes that don't matter
        mHeadsetMediaButton.onCallEndpointChanged(
                new CallEndpoint("Earpiece", CallEndpoint.TYPE_EARPIECE));
        mHeadsetMediaButton.onCallEndpointChanged(
                new CallEndpoint("Speaker", CallEndpoint.TYPE_SPEAKER));
        mHeadsetMediaButton.onCallEndpointChanged(
                new CallEndpoint("BT", CallEndpoint.TYPE_BLUETOOTH));
        waitForHandlerAction(mHeadsetMediaButton.getHandler(), TEST_TIMEOUT_MILLIS);
        verify(mMediaSessionAdapter, never()).setActive(eq(true));

        // ... and thus we see how the original code isn't amenable to tests.
        when(mMediaSessionAdapter.isActive()).thenReturn(false);

        // Still should not have done anything; we never hit wired headset
        mHeadsetMediaButton.onCallRemoved(regularCall);
        waitForHandlerAction(mHeadsetMediaButton.getHandler(), TEST_TIMEOUT_MILLIS);
        verify(mMediaSessionAdapter, never()).setActive(eq(false));
    }

    /**
     * Call is added and then routed to headset after call start
     */
    @SmallTest
    @Test
    public void testAddCallThenRouteToHeadset() {
        Call regularCall = getRegularCall();

        mHeadsetMediaButton.onCallAdded(regularCall);
        waitForHandlerAction(mHeadsetMediaButton.getHandler(), TEST_TIMEOUT_MILLIS);
        verify(mMediaSessionAdapter, never()).setActive(eq(true));

        mHeadsetMediaButton.onCallEndpointChanged(
                new CallEndpoint("Wired Headset", CallEndpoint.TYPE_WIRED_HEADSET));
        waitForHandlerAction(mHeadsetMediaButton.getHandler(), TEST_TIMEOUT_MILLIS);
        verify(mMediaSessionAdapter).setActive(eq(true));

        // ... and thus we see how the original code isn't amenable to tests.
        when(mMediaSessionAdapter.isActive()).thenReturn(true);

        // Remove the one call; we should release the session.
        mHeadsetMediaButton.onCallRemoved(regularCall);
        waitForHandlerAction(mHeadsetMediaButton.getHandler(), TEST_TIMEOUT_MILLIS);
        verify(mMediaSessionAdapter).setActive(eq(false));
        when(mMediaSessionAdapter.isActive()).thenReturn(false);

        // Add a new call; make sure we go active once more.
        mHeadsetMediaButton.onCallAdded(regularCall);
        mHeadsetMediaButton.onCallEndpointChanged(
                new CallEndpoint("Wired Headset", CallEndpoint.TYPE_WIRED_HEADSET));
        waitForHandlerAction(mHeadsetMediaButton.getHandler(), TEST_TIMEOUT_MILLIS);
        verify(mMediaSessionAdapter, times(2)).setActive(eq(true));
    }

    /**
     * Call is added and then routed to headset after call start
     */
    @SmallTest
    @Test
    public void testAddCallThenRouteToHeadsetAndBack() {
        Call regularCall = getRegularCall();

        when(mMockCallsManager.hasAnyCalls()).thenReturn(true);
        mHeadsetMediaButton.onCallAdded(regularCall);
        waitForHandlerAction(mHeadsetMediaButton.getHandler(), TEST_TIMEOUT_MILLIS);
        verify(mMediaSessionAdapter, never()).setActive(eq(true));

        mHeadsetMediaButton.onCallEndpointChanged(
                new CallEndpoint("Wired Headset", CallEndpoint.TYPE_WIRED_HEADSET));
        waitForHandlerAction(mHeadsetMediaButton.getHandler(), TEST_TIMEOUT_MILLIS);
        verify(mMediaSessionAdapter).setActive(eq(true));

        // ... and thus we see how the original code isn't amenable to tests.
        when(mMediaSessionAdapter.isActive()).thenReturn(true);

        mHeadsetMediaButton.onCallEndpointChanged(
                new CallEndpoint("Earpiece", CallEndpoint.TYPE_EARPIECE));
        waitForHandlerAction(mHeadsetMediaButton.getHandler(), TEST_TIMEOUT_MILLIS);
        verify(mMediaSessionAdapter).setActive(eq(false));
        when(mMediaSessionAdapter.isActive()).thenReturn(false);

        // Remove the one call; we should not release again.
        mHeadsetMediaButton.onCallRemoved(regularCall);
        waitForHandlerAction(mHeadsetMediaButton.getHandler(), TEST_TIMEOUT_MILLIS);
        // Remember, mockito counts total invocations; we should have went active once and then
        // inactive again when we hit earpiece.
        verify(mMediaSessionAdapter, times(1)).setActive(eq(true));
        verify(mMediaSessionAdapter, times(1)).setActive(eq(false));
    }

    /**
     * Test a case where a regular call becomes an external call, and back again.
     */
    @SmallTest
    @Test
    public void testRegularCallThatBecomesExternal() {
        Call regularCall = getRegularCall();

        // Start with a regular old call.
        when(mMockCallsManager.hasAnyCalls()).thenReturn(true);
        mHeadsetMediaButton.onCallAdded(regularCall);
        mHeadsetMediaButton.onCallEndpointChanged(
                new CallEndpoint("Wired Headset", CallEndpoint.TYPE_WIRED_HEADSET));
        waitForHandlerAction(mHeadsetMediaButton.getHandler(), TEST_TIMEOUT_MILLIS);
        verify(mMediaSessionAdapter).setActive(eq(true));
        when(mMediaSessionAdapter.isActive()).thenReturn(true);

        // Change so it is external.
        when(regularCall.isExternalCall()).thenReturn(true);
        when(mMockCallsManager.hasAnyCalls()).thenReturn(false);
        mHeadsetMediaButton.onExternalCallChanged(regularCall, true);
        // Expect to set session inactive.
        waitForHandlerAction(mHeadsetMediaButton.getHandler(), TEST_TIMEOUT_MILLIS);
        verify(mMediaSessionAdapter).setActive(eq(false));
        when(mMediaSessionAdapter.isActive()).thenReturn(false);

        // For good measure lets make it non-external again.
        when(regularCall.isExternalCall()).thenReturn(false);
        when(mMockCallsManager.hasAnyCalls()).thenReturn(true);
        mHeadsetMediaButton.onExternalCallChanged(regularCall, false);
        // Expect to set session active.
        waitForHandlerAction(mHeadsetMediaButton.getHandler(), TEST_TIMEOUT_MILLIS);
        verify(mMediaSessionAdapter, times(2)).setActive(eq(true));
    }

    @MediumTest
    @Test
    public void testExternalCallNotChangesState() {
        Call externalCall = getRegularCall();
        when(externalCall.isExternalCall()).thenReturn(true);

        mHeadsetMediaButton.onCallAdded(externalCall);
        mHeadsetMediaButton.onCallEndpointChanged(
                new CallEndpoint("Wired Headset", CallEndpoint.TYPE_WIRED_HEADSET));
        waitForHandlerAction(mHeadsetMediaButton.getHandler(), TEST_TIMEOUT_MILLIS);
        verify(mMediaSessionAdapter, never()).setActive(eq(true));

        mHeadsetMediaButton.onCallRemoved(externalCall);
        waitForHandlerAction(mHeadsetMediaButton.getHandler(), TEST_TIMEOUT_MILLIS);
        verify(mMediaSessionAdapter, never()).setActive(eq(false));
    }

    @SmallTest
    @Test
    public void testCallbackReceivesKeyEventUnaware() {
        mSessionCallback.onMediaButtonEvent(getKeyEventIntent(
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_0, false));
        verify(mMockCallsManager, never()).onMediaButton(anyInt());
    }

    @SmallTest
    @Test
    public void testCallbackReceivesKeyEventShortClick() {
        mSessionCallback.onMediaButtonEvent(getKeyEventIntent(
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK, false));
        verify(mMockCallsManager, never()).onMediaButton(anyInt());

        mSessionCallback.onMediaButtonEvent(getKeyEventIntent(
                KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK, false));
        verify(mMockCallsManager, times(1)).onMediaButton(HeadsetMediaButton.SHORT_PRESS);
    }

    @SmallTest
    @Test
    public void testCallbackReceivesKeyEventLongClick() {
        mSessionCallback.onMediaButtonEvent(getKeyEventIntent(
                KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK, true));
        verify(mMockCallsManager, times(1)).onMediaButton(HeadsetMediaButton.LONG_PRESS);

        mSessionCallback.onMediaButtonEvent(getKeyEventIntent(
                KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK, false));
        verify(mMockCallsManager, times(1)).onMediaButton(HeadsetMediaButton.LONG_PRESS);
    }

    @SmallTest
    @Test
    public void testMediaSessionWrapperSetActive() {
        MediaSession session = Mockito.mock(MediaSession.class);
        MediaSessionWrapper wrapper = mHeadsetMediaButton.new MediaSessionWrapper(session);

        final boolean active = true;
        wrapper.setActive(active);
        verify(session).setActive(active);
    }

    @SmallTest
    @Test
    public void testMediaSessionWrapperSetCallback() {
        MediaSession session = Mockito.mock(MediaSession.class);
        MediaSessionWrapper wrapper = mHeadsetMediaButton.new MediaSessionWrapper(session);

        wrapper.setCallback(mSessionCallback);
        verify(session).setCallback(mSessionCallback);
    }

    @SmallTest
    @Test
    public void testMediaSessionWrapperIsActive() {
        MediaSession session = Mockito.mock(MediaSession.class);
        MediaSessionWrapper wrapper = mHeadsetMediaButton.new MediaSessionWrapper(session);

        final boolean active = true;
        when(session.isActive()).thenReturn(active);
        assertEquals(active, wrapper.isActive());
    }

    /**
     * @return a mock call instance of a regular non-external call.
     */
    private Call getRegularCall() {
        Call regularCall = Mockito.mock(Call.class);
        when(regularCall.isExternalCall()).thenReturn(false);
        return regularCall;
    }

    private Intent getKeyEventIntent(int action, int code, boolean longPress) {
        KeyEvent e = new KeyEvent(action, code);
        if (longPress) {
            e = KeyEvent.changeFlags(e, KeyEvent.FLAG_LONG_PRESS);
        }

        Intent intent = new Intent();
        intent.putExtra(Intent.EXTRA_KEY_EVENT, e);
        return intent;
    }
}