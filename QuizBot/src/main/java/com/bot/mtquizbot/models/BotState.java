package com.bot.mtquizbot.models;

public enum BotState {
    idle,
    waitingForGroupCode,
    waitingTestAnswer,
    waitingForGroupName,
    waitingForGroupDescription
}