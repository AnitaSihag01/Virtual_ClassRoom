package com.classroom.controller;

import com.classroom.dto.Dto.*;
import com.classroom.security.AppUserDetails;
import com.classroom.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/** Endpoints shared by teacher and student. Every one checks class membership on the server (SYS-7). */
@RestController
@RequestMapping("/api/common")
@RequiredArgsConstructor
public class CommonController {
    private final UserService userService;
    private final SessionService sessionService;
    private final MaterialService materials;

    @GetMapping("/me")
    public Map<String, Object> me(@AuthenticationPrincipal AppUserDetails me) {
        return Map.of("id", me.getId(), "name", me.getName(), "email", me.getEmail(), "role", me.getRole(),
                "mustChangePassword", me.isMustChangePassword());
    }

    @PostMapping("/change-password")                                            // TCH-1, STD-1
    public Map<String, String> changePassword(@AuthenticationPrincipal AppUserDetails me, @Valid @RequestBody ChangePasswordReq r) {
        userService.changePassword(me, r);
        return Map.of("message", "Password changed");
    }

    @GetMapping("/classes/{classId}/sessions")
    public List<SessionView> sessions(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long classId) {
        return sessionService.classSessions(me, classId);
    }

    @GetMapping("/classes/{classId}/active-session")                            // STD-3
    public ResponseEntity<SessionView> active(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long classId) {
        return sessionService.active(me, classId).map(ResponseEntity::ok).orElse(ResponseEntity.noContent().build());
    }

    @GetMapping("/sessions/{id}/state")                                         // STD-4, STD-12, SYS-13
    public StateView state(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id) { return sessionService.state(me, id); }

    @GetMapping("/sessions/{id}/participants")                                  // TCH-9, STD-8
    public List<Participant> participants(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id) {
        return sessionService.participants(me, id);
    }

    @GetMapping("/sessions/{id}/replay")                                        // TCH-14, STD-10
    public List<EventView> replay(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id) {
        return sessionService.replay(me, id);
    }

    @GetMapping("/classes/{classId}/materials")                                 // STD-9
    public List<MaterialView> materials(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long classId) {
        return materials.list(me, classId);
    }

    @GetMapping("/materials/{id}/download")                                     // STD-9, SYS-9
    public ResponseEntity<Resource> download(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id) {
        MaterialService.Download d = materials.load(me, id);
        MediaType type = MediaTypeFactory.getMediaType(d.fileName()).orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(d.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(d.resource());
    }
}
