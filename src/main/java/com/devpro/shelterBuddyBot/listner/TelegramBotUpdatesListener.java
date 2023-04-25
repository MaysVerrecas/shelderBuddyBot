package com.devpro.shelterBuddyBot.listner;

import com.devpro.shelterBuddyBot.dao.ShelterDao;
import com.devpro.shelterBuddyBot.model.Shelter;
import com.devpro.shelterBuddyBot.service.ShelterModeService;
import com.devpro.shelterBuddyBot.util.CallbackRequest;
import com.devpro.shelterBuddyBot.util.ShelterType;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.CallbackQuery;
import com.pengrad.telegrambot.model.Message;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.model.request.*;
import com.pengrad.telegrambot.request.SendMessage;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Objects;
import java.util.Optional;

@Component
@RequiredArgsConstructor
//@Service
public class TelegramBotUpdatesListener {

    private final Logger logger = LoggerFactory.getLogger(TelegramBotUpdatesListener.class);
    private final TelegramBot telegramBot;
    private final ShelterModeService shelterModeService = ShelterModeService.getInstance();

    private final ShelterDao shelterDao;


//    public TelegramBotUpdatesListener(TelegramBot telegramBot) {
//        this.telegramBot = telegramBot;
//    }

    @PostConstruct
    public void init() {

        telegramBot.setUpdatesListener(updates -> {
            updates.forEach(this::process);

            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
    }

    //обрабатываем каждое событие в боте
    public void process(Update update) {


        SendMessage request = null;

        //события с кнопок InlineKeyboardMarkup
        if (Objects.nonNull(update.callbackQuery())) {
            request = handleCallback(update.callbackQuery());
        }

        //события с контактом и обычным сообещнием
        if (Objects.nonNull(update.message())) {
            request = handleMassage(update.message());
        }

        //выполняет запрос
        if (request != null) {
            telegramBot.execute(request);
        }
    }

    private SendMessage handleMassage(Message message) {

        long chatId = message.chat().id();

        //обрабатывваем отправку контакта и удаляем кнопки ReplyKeyboardRemove
        if (Objects.nonNull(message.contact())) {
            return new SendMessage(chatId, "Спасибо я записал твой номер!").replyMarkup(new ReplyKeyboardRemove());
        }

        //обрабатываем старт
        if (message.text().equals("/start")) {

            InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup();
            addButton(inlineKeyboard, CallbackRequest.CATS.getName(), CallbackRequest.CATS);
            addButton(inlineKeyboard, CallbackRequest.DOGS.getName(), CallbackRequest.DOGS);

            return new SendMessage(chatId, """
                    Привет я расскажу тебе о приютах нашего города и помогу тебе найти  или пристроить потеряшку!

                    Какой вы ищите приют?""").replyMarkup(inlineKeyboard);
        } else {
            return new SendMessage(chatId, """
                    Бот не знает такой команды введинте /start для начала работы с ботом""");
        }

    }

    private SendMessage handleCallback(CallbackQuery callbackQuery) {
        Message message = callbackQuery.message();
        long chatId = message.chat().id();
        String data = callbackQuery.data();
        ObjectMapper objectMapper = new ObjectMapper();
        CallbackRequest callbackRequest = null;
        try { //десериализуем данные из события бота в объект
            callbackRequest = objectMapper.readValue(data, CallbackRequest.class);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        if (Objects.isNull(callbackRequest)) {
            return new SendMessage(chatId, "Вызываю волонтера!");
        }
        InlineKeyboardMarkup inlineKeyboard = new InlineKeyboardMarkup(); //пробегаем по всем возможным событиям и что то делаем
        switch (callbackRequest) {
            case DOGS:
            case CATS:
                ShelterType shelterType = ShelterType.getByName(data);
                shelterModeService.setShelter(chatId, shelterType);
                //добавляем кнопки
                addButton(inlineKeyboard, CallbackRequest.SHELTER_INFO.getName(), CallbackRequest.SHELTER_INFO);
                addButton(inlineKeyboard, CallbackRequest.GET_ANIMAL.getName(), CallbackRequest.GET_ANIMAL);
                addButton(inlineKeyboard, CallbackRequest.REPORT_ANIMAL.getName(), CallbackRequest.SHELTER_INFO);
                addButton(inlineKeyboard, CallbackRequest.HELP.getName(), CallbackRequest.HELP);
                return new SendMessage(chatId, "Отличный выбор! Чем я могу тебе помочь?").replyMarkup(inlineKeyboard);
            case SHELTER_INFO:
                addButton(inlineKeyboard, CallbackRequest.SHELTER_CONTACTS.getName(), CallbackRequest.SHELTER_CONTACTS);
                addButton(inlineKeyboard, CallbackRequest.PHONE_SECURITY.getName(), CallbackRequest.PHONE_SECURITY);
                addButton(inlineKeyboard, CallbackRequest.SAFETY_PRECAUTIONS.getName(), CallbackRequest.SAFETY_PRECAUTIONS);
                addButton(inlineKeyboard, CallbackRequest.PUT_MY_PHONE.getName(), CallbackRequest.PUT_MY_PHONE);
                addButton(inlineKeyboard, CallbackRequest.HELP.getName(), CallbackRequest.HELP);
                return new SendMessage(chatId, "Что именно тебя интересует?").replyMarkup(inlineKeyboard);
            case GET_SHELTER_INFO:
                return new SendMessage(chatId, "Рассказываю тебе о приюте!");
            case PHONE_SECURITY:
                ShelterType shelter = shelterModeService.getShelter(chatId);
                Optional<Shelter> byId;



                if (shelter == ShelterType.CATS) {
                    byId = shelterDao.findById(2);
                } else {
                    byId = shelterDao.findById(1);
                }

                if (byId.isPresent()) {

                    return new SendMessage(chatId, "Даю тебе номер телефона охраны! " + byId.get().getSecurityPhone());
                }
                return new SendMessage(chatId, "Нету!");
            case SAFETY_PRECAUTIONS:
                return new SendMessage(chatId, "Рассказываю тебе о технике безопасности!");
            case SHELTER_CONTACTS:
                return new SendMessage(chatId, "Даю тебе контакты приюта!");
            case PUT_MY_PHONE:
                Keyboard keyboard = new ReplyKeyboardMarkup(new KeyboardButton("Отдать свой номер телефона").requestContact(true));
                return new SendMessage(chatId, "Забираю у тебя номер телефона!").replyMarkup(keyboard);
            case GET_ANIMAL:
                return new SendMessage(chatId, "Рассказываю тебе как взять животное!");
            case REPORT_ANIMAL:
                return new SendMessage(chatId, "Прислаю отчет о питомце!");
            default:
                return new SendMessage(chatId, "Вызываю волонтера!");
        }
    }

    private void addButton(InlineKeyboardMarkup inlineKeyboard, String buttonName, CallbackRequest callbackData) {
        ObjectMapper objectMapper = new ObjectMapper();
        try {
            //добавляем кнопку в новую строку addRow и сериализуем объект в json
            inlineKeyboard.addRow(new InlineKeyboardButton(buttonName).callbackData(objectMapper.writeValueAsString(callbackData)));
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }
}