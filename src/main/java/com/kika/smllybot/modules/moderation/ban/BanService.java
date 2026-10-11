package com.kika.smllybot.modules.moderation.ban;

import com.kika.smllybot.database.sql.ban.BanTable;
import com.kika.smllybot.database.sql.ban.dto.BanCreateData;
import com.kika.smllybot.handler.ErrorThrow;
import com.kika.smllybot.modules.moderation.ban.ui.BanUI;
import com.kika.smllybot.utils.TimeUtil;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;

import java.time.OffsetDateTime;
import java.util.concurrent.TimeUnit;

public class BanService {

    private final BanTable banTable;
    public BanService(BanTable banTable) {
        this.banTable = banTable;
    }

    protected void ban(MessageReceivedEvent event, Member target,
                       Member moderator, String rawAmount, String unit, String reason) {
        Guild guild = event.getGuild();

        // Без проверки роли модератора любой участник с правом бана мог забанить того, кто выше него
        if (!guild.getSelfMember().canInteract(target) || !moderator.canInteract(target)) {
            ErrorThrow.smallPermission(event);
            return;
        }

        Long amount = null;
        OffsetDateTime until = null;
        if (rawAmount != null && unit != null) {
            try {
                amount = Long.parseLong(rawAmount);
            } catch (NumberFormatException e) {
                ErrorThrow.notNumber(event, rawAmount);
                return;
            }

            until = TimeUtil.calculateUntil(amount, unit);
            // Неизвестная единица времени или число <= 0 раньше приводили к вечному бану / мгновенному разбану
            if (amount <= 0 || until == null) {
                ErrorThrow.invalidTime(event, rawAmount + " " + unit);
                return;
            }
        }
        final OffsetDateTime banUntil = until;

        target.ban(0, TimeUnit.MINUTES).reason(reason).queue(
                success -> {
                    BanCreateData data = new BanCreateData(
                            guild.getIdLong(),
                            target.getIdLong(),
                            moderator.getIdLong(),
                            reason,
                            banUntil
                    );
                    banTable.createBan(data);

                    var response = BanUI.ban(target.getIdLong(), moderator.getIdLong(), banUntil, reason);
                    event.getChannel().sendMessageComponents(response).useComponentsV2(true).queue();
                },
                error -> ErrorThrow.undefined(event, error.getMessage())
        );
    }

    protected void unban(MessageReceivedEvent event, User target, Member moderator) {
        Guild guild = event.getGuild();

        var response = BanUI.unban(target.getIdLong());

        // Сначала помечаем в БД, чтобы SelfUnban (событие GuildUnbanEvent) не записал снявшим "бота"
        banTable.markAsUnbanned(target.getIdLong(), moderator.getIdLong(), guild.getIdLong());
        guild.unban(target).queue(
                success -> event.getChannel().sendMessageComponents(response).useComponentsV2(true).queue(),
                error -> {
                    if (error instanceof ErrorResponseException e
                            && e.getErrorResponse() == ErrorResponse.UNKNOWN_BAN) {
                        ErrorThrow.notBanned(event, target.getIdLong());
                    } else {
                        ErrorThrow.undefined(event, error.getMessage());
                    }
                }
        );
    }

}
