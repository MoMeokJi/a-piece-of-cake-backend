package com.momeokji.aiDiarybackend.security;

import com.momeokji.aiDiarybackend.common.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

	private final JwtUtil jwt;

	// 필터를 적용하지 않을 경로(비인증 허용)
	private static final Set<String> WHITELIST = Set.of(
		"/users",
		"/auth/refresh"
	);

	@Override
	protected boolean shouldNotFilter(HttpServletRequest request) {
		// 프리플라이트 요청은 스킵
		if ("OPTIONS".equalsIgnoreCase(request.getMethod()))
			return true;

		//whiteList 체크
		String path = request.getServletPath();
		return WHITELIST.contains(path);
	}


	@Override
	protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
		throws ServletException, IOException {

		String token = JwtUtil.resolveBearer(req.getHeader(HttpHeaders.AUTHORIZATION));

		if (token != null) {
			try {
				var jws = jwt.parse(token);
				var claims = jws.getPayload();
				if (!"ACCESS".equals(claims.get("typ"))) {
					throw new RuntimeException("not access token");
				}

				String userId = claims.getSubject();
				var auth = new UsernamePasswordAuthenticationToken(userId, null, java.util.List.of());
				auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(req));
				SecurityContextHolder.getContext().setAuthentication(auth);
			} catch (Exception e) {
				SecurityContextHolder.clearContext(); // 토큰 문제면 인증 없이 진행
			}
		}
		chain.doFilter(req, res);
	}
}
