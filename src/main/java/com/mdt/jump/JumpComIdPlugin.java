package com.mdt.jump;

import arc.Events;
import arc.util.CommandHandler;
import arc.util.Log;
import com.mdt.jump.api.ComIdRecord;
import com.mdt.jump.api.JumpComIdApi;
import com.mdt.jump.config.PluginConfiguration;
import com.mdt.jump.http.HttpApiServer;
import com.mdt.jump.service.ComIdService;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.net.BindException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import mindustry.game.EventType.DisposeEvent;
import mindustry.game.EventType.PlayerJoin;
import mindustry.gen.Player;
import mindustry.mod.Plugin;

public final class JumpComIdPlugin extends Plugin {
    private static final Path DATA_DIRECTORY = Paths.get("config", "mods", "config", "mdt-jump-plugin");

    private static volatile JumpComIdApi api;

    private Thread shutdownHook;
    private PluginConfiguration configuration;
    private ComIdService service;
    private HttpApiServer httpApiServer;
    private boolean apiRunning;

    public static JumpComIdApi getApi() {
        return api;
    }

    @Override
    public void init() {
        try {
            configuration = PluginConfiguration.load(DATA_DIRECTORY);
            service = new ComIdService(configuration);
            api = service;
            registerSharedServices();
            apiRunning = false;

            if (configuration.isApiEnabled()) {
                try {
                    httpApiServer = new HttpApiServer(configuration, service);
                    httpApiServer.start();
                    apiRunning = true;
                    registerShutdownHook();
                    Events.on(DisposeEvent.class, event -> shutdownHttpApiServer());
                } catch (BindException bindException) {
                    httpApiServer = null;
                    Log.warn("MdtJumpPlugin API port already in use. Running without HTTP API. host=@ port=@",
                        configuration.getApiHost(), configuration.getApiPort());
                }
            }

            Events.on(PlayerJoin.class, event -> service.getOrCreate(resolveUuid(event.player)));

            Log.info(
                "MdtJumpPlugin loaded. local=@ remote=@ apiEnabled=@ apiRunning=@ config=@",
                service.isLocalStorageEnabled(),
                service.isRemoteStorageEnabled(),
                configuration.isApiEnabled(),
                apiRunning,
                configuration.getConfigFile()
            );
        } catch (Exception exception) {
            throw new RuntimeException("MdtJumpPlugin init failed.", exception);
        }
    }

    private void shutdownHttpApiServer() {
        if (httpApiServer != null) {
            Log.info("MdtJumpPlugin shutting down HTTP API.");
            httpApiServer.close();
            httpApiServer = null;
        }
        apiRunning = false;
        unregisterSharedServices();
        if (shutdownHook != null) {
            try {
                Runtime.getRuntime().removeShutdownHook(shutdownHook);
            } catch (IllegalStateException ignored) {
                // JVM is already shutting down.
            }
            shutdownHook = null;
        }
    }

    private void registerShutdownHook() {
        if (shutdownHook != null) {
            return;
        }
        shutdownHook = new Thread(new Runnable() {
            @Override
            public void run() {
                shutdownHttpApiServer();
            }
        }, "mdt-jump-plugin-shutdown");
        shutdownHook.setDaemon(true);
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    private void registerSharedServices() {
        registerSharedService("mdt.jump.api", api);
        registerSharedService("com.mdt.jump.api.JumpComIdApi", api);
    }

    private void unregisterSharedServices() {
        unregisterSharedService("mdt.jump.api");
        unregisterSharedService("com.mdt.jump.api.JumpComIdApi");
    }

    private void registerSharedService(String key, Object service) {
        try {
            Class<?> hub = Class.forName("mdt.ServeMdtPlugin");
            hub.getMethod("registerSharedService", String.class, Object.class).invoke(null, key, service);
        } catch (Exception ignored) {
            // Core hub is optional.
        }
    }

    private void unregisterSharedService(String key) {
        try {
            Class<?> hub = Class.forName("mdt.ServeMdtPlugin");
            hub.getMethod("unregisterSharedService", String.class).invoke(null, key);
        } catch (Exception ignored) {
            // Core hub is optional.
        }
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("jump-comid-get", "<uuid>", "Get or create a comid from UUID.", args -> {
            ComIdRecord record = service.getOrCreate(args[0]);
            Log.info("@ -> @", record.getUuid(), record.getComId());
        });

        handler.register("jump-comid-find", "<comId>", "Find UUID by comid.", args -> {
            Optional<ComIdRecord> record = service.findByComId(args[0]);
            if (record.isPresent()) {
                Log.info("@ -> @", record.get().getComId(), record.get().getUuid());
            } else {
                Log.info("comid not found: @", args[0]);
            }
        });

        handler.register("jump-comid-status", "Show plugin status.", args -> {
            Log.info(
                "local=@ remote=@ apiEnabled=@ apiRunning=@ @:@",
                service.isLocalStorageEnabled(),
                service.isRemoteStorageEnabled(),
                configuration.isApiEnabled(),
                apiRunning,
                configuration.getApiHost(),
                configuration.getApiPort()
            );
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("comid", "[uuid]", "Show your comid or query another UUID.", (args, player) -> {
            ComIdRecord record = args.length == 0 ? service.getOrCreate(resolveUuid(player)) : service.getOrCreate(args[0]);
            player.sendMessage("[accent]UUID[]: " + record.getUuid() + "\n[accent]com id[]: " + record.getComId());
        });
    }

    private String resolveUuid(Player player) {
        try {
            Method method = player.getClass().getMethod("uuid");
            Object value = method.invoke(player);
            if (value != null) {
                return value.toString();
            }
        } catch (ReflectiveOperationException ignored) {
            // Continue by trying field access.
        }

        try {
            Field field = player.getClass().getField("uuid");
            Object value = field.get(player);
            if (value != null) {
                return value.toString();
            }
        } catch (ReflectiveOperationException ignored) {
            // Throw a clear error below.
        }

        throw new IllegalStateException("Unable to resolve UUID from Player.");
    }
}
