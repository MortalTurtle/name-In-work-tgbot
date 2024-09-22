package com.bot.mtquizbot.tgbot;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.function.Consumer;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Message;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.InlineKeyboardButton;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import com.bot.mtquizbot.models.BotState;
import com.bot.mtquizbot.models.GroupRole;
import com.bot.mtquizbot.models.TestGroup;
import com.bot.mtquizbot.models.User;
import com.bot.mtquizbot.service.GroupService;
import com.bot.mtquizbot.service.RoleService;
import com.bot.mtquizbot.service.UserService;

import lombok.Getter;
@Component
@Getter
public class MtQuizBot extends TelegramLongPollingBot {
    private final String botUsername;
    private final String botToken;
    private final UserService userService;
    private final GroupService groupService;
    private final RoleService roleService;
    private final HashMap<Long, BotState> botStateByUser = new HashMap<>();
    private final HashMap<BotState, Consumer<Update>> actionByBotState = new HashMap<>();
    private final HashMap<Long, HashMap<String, String>> infoByUser = new HashMap<>();
    private final HashMap<String, Consumer<Update>> actionByCommand = new HashMap<>();
    public MtQuizBot(TelegramBotsApi telegramBotsApi,
                     @Value("${telegram.bot.username}") String botUsername,
                     @Value("${telegram.bot.token}") String botToken,
                     UserService userService,
                     GroupService groupService,
                     RoleService roleService) throws TelegramApiException {
        this.botUsername = botUsername;
        this.botToken = botToken;
        this.userService = userService;
        this.groupService = groupService;
        this.roleService = roleService;
        RegisterCommands();
        telegramBotsApi.registerBot(this);
    }

    private void sendText(Long who, String what) {
        SendMessage sm = SendMessage.builder()
                .chatId(who.toString())
                .text(what).build();
        try {
            execute(sm);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    public void sendInlineMenu(Long who, String txt, InlineKeyboardMarkup kb){
        SendMessage sm = SendMessage.builder().chatId(who.toString())
                .parseMode("HTML").text(txt)
                .replyMarkup(kb).build();
    
        try {
            execute(sm);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void onUpdateReceived(Update update) {
        Message msg;
        if (update.hasCallbackQuery()) {
            var callbackData = update.getCallbackQuery();
            var data = callbackData.getData();
            msg = new Message();
            msg.setFrom(callbackData.getFrom());
            msg.setText(data);
            update.setMessage(msg);
        }
        msg = update.getMessage();
        var user = msg.getFrom();
        var id = user.getId();
        if (!infoByUser.containsKey(id))
            infoByUser.put(id, new HashMap<>());
        userService.insert(new User(id, user.getUserName(), null));
        if (!botStateByUser.containsKey(id))
            botStateByUser.put(id, BotState.idle);
        if (msg.hasText() && actionByCommand.containsKey(msg.getText())) {
            actionByCommand.get(msg.getText()).accept(update);
            return;
        }
        actionByBotState.get(botStateByUser.get(id)).accept(update);
    }

    private void RegisterCommands() {
        var methods = this.getClass().getDeclaredMethods();
        for (var method : methods) {
            if (method.isAnnotationPresent(CommandAction.class) && 
                method.getReturnType().equals(Void.TYPE) &&
                method.getParameterCount() == 1 &&
                method.getParameters()[0].getType().equals(Update.class)) {
                String command = method.getAnnotation(CommandAction.class).value();
                method.setAccessible(true);
                actionByCommand.put(command, (Update upd) -> {
                    try { method.invoke(this,upd); }
                    catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }
        actionByBotState.put(BotState.idle, (Update update) -> {
            var id = update.getMessage().getFrom().getId();
            sendText(id, "Please enter a valid command");
        });
        actionByBotState.put(BotState.waitingForGroupCode, (Update update) -> {
            var msg = update.getMessage();
            var id = update.getMessage().getFrom().getId();
            TestGroup group;
            if (msg.hasText()) {
                group = groupService.getById(msg.getText());
                if (group == null) {
                    sendText(id, "Wrong group code");
                    return;
                }
                userService.updateGroupById(id, group.getId());
            }
        });
        actionByBotState.put(BotState.waitingForGroupName, (Update update) -> {
            var msg = update.getMessage();
            var id = update.getMessage().getFrom().getId();
            if (!msg.hasText()) {
                sendText(id, "No text for group name");
                return;
            }
            botStateByUser.replace(id, BotState.waitingForGroupDescription);
            infoByUser.get(id).put("Name", msg.getText());
            sendText(id, "Please enter group description");
        });
        actionByBotState.put(BotState.waitingForGroupDescription, (Update update) -> {
            var msg = update.getMessage();
            var id = update.getMessage().getFrom().getId();
            if (!msg.hasText()) {
                sendText(id, "No text for group description");
                return;
            }
            botStateByUser.replace(id, BotState.idle);
            var group = groupService.create(infoByUser.get(id).get("Name"), msg.getText());
            userService.updateGroupById(id, group.getId());
            roleService.addUserRole(group, userService.getById(id), GroupRole.Owner);
            sendText(id, "Your group: " + 
            group.getName() + 
            " - " +
            group.getDescription() + 
            "\nWas created, its ID is\n" + group.getId() + 
            "\nPlease write it down");
            infoByUser.get(id).remove("Name");
        });
    }

    @CommandAction("/creategroup")
    private void CreateGroupCommand(Update update) {
        var id = update.getMessage().getFrom().getId();
        botStateByUser.replace(id, BotState.waitingForGroupName);
        sendText(id, "Please enter a group name");
    }

    @CommandAction("/join")
    private void JoinCommand(Update update) {
        var id = update.getMessage().getFrom().getId();
        botStateByUser.replace(id, BotState.waitingForGroupCode);
        sendText(id, "Please enter a group code ");
    }
    
    @CommandAction("/start")
    private void StartCommand(Update update) {
        var id = update.getMessage().getFrom().getId();
        var joinButton = InlineKeyboardButton.builder()
        .text("Join")
        .callbackData("/join")
        .build();
        var createButton = InlineKeyboardButton.builder()
        .text("Create")
        .callbackData("/creategroup")
        .build();
        var menu = InlineKeyboardMarkup.builder()
        .keyboardRow(List.of(joinButton,createButton))
        .build();
        sendInlineMenu(id, "" + 
        "Welcome to bot, enter command /join to join group " +
        "\n /creategroup to create one", menu);
    }
    
    @CommandAction("/groupinfo")
    private void GroupInfoCommand(Update update) {
        var id = update.getMessage().getFrom().getId();
        var user = userService.getById(id);
        var group = groupService.getUserGroup(user);
        if (group == null) {
            sendText(id, "No group found, please enter /join to enter a group or" +
            "\n /creategroup to create one");
            return;
        }
        sendText(id, "Your group: " + 
        group.getName() + 
        " - " +
        group.getDescription() + 
        "\nWas created, its ID is\n" + group.getId() + 
        "\nPlease write it down");
    }

    @Override
    public String getBotUsername() {
        return botUsername;
    }
    @Override
    public String getBotToken() {
        return botToken;
    }
}
