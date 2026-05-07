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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import mindustry.game.EventType.PlayerJoin;
import mindustry.gen.Player;
import mindustry.mod.Plugin;

public final class JumpComIdPlugin extends Plugin {
    private static final Path DATA_DIRECTORY = Paths.get("config", "mdt-jump-plugin");

    private static volatile JumpComIdApi api;

    private PluginConfiguration configuration;
    private ComIdService service;
    private HttpApiServer httpApiServer;

    public static JumpComIdApi getApi() {
        return api;
    }

    @Override
    public void init() {
        try {
            configuration = PluginConfiguration.load(DATA_DIRECTORY);
            service = new ComIdService(configuration);
            api = service;

            if (configuration.isApiEnabled()) {
                httpApiServer = new HttpApiServer(configuration, service);
                httpApiServer.start();
            }

            Events.on(PlayerJoin.class, event -> service.getOrCreate(resolveUuid(event.player)));

            Log.info(
                "MdtJumpPlugin 已加载。local=@ remote=@ api=@ config=@",
                service.isLocalStorageEnabled(),
                service.isRemoteStorageEnabled(),
                configuration.isApiEnabled(),
                configuration.getConfigFile()
            );
        } catch (Exception exception) {
            throw new RuntimeException("MdtJumpPlugin 初始化失败。", exception);
        }
    }

    @Override
    public void registerServerCommands(CommandHandler handler) {
        handler.register("jump-comid-get", "<uuid>", "根据 UUID 查询或创建 com id。", args -> {
            ComIdRecord record = service.getOrCreate(args[0]);
            Log.info("@ -> @", record.getUuid(), record.getComId());
        });

        handler.register("jump-comid-find", "<comId>", "通过 com id 反查 UUID。", args -> {
            Optional<ComIdRecord> record = service.findByComId(args[0]);
            if (record.isPresent()) {
                Log.info("@ -> @", record.get().getComId(), record.get().getUuid());
            } else {
                Log.info("未找到 com id: @", args[0]);
            }
        });

        handler.register("jump-comid-status", "查看插件状态。", args -> {
            Log.info(
                "local=@ remote=@ api=@ @:@",
                service.isLocalStorageEnabled(),
                service.isRemoteStorageEnabled(),
                configuration.isApiEnabled(),
                configuration.getApiHost(),
                configuration.getApiPort()
            );
        });
    }

    @Override
    public void registerClientCommands(CommandHandler handler) {
        handler.<Player>register("comid", "[uuid]", "查询自己的 com id，或按 UUID 查询。", (args, player) -> {
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
            // 继续尝试字段读取。
        }

        try {
            Field field = player.getClass().getField("uuid");
            Object value = field.get(player);
            if (value != null) {
                return value.toString();
            }
        } catch (ReflectiveOperationException ignored) {
            // 最后抛出明确错误。
        }

        throw new IllegalStateException("无法从 Player 对象读取 UUID。");
    }
}
