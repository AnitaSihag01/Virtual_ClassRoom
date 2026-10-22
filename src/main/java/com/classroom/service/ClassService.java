package com.classroom.service;

import com.classroom.dto.Dto.*;
import com.classroom.entity.*;
import com.classroom.exception.ApiException;
import com.classroom.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ClassService {
    private final ClassRepository classes;
    private final UserRepository users;
    private final EnrollmentRepository enrollments;
    private final LiveSessionRepository sessions;
    private final SessionEventRepository eventRepo;
    private final AttendanceRepository attendance;
    private final MaterialService materials;
    private final RoomRegistry registry;

    private ClassRoom get(Long id) {
        return classes.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Class not found"));
    }

    private User teacher(Long id) {
        User t = users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Teacher not found"));
        if (t.getRole() != Role.TEACHER || !t.isActive())
            throw new ApiException(HttpStatus.BAD_REQUEST, "User is not an active teacher");
        return t;
    }

    public ClassView create(ClassReq r) {                                       // ADM-7, ADM-8
        ClassRoom c = new ClassRoom();
        c.setName(r.name().trim()); c.setSubject(r.subject().trim()); c.setSemester(r.semester().trim());
        if (r.teacherId() != null) c.setTeacher(teacher(r.teacherId()));
        return ClassView.of(classes.save(c));
    }

    public ClassView update(Long id, ClassReq r) {
        ClassRoom c = get(id);
        c.setName(r.name().trim()); c.setSubject(r.subject().trim()); c.setSemester(r.semester().trim());
        if (r.teacherId() != null) c.setTeacher(teacher(r.teacherId()));
        return ClassView.of(classes.save(c));
    }

    public ClassView assignTeacher(Long classId, Long teacherId) {              // ADM-8
        ClassRoom c = get(classId);
        c.setTeacher(teacher(teacherId));
        return ClassView.of(classes.save(c));
    }

    @Transactional
    public void delete(Long id) {                                               // ADM-7 (delete) - removes everything under it
        ClassRoom c = get(id);
        for (LiveSession s : sessions.findByClassRoomIdOrderByStartedAtDesc(id)) {
            registry.close(s.getId());
            eventRepo.deleteBySessionId(s.getId());
            attendance.deleteBySessionId(s.getId());
        }
        sessions.deleteByClassRoomId(id);
        materials.deleteAllForClass(id);
        enrollments.deleteByClassRoomId(id);
        classes.delete(c);
    }

    public List<ClassView> listAll() { return classes.findAll().stream().map(ClassView::of).toList(); }

    public List<ClassView> forTeacher(Long teacherId) {                         // TCH-2
        return classes.findByTeacherId(teacherId).stream().map(ClassView::of).toList();
    }

    public List<ClassView> forStudent(Long studentId) {                         // STD-2
        return enrollments.findByStudentId(studentId).stream().map(e -> ClassView.of(e.getClassRoom())).toList();
    }

    public void enroll(Long classId, Long studentId) {                          // ADM-9
        ClassRoom c = get(classId);
        User s = users.findById(studentId).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Student not found"));
        if (s.getRole() != Role.STUDENT) throw new ApiException(HttpStatus.BAD_REQUEST, "User is not a student");
        if (enrollments.existsByClassRoomIdAndStudentId(classId, studentId))
            throw new ApiException(HttpStatus.CONFLICT, "Already enrolled");
        enrollments.save(new Enrollment(c, s));
    }

    @Transactional
    public void unenroll(Long classId, Long studentId) {
        get(classId);
        enrollments.deleteByClassRoomIdAndStudentId(classId, studentId);
    }

    public List<UserView> students(Long classId) {
        get(classId);
        return enrollments.findByClassRoomId(classId).stream().map(e -> UserView.of(e.getStudent())).toList();
    }
}
