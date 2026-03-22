package com.momeokji.aiDiarybackend.security;

import com.momeokji.aiDiarybackend.common.util.JwtUtil;
import com.momeokji.aiDiarybackend.entity.Member;
import com.momeokji.aiDiarybackend.repository.MemberRepository;
import com.momeokji.aiDiarybackend.service.DiaryService;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

	private final JwtUtil jwt;
	private final MemberRepository memberRepository;
	private final DiaryService diaryService;

	private static final Set<String> WHITELIST = Set.of(
		"/auth/refresh",
		"/auth/login",
		"/health",
		"/error"
	);

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		String method = request.getMethod();
		String path = request.getServletPath();

		//에러 로그 체크
		log.info("[AUTH] shouldNotFilter? method={} path={}", method, path);

		if ("OPTIONS".equalsIgnoreCase(method)) {
			return true;
		}

		if (path.startsWith("/error")){
			return true;
		}

		if ("/users".equals(path)) {
			return "POST".equalsIgnoreCase(method);
		}

		return WHITELIST.contains(path);
	}

	@Override
	protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
		throws ServletException, IOException {

		final String access  = JwtUtil.resolveBearer(req.getHeader(HttpHeaders.AUTHORIZATION));
		final String refresh = req.getHeader("Refresh-Token");

		// 1) ACCESS 먼저 검증
		if (access != null) {
			try {
				Jws<Claims> ajws = jwt.parse(access);
				Claims ac = ajws.getPayload();

				if(!"ACCESS".equals(ac.get("typ"))){
					throw new JwtException("not access");
				}

				String userId = ac.getSubject();

				Member member = memberRepository.findByMemberIdAndIsValidTrue(userId)
						.orElseThrow(() -> new JwtException("탈퇴한 유저입니다."));

				setAuth(userId, req);

				//토큰 검증 하면서 api호출 시간 갱신
				diaryService.updateLastActiveAt(userId);

				// refresh 유효성 보장: 없거나/무효/만료면 새로 발급해서 내려줌
				String ensuredRefresh = ensureValidRefresh(userId, refresh);

				res.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + access);
				res.setHeader("Refresh-Token", ensuredRefresh);
				res.addHeader("Access-Control-Expose-Headers", "Authorization, Refresh-Token");

				chain.doFilter(req, res);
				return;

			} catch (ExpiredJwtException ignored) {
				// access 만료 → 아래 refresh 로직
			} catch (JwtException ignored) {
				// access 위조/형식오류 → 아래 refresh 로직
			}
		}

		// 2) ACCESS 실패 → REFRESH로 자동 리프레시(항상 새 refresh 회전)
		if (refresh != null) {
			try {
				Jws<Claims> rjws = jwt.parse(refresh);
				Claims rc = rjws.getPayload();

				if(!"REFRESH".equals(rc.get("typ"))){
					throw new JwtException("not refresh");
				}

				String userId = rc.getSubject();

				Member member = memberRepository.findByMemberIdAndIsValidTrue(userId)
					.orElseThrow(() -> new JwtException("탈퇴한 유저입니다."));

				String newAccess  = jwt.generateAccessToken(member);
				String newRefresh = jwt.generateRefreshToken(member);

				setAuth(userId, req);

				//갱신
				diaryService.updateLastActiveAt(userId);

				res.setHeader(HttpHeaders.AUTHORIZATION, "Bearer " + newAccess);
				res.setHeader("Refresh-Token", newRefresh);
				res.addHeader("Access-Control-Expose-Headers", "Authorization, Refresh-Token");

				chain.doFilter(req, res);
				return;

			} catch (JwtException ignored) {
				// refresh도 실패 → 401
			}
		}

		// 3) 둘 다 실패 → 401
		SecurityContextHolder.clearContext();
		res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
		res.setContentType("application/json");
		res.getWriter().write(
			"{\"code\":\"TOKEN_EXPIRED\",\"message\":\"Access & Refresh expired or invalid\"}"
		);
	}

	private String ensureValidRefresh(String userId, String incomingRefresh) {
		if (incomingRefresh == null) {
			return issueNewRefresh(userId);
		}

		try {
			Jws<Claims> rjws = jwt.parse(incomingRefresh);
			Claims rc = rjws.getPayload();

			if(!"REFRESH".equals(rc.get("typ"))){
				return issueNewRefresh(userId);
			}
			if(!userId.equals(rc.getSubject())){
				return issueNewRefresh(userId);
			}
			return incomingRefresh; // 유효하므로 그대로 사용

		}catch(JwtException e){
			return issueNewRefresh(userId);
		}
	}

	private String issueNewRefresh(String userId) {
		Member member = memberRepository.findByMemberIdAndIsValidTrue(userId)
			.orElseThrow(() -> new JwtException("탈퇴한 유저입니다."));
		return jwt.generateRefreshToken(member);
	}

	private void setAuth(String userId, HttpServletRequest req) {
		UsernamePasswordAuthenticationToken auth =
			new UsernamePasswordAuthenticationToken(userId, null, List.of());
		auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
		SecurityContextHolder.getContext().setAuthentication(auth);
	}
}
