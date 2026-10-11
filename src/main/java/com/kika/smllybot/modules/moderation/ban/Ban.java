package com.kika.smllybot.modules.moderation.ban;

import com.kika.smllybot.database.sql.guild.GuildTable;
import com.kika.smllybot.database.sql.guild.dto.GuildData;
import com.kika.smllybot.handler.ErrorThrow;
import com.kika.smllybot.other.BaseCmd;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;

import java.util.Set;

public class Ban extends BaseCmd {

    private final BanService banService;
    public Ban(BanService banService) {
        super(Set.of("бан"));
        this.banService = banService;
    }

    @Override
    public Container execute(MessageReceivedEvent event, String raw, String args) {
        if (!event.isFromGuild()) return null;
        GuildData guildData = GuildTable.getOrCreateGuild(event.getGuild().getIdLong(), event.getGuild().getName());
        if (!guildData.getStaging()) {
            ErrorThrow.onlyOnStaging(event);
            return null;
        }

        Guild guild = event.getGuild();
        Member moderator = event.getMember();

        // Проверка прав модератора и бота
        if (moderator == null || !moderator.hasPermission(Permission.BAN_MEMBERS)) {
            ErrorThrow.noAccessPermission(event, Permission.BAN_MEMBERS);
            return null;
        }

        if (!guild.getSelfMember().hasPermission(Permission.BAN_MEMBERS)) {
            ErrorThrow.noAccessPermissionBot(event, Permission.BAN_MEMBERS);
            return null;
        }

        String[] matches = raw.trim().split("\\n", 2);
        String firstLine = matches[0].trim();
        String[] parts = firstLine.split("\\h+");

        String reason = (matches.length >= 2 && !matches[1].isBlank()) ? matches[1].trim() : null;

        // Ответом на сообщение: бан [время] [единица]
        if (event.getMessage().getReferencedMessage() != null) {
            Member target = event.getMessage().getReferencedMessage().getMember();
            if (target == null) {
                ErrorThrow.userNotFound(event, "");
                return null;
            }

            executeBan(event, target, moderator, parts, 1, reason);
            return null;
        }

        // Упоминание -> @username
        if (!event.getMessage().getMentions().getMembers().isEmpty()) {
            Member target = event.getMessage().getMentions().getMembers().getFirst();

            executeBan(event, target, moderator, parts, 2, reason);
            return null;
        }

        if (parts.length < 2) {
            return null;
        }

        String arg = parts[1];

        // ID -> 12345678910121314
        if (arg.matches("\\d{15,19}")) {
            long targetId = Long.parseLong(arg);
            guild.retrieveMemberById(targetId).queue(
                    target -> executeBan(event, target, moderator, parts, 2, reason),
                    failure -> ErrorThrow.userNotFound(event, arg)
            );
            return null;
        }

        // Никнейм -> kefichik.
        var members = guild.getMembersByName(arg, true);
        if (members.isEmpty()) {
            members = guild.getMembersByNickname(arg, true);
        }

        if (!members.isEmpty()) {
            executeBan(event, members.getFirst(), moderator, parts, 2, reason);
        } else {
            ErrorThrow.userNotFound(event, arg);
        }

        return null;
    }

    // Время необязательно: без него бан выдается навсегда. Если число указано без единицы - это ошибка
    private void executeBan(MessageReceivedEvent event, Member target, Member moderator,
                            String[] parts, int timeIndex, String reason) {
        String amount = null;
        String unit = null;

        if (parts.length > timeIndex && !parts[timeIndex].isBlank()) {
            amount = parts[timeIndex];

            if (parts.length <= timeIndex + 1 || parts[timeIndex + 1].isBlank()) {
                ErrorThrow.invalidTime(event, amount);
                return;
            }
            unit = parts[timeIndex + 1];
        }

        banService.ban(event, target, moderator, amount, unit, reason);
    }

}
