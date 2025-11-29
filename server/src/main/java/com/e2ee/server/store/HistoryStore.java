package com.e2ee.server.store;

import com.e2ee.server.protocol.ChatMessage;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;

public class HistoryStore {

    private static final String HISTORY_FILE = "data/history.json";

    private final Gson gson = new Gson();

    // 메모리 캐시
    private final List<ChatMessage> logs = new ArrayList<>();

    public HistoryStore() {
        load();
    }

    // 로드
    private void load() {
        try {
            File f = new File(HISTORY_FILE);
            if (!f.exists()) {
                save();
                return;
            }

            Reader reader = new FileReader(f);
            Type type = new TypeToken<List<ChatMessage>>(){}.getType();
            List<ChatMessage> loaded = gson.fromJson(reader, type);

            if (loaded != null) logs.addAll(loaded);

            reader.close();
            System.out.println("[HistoryStore] 기록 로드: " + logs.size());

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 저장
    private void save() {
        try {
            File f = new File(HISTORY_FILE);
            f.getParentFile().mkdirs();

            Writer writer = new FileWriter(f);
            gson.toJson(logs, writer);
            writer.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 추가 후 저장
    public synchronized void add(ChatMessage msg) {
        logs.add(msg);
        save();
    }

    public List<ChatMessage> getAll() {
        return new ArrayList<>(logs);
    }
}
