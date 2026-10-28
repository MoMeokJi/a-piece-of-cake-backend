#!/usr/bin/env bash
# ============================================================
# music.duration_seconds 백필용 UPDATE SQL 생성기 (1회성, 사람이 실행)
#
# 표준 입력으로 video_id 를 한 줄에 하나씩 받아 YouTube Data API v3 의
# videos.list(part=contentDetails) 를 50개씩 호출하고, ISO 8601 duration(PT3M2S)을
# 초(182)로 바꿔 UPDATE 문을 표준 출력으로 낸다. DB 에는 직접 쓰지 않는다.
#
# 필요: curl, jq, 환경변수 YOUTUBE_API_KEY
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

# 50개씩 묶어 호출 (API 의 id 파라미터 최대 개수)
mapfile -t IDS < <(grep -v '^[[:space:]]*$' | tr -d '\r')
total=${#IDS[@]}
echo "-- 대상 video_id ${total}개" >&2

for ((i = 0; i < total; i += 50)); do
	chunk=("${IDS[@]:i:50}")
	joined=$(IFS=,; echo "${chunk[*]}")
	curl -sf "${API}?part=contentDetails&id=${joined}&key=${YOUTUBE_API_KEY}" \
	| jq -r --arg q "'" '
		def secs:
			capture("^PT(?:(?<h>[0-9]+)H)?(?:(?<m>[0-9]+)M)?(?:(?<s>[0-9]+)S)?$")
			| ((.h // "0" | tonumber) * 3600) + ((.m // "0" | tonumber) * 60) + (.s // "0" | tonumber);
		.items[]
		| "UPDATE music SET duration_seconds = \(.contentDetails.duration | secs) WHERE video_id = \($q)\(.id)\($q);"
	'
done

echo "-- 끝. 응답에 없는 video_id(비공개·삭제)는 UPDATE 가 생성되지 않았다." >&2
