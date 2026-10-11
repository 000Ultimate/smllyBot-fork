package com.kika.smllybot.schedule;

import com.kika.smllybot.database.sql.ban.BanTable;
import com.kika.smllybot.database.sql.ban.dto.BanData;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.UserSnowflake;
import net.dv8tion.jda.api.exceptions.ErrorResponseException;
import net.dv8tion.jda.api.requests.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class BanScheduler {

    private static final Logger log = LoggerFactory.getLogger(BanScheduler.class);
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private final JDA jda;
    private final BanTable banTable;

    public BanScheduler(JDA jda, BanTable banTable) {
        this.jda = jda;
        this.banTable = banTable;
    }

    public void start() {
        scheduler.scheduleWithFixedDelay(this::checkExpiredBans, 10, 30, TimeUnit.SECONDS);
    }

    private void checkExpiredBans() {
        try {
            List<BanData> expiredBans = banTable.getExpiredBans();

            for (BanData ban : expiredBans) {
                Guild guild = jda.getGuildById(ban.getGuildId());

                if (guild == null) {
                    banTable.markAsUnbanned(ban.getDiscordId(), null, ban.getGuildId());
                    continue;
                }

                // markAsUnbanned принимает discord_id, а не id строки в таблице ban
                guild.unban(UserSnowflake.fromId(ban.getDiscordId()))
                        .reason("⏳ Истек срок блокировки")
                        .queue(
                                success -> {
                                    banTable.markAsUnbanned(ban.getDiscordId(), null, ban.getGuildId());
                                    log.info("✅ Пользователь {} разбанен", ban.getDiscordId());
                                },
                                error -> {
                                    // Пользователя уже разбанили вручную - просто закрываем запись, иначе она
                                    // будет вечно проверяться каждые 30 секунд
                                    if (error instanceof ErrorResponseException e
                                            && e.getErrorResponse() == ErrorResponse.UNKNOWN_BAN) {
                                        banTable.markAsUnbanned(ban.getDiscordId(), null, ban.getGuildId());
                                    } else {
                                        log.error("❌ Не удалось разблокировать пользователя {}: {}",
                                                ban.getDiscordId(), error.getMessage());
                                    }
                                }
                        );
            }
        } catch (Exception e) {
            log.error("❌ Ошибка при проверке на истекшие баны: ", e);
        }
    }

}
