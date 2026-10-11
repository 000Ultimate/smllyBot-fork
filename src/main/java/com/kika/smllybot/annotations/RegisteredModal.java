package com.kika.smllybot.annotations;

import net.dv8tion.jda.api.events.interaction.ModalInteractionEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public record RegisteredModal(Object instance, Method method) {

    private static final Logger log = LoggerFactory.getLogger(RegisteredModal.class);

    public void invoke(ModalInteractionEvent event, String[] args) {
        try {
            method.invoke(instance, event, args);
        } catch (InvocationTargetException e) {
            log.error("❌ Ошибка внутри обработчика модалки: ", e.getCause() != null ? e.getCause() : e);
        } catch (Exception e) {
            log.error("❌ Не удалось вызвать обработчик модалки: ", e);
        }
    }

}
