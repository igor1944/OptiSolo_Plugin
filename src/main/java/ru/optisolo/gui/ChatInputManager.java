package ru.optisolo.gui;

import org.bukkit.entity.Player;
import ru.optisolo.OptiSoloPlugin;
import ru.optisolo.config.Messages;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Ввод данных через чат после клика в меню:
 * ники для приглашений, названия точек, текст жалоб.
 */
public class ChatInputManager {

    private final OptiSoloPlugin plugin;
    private final Messages msg;
    private final Map<UUID, PendingInput> pending = new HashMap<>();

    /** Время на ввод — 60 секунд. */
    private static final long TIMEOUT = 60_000L;

    public ChatInputManager(OptiSoloPlugin plugin) {
        this.plugin = plugin;
        this.msg = plugin.getMessages();
    }

    public enum InputType {
        INVITE, TRUST, VISIT, HOME_CREATE, REPORT_PLAYER, REPORT_REASON
    }

    public static class PendingInput {
        public final InputType type;
        public final String context;
        public final long expiry;

        public PendingInput(InputType type, String context) {
            this.type = type;
            this.context = context;
            this.expiry = System.currentTimeMillis() + TIMEOUT;
        }
    }

    public void request(Player player, InputType type, String context) {
        pending.put(player.getUniqueId(), new PendingInput(type, context));
        switch (type) {
            case INVITE:
                msg.send(player, "prompt-invite");
                break;
            case TRUST:
                msg.send(player, "prompt-trust");
                break;
            case VISIT:
                msg.send(player, "prompt-visit");
                break;
            case HOME_CREATE:
                msg.send(player, "prompt-home");
                break;
            case REPORT_PLAYER:
                msg.send(player, "prompt-report-player");
                break;
            case REPORT_REASON:
                msg.send(player, "prompt-report-reason");
                break;
        }
    }

    /** Забрать ожидание (null — нет активного). Вызывать в любом потоке. */
    public PendingInput take(UUID uuid) {
        PendingInput p = pending.remove(uuid);
        if (p == null) return null;
        if (p.expiry < System.currentTimeMillis()) {
            return new PendingInput(null, null); // маркер "истекло"
        }
        return p;
    }

    /** Обработка ввода. Вызывать ТОЛЬКО в главном потоке. */
    public void processSync(Player player, PendingInput input, String text) {
        if (input.type == null) {
            msg.send(player, "input-expired");
            return;
        }
        if (text.equalsIgnoreCase("отмена") || text.equalsIgnoreCase("cancel")) {
            msg.send(player, "input-cancelled");
            return;
        }
        switch (input.type) {
            case INVITE:
                plugin.getWorldManager().invitePlayer(player, text);
                break;
            case TRUST:
                plugin.getWorldManager().trustPlayer(player, text);
                break;
            case VISIT:
                plugin.getWorldManager().visit(player, text);
                break;
            case HOME_CREATE:
                plugin.getWorldManager().createHomePoint(player, text);
                break;
            case REPORT_PLAYER:
                if (text.length() < 3 || text.length() > 16) {
                    msg.send(player, "player-not-found", "target", text);
                    return;
                }
                request(player, InputType.REPORT_REASON, text);
                break;
            case REPORT_REASON:
                if (text.length() < 3) {
                    msg.send(player, "report-usage");
                    return;
                }
                plugin.getReportManager().createReport(player, input.context, text);
                break;
        }
    }
}
