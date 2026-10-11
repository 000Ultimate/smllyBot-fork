package com.kika.smllybot.modules.moderation.mute;

import com.kika.smllybot.database.sql.guild.GuildTable;
import com.kika.smllybot.database.sql.guild.dto.GuildData;
import com.kika.smllybot.handler.ErrorThrow;
import com.kika.smllybot.other.BaseCmd;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

public class Mute extends BaseCmd {

    private static final Logger log = LoggerFactory.getLogger(Mute.class);

    private final MuteService muteService;

    public Mute(MuteService muteService) {
        super(Set.of("мут", "мьют", "mute"));
        this.muteService = muteService;
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
        if (moderator == null || !moderator.hasPermission(Permission.MODERATE_MEMBERS)) {
            ErrorThrow.noAccessPermission(event, Permission.MODERATE_MEMBERS);
            return null;
        }

        if (!guild.getSelfMember().hasPermission(Permission.MODERATE_MEMBERS)) {
            ErrorThrow.noAccessPermissionBot(event, Permission.MODERATE_MEMBERS);
            return null;
        }

        String[] matches = raw.trim().split("\\n", 2);
        String firstLine = matches[0].trim();
        String[] parts = firstLine.split("\\h+");

        String reason = (matches.length >= 2 && !matches[1].isBlank()) ? matches[1].trim() : null;

        // Ответом на сообщение: мут [время] [единица]
        if (event.getMessage().getReferencedMessage() != null) {
            Member target = event.getMessage().getReferencedMessage().getMember();
            if (target == null) {
                log.error("❌ Не удалось получить участника из отвеченного сообщения.");
                ErrorThrow.userNotFound(event, "");
                return null;
            }

            executeMute(event, target, moderator, parts, 1, reason);
            return null;
        }

        // Упоминание -> @username
        if (!event.getMessage().getMentions().getMembers().isEmpty()) {
            Member target = event.getMessage().getMentions().getMembers().getFirst();

            executeMute(event, target, moderator, parts, 2, reason);
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
                    target -> executeMute(event, target, moderator, parts, 2, reason),
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
            executeMute(event, members.getFirst(), moderator, parts, 2, reason);
        } else {
            // Раньше здесь вызывался members.getFirst() на пустом списке -> NoSuchElementException
            ErrorThrow.userNotFound(event, arg);
        }

        return null;
    }

    // Для мута время обязательно: нужны и число, и единица измерения
    private void executeMute(MessageReceivedEvent event, Member target, Member moderator,
                             String[] parts, int timeIndex, String reason) {
        if (parts.length <= timeIndex + 1) {
            ErrorThrow.invalidTime(event, parts.length > timeIndex ? parts[timeIndex] : null);
            return;
        }

        muteService.mute(event, target, moderator, parts[timeIndex], parts[timeIndex + 1], reason);
    }

}
