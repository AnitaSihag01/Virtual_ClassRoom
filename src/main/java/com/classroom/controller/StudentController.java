package com.classroom.controller;

import com.classroom.dto.Dto.*;
import com.classroom.security.AppUserDetails;
import com.classroom.service.ClassService;
import com.classroom.service.SessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/student")
@RequiredArgsConstructor
public class StudentController {
    private final ClassService classService;
    private final SessionService sessionService;

    @GetMapping("/classes")                                                     // STD-2
    public List<ClassView> classes(@AuthenticationPrincipal AppUserDetails me) { return classService.forStudent(me.getId()); }

    @GetMapping("/attendance")                                                  // STD-11
    public List<AttendanceView> attendance(@AuthenticationPrincipal AppUserDetails me,
                                           @RequestParam(required = false) Long classId) {
        return sessionService.myAttendance(me, classId);
    }
}
