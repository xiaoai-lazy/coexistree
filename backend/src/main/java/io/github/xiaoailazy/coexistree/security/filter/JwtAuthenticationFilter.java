package io.github.xiaoailazy.coexistree.security.filter;

import io.github.xiaoailazy.coexistree.security.jwt.JwtUtil;
import io.github.xiaoailazy.coexistree.security.model.SecurityUserDetails;
import io.github.xiaoailazy.coexistree.user.entity.UserEntity;
import io.github.xiaoailazy.coexistree.user.repository.UserRepository;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        // We WANT to filter async dispatches to restore SecurityContext
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        
        // Step 1: Check if this is an async dispatch
        if (request.getDispatcherType() == DispatcherType.ASYNC) {
            // Restore SecurityContext from request attribute
            SecurityContext savedContext = (SecurityContext) request.getAttribute("SPRING_SECURITY_CONTEXT");
            if (savedContext != null) {
                SecurityContextHolder.setContext(savedContext);
                logger.debug("[JWT] Restored SecurityContext from request attribute for async dispatch");
            }
            filterChain.doFilter(request, response);
            return;
        }
        
        // Step 2: Normal request processing (initial request)
        String jwt = getJwtFromRequest(request);
        if (StringUtils.hasText(jwt)) {
            try {
                if (jwtUtil.validateToken(jwt)) {
                    Long userId = jwtUtil.getUserIdFromToken(jwt);
                    UserEntity user = userRepository.findById(userId).orElse(null);
                    if (user != null) {
                        SecurityUserDetails userDetails = new SecurityUserDetails(user);
                        UsernamePasswordAuthenticationToken authentication =
                                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                        SecurityContext context = SecurityContextHolder.createEmptyContext();
                        context.setAuthentication(authentication);
                        SecurityContextHolder.setContext(context);
                        
                        // Step 3: Save SecurityContext to request attribute for async dispatch
                        request.setAttribute("SPRING_SECURITY_CONTEXT", context);
                        logger.debug("[JWT] Saved SecurityContext to request attribute for potential async dispatch");
                    }
                }
            } catch (Exception ex) {
                logger.warn("[JWT] Error processing token: " + ex.getMessage());
            }
        }

        filterChain.doFilter(request, response);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
