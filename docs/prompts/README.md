# OpenAI 프롬프트

프롬프트 원본은 **`src/main/resources/prompts/<key>.md`** 다(content, summary, colors, music, sympathy). 각 파일은 Responses API 에 system 메시지로 그대로 들어가고, 입력 객체의 JSON 문자열(예: `{"text":"일기 본문"}`)이 user 메시지로 뒤따른다. OpenAI 대시보드의 저장 프롬프트(`pmpt_…` ID/버전)는 2026-11-30 에 종료되어 더 이상 쓰지 않는다. 모델·temperature·max tokens·reasoning effort 는 `application.properties` 의 `openai.prompts.<key>.*` 에 있다(모델은 gpt-6-luna, reasoning effort 가 none 이 아니면 temperature 를 둘 수 없다). 프롬프트를 바꾸려면 해당 `.md` 파일을 고쳐 PR 을 올리고 코드 리뷰를 거쳐 배포한다. 대시보드에서 고치는 것은 서버에 반영되지 않는다.
