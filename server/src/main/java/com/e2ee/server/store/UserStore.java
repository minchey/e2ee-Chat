package com.e2ee.server.store;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class UserStore {

    private static final String USER_FILE = "data/users.json";

    private final Gson gson = new Gson();

    // 메모리에 캐시 형태로 올려둠
    private final Map<String, UserRecord> users = new ConcurrentHashMap<>();

    public UserStore() {
        load();
    }

    // -------- User 구조 --------
    public static class UserRecord {
        public String id;
        public String password;
        public String publicKey;

        public UserRecord(String id, String pw, String publicKey) {
            this.id = id;
            this.password = pw;
            this.publicKey = publicKey;
        }
    }

    // 파일 -> 메모리 로드
    private void load() {
        try {
            File f = new File(USER_FILE);
            if (!f.exists()) {
                System.out.println("[UserStore] users.json 없음 → 새로 생성");
                save();    // 빈 파일 생성
                return;
            }

            Reader reader = new FileReader(f);
            Type type = new TypeToken<Map<String, UserRecord>>(){}.getType();
            Map<String, UserRecord> loaded = gson.fromJson(reader, type);

            if (loaded != null) users.putAll(loaded);

            reader.close();
            System.out.println("[UserStore] 회원정보 로드완료: " + users.size() + "명");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // 메모리 → 파일 저장
    private synchronized void save() {
        try {
            File f = new File(USER_FILE);
            f.getParentFile().mkdirs();

            Writer writer = new FileWriter(f);
            gson.toJson(users, writer);
            writer.close();

            System.out.println("[UserStore] 회원정보 저장완료");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // ---------- 외부에서 사용하는 메서드 -----------

    public boolean exists(String id) {
        return users.containsKey(id);
    }

    public boolean checkPassword(String id, String pw) {
        UserRecord r = users.get(id);
        if (r == null) return false;
        return r.password.equals(pw);
    }

    public String getPublicKey(String id) {
        UserRecord r = users.get(id);
        if (r == null) return null;
        return r.publicKey;
    }

    public void addUser(String id, String pw, String publicKey) {
        users.put(id, new UserRecord(id, pw, publicKey));
        save();
    }
}
