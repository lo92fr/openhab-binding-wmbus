/**
 * Copyright (c) 2010-2021 Contributors to the openHAB project
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.openhab.binding.wmbus.handler;

import static org.openhab.binding.wmbus.WMBusBindingConstants.*;

import java.util.Collection;
import java.util.concurrent.ScheduledFuture;

import org.eclipse.jdt.annotation.NonNull;
import org.eclipse.jdt.annotation.Nullable;
import org.openhab.core.config.core.status.ConfigStatusMessage;
import org.openhab.core.thing.Bridge;
import org.openhab.core.thing.ThingStatus;
import org.openhab.io.transport.mbus.wireless.KeyStorage;
import org.openmuc.jmbus.MBusConnection;
import org.openmuc.jmbus.MBusConnection.MBusTcpBuilder;
import org.openmuc.jmbus.wireless.WMBusConnection.WMBusManufacturer;

/**
 * The {@link TCPBusBridgeHandler} class defines This class represents the WMBus bridge and handles general events for
 * the whole group of WMBus devices.
 *
 * @author Hanno - Felix Wagner - Initial contribution
 */

public class TCPBusBridgeHandler extends WMBusBridgeHandlerBase {

    private ScheduledFuture<?> initFuture;
    private @Nullable MBusConnection mBusConnection;

    public TCPBusBridgeHandler(Bridge bridge, KeyStorage keyStorage) {
        super(bridge, keyStorage);
    }

    /**
     * Connects to the WMBus radio module and updates bridge status.
     *
     * @see org.openhab.core.thing.binding.BaseThingHandler#initialize()
     */
    @Override
    public void initialize() {
        logger.debug("WMBusBridgeHandler: initialize()");

        updateStatus(ThingStatus.UNKNOWN);
        MBusTcpBuilder builder = MBusConnection.newTcpBuilder("192.168.254.51", 2460).setTimeout(5000)
                .setConnectionTimeout(10000);

        try {
            mBusConnection = builder.build();
            logger.debug("mbus:: open Sap Connection");
        } catch (Exception ex) {
            logger.debug("exception:" + ex.getMessage());
        }
        logger.debug("Connected to MBus tcp gateway");

        // success
        logger.debug("WMBusBridgeHandler: Initialization done! Setting bridge online");
        updateStatus(ThingStatus.ONLINE);
    }

    private static WMBusManufacturer parseManufacturer(String manufacturer) {
        switch (manufacturer.toLowerCase()) {
            case MANUFACTURER_AMBER:
                return WMBusManufacturer.AMBER;
            case MANUFACTURER_RADIO_CRAFTS:
                return WMBusManufacturer.RADIO_CRAFTS;
            case MANUFACTURER_IMST:
                return WMBusManufacturer.IMST;
            default:
                return null;
        }
    }

    @Override
    public void dispose() {
        super.dispose();
        logger.debug("WMBus bridge Handler disposed.");

        if (mBusConnection != null) {
            logger.debug("Close serial device connection");
            try {
                mBusConnection.close();
            } catch (Exception e) {
                logger.error("An exception occurred while closing the wmbusConnection", e);
            }
            mBusConnection = null;
        }
    }

    @Override
    public Collection<@NonNull ConfigStatusMessage> getConfigStatus() {
        // TODO Auto-generated method stub
        throw new RuntimeException("NIY");
    }
}
