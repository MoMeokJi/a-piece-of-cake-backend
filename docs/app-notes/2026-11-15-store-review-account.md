# 스토어 심사용 계정 준비

작성일 2026-11-15. 서버 배포와는 별개로, 앱을 스토어에 올리는 사람이 하는 일이다.

## 왜 필요한가

이번 버전부터 로그인(구글·애플)이 필수다. 로그인이 있는 앱은 애플과 구글 모두 심사자가 들어올 방법을 요구한다. 비워 두면 흔히 반려된다.

게스트 로그인을 만들 필요는 없다. 심사 전용 구글 계정을 하나 만들어 스토어에 적어 주면 된다.

## 처음 한 번만 하는 일 (20~30분)

- [ ] **심사 전용 Gmail 을 새로 만든다.**
  - 팀 계정을 주지 않는다. 팀 계정은 GCP 와 OpenAI 를 소유하고 있어 비밀번호를 외부에 넘기면 안 된다.
  - **2단계 인증을 끈다.** 구글이 "2단계 인증이나 일회용 코드 없이, 언제든, 어느 지역에서든 재사용할 수 있는 계정"을 요구한다.
- [ ] **운영 배포가 끝난 뒤**, 그 계정으로 앱에 구글 로그인해 일기를 두세 편 쓴다. 문답 일기와 자유 일기를 하나씩 쓰면 좋다. 빈 계정이면 심사자가 색상·음악·모아보기를 볼 수 없다.
- [ ] **App Store Connect** → 앱 → 앱 심사 정보
  - "로그인 필요" 체크
  - 사용자 이름·암호에 심사용 Gmail 의 아이디·비밀번호
  - 메모(아래 문구를 그대로 써도 된다)
- [ ] **Play Console** → 앱 콘텐츠 → 앱 액세스 권한
  - "전체 또는 일부 기능이 제한됨" 선택
  - 같은 계정의 아이디·비밀번호와 로그인 순서를 적는다(아래 문구)

## 심사 메모에 적을 문구

App Store Connect (영문 권장):

```
This app requires sign-in with Google or Apple.
- To sign in with Google: tap "Continue with Google" and use the demo account provided above.
- To sign in with Apple: you can use your own Apple ID.
The demo account already has a few diary entries so you can see the color, music and message features.
Account deletion: My Page > Delete account.
```

Play Console:

```
1. 앱을 실행하고 "Google로 계속하기"를 누른다.
2. 위의 테스트 계정(Gmail)으로 로그인한다. 2단계 인증은 꺼져 있다.
3. 로그인하면 홈 화면에서 일기를 쓰고, 지난 일기를 볼 수 있다.
```

버튼 이름과 메뉴 위치는 실제 앱 화면에 맞춰 고친다.

## 업데이트 심사 때

두 스토어 모두 적어 둔 심사 정보가 다음 버전에 그대로 남는다. 다시 적을 필요가 없다. 심사용 계정의 비밀번호만 바꾸지 않으면 된다.

## 반려되면

- **구글이 "본인 확인"을 요구해 심사자가 로그인하지 못한 경우**: 심사자가 새 기기에서 로그인할 때 생길 수 있다. 이 사유로 반려되면 심사 전용 로그인(아이디·비밀번호)을 서버에 추가하는 것을 그때 검토한다. 미리 만들지 않는다.
- **구글 로그인이 아예 안 되는 경우**: 구글 OAuth 동의 화면의 게시 상태가 "테스트"인지 본다. "프로덕션"이어야 한다. 배포 체크리스트 6-3 에 확인 항목이 있다.

## 확인하지 못한 것

애플 심사자가 본인 Apple ID 로 애플 로그인을 써도 되는지는 공식 문서로 확인하지 못했다. 그렇게 심사받은 사례는 있다. 그래서 구글 계정을 반드시 같이 적는다.

## 근거

- Google Play Console 고객센터, 앱 액세스 권한 요건: https://support.google.com/googleplay/android-developer/answer/15748846
- Google Play Console 고객센터, 앱 검토 준비: https://support.google.com/googleplay/android-developer/answer/9859455
- App Store 심사 가이드라인 2.1(로그인이 있으면 데모 계정 제공)
