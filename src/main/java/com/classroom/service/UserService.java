package com.classroom.service;

import com.classroom.dto.Dto.*;
import com.classroom.entity.Role;
import com.classroom.entity.User;
import com.classroom.exception.ApiException;
import com.classroom.repository.UserRepository;
import com.classroom.security.AppUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository users;
    private final PasswordEncoder encoder;

    private void checkEmailFree(String email, Long selfId) {
        users.findByEmail(email).ifPresent(u -> {
            if (!u.getId().equals(selfId)) throw new ApiException(HttpStatus.CONFLICT, "Email already exists");
        });
    }

    public UserView createTeacher(CreateTeacherReq r) {                       // ADM-3
        String email = r.email().trim().toLowerCase();
        checkEmailFree(email, null);
        User u = new User();
        u.setName(r.name().trim()); u.setEmail(email); u.setRole(Role.TEACHER);
        u.setPassword(encoder.encode(r.password()));
        u.setMustChangePassword(true);
        return UserView.of(users.save(u));
    }

    public UserView createStudent(CreateStudentReq r) {                       // ADM-4
        String email = r.email().trim().toLowerCase();
        checkEmailFree(email, null);
        if (users.existsByEnrollmentNumber(r.enrollmentNumber().trim()))
            throw new ApiException(HttpStatus.CONFLICT, "Enrollment number already exists");
        User u = new User();
        u.setName(r.name().trim()); u.setEmail(email); u.setRole(Role.STUDENT);
        u.setEnrollmentNumber(r.enrollmentNumber().trim());
        u.setPassword(encoder.encode(r.password()));
        u.setMustChangePassword(true);
        return UserView.of(users.save(u));
    }

    public List<UserView> list(Role role) {                                    // ADM-5 (view)
        if (role == null || role == Role.ADMIN) throw new ApiException(HttpStatus.BAD_REQUEST, "role must be TEACHER or STUDENT");
        return users.findByRoleOrderByNameAsc(role).stream().map(UserView::of).toList();
    }

    private User managed(Long id) {
        User u = users.findById(id).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (u.getRole() == Role.ADMIN) throw new ApiException(HttpStatus.FORBIDDEN, "Admin accounts cannot be managed here");
        return u;
    }

    public UserView update(Long id, UpdateUserReq r) {                         // ADM-5 (edit)
        User u = managed(id);
        String email = r.email().trim().toLowerCase();
        checkEmailFree(email, id);
        u.setName(r.name().trim());
        u.setEmail(email);
        if (u.getRole() == Role.STUDENT && r.enrollmentNumber() != null && !r.enrollmentNumber().isBlank())
            u.setEnrollmentNumber(r.enrollmentNumber().trim());
        return UserView.of(users.save(u));
    }

    public UserView setActive(Long id, boolean active) {                       // ADM-5 (deactivate / reactivate)
        User u = managed(id);
        u.setActive(active);
        return UserView.of(users.save(u));
    }

    public void resetPassword(Long id, String newPassword) {                   // ADM-6
        User u = managed(id);
        u.setPassword(encoder.encode(newPassword));
        u.setMustChangePassword(true);
        u.setFailedAttempts(0);
        u.setLockUntil(null);
        users.save(u);
    }

    public void changePassword(AppUserDetails me, ChangePasswordReq r) {       // TCH-1 / STD-1
        User u = users.findById(me.getId()).orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found"));
        if (!encoder.matches(r.oldPassword(), u.getPassword()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Old password is incorrect");
        if (r.oldPassword().equals(r.newPassword()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "New password must be different");
        u.setPassword(encoder.encode(r.newPassword()));
        u.setMustChangePassword(false);
        users.save(u);
        me.setMustChangePassword(false);
    }
}
