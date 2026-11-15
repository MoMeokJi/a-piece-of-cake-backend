package com.momeokji.aiDiarybackend.service.push;

import org.springframework.stereotype.Component;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;

/** Firebase Admin SDK 로 실제 발송한다. */
@Component
public class FirebaseFcmSender implements FcmSender {

	@Override
	public String send(Message message) throws FirebaseMessagingException {
		return FirebaseMessaging.getInstance().send(message);
	}
}
