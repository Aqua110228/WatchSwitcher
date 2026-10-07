/*  Copyright (C) 2015-2026 Andreas Böhler, Andreas Shimokawa, Carsten
    Pfeiffer, Cre3per, Daniel Dakhno, Daniele Gobbetti, Gordon Williams, José
    Rebelo, Sergey Trofimov, Taavi Eomäe, Uwe Hermann, Yoran Vulker, Thomas Kuehne

    This file is part of Gadgetbridge.

    Gadgetbridge is free software: you can redistribute it and/or modify
    it under the terms of the GNU Affero General Public License as published
    by the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Gadgetbridge is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU Affero General Public License for more details.

    You should have received a copy of the GNU Affero General Public License
    along with this program.  If not, see <https://www.gnu.org/licenses/>. */
package nodomain.freeyourgadget.gadgetbridge.service.btle;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Process;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import nodomain.freeyourgadget.gadgetbridge.GBApplication;
import nodomain.freeyourgadget.gadgetbridge.GBExceptionHandler;
import nodomain.freeyourgadget.gadgetbridge.R;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice;
import nodomain.freeyourgadget.gadgetbridge.impl.GBDevice.State;
import nodomain.freeyourgadget.gadgetbridge.service.DeviceSupport;
import nodomain.freeyourgadget.gadgetbridge.service.btle.actions.WriteAction;
import nodomain.freeyourgadget.gadgetbridge.util.GB;

/**
 * One queue/thread per connectable device.
 */
@SuppressLint("MissingPermission") // if we're using this, we have bluetooth permissions
public final class BtLEQueue implements Thread.UncaughtExceptionHandler {
    private final Logger LOG;
    private static final byte[] EMPTY = new byte[0];
    private static final AtomicLong QUEUE_COUNTER = new AtomicLong(0L);
    private static final long SERVER_ACTION_RESULT_TIMEOUT_SECONDS = 30L;
    private final long mQueueIdx;

    private final Object mGattMonitor;
    private final GBDevice mGbDevice;
    private final BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mBluetoothGatt;
    private BluetoothGattServer mBluetoothGattServer;
    private final Set<? extends BluetoothGattService> mSupportedServerServices;

    private final BlockingDeque<AbstractTransaction> mTransactions;
    private final AtomicBoolean mDisposed;
    private volatile boolean mAbortTransaction;
    private volatile boolean mAbortServerTransaction;
    private volatile boolean mPauseTransaction;

    private final Context mContext;
    private CountDownLatch mWaitForActionResultLatch;
    private CountDownLatch mWaitForServerActionResultLatch;
    private CountDownLatch mConnectionLatch;
    private BluetoothGattCharacteristic mWaitCharacteristic;
    private final NoThrowBluetoothGattCallback<InternalGattCallback> internalGattCallback;
    private final InternalGattServerCallback internalGattServerCallback;
    private final AbstractBTLEDeviceSupport mDeviceSupport;
    private final boolean mImplicitGattCallbackModify;
    private final boolean mSendWriteRequestResponse;

    private final boolean connectionForceLegacyGatt;
    private final Thread mDispatchThread;
    private final HandlerThread mReceiverThread;
    private final Handler mReceiverHandler;
    private final Handler mGattConnectTimeoutHandler;

    private class DispatchRunnable implements Runnable {
        @Override
        public void run() {
            LOG.debug("mDispatchThread: started thread {} for {}", getThreadLabel(), mGbDevice.getAddress());
            boolean crashed = false;

            while (!mDisposed.get() && !crashed) {
                try {
                    AbstractTransaction qTransaction = mTransactions.takeFirst();

                    if (!isConnected()) {
                        LOG.debug("mDispatchThread: not connected, waiting for connection ...");
                        // TODO: request connection and initialization from the outside and wait until finished
                        internalGattCallback.Delegate.reset();

                        // wait until the connection succeeds before running the actions
                        // Note that no automatic connection is performed. This has to be triggered
                        // on the outside typically by the DeviceSupport. The reason is that
                        // devices have different kinds of initializations and this class has no
                        // idea about them.
                        mConnectionLatch = new CountDownLatch(1);
                        mConnectionLatch.await();
                        mConnectionLatch = null;
                    }

                    if (qTransaction instanceof final ServerTransaction serverTransaction) {
                        internalGattServerCallback.setTransactionGattCallback(serverTransaction.getGattCallback());
                        mAbortServerTransaction = false;

                        for (final BtLEServerAction action : serverTransaction.getActions()) {
                            if (mAbortServerTransaction) { // got disconnected
                                LOG.info("server exec: Aborting running server transaction");
                                break;
                            }
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("server exec: {}", action);
                            }
                            // Create latch BEFORE running the action, because the
                            // callback (e.g. onNotificationSent) may fire immediately
                            // on the same thread or a different one.
                            mWaitForServerActionResultLatch = new CountDownLatch(1);
                            if (action.run(mBluetoothGattServer)) {
                                // check again, maybe due to some condition, action did not need to write, so we can't wait
                                boolean waitForResult = action.expectsResult();
                                if (waitForResult) {
                                    if (!mWaitForServerActionResultLatch.await(SERVER_ACTION_RESULT_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                                        LOG.error("Timed out waiting for server action result: {}", action);
                                        mAbortServerTransaction = true;
                                        handleDisconnected(0x93 /* BluetoothGatt.GATT_CONNECTION_TIMEOUT */);
                                    }
                                    mWaitForServerActionResultLatch = null;
                                    if (mAbortServerTransaction) {
                                        break;
                                    }
                                }
                            } else {
                                LOG.error("server exec: action returned false - {}", action);
                                mWaitForServerActionResultLatch = null;
                                break; // abort the transaction
                            }
                        }
                    }

                    if (qTransaction instanceof final Transaction transaction) {
                        LOG.trace("exec: Changing GattCallback for {}? {}", transaction.getTaskName(), transaction.isModifyGattCallback());
                        if (mImplicitGattCallbackModify || transaction.isModifyGattCallback()) {
                            internalGattCallback.Delegate.setTransactionGattCallback(transaction.getGattCallback());
                        }
                        mAbortTransaction = false;
                        // Run all actions of the transaction until one doesn't succeed
                        final List<BtLEAction> actions = transaction.getActions();
                        final int actionMaxIndex = actions.size();
                        for (int actionIndex = 1; actionIndex <= actionMaxIndex; actionIndex++) {
                            final BtLEAction action = actions.get(actionIndex - 1);
                            if (mAbortTransaction) { // got disconnected
                                LOG.info("exec: aborting running transaction {}", transaction.getTaskName());
                                break;
                            }
                            while ((action instanceof WriteAction) && mPauseTransaction && !mAbortTransaction) {
                              LOG.info("exec: pausing WriteAction #{}", actionIndex);
                              try {
                                  Thread.sleep(100L);
                              } catch (Exception e) {
                                  LOG.info("exec: exception during sleep", e);
                                  break;
                              }
                            }
                            mWaitCharacteristic = action.getCharacteristic();
                            mWaitForActionResultLatch = new CountDownLatch(1);
                            if (LOG.isDebugEnabled()) {
                                LOG.debug("exec: #{} for {} {}",
                                        actionIndex, transaction.getTaskName(), action);
                            }
                            if (action instanceof final GattListenerAction listenerAction) {
                                // this special action overwrites the transaction gatt listener (if any), it must
                                // always be the last action in the transaction
                                internalGattCallback.Delegate.setTransactionGattCallback(listenerAction.getGattCallback());
                            }
                            if (action.run(mBluetoothGatt)) {
                                // check again, maybe due to some condition, action did not need to write, so we can't wait
                                boolean waitForResult = action.expectsResult();
                                if (waitForResult) {
                                    mWaitForActionResultLatch.await();
                                    mWaitForActionResultLatch = null;
                                    if (mAbortTransaction) {
                                        break;
                                    }
                                }
                            } else {
                                LOG.error("exec: #{} returned false, cancelling further actions in transaction - {}", actionIndex, action);
                                break; // abort the transaction
                            }
                        }
                    }
                } catch (InterruptedException ex) {
                    mConnectionLatch = null;
                    LOG.debug("mDispatchThread: thread {} got interrupted", getThreadLabel());
                } catch (Throwable ex) {
                    LOG.error("mDispatchThread: thread {} died", getThreadLabel(), ex);
                    crashed = true;
                    mConnectionLatch = null;
                } finally {
                    mWaitForActionResultLatch = null;
                    mWaitCharacteristic = null;
                }
            }
            LOG.debug("mDispatchThread: thread {} finished", getThreadLabel());
        }
    }

    BtLEQueue(@NonNull GBDevice gbDevice, @NonNull Set<? extends BluetoothGattService> supportedServerServices, @NonNull AbstractBTLEDeviceSupport deviceSupport) {
        mQueueIdx = QUEUE_COUNTER.getAndIncrement();

        LOG = LoggerFactory.getLogger(BtLEQueue.class.getName() + "_" + mQueueIdx);

        LOG.debug("ctor: initializing BtLEQueue_{} for {} ({})", mQueueIdx, gbDevice.getAddress(), gbDevice.getName());

        // 1) apply all settings
        mBluetoothAdapter = deviceSupport.getBluetoothAdapter();
        mContext = deviceSupport.getContext();
        mDeviceSupport = deviceSupport;
        mGbDevice = gbDevice;
        mImplicitGattCallbackModify = deviceSupport.getImplicitCallbackModify();
        mPauseTransaction = false;
        mSendWriteRequestResponse = deviceSupport.getSendWriteRequestResponse();
        mSupportedServerServices = supportedServerServices;
        // #5414 - some older android versions misbehave with the new constructor
        connectionForceLegacyGatt = deviceSupport.getDevicePrefs().getConnectionForceLegacyGatt();
        mGattConnectTimeoutHandler = new Handler(Looper.getMainLooper());

        // 2) create new objects
        mDisposed = new AtomicBoolean(false);
        mGattMonitor = new Object();
        mTransactions = new LinkedBlockingDeque<>();
        internalGattCallback = new NoThrowBluetoothGattCallback<>(new InternalGattCallback(deviceSupport));
        internalGattServerCallback = new InternalGattServerCallback(deviceSupport);
        mDispatchThread = new Thread(new DispatchRunnable(), "BtLEQueue_" + mQueueIdx + "_out");
        mDispatchThread.setUncaughtExceptionHandler(this);

        // 3) start the thread
        mDispatchThread.start();

        // 4) handler thread ensure serial processing and informative thread name in the log
        if (GBApplication.isRunningOreoOrLater() && !connectionForceLegacyGatt) {
            mReceiverThread = new HandlerThread("BtLEQueue_" + mQueueIdx + "_in");
            mReceiverThread.setUncaughtExceptionHandler(this);
            mReceiverThread.start();
            mReceiverHandler = new Handler(mReceiverThread.getLooper());
            mReceiverHandler.post(() -> LOG.debug("mReceiverThread: started thread {} for {}", getThreadLabel(), gbDevice.getAddress()));
        } else {
            mReceiverThread = null;
            mReceiverHandler = null;
        }
    }

    @Override
    public void uncaughtException(@NonNull Thread t, @NonNull Throwable e) {
        LOG.error("exception in thread {} (threadId={})", t.getName(), t.getId(), e);

        // TODO implement actual exception handling for mDispatchThread and mReceiverThread
        new GBExceptionHandler(null, true).uncaughtException(t, e);
    }

    @NonNull
    static String getThreadLabel() {
        Thread thread = Thread.currentThread();
        int pid = Process.myPid();
        int tid = Process.myTid();
        long threadId = thread.getId();
        return thread.getName() + " (threadId=" + threadId + ", "+ pid + "-" + tid + ")";
    }

    boolean isConnected() {
        State state = mGbDevice.getState();
        boolean gatt = (mBluetoothGatt != null);
        boolean dispatch = mDispatchThread.isAlive();
        boolean receiver = (mReceiverThread == null || mReceiverThread.isAlive());
        if (state.equalsOrHigherThan(State.CONNECTED) && gatt && dispatch && receiver) {
            return true;
        }
        LOG.debug("not connected: state={} gatt={} dispatch={} receiver={}", state, gatt, dispatch, receiver);
        return false;
    }

    /**
     * Connects to the given remote {@link BluetoothDevice}. Note that this does not perform any device
     * specific initialization. This should be done in the specific {@link DeviceSupport}
     * class.
     *
     * @return {@code true} whether the connection attempt was successfully triggered and {@code false} if that failed or if there is already a connection
     */
    boolean connect() {
        synchronized (mGattMonitor) {
            State state = mGbDevice.getState();
            if (state.equalsOrHigherThan(State.CONNECTING)) {
                LOG.warn("connect: ignored, state is {}", state);
                return false;
            } else if (mBluetoothGatt != null) {
                LOG.warn("connect: ignored, mBluetoothGatt isn't null");
                return false;
            } else if (mDisposed.get()) {
                LOG.error("connect: queue has already been disposed");
                String message = mContext.getString(R.string.error_queue_is_dead);
                throw new IllegalStateException(message);
            } else if (!mDispatchThread.isAlive()) {
                LOG.error("connect: mDispatchThread {} is dead", mDispatchThread.getName());
                String message = mContext.getString(R.string.error_sender_is_dead);
                throw new IllegalStateException(message);
            } else if (mReceiverThread != null && !mReceiverThread.isAlive()) {
                LOG.error("connect: mReceiverThread {} is dead", mReceiverThread.getName());
                String message = mContext.getString(R.string.error_receiver_is_dead);
                throw new IllegalStateException(message);
            }

            if (connectImp()) {
                setDeviceConnectionState(State.CONNECTING);
                return true;
            } else {
                return false;
            }
        }
    }

    private boolean connectImp() {
        mPauseTransaction = false;
        LOG.info("conenctImp: Attempting to connect to {} ({})", mGbDevice.getAddress(), mGbDevice.getName());
        final boolean lowPower = GBApplication.getDevicePrefs(mGbDevice).getConnectionPriorityLowPower();
        // 30 seconds: the longest allowed ATT transaction timeout
        // 32 seconds: the longest allowed BLE connection timeout for an established  connection (supervision timeout)
        // => wait a few more seconds for establishing a new connection
        mGattConnectTimeoutHandler.postDelayed(() -> {
            LOG.warn("conenctImp: Timed out connecting to GATT for {} ({})", mGbDevice.getAddress(), mGbDevice.getName());
            handleDisconnected(0x93 /* BluetoothGatt.GATT_CONNECTION_TIMEOUT */);
        }, lowPower ? 45000L : 5000L);

        mBluetoothAdapter.cancelDiscovery();
        BluetoothDevice remoteDevice = mBluetoothAdapter.getRemoteDevice(mGbDevice.getAddress());
        if(!mSupportedServerServices.isEmpty()) {
            BluetoothManager bluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
            if (bluetoothManager == null) {
                LOG.error("conenctImp: Error getting BluetoothManager");
                return false;
            }
            mBluetoothGattServer = bluetoothManager.openGattServer(mContext, internalGattServerCallback);
            if (mBluetoothGattServer == null) {
                LOG.error("conenctImp: Error opening BluetoothGattServer");
                return false;
            }
            for(BluetoothGattService service : mSupportedServerServices) {
                mBluetoothGattServer.addService(service);
            }
        }


        // connectGatt with true doesn't really work ;( too often connection problems
        if (GBApplication.isRunningOreoOrLater() && !connectionForceLegacyGatt) {
            mBluetoothGatt = remoteDevice.connectGatt(mContext, false,
                    internalGattCallback, BluetoothDevice.TRANSPORT_LE,
                    mGbDevice.getDeviceCoordinator().getBlePhyMask(), mReceiverHandler);
        } else {
            mBluetoothGatt = remoteDevice.connectGatt(mContext, false,
                    internalGattCallback, BluetoothDevice.TRANSPORT_LE);
        }

        return mBluetoothGatt != null;
    }

    private void setDeviceConnectionState(final State newState) {
        LOG.debug("setDeviceConnectionState: from {} to {}", mGbDevice.getState(), newState);
        mGbDevice.setState(newState);
        mGbDevice.sendDeviceUpdateIntent(mContext, GBDevice.DeviceUpdateSubject.CONNECTION_STATE);
    }

    void disconnect() {
        synchronized (mGattMonitor) {
            LOG.debug("disconnect: BtLEQueue_{}", mQueueIdx);
            mGattConnectTimeoutHandler.removeCallbacksAndMessages(null);
            BluetoothGatt gatt = mBluetoothGatt;
            if (gatt != null) {
                mBluetoothGatt = null;
                LOG.info("disconnect: disconnecting BluetoothGatt");
                gatt.disconnect();
                gatt.close();
            }
            mPauseTransaction = false;
            BluetoothGattServer gattServer = mBluetoothGattServer;
            if (gattServer != null) {
                mBluetoothGattServer = null;
                LOG.info("disconnect: disconnecting BluetoothGattServer");
                gattServer.clearServices();
                gattServer.close();
            }

            if (mGbDevice.getState() != State.NOT_CONNECTED) {
                setDeviceConnectionState(State.NOT_CONNECTED);
            }
        }
    }

    private void handleDisconnected(int status) {
        LOG.debug("handleDisconnected: {}", BleNamesResolver.getStatusString(status));
        internalGattCallback.Delegate.reset();
        mTransactions.clear();
        mPauseTransaction = false;
        mAbortTransaction = true;
        mAbortServerTransaction = true;
        mGattConnectTimeoutHandler.removeCallbacksAndMessages(null);
        final CountDownLatch clientLatch = mWaitForActionResultLatch;
        if (clientLatch != null) {
            clientLatch.countDown();
        }
        final CountDownLatch serverLatch = mWaitForServerActionResultLatch;
        if (serverLatch != null) {
            serverLatch.countDown();
        }

        boolean forceDisconnect;
        //noinspection EnhancedSwitchMigration
        switch(status) {
            case 0x81: // 0x81 129 GATT_INTERNAL_ERROR
            case 0x85: // 0x85 133 GATT_ERROR
                // Bluetooth stack has a fundamental problem:
            case 0x8: // BluetoothGatt.GATT_INSUFFICIENT_AUTHORIZATION only on API 35
            case BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION:
            case BluetoothGatt.GATT_INSUFFICIENT_ENCRYPTION:
                // a Bluetooth bonding / pairing issue
                // some devices report AUTHORIZATION instead of TIMEOUT during connection setup
            case 0x93: // BluetoothGatt.GATT_CONNECTION_TIMEOUT only on API 35
                forceDisconnect = true;
                break;
            default:
                forceDisconnect = false;
        }

        if (forceDisconnect) {
            // TODO: There is likely a race condition, the device and the gatt object should not be
            // null at this point. For multi queue objects, this seems to break, don't remove this
            // check.
            BluetoothDevice device = null;
            if (mBluetoothGatt != null) {
                device = mBluetoothGatt.getDevice();
            }
            LOG.warn("handleDisconnected: unhealthy disconnect {} {}", device == null ?  "<UNKNOWN>" : device.getAddress(),
                    BleNamesResolver.getStatusString(status));
        } else if (mBluetoothGatt != null) {
            // try to reconnect immediately
            if (mDeviceSupport.getAutoReconnect()) {
                if (mDeviceSupport.getScanReconnect()) {
                    // connect() would first disconnect() anyway
                    forceDisconnect = true;
                } else {
                    LOG.info("handleDisconnected: enabling automatic immediate BLE reconnection");
                    mPauseTransaction = false;
                    if (mBluetoothGatt.connect()) {
                        setDeviceConnectionState(State.CONNECTING);
                    } else {
                        forceDisconnect = true;
                    }
                }
            } else {
                forceDisconnect = true;
            }
        }

        if (forceDisconnect) {
            disconnect();
        }

        if (mBluetoothGatt == null) {
            if (mDeviceSupport.getAutoReconnect()) {
                // don't reconnect immediately to give the Bluetooth stack some time to settle down
                // use BluetoothConnectReceiver or AutoConnectIntervalReceiver instead
                if (mDeviceSupport.getScanReconnect()) {
                    LOG.info("handleDisconnected: waiting for BLE scan before attempting reconnection");
                    setDeviceConnectionState(State.WAITING_FOR_SCAN);
                } else {
                    LOG.info("handleDisconnected: enabling automatic delayed BLE reconnection");
                    setDeviceConnectionState(State.WAITING_FOR_RECONNECT);
                }
            } else if (!forceDisconnect) {
                setDeviceConnectionState(State.NOT_CONNECTED);
            }
        }
    }

    public void setPaused(boolean paused) {
      mPauseTransaction = paused;
    }

    void dispose() {
        if (mDisposed.getAndSet(true)) {
            LOG.warn("dispose: was called repeatedly");
            return;
        }

        disconnect();

        if (mReceiverThread != null && mReceiverThread.isAlive()) {
            mReceiverHandler.post(() -> {
                LOG.debug("dispose: finish thread {}", getThreadLabel());
                mReceiverThread.quitSafely();
            });
        }

        if (mDispatchThread != null) {
            mDispatchThread.interrupt();
        }
    }

    /**
     * Adds a transaction to the end of the queue.
     */
    void add(Transaction transaction) {
        LOG.debug("add: {}", transaction);
        if (!transaction.isEmpty()) {
            mTransactions.addLast(transaction);
        }
    }

    /**
     * Aborts the currently running transaction
     */
    public void abortCurrentTransaction() {
        mAbortTransaction = true;
        final CountDownLatch latch = mWaitForActionResultLatch;
        if (latch != null) {
            latch.countDown();
        }
    }

    /**
     * Adds a serverTransaction to the end of the queue
     */
    void add(ServerTransaction transaction) {
        LOG.debug("add server: {}", transaction);
        if(!transaction.isEmpty()) {
            mTransactions.addLast(transaction);
        }
    }

    /**
     * Adds a transaction to the beginning of the queue.
     * Note that actions of the *currently executing* transaction
     * will still be executed before the given transaction.
     */
    void insert(Transaction transaction) {
        LOG.debug("about to insert: {}", transaction);
        if (!transaction.isEmpty()) {
            mTransactions.addFirst(transaction);
        }
    }

    public void clear() {
        mTransactions.clear();
    }

    /** @noinspection BooleanMethodIsAlwaysInverted*/
    private boolean checkCorrectGattInstance(BluetoothGatt gatt, String where) {
        if (gatt != mBluetoothGatt && mBluetoothGatt != null) {
            LOG.warn("Ignoring event from wrong BluetoothGatt instance: {}; {}", where, gatt);
            return false;
        }
        return true;
    }

    /** @noinspection BooleanMethodIsAlwaysInverted*/
    private boolean checkCorrectBluetoothDevice(BluetoothDevice device) {
        //BluetoothDevice clientDevice = mBluetoothAdapter.getRemoteDevice(mGbDevice.getAddress());

        if(!device.getAddress().equals(mGbDevice.getAddress())) { // != clientDevice && clientDevice != null) {
            LOG.warn("Ignoring request from wrong BluetoothDevice: {}", device.getAddress());
            return false;
        }
        return true;
    }

    // Implements callback methods for GATT events that the app cares about.  For example,
    // connection change and services discovered.
    private final class InternalGattCallback extends BluetoothGattCallback {
        private
        @Nullable
        GattCallback mTransactionGattCallback;
        private final GattCallback mExternalGattCallback;

        InternalGattCallback(GattCallback externalGattCallback) {
            mExternalGattCallback = externalGattCallback;
        }

        void setTransactionGattCallback(@Nullable GattCallback callback) {
            mTransactionGattCallback = callback;
        }

        private GattCallback getCallbackToUse() {
            final GattCallback callback = mTransactionGattCallback;
            if (callback != null) {
                return callback;
            }
            return mExternalGattCallback;
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            final int bondState = gatt.getDevice().getBondState();
            LOG.debug("onConnectionStateChange: {} {} {}",
                    BleNamesResolver.getStatusString(status), BleNamesResolver.getStateString(newState),
                    BleNamesResolver.getBondStateString(bondState));

            if (!checkCorrectGattInstance(gatt, "onConnectionStateChange check#1")) {
                return;
            }

            synchronized (mGattMonitor) {
                if (mBluetoothGatt == null) {
                    mBluetoothGatt = gatt;
                }
            }

            if (!checkCorrectGattInstance(gatt, "onConnectionStateChange check#2")) {
                return;
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                LOG.warn("onConnectionStateChange with status: {}", BleNamesResolver.getStatusString(status));
            }

            final GattCallback callback = getCallbackToUse();
            if (callback != null) {
                try {
                    callback.onConnectionStateChange(gatt, status, newState);
                } catch (Exception ex) {
                    LOG.error("onConnectionStateChange: GattCallback failed", ex);
                }
            }

            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    LOG.info("onConnectionStateChange: Connected to {}", gatt.getDevice().getAddress());
                    mGattConnectTimeoutHandler.removeCallbacksAndMessages(null);
                    setDeviceConnectionState(State.CONNECTED);

                    // discover services in the main thread (appears to fix Samsung connection problems)
                    final long delayMillis = mDeviceSupport.getServiceDiscoveryDelay(bondState != BluetoothDevice.BOND_NONE);
                    new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            BluetoothGatt bluetoothGatt = mBluetoothGatt;
                            if (bluetoothGatt == null) {
                                return;
                            }
                            List<BluetoothGattService> services = bluetoothGatt.getServices();
                            if (services != null && !services.isEmpty()) {
                                LOG.info("onConnectionStateChange: using cached services, skipping discovery");
                                onServicesDiscovered(bluetoothGatt, BluetoothGatt.GATT_SUCCESS);
                            } else {
                                LOG.debug("onConnectionStateChange: discoverServices");
                                bluetoothGatt.discoverServices();
                            }
                        }
                    }, delayMillis);
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    LOG.info("onConnectionStateChange: Disconnected from BluetoothGattServer");
                    synchronized (mGattMonitor) {
                        handleDisconnected(status);
                    }
                    break;
                case BluetoothProfile.STATE_CONNECTING:
                    LOG.info("onConnectionStateChange: Connecting to BluetoothGattServer ...");
                    setDeviceConnectionState(State.CONNECTING);
                    break;
            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            LOG.debug("onServicesDiscovered: {}", BleNamesResolver.getStatusString(status));

            if (!checkCorrectGattInstance(gatt, "onServicesDiscovered check")) {
                return;
            }

            if (status == BluetoothGatt.GATT_SUCCESS) {
                final GattCallback callback = getCallbackToUse();
                if (callback != null) {
                    // only propagate the successful event
                    try {
                        callback.onServicesDiscovered(gatt);
                    } catch (Exception ex) {
                        LOG.error("onServicesDiscovered: GattCallback failed", ex);
                    }
                }
                final CountDownLatch latch = mConnectionLatch;
                if (latch != null) {
                    latch.countDown();
                }
            } else {
                LOG.warn("onServicesDiscovered with status: {}", BleNamesResolver.getStatusString(status));
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            LOG.debug("onCharacteristicWrite: {} {}", characteristic.getUuid(), BleNamesResolver.getStatusString(status));
            if (!checkCorrectGattInstance(gatt, "onCharacteristicWrite check")) {
                return;
            }

            final GattCallback callback = getCallbackToUse();
            if (callback != null) {
                try {
                    callback.onCharacteristicWrite(gatt, characteristic, status);
                } catch (Exception ex) {
                    LOG.error("onCharacteristicWrite: GattCallback failed", ex);
                }
            }
            checkWaitingCharacteristic(characteristic, status);
        }

        @Override
        public void onMtuChanged(BluetoothGatt gatt, int mtu, int status) {
            LOG.debug("onMtuChanged: mtu={} {}", mtu, BleNamesResolver.getStatusString(status));

            if (!checkCorrectGattInstance(gatt, "onMtuChanged check")) {
                return;
            }

            final GattCallback callback = getCallbackToUse();
            if (callback != null) {
                try {
                    callback.onMtuChanged(gatt, mtu, status);
                } catch (Exception ex) {
                    LOG.error("onMtuChanged: GattCallback failed", ex);
                }
            }

            final CountDownLatch latch = mWaitForActionResultLatch;
            if (latch != null) {
                latch.countDown();
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            byte[] value = emulateMemorySafeValue(characteristic, status);
            onCharacteristicRead(gatt, characteristic, value, status);
        }

        @Override
        public void onCharacteristicRead(@NonNull BluetoothGatt gatt,
                                         @NonNull BluetoothGattCharacteristic characteristic,
                                         @NonNull byte[] value, int status) {
            if (LOG.isDebugEnabled()) {
                String content = GB.hexdump(value);
                LOG.debug(
                        "onCharacteristicRead: {} {} <@ {}", BleNamesResolver.getStatusString(status),
                        characteristic.getUuid(), content
                );
            }

            if (!checkCorrectGattInstance(gatt, "onCharacteristicRead check")) {
                return;
            }

            final GattCallback callback = getCallbackToUse();
            if (callback != null) {
                try {
                    callback.onCharacteristicRead(gatt, characteristic, value, status);
                } catch (Exception ex) {
                    LOG.error("onCharacteristicRead: GattCallback failed", ex);
                }
            }
            checkWaitingCharacteristic(characteristic, status);
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            byte[] value = emulateMemorySafeValue(descriptor, status);
            onDescriptorRead(gatt, descriptor, status, value);
        }

        @Override
        public void onDescriptorRead(@NonNull BluetoothGatt gatt, @NonNull BluetoothGattDescriptor descriptor, int status, @NonNull byte[] value) {
            if (LOG.isDebugEnabled()) {
                String content = GB.hexdump(value);
                LOG.debug("onDescriptorRead: {} {} <@ {}", BleNamesResolver.getStatusString(status),
                        descriptor.getUuid(), content);
            }

            if (!checkCorrectGattInstance(gatt, "onDescriptorRead check")) {
                return;
            }

            final GattCallback callback = getCallbackToUse();
            if (callback != null) {
                try {
                    callback.onDescriptorRead(gatt, descriptor, status, value);
                } catch (Exception ex) {
                    LOG.error("onDescriptorRead: GattCallback failed", ex);
                }
            }
            checkWaitingCharacteristic(descriptor.getCharacteristic(), status);
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            LOG.debug("onDescriptorWrite: {} {}", descriptor.getUuid(), BleNamesResolver.getStatusString(status));
            if (!checkCorrectGattInstance(gatt, "onDescriptorWrite check")) {
                return;
            }

            final GattCallback callback = getCallbackToUse();
            if (callback != null) {
                try {
                    callback.onDescriptorWrite(gatt, descriptor, status);
                } catch (Exception ex) {
                    LOG.error("onDescriptorWrite: GattCallback failed", ex);
                }
            }
            checkWaitingCharacteristic(descriptor.getCharacteristic(), status);
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            byte[] value = emulateMemorySafeValue(characteristic, BluetoothGatt.GATT_SUCCESS);
            onCharacteristicChanged(gatt, characteristic, value);
        }

        @Override
        public void onCharacteristicChanged(@NonNull BluetoothGatt gatt,
                                            @NonNull BluetoothGattCharacteristic characteristic,
                                            @NonNull byte[] value) {
            if (LOG.isDebugEnabled()) {
                String content = GB.hexdump(value);
                LOG.debug("onCharacteristicChanged: {} <@ {}", characteristic.getUuid(), content);
            }
            if (!checkCorrectGattInstance(gatt, "onCharacteristicChanged check")) {
                return;
            }

            final GattCallback callback = getCallbackToUse();
            if (callback != null) {
                try {
                    callback.onCharacteristicChanged(gatt, characteristic, value);
                } catch (Exception ex) {
                    LOG.error("onCharacteristicChanged: GattCallback failed", ex);
                }
            } else {
                LOG.info("onCharacteristicChanged: ignored, no GattCallback registered");
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            LOG.debug("onReadRemoteRssi: rssi={} {}", rssi, BleNamesResolver.getStatusString(status));
            if (!checkCorrectGattInstance(gatt, "onReadRemoteRssi check")) {
                return;
            }

            final GattCallback callback = getCallbackToUse();
            if (callback != null) {
                try {
                    callback.onReadRemoteRssi(gatt, rssi, status);
                } catch (Exception ex) {
                    LOG.error("onReadRemoteRssi: GattCallback failed", ex);
                }
            }
        }

        @Override
        public void onReliableWriteCompleted(BluetoothGatt gatt, int status) {
            LOG.debug("onReliableWriteCompleted: {}", BleNamesResolver.getStatusString(status));

            if (!checkCorrectGattInstance(gatt, "onReliableWriteCompleted check")) {
                return;
            }

            final GattCallback callback = getCallbackToUse();
            if (callback != null) {
                try {
                    callback.onReliableWriteCompleted(gatt, status);
                } catch (Exception ex) {
                    LOG.error("onReliableWriteCompleted: GattCallback failed", ex);
                }
            }

            final CountDownLatch latch = mWaitForActionResultLatch;
            if (latch != null) {
                latch.countDown();
            }
        }

        @Override
        @RequiresApi(Build.VERSION_CODES.O)
        public void onPhyRead(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
            LOG.debug("onPhyRead: txPhy={} rxPhy={} {}", txPhy, rxPhy,
                    BleNamesResolver.getStatusString(status));

            if (!checkCorrectGattInstance(gatt, "onPhyRead check")) {
                return;
            }

            final GattCallback callback = getCallbackToUse();
            if (callback != null) {
                try {
                    callback.onPhyRead(gatt, txPhy, rxPhy, status);
                } catch (Exception ex) {
                    LOG.error("onPhyRead: GattCallback failed", ex);
                }
            }

            final CountDownLatch latch = mWaitForActionResultLatch;
            if (latch != null) {
                latch.countDown();
            }
        }

        @Override
        @RequiresApi(Build.VERSION_CODES.O)
        public void onPhyUpdate(BluetoothGatt gatt, int txPhy, int rxPhy, int status) {
            LOG.debug("onPhyUpdate: tx={} rx={} {}", txPhy, rxPhy,
                    BleNamesResolver.getStatusString(status));

            if (!checkCorrectGattInstance(gatt, "onPhyUpdate check")) {
                return;
            }

            final GattCallback callback = getCallbackToUse();
            if (callback != null) {
                try {
                    callback.onPhyUpdate(gatt, txPhy, rxPhy, status);
                } catch (Exception ex) {
                    LOG.error("onPhyUpdate: GattCallback failed", ex);
                }
            }

            // not all updates are triggered by GB:
            // can't use mWaitForActionResultLatch here
        }

        @Override
        @RequiresApi(Build.VERSION_CODES.S)
        public void onServiceChanged(@NonNull BluetoothGatt gatt) {
            LOG.debug("onServiceChanged");

            if (!checkCorrectGattInstance(gatt, "onServiceChanged check")) {
                return;
            }

            final GattCallback callback = getCallbackToUse();
            if (callback != null) {
                try {
                    callback.onServiceChanged(gatt);
                } catch (Exception ex) {
                    LOG.error("onServiceChanged: GattCallback failed", ex);
                }
            }
        }

        private void checkWaitingCharacteristic(BluetoothGattCharacteristic characteristic, int status) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                if (characteristic != null) {
                    LOG.warn("checkWaitingCharacteristic: failed BtLEAction, aborting transaction: {} {}", characteristic.getUuid(), BleNamesResolver.getStatusString(status));
                }
                mAbortTransaction = true;
            }
            final BluetoothGattCharacteristic waitCharacteristic = mWaitCharacteristic;
            if (characteristic != null && waitCharacteristic != null && characteristic.getUuid().equals(waitCharacteristic.getUuid())) {
                final CountDownLatch resultLatch = mWaitForActionResultLatch;
                if (resultLatch != null) {
                    resultLatch.countDown();
                }
            } else {
                if (waitCharacteristic != null) {
                    LOG.error(
                            "checkWaitingCharacteristic: mismatched characteristic received: {}",
                            (characteristic != null && characteristic.getUuid() != null) ? characteristic.getUuid().toString() : "(null)"
                    );
                }
            }
        }

        void reset() {
            if (LOG.isDebugEnabled()) {
                LOG.debug("reset: set internal GattCallback to null");
            }
            mTransactionGattCallback = null;
        }

        /// helper to emulate Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU on older APIs
        private static byte[] emulateMemorySafeValue(BluetoothGattCharacteristic characteristic,
                                              int status){
            if(status == BluetoothGatt.GATT_SUCCESS) {
                byte[] value = characteristic.getValue();
                if (value != null) {
                    return value.clone();
                }
            }
            return EMPTY;
        }

        /// helper to emulate Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU on older APIs
        private static byte[] emulateMemorySafeValue(BluetoothGattDescriptor descriptor,
                                              int status){
            if(status == BluetoothGatt.GATT_SUCCESS) {
                byte[] value = descriptor.getValue();
                if (value != null) {
                    return value.clone();
                }
            }
            return EMPTY;
        }
    }

    // Implements callback methods for GATT server events that the app cares about.  For example,
    // connection change and read/write requests.
    private final class InternalGattServerCallback extends BluetoothGattServerCallback {
        private
        @Nullable
        GattServerCallback mTransactionGattCallback;
        private final GattServerCallback mExternalGattServerCallback;

        InternalGattServerCallback(GattServerCallback externalGattServerCallback) {
            mExternalGattServerCallback = externalGattServerCallback;
        }

        void setTransactionGattCallback(@Nullable GattServerCallback callback) {
            mTransactionGattCallback = callback;
        }

        private GattServerCallback getCallbackToUse() {
            final GattServerCallback callback = mTransactionGattCallback;
            if (callback != null) {
                return callback;
            }
            return mExternalGattServerCallback;
        }

        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            LOG.debug("server onConnectionStateChange: {} {}",
                    BleNamesResolver.getStatusString(status),
                    BleNamesResolver.getStateString(newState));

            if(!checkCorrectBluetoothDevice(device)) {
                return;
            }

            if (status != BluetoothGatt.GATT_SUCCESS) {
                LOG.warn("server onConnectionStateChange with status: {}", BleNamesResolver.getStatusString(status));
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattCharacteristic characteristic) {
            if(!checkCorrectBluetoothDevice(device)) {
                return;
            }
            LOG.debug("server onCharacteristicReadRequest: {} requestId={} offset={} {}",
                    device.getAddress(), requestId, offset, characteristic.getUuid());
            final GattServerCallback callback = getCallbackToUse();
            if (callback != null) {
                try {
                    callback.onCharacteristicReadRequest(device, requestId, offset, characteristic);
                } catch (Exception ex) {
                    LOG.error("server onCharacteristicReadRequest: GattServerCallback failed", ex);
                }
            }
        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device, int requestId, BluetoothGattCharacteristic characteristic, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            if(!checkCorrectBluetoothDevice(device)) {
                return;
            }

            if (LOG.isDebugEnabled()) {
                String content = GB.hexdump(value);
                LOG.debug("server onCharacteristicWriteRequest: {} requestId={} preparedWrite={} responseNeeded={} offset={} {} <@ {}",
                        device.getAddress(), requestId, preparedWrite, responseNeeded, offset, characteristic.getUuid(), content);
            }

            boolean success = false;
            final GattServerCallback callback = getCallbackToUse();
            if (callback != null) {
                try {
                    success = callback.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
                } catch (Exception ex) {
                    LOG.error("server onCharacteristicWriteRequest: GattServerCallback failed", ex);
                }
            }
            if (responseNeeded && mSendWriteRequestResponse) {
                mBluetoothGattServer.sendResponse(device, requestId, success ? BluetoothGatt.GATT_SUCCESS : BluetoothGatt.GATT_FAILURE, 0, EMPTY);
            }
        }

        @Override
        public void onDescriptorReadRequest(BluetoothDevice device, int requestId, int offset, BluetoothGattDescriptor descriptor) {
            if(!checkCorrectBluetoothDevice(device)) {
                return;
            }
            LOG.debug("server onDescriptorReadRequest: {} requestId={} offset={} {} ", device.getAddress(), requestId, offset, descriptor.getUuid());
            final GattServerCallback callback = getCallbackToUse();
            if (callback != null) {
                try {
                    callback.onDescriptorReadRequest(device, requestId, offset, descriptor);
                } catch (Exception ex) {
                    LOG.error("server onDescriptorReadRequest: GattServerCallback failed", ex);
                }
            }
        }

        @Override
        public void onDescriptorWriteRequest(BluetoothDevice device, int requestId, BluetoothGattDescriptor descriptor, boolean preparedWrite, boolean responseNeeded, int offset, byte[] value) {
            if(!checkCorrectBluetoothDevice(device)) {
                return;
            }

            if (LOG.isDebugEnabled()) {
                String content = GB.hexdump(value);
                LOG.debug("server onDescriptorWriteRequest: {} requestId={} preparedWrite={} responseNeeded={} offset={} {}  <@ {}",
                        device.getAddress(), requestId, preparedWrite, responseNeeded, offset, descriptor.getUuid(), content);
            }

            boolean success = false;
            final GattServerCallback callback = getCallbackToUse();
            if (callback != null) {
                try {
                    success = callback.onDescriptorWriteRequest(device, requestId, descriptor, preparedWrite, responseNeeded, offset, value);
                } catch (Exception ex) {
                    LOG.error("server onDescriptorWriteRequest: GattServerCallback failed", ex);
                }
            }
            if (responseNeeded && mSendWriteRequestResponse) {
                mBluetoothGattServer.sendResponse(device, requestId, success ? BluetoothGatt.GATT_SUCCESS : BluetoothGatt.GATT_FAILURE, 0, EMPTY);
            }
        }

        @Override
        public void onServiceAdded(int status, BluetoothGattService service) {
            LOG.debug("server onServiceAdded: {} {} instance={}",
                    BleNamesResolver.getStatusString(status),
                    service.getUuid(), service.getInstanceId());
        }

        @Override
        public void onExecuteWrite(BluetoothDevice device, int requestId, boolean execute) {
            LOG.debug("Sever.onExecuteWrite: {} requestId={} execute={}",
                    device.getAddress(), requestId, execute);
        }

        @Override
        public void onNotificationSent(BluetoothDevice device, int status) {
            LOG.debug("server onNotificationSent: {} {}",
                    device.getAddress(), BleNamesResolver.getStatusString(status));
            final CountDownLatch latch = mWaitForServerActionResultLatch;
            if (latch != null) {
                latch.countDown();
            }
        }

        @Override
        public void onMtuChanged(BluetoothDevice device, int mtu) {
            LOG.debug("server onMtuChanged: {} mtu={}", device.getAddress(), mtu);
        }

        @Override
        public void onPhyUpdate(BluetoothDevice device, int txPhy, int rxPhy, int status) {
            LOG.debug("server onPhyUpdate: {} txPhy={} rxPhy={} {}",
                    device.getAddress(), txPhy, rxPhy,
                    BleNamesResolver.getStatusString(status));
        }

        @Override
        public void onPhyRead(BluetoothDevice device, int txPhy, int rxPhy, int status) {
            LOG.debug("server onPhyRead: {} txPhy={} rxPhy={} {}",
                    device.getAddress(), txPhy, rxPhy,
                    BleNamesResolver.getStatusString(status));
        }
    }
}
