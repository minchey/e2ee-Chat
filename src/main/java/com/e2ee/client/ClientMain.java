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
        }else {
            System.out.println("잘못된 메뉴입니다. 프로그램을 종료합니다.");
            return;
        }

        // 여기까지 오면 id, pw 입력은 끝난 상태
        System.out.println("\n[DEBUG] 현재 로그인 사용자 ID: " + id);
    }

    // ==== 여기부터는 E2EE 클라이언트 공통 준비 ====

}
