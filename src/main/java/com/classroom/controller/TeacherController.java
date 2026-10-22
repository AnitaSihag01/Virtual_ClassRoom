package com.classroom.controller;

import com.classroom.dto.Dto.*;
import com.classroom.entity.LiveSession;
import com.classroom.security.AppUserDetails;
import com.classroom.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/teacher")
@RequiredArgsConstructor
public class TeacherController {
    private final ClassService classService;
    private final SessionService sessionService;
    private final RoomService roomService;
    private final MaterialService materials;
    private final EventService events;
    private final AccessService access;
    private final WhiteboardExportService export;

    @GetMapping("/classes")                                                     // TCH-2
    public List<ClassView> classes(@AuthenticationPrincipal AppUserDetails me) { return classService.forTeacher(me.getId()); }

    @PostMapping("/classes/{classId}/sessions")                                 // TCH-3
    public SessionView start(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long classId) {
        return sessionService.start(me, classId);
    }

    @PostMapping("/sessions/{id}/end")                                          // TCH-4
    public SessionView end(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id) {
        return sessionService.end(me, id);
    }

    @PutMapping("/sessions/{id}/drawing/{studentId}")                           // TCH-6 give
    public Map<String, String> allowDrawing(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id, @PathVariable Long studentId) {
        roomService.setDrawing(sessionService.teacherSession(me, id), studentId);
        return Map.of("message", "Drawing permission given");
    }

    @DeleteMapping("/sessions/{id}/drawing")                                    // TCH-6 take back
    public Map<String, String> revokeDrawing(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id) {
        roomService.setDrawing(sessionService.teacherSession(me, id), null);
        return Map.of("message", "Drawing permission removed");
    }

    @PutMapping("/sessions/{id}/mute/{studentId}")                              // TCH-8 mute
    public Map<String, String> mute(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id, @PathVariable Long studentId) {
        roomService.setMuted(sessionService.teacherSession(me, id), studentId, true);
        return Map.of("message", "Student muted");
    }

    @DeleteMapping("/sessions/{id}/mute/{studentId}")                           // TCH-8 unmute
    public Map<String, String> unmute(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id, @PathVariable Long studentId) {
        roomService.setMuted(sessionService.teacherSession(me, id), studentId, false);
        return Map.of("message", "Student unmuted");
    }

    @DeleteMapping("/sessions/{id}/chat")                                       // TCH-8 clear chat
    public Map<String, String> clearChat(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id) {
        roomService.clearChat(sessionService.teacherSession(me, id));
        return Map.of("message", "Chat cleared");
    }

    @DeleteMapping("/sessions/{id}/students/{studentId}")                       // TCH-10
    public Map<String, String> kick(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id, @PathVariable Long studentId) {
        roomService.kick(sessionService.teacherSession(me, id), studentId);
        return Map.of("message", "Student removed from session");
    }

    @GetMapping("/sessions/{id}/attendance")                                    // TCH-13
    public List<AttendanceView> attendance(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id) {
        return sessionService.attendanceForTeacher(me, id);
    }

    @PostMapping(value = "/classes/{classId}/materials", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)   // TCH-11
    public MaterialView upload(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long classId,
                               @RequestParam("file") MultipartFile file) {
        return materials.upload(me, classId, file);
    }

    @DeleteMapping("/materials/{id}")                                           // TCH-12
    public Map<String, String> deleteMaterial(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id) {
        materials.delete(me, id);
        return Map.of("message", "Material deleted");
    }

    @GetMapping(value = "/sessions/{id}/whiteboard.png", produces = MediaType.IMAGE_PNG_VALUE)   // TCH-15
    public ResponseEntity<byte[]> png(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id) {
        access.requireSessionTeacher(me, id);
        return file(export.png(events.boardStrokes(id)), MediaType.IMAGE_PNG, "whiteboard-" + id + ".png");
    }

    @GetMapping(value = "/sessions/{id}/whiteboard.pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> pdf(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long id) {
        access.requireSessionTeacher(me, id);
        return file(export.pdf(events.boardStrokes(id)), MediaType.APPLICATION_PDF, "whiteboard-" + id + ".pdf");
    }

    private ResponseEntity<byte[]> file(byte[] data, MediaType type, String name) {
        return ResponseEntity.ok().contentType(type)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(name).build().toString())
                .body(data);
    }
}
