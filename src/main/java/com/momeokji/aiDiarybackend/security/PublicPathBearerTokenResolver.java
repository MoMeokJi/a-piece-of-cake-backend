package com.momeokji.aiDiarybackend.security;

import java.util.List;

import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.util.matcher.RequestMatcher;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 공개 경로에서는 Bearer 토큰을 아예 읽지 않는다(스펙 7.2).
 * 만료된 access 를 붙인 채 {@code /auth/refresh} 를 불러도 정상 동작해야 하기 때문이다.
 */
public class PublicPathBearerTokenResolver implements BearerTokenResolver {

	private final List<RequestMatcher> publicPaths;
	private final BearerTokenResolver delegate = new DefaultBearerTokenResolver();

	public PublicPathBearerTokenResolver(RequestMatcher... publicPaths) {
		this.publicPaths = List.of(publicPaths);
	}

	@Override
	public String resolve(HttpServletRequest request) {
		for (RequestMatcher matcher : publicPaths) {
			if (matcher.matches(request)) {
				return null;
			}
		}
		return delegate.resolve(request);
	}
}
