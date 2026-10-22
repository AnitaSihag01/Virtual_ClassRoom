package com.classroom.controller;

import com.classroom.dto.Dto.*;
import com.classroom.entity.Role;
import com.classroom.repository.*;
import com.classroom.service.*;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final UserService userService;
    private final ClassService classService;
    private final SessionService sessionService;
    private final LoginHistoryRepository loginHistory;
    private final UserRepository users;
    private final ClassRepository classes;
    private final LiveSessionRepository sessions;

    // ----- users (ADM-3 .. ADM-6) -----
    @PostMapping("/teachers") @ResponseStatus(HttpStatus.CREATED)
    public UserView createTeacher(@Valid @RequestBody CreateTeacherReq r) { return userService.createTeacher(r); }

    @PostMapping("/students") @ResponseStatus(HttpStatus.CREATED)
    public UserView createStudent(@Valid @RequestBody CreateStudentReq r) { return userService.createStudent(r); }

    @GetMapping("/users")
    public List<UserView> users(@RequestParam Role role) { return userService.list(role); }

    @PutMapping("/users/{id}")
    public UserView update(@PathVariable Long id, @Valid @RequestBody UpdateUserReq r) { return userService.update(id, r); }

    @PatchMapping("/users/{id}/deactivate")
    public UserView deactivate(@PathVariable Long id) { return userService.setActive(id, false); }

    @PatchMapping("/users/{id}/activate")
    public UserView activate(@PathVariable Long id) { return userService.setActive(id, true); }

    @PostMapping("/users/{id}/reset-password")
    public Map<String, String> reset(@PathVariable Long id, @Valid @RequestBody ResetPasswordReq r) {
        userService.resetPassword(id, r.newPassword());
        return Map.of("message", "Password reset. The user must change it at next login.");
    }

    // ----- classes (ADM-7 .. ADM-9) -----
    @PostMapping("/classes") @ResponseStatus(HttpStatus.CREATED)
    public ClassView createClass(@Valid @RequestBody ClassReq r) { return classService.create(r); }

    @GetMapping("/classes")
    public List<ClassView> classes() { return classService.listAll(); }

    @PutMapping("/classes/{id}")
    public ClassView updateClass(@PathVariable Long id, @Valid @RequestBody ClassReq r) { return classService.update(id, r); }

    @DeleteMapping("/classes/{id}")
    public Map<String, String> deleteClass(@PathVariable Long id) {
        classService.delete(id);
        return Map.of("message", "Class deleted");
    }

    @PutMapping("/classes/{classId}/teacher/{teacherId}")
    public ClassView assignTeacher(@PathVariable Long classId, @PathVariable Long teacherId) {
        return classService.assignTeacher(classId, teacherId);
    }

    @GetMapping("/classes/{classId}/students")
    public List<UserView> students(@PathVariable Long classId) { return classService.students(classId); }

    @PostMapping("/classes/{classId}/students/{studentId}") @ResponseStatus(HttpStatus.CREATED)
    public Map<String, String> enroll(@PathVariable Long classId, @PathVariable Long studentId) {
        classService.enroll(classId, studentId);
        return Map.of("message", "Student enrolled");
    }

    @DeleteMapping("/classes/{classId}/students/{studentId}")
    public Map<String, String> unenroll(@PathVariable Long classId, @PathVariable Long studentId) {
        classService.unenroll(classId, studentId);
        return Map.of("message", "Student removed from class");
    }

    // ----- reports (ADM-10 .. ADM-12) -----
    @GetMapping("/sessions")
    public List<SessionView> sessions() { return sessionService.all(); }

    @GetMapping("/login-history")
    public List<LoginHistoryView> loginHistory(@RequestParam(defaultValue = "200") int limit) {
        return loginHistory.findAllByOrderByLoggedAtDesc(PageRequest.of(0, Math.min(Math.max(limit, 1), 1000)))
                .stream().map(LoginHistoryView::of).toList();
    }

    @GetMapping("/dashboard")
    public Map<String, Long> dashboard() {
        return Map.of("teachers", users.countByRole(Role.TEACHER), "students", users.countByRole(Role.STUDENT),
                "classes", classes.count(), "sessions", sessions.count());
    }
}
