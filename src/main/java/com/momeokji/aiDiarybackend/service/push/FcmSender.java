package com.momeokji.aiDiarybackend.service.push;

import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;

/** FCM 발송 경계. 테스트에서 mock 으로 바꿔 Firebase 없이 발송 로직을 검사한다. */
public interface FcmSender {

	/** @return Firebase 가 돌려준 메시지 ID */
	String send(Message message) throws FirebaseMessagingException;
}
