package com.e2ee.client;

import com.e2ee.crypto.EncryptedPayload;
import com.e2ee.crypto.EcdhUtil;
import com.e2ee.crypto.AesGcmUtil;

import java.security.KeyPair;
import java.util.Scanner;

public class ClientMain {

    public static void main(String[] args) throws Exception {

        Scanner sc = new Scanner(System.in);

        System.out.println("1. 회원가입 2. 로그인");

        int choiceMenu = sc.nextInt();
        sc.nextLine();

        String id = null;
        String pw = null;

        if (choiceMenu == 1) {

            System.out.println("====== 회원가입 ======");
            System.out.println("아이디를 입력하세요 : ");
            id = sc.nextLine();

            System.out.println("비밀번호를 입력하세요 : ");
            pw = sc.nextLine();

        } else if (choiceMenu == 2) {
            System.out.println("==== 로그인 ====");
            System.out.println("아이디를 입력하세요 : ");
            id = sc.nextLine();
            System.out.println("비밀번호를 입력하세요 : ");
            pw = sc.nextLine();
        } else {
            System.out.println("잘못된 메뉴입니다. 프로그램을 종료합니다.");
            return;
        }

        // 여기까지 오면 id, pw 입력은 끝난 상태
        System.out.println("\n[DEBUG] 현재 로그인 사용자 ID: " + id);


        // ==== 여기부터는 E2EE 클라이언트 공통 준비 ====

        // (1) 채팅에서 사용할 "고정 식별자" 만들기 (예: foo#0001)
        //     나중에는 서버가 #숫자 부분을 내려줄 예정이라고 가정하고,
        //     지금은 임시값으로 "#0001"을 사용.
        String userTag = id + "#0001";
        System.out.println("[INFO] 이 클라이언트의 채팅 ID는 " + userTag + " 입니다.");

        // (2) ECDH 키쌍 생성
        System.out.println("[INFO] ECDH 키쌍 생성 중...");
        KeyPair myKeyPair = EcdhUtil.generateKeyPair();
        System.out.println("[OK] 키쌍 생성 완료! 이제 이 키로 세션을 만들 수 있습니다.");

        // (3) 이제부터는 콘솔 명령 루프
        while (true) {
            System.out.println("> ");
            String line = sc.nextLine();

            if (line.equalsIgnoreCase("/quit")) {
                System.out.println("클라이언트를 종료합니다.");
                break;
            }
            if (line.startsWith("/key ")) {
                String target = line.substring(5).trim(); // 예: /key foo#0001
                System.out.println("[DEBUG] /key 명령 입력됨. 대상: " + target);
            } else if (line.startsWith("/history ")) {
                String target = line.substring(9).trim(); // 예: /history foo#0001
                System.out.println("[DEBUG] /history 명령 입력됨. 대상: " + target);
            } else {
                // 일반 채팅 메시지
                System.out.println("[INFO] 일반 메시지 입력됨: " + line);
            }
        }
    }


}
