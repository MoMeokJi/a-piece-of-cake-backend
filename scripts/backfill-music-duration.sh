#!/usr/bin/env bash
# ============================================================
# music.duration_seconds 백필용 UPDATE SQL 생성기 (1회성, 사람이 실행)
#
# 표준 입력으로 video_id 를 한 줄에 하나씩 받아 YouTube Data API v3 의
# videos.list(part=contentDetails) 를 50개씩 호출하고, ISO 8601 duration(PT3M2S)을
# 초(182)로 바꿔 UPDATE 문을 표준 출력으로 낸다. DB 에는 직접 쓰지 않는다.
#
# 필요: curl, jq, 환경변수 YOUTUBE_API_KEY (macOS 기본 bash 3.2 에서도 동작)
# 사용:
#   mysql -N -e "SELECT video_id FROM music WHERE duration_seconds IS NULL" aiDiary \
#     | ./scripts/backfill-music-duration.sh > update-music-duration.sql
#   # 내용 확인 후
#   mysql aiDiary < update-music-duration.sql
# ============================================================
set -euo pipefail

: "${YOUTUBE_API_KEY:?YOUTUBE_API_KEY 환경변수가 필요합니다}"
command -v jq >/dev/null || { echo "jq 가 필요합니다" >&2; exit 1; }

API="https://www.googleapis.com/youtube/v3/videos"

# 표준 입력의 video_id 를 모은다 (빈 줄과 CR 제거). mapfile 은 bash 4 전용이라 쓰지 않는다.
IDS=()
while IFS= read -r line || [[ -n "$line" ]]; do
	id="${line//[[:space:]]/}"
	[[ -n "$id" ]] && IDS+=("$id")
done
total=${#IDS[@]}
echo "-- 대상 video_id ${total}개" >&2

# 응답에 온 id 를 모아 두었다가 끝에 빠진 것을 알린다
FOUND=""

# 50개씩 묶어 호출 (API 의 id 파라미터 최대 개수)
for ((i = 0; i < total; i += 50)); do
	chunk=("${IDS[@]:i:50}")
	joined=$(IFS=,; echo "${chunk[*]}")
	out=$(curl -sf "${API}?part=contentDetails&id=${joined}&key=${YOUTUBE_API_KEY}" \
	| jq -r --arg q "'" '
		# ISO 8601 duration → 초. 일(D) 단위까지 받는다. 라이브(P0D)는 0초.
		def secs:
			capture("^P(?:(?<d>[0-9]+)D)?(?:T(?:(?<h>[0-9]+)H)?(?:(?<m>[0-9]+)M)?(?:(?<s>[0-9]+)S)?)?$")
			| ((.d // "0" | tonumber) * 86400) + ((.h // "0" | tonumber) * 3600)
			  + ((.m // "0" | tonumber) * 60) + (.s // "0" | tonumber);
		.items[]
		| (.contentDetails.duration | secs) as $sec
		| if $sec > 0
		  then "UPDATE music SET duration_seconds = \($sec) WHERE video_id = \($q)\(.id)\($q);"
		  else "-- 건너뜀(길이 0, 라이브?): \(.id)"
		  end
	')
	printf '%s\n' "$out"
	FOUND+=" $(printf '%s\n' "$out" | sed -n "s/.*video_id = '\([^']*\)'.*/\1/p; s/^-- 건너뜀.*: //p" | tr '\n' ' ')"
done

for id in "${IDS[@]}"; do
	case " $FOUND " in *" $id "*) ;; *) echo "-- 응답 없음(비공개·삭제?): $id" >&2 ;; esac
done
echo "-- 끝." >&2
