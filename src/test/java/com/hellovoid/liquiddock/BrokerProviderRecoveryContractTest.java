package com.hellovoid.liquiddock;

import static org.junit.Assert.*;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.junit.Test;

public class BrokerProviderRecoveryContractTest {
    private static String source(String name) throws Exception {
        return Files.readString(Paths.get(
                "src/main/java/com/hellovoid/liquiddock/" + name), StandardCharsets.UTF_8);
    }

    @Test public void brokerProtocolHasEventDrivenProviderWatcher() throws Exception {
        String protocol = source("FreeformLeashProtocol.java");
        assertTrue(protocol.contains("BROKER_PROVIDER_CALLBACK_DESCRIPTOR"));
        assertTrue(protocol.contains("TRANSACTION_WATCH_PROVIDER"));
        assertTrue(protocol.contains("TRANSACTION_PROVIDER_CHANGED"));
    }

    @Test public void brokerPushesProviderRegistrationAndDeathToLauncherWatcher() throws Exception {
        String service = source("FreeformLeashBrokerService.java");
        assertTrue(service.contains("providerWatcher"));
        assertTrue(service.contains("TRANSACTION_WATCH_PROVIDER"));
        assertTrue(service.contains("replaceProviderWatcher"));
        assertTrue(service.contains("notifyProviderWatcher"));
        assertTrue(service.contains("TRANSACTION_PROVIDER_CHANGED"));
        assertTrue("provider changes must notify without Launcher polling",
                service.contains("notifyProviderWatcher(watcherToNotify"));
    }

    @Test public void launcherRegistersWatcherAndConsumesProviderPushes() throws Exception {
        String client = source("FreeformLeashBrokerClient.java");
        assertTrue(client.contains("registerProviderWatcherAsync"));
        assertTrue(client.contains("BROKER_PROVIDER_CALLBACK_DESCRIPTOR"));
        assertTrue(client.contains("TRANSACTION_PROVIDER_CHANGED"));
        assertTrue(client.contains("TRANSACTION_WATCH_PROVIDER"));
        assertFalse("provider recovery must not use a delayed polling loop",
                client.contains("postDelayed(this::refreshLauncherProviderAsync"));
    }
}
