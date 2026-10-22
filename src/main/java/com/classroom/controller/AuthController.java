package com.classroom.controller;

import com.classroom.dto.Dto.LoginReq;
import com.classroom.security.AppUserDetails;
import com.classroom.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService auth;

    /** SYS-1 / SYS-2: the Admin / Teacher / Student choice on the first page sends its role here. Logout: POST /api/auth/logout */
    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginReq req, HttpServletRequest rq, HttpServletResponse rs) {
        AppUserDetails d = auth.login(req.email(), req.password(), req.role(), rq, rs);
        return Map.of("id", d.getId(), "name", d.getName(), "role", d.getRole(),
                "mustChangePassword", d.isMustChangePassword(),
                "home", "/" + d.getRole().name().toLowerCase() + "/home.html");
    }
}
