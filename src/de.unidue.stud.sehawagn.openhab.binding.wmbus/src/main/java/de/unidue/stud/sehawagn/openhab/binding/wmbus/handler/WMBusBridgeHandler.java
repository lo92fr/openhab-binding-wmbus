package de.unidue.stud.sehawagn.openhab.binding.wmbus.handler;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import org.eclipse.smarthome.config.core.status.ConfigStatusMessage;
import org.eclipse.smarthome.core.thing.Bridge;
import org.eclipse.smarthome.core.thing.ChannelUID;
import org.eclipse.smarthome.core.thing.ThingStatus;
import org.eclipse.smarthome.core.thing.ThingStatusDetail;
import org.eclipse.smarthome.core.thing.ThingTypeUID;
import org.eclipse.smarthome.core.thing.binding.ConfigStatusBridgeHandler;
import org.eclipse.smarthome.core.types.Command;
import org.openmuc.jmbus.wireless.WMBusConnection;
import org.openmuc.jmbus.wireless.WMBusConnection.WMBusSerialBuilder;
import org.openmuc.jmbus.wireless.WMBusConnection.WMBusSerialBuilder.WMBusManufacturer;
import org.openmuc.jmbus.wireless.WMBusMessage;
import org.openmuc.jmbus.wireless.WMBusMode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.unidue.stud.sehawagn.openhab.binding.wmbus.WMBusBindingConstants;
import de.unidue.stud.sehawagn.openhab.binding.wmbus.internal.WMBusReceiver;

/**
 * This class represents the WMBus bridge and handles general events for the whole group of WMBus devices.
 */
public class WMBusBridgeHandler extends ConfigStatusBridgeHandler {

    public final static Set<ThingTypeUID> SUPPORTED_THING_TYPES = Collections
            .singleton(WMBusBindingConstants.THING_TYPE_BRIDGE);

    private static final String DEVICE_STATE_ADDED = "added";

    private static final String DEVICE_STATE_CHANGED = "changed";

    private Logger logger = LoggerFactory.getLogger(WMBusBridgeHandler.class);

    private WMBusReceiver wmbusReceiver = null;
    private WMBusConnection wmbusConnection = null;

    private Map<String, WMBusMessage> knownDevices = new HashMap<>();

    private List<WMBusMessageListener> wmBusMessageListeners = new CopyOnWriteArrayList<>();

    public WMBusBridgeHandler(Bridge bridge) {
        super(bridge);
    }

    @Override
    public Collection<ConfigStatusMessage> getConfigStatus() {
        return Collections.emptyList(); // all good, otherwise add some messages
    }

    @Override
    public void handleCommand(ChannelUID channelUID, Command command) {
        // judging from the hue bridge, this seems to be not needed...?
        logger.debug("WARNING: Unexpected call of handleCommand(). Parameters are channelUID={} and command={}", channelUID, command);
    }

    /**
     * Connects to the WMBus radio module and updates bridge status.
     *
     * @see org.eclipse.smarthome.core.thing.binding.BaseThingHandler#initialize()
     */
    @Override
    public void initialize() {
        logger.debug("Initializing WMBus bridge handler.");

        // check serial device name
        if (!getConfig().containsKey(WMBusBindingConstants.CONFKEY_INTERFACE_NAME)
                || getConfig().get(WMBusBindingConstants.CONFKEY_INTERFACE_NAME) == null
                || ((String) getConfig().get(WMBusBindingConstants.CONFKEY_RADIO_MODE)).isEmpty()) {
            logger.error("Cannot open WMBus device. Serial device name not given.");
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR, "Cannot open WMBus device. Serial device name not given.");
            return;
        }

        // check radio mode
        if (!getConfig().containsKey(WMBusBindingConstants.CONFKEY_RADIO_MODE)
                || getConfig().get(WMBusBindingConstants.CONFKEY_RADIO_MODE) == null
                || ((String) getConfig().get(WMBusBindingConstants.CONFKEY_RADIO_MODE)).isEmpty()) {
            logger.error("Cannot open WMBus device. Radio mode not given.");
            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR, "Cannot open WMBus device. Radio mode not given.");
            return;
        }

        // set up WMBus receiver = handler for radio telegrams
        if (wmbusReceiver == null) {
            String interfaceName = (String) getConfig().get(WMBusBindingConstants.CONFKEY_INTERFACE_NAME);
            String radioModeStr = (String) getConfig().get(WMBusBindingConstants.CONFKEY_RADIO_MODE);
            WMBusMode radioMode;

            // check and convert radio mode
            switch (radioModeStr) {
                case "S":
                    radioMode = WMBusMode.S;
                    break;
                case "T":
                    radioMode = WMBusMode.T;
                    break;
                case "C":
//                    radioMode = WMBusMode.C;
//                    break;
                default:
                    logger.error("Cannot open WMBus device. Unknown radio mode given: " + radioModeStr + ". Expected 'S' or 'T'.");
                    updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR, "Cannot open WMBus device. Unknown radio mode given: " + radioModeStr + ". Expected 'S' or 'T'.");
                    return;
            }

            // connect to the radio module / open WMBus connection
            logger.debug("Opening wmbus serial port {} in mode {}", interfaceName, radioMode.toString());
//            String receiverManufacturer = "imst"; // TODO verallgemeinern
            String receiverManufacturer = "amber";
            WMBusManufacturer wmBusManufacturer = parseManufacturer(receiverManufacturer);
            if (wmBusManufacturer == null) {
                logger.error("Cannot open WMBus device. Unknown manufacturer given: " + receiverManufacturer
                        + ". Expected 'amber' or 'imst' or 'rc'.");
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR,
                        "Cannot open WMBus device. Unknown manufacturer given: " + receiverManufacturer
                                + ". Expected 'amber' or 'imst' or 'rc'.");
                return;
            }
            logger.debug("Building new connection");

            wmbusReceiver = new WMBusReceiver(this);

            WMBusSerialBuilder connectionBuilder = new WMBusSerialBuilder(wmBusManufacturer, wmbusReceiver,
                    interfaceName);
            logger.debug("Setting WMBus radio mode");
            connectionBuilder.setMode(radioMode);
            try {
                logger.debug("Building/opening connection");
                this.wmbusConnection = connectionBuilder.build();
            } catch (IOException e) {
                logger.error("Cannot open WMBus device. Connection builder returned: " + e.getMessage());
                updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR,
                        "Cannot open WMBus device. Connection builder returned: " + e.getMessage());
                return;
            }

            // TODO Verschlüsselung hinzufügen
            /*
             * Map<SecondaryAddress, byte[]> keyPairs = getKeyPairs();
             * for (Entry<SecondaryAddress, byte[]> keyPair : keyPairs.entrySet()) {
             * wmBusConnection.addKey(keyPair.getKey(), keyPair.getValue());
             * }
             */

            logger.debug("Connected to WMBus serial port");

            // close WMBus connection on shutdown
            Runtime.getRuntime().addShutdownHook(new Thread() {
                @Override
                public void run() {
                    if (wmbusConnection != null) {
                        try {
                            logger.debug("Closing connection to WMBus radio device");
                            wmbusConnection.close();
                        } catch (IOException e) {
                            logger.error("Cannot close connection to WMBus radio module: " + e.getMessage());
                            updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR,
                                    "Cannot close connection to WMBus radio module: " + e.getMessage());
                            return;
                        }
                    }
                }
            });

            /*
             * this.wmbusReceiver = new TechemReceiver(this);
             * try {
             * wmbusReceiver.init(interfaceName, radioMode);
             * } catch (IOException e) {
             * logger.error("Cannot open WMBus device: " + e.getMessage());
             * updateStatus(ThingStatus.OFFLINE, ThingStatusDetail.OFFLINE.CONFIGURATION_ERROR,
             * "Cannot open WMBus device: " + e.getMessage());
             * wmbusReceiver = null; // should free serial device if in use
             * return;
             * }
             */

            // success
            logger.debug("WMBusBridgeHandler: Initialization done! Setting bridge online");
            updateStatus(ThingStatus.ONLINE);
        }
    }

    private static WMBusManufacturer parseManufacturer(String manufacturer) {
        switch (manufacturer.toLowerCase()) {
            case "amber":
                return WMBusManufacturer.AMBER;
            case "rc":
                return WMBusManufacturer.RADIO_CRAFTS;
            case "imst":
                return WMBusManufacturer.IMST;
            default:
                return null;
            // this.cliPrinter.printError("Not supported transceiver.", true);
            // throw new RuntimeException();
        }
    }

    public boolean registerWMBusMessageListener(WMBusMessageListener wmBusMessageListener) {
        if (wmBusMessageListener == null) {
            throw new NullPointerException("It's not allowed to pass a null WMBusMessageListener.");
        }
        logger.debug("register listener: Adding");
        boolean result = wmBusMessageListeners.add(wmBusMessageListener);
        logger.debug("register listener: Success");
        if (result) {
            // inform the listener initially about all devices and their states
            for (WMBusMessage device : knownDevices.values()) {
                wmBusMessageListener.onNewWMBusDevice(device);
            }
        }
        return result;
    }

    /**
     * Iterate through wmBusMessageListeners and notify them about a newly received message.
     *
     * @param message
     */
    private void notifyWMBusMessageListeners(final WMBusMessage message, final String type) {
        logger.debug("bridge: notify message listeners: sending to all");
        for (WMBusMessageListener wmBusMessageListener : wmBusMessageListeners) {
            try {
                switch (type) {
                    case DEVICE_STATE_ADDED: {
                        wmBusMessageListener.onNewWMBusDevice(message);
                        break;
                    }
                    case DEVICE_STATE_CHANGED: {
                        wmBusMessageListener.onChangedWMBusDevice(message);
                        break;
                    }
                    default: {
                        throw new IllegalArgumentException("Could not notify wmBusMessageListeners for unknown event type " + type);
                    }
                }
            } catch (Exception e) {
                logger.error("An exception occurred while notifying the WMBusMessageListener", e);
            }
        }
        logger.debug("bridge: notify message listeners: return");
    }

    @Override
    public void dispose() {
        logger.debug("WMBus bridge Handler disposed.");

        if (wmbusReceiver != null) {
            wmbusReceiver = null;
        }
    }

    public void processMessage(WMBusMessage message) {
        logger.debug("bridge: processMessage begin");
        String deviceId = message.getSecondaryAddress().getDeviceId().toString();
        String deviceState = DEVICE_STATE_ADDED;
        if (knownDevices.containsKey(deviceId)) {
            deviceState = DEVICE_STATE_CHANGED;
        }
        knownDevices.put(deviceId, message);
        logger.debug("bridge processMessage: notifying listeners");
        notifyWMBusMessageListeners(message, deviceState);
        logger.debug("bridge: processMessage end");
    }

    public WMBusMessage getDeviceById(String deviceId) {
        logger.debug("bridge: get device by id: " + deviceId);
        if (knownDevices.containsKey(deviceId)) {
            logger.debug("bridge: found device");
        } else {
            logger.debug("bridge: device not found");
        }
        return knownDevices.get(deviceId);
    }

}
