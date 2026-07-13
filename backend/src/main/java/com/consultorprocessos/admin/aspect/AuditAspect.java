package com.consultorprocessos.admin.aspect;

import com.consultorprocessos.admin.annotation.Audited;
import com.consultorprocessos.admin.entity.AuditLog;
import com.consultorprocessos.admin.repository.AuditLogRepository;
import com.consultorprocessos.auth.security.UserDetailsImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.consultorprocessos.auth.repository.UserRepository;

import java.lang.reflect.Method;
import java.util.UUID;

@Aspect
@Component
@RequiredArgsConstructor
@Slf4j
public class AuditAspect {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;

    @Around("@annotation(com.consultorprocessos.admin.annotation.Audited)")
    public Object audit(ProceedingJoinPoint jp) throws Throwable {
        Object result = jp.proceed();

        try {
            MethodSignature sig    = (MethodSignature) jp.getSignature();
            Method          method = sig.getMethod();
            Audited         ann    = method.getAnnotation(Audited.class);

            String entityId = extractEntityId(jp.getArgs());
            String ipAddress = extractIpAddress();
            UserDetailsImpl actor = extractActor();

            AuditLog log = new AuditLog();
            if (actor != null) {
                log.setActorEmail(actor.getUsername());
                log.setActor(userRepository.getReferenceById(actor.getUserId()));
            } else {
                log.setActorEmail("system");
            }
            log.setAction(ann.action());
            log.setEntityType(ann.entityType());
            log.setEntityId(entityId);
            log.setIpAddress(ipAddress);
            auditLogRepository.save(log);

        } catch (Exception e) {
            log.warn("AuditAspect: falha ao gravar audit_log: {}", e.getMessage());
        }

        return result;
    }

    private String extractEntityId(Object[] args) {
        if (args == null || args.length == 0) return "unknown";
        Object first = args[0];
        if (first instanceof UUID) return first.toString();
        if (first instanceof String) return (String) first;
        return first != null ? first.toString() : "unknown";
    }

    private UserDetailsImpl extractActor() {
        try {
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof UserDetailsImpl u) return u;
        } catch (Exception ignored) {}
        return null;
    }

    private String extractIpAddress() {
        try {
            var attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs != null) return attrs.getRequest().getRemoteAddr();
        } catch (Exception ignored) {}
        return null;
    }
}