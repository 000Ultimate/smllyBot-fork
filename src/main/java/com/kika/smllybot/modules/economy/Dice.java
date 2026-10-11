package com.kika.smllybot.modules.economy;

import com.kika.smllybot.other.BaseCmd;
import net.dv8tion.jda.api.components.container.Container;
import net.dv8tion.jda.api.components.container.ContainerChildComponent;
import net.dv8tion.jda.api.components.mediagallery.MediaGallery;
import net.dv8tion.jda.api.components.mediagallery.MediaGalleryItem;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.utils.FileUpload;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

public class Dice extends BaseCmd {

    private static final Logger log = LoggerFactory.getLogger(Dice.class);
    private static final String[] FACES = {"one", "two", "three", "four", "five", "six"};

    // Гифки читаем один раз в память. Раньше хранился один InputStream, который после первого
    // использования был прочитан до конца, и команда со второго раза отправляла пустой файл
    private final byte[][] gifs = new byte[FACES.length][];

    public Dice() {
        super(Set.of("кубик", "dice"));

        for (int i = 0; i < FACES.length; i++) {
            try (InputStream in = getClass().getResourceAsStream("/images/" + FACES[i] + ".gif")) {
                gifs[i] = in != null ? in.readAllBytes() : null;
            } catch (IOException e) {
                log.error("❌ Не удалось загрузить гифку кубика {}: ", FACES[i], e);
            }
        }
    }

    @Override
    public Container execute(MessageReceivedEvent event, String raw, String args) {
        // Раньше всегда отправлялась пятерка
        int roll = ThreadLocalRandom.current().nextInt(FACES.length);
        byte[] gif = gifs[roll];
        if (gif == null) return null;

        FileUpload upload = FileUpload.fromData(gif, FACES[roll] + ".gif");
        ContainerChildComponent response = MediaGallery.of(
                MediaGalleryItem.fromFile(upload)
        );

        event.getChannel().sendMessageComponents(Container.of(response))
                .useComponentsV2(true)
                .queue();

        return null;
    }
}
