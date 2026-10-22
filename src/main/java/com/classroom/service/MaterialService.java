package com.classroom.service;

import com.classroom.dto.Dto.MaterialView;
import com.classroom.entity.*;
import com.classroom.exception.ApiException;
import com.classroom.repository.*;
import com.classroom.security.AppUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** TCH-11, TCH-12, STD-9, SYS-9: files live outside the web folder and are served only after a membership check. */
@Service
@RequiredArgsConstructor
public class MaterialService {
    private static final Set<String> ALLOWED = Set.of("pdf", "ppt", "pptx", "png", "jpg", "jpeg");
    public record Download(Resource resource, String fileName) {}

    private final MaterialRepository repo;
    private final AccessService access;
    private final UserRepository users;
    @Value("${app.upload-dir}") private String uploadDir;
    @Value("${app.upload.max-bytes}") private long maxBytes;

    private Path root() { return Paths.get(uploadDir).toAbsolutePath().normalize(); }

    public MaterialView upload(AppUserDetails me, Long classId, MultipartFile file) {
        ClassRoom c = access.requireClassTeacher(me, classId);
        if (file == null || file.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST, "No file");
        if (file.getSize() > maxBytes) throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE, "File is too large");
        String original = StringUtils.cleanPath(file.getOriginalFilename() == null ? "file" : file.getOriginalFilename());
        original = original.substring(original.lastIndexOf('/') + 1);
        int dot = original.lastIndexOf('.');
        String ext = dot < 0 ? "" : original.substring(dot + 1).toLowerCase();
        if (!ALLOWED.contains(ext)) throw new ApiException(HttpStatus.BAD_REQUEST, "Allowed types: PDF, PPT, PPTX, PNG, JPG");
        try {
            Path dir = root().resolve("class-" + classId);
            Files.createDirectories(dir);
            String stored = UUID.randomUUID() + "." + ext;
            Files.copy(file.getInputStream(), dir.resolve(stored));
            Material m = new Material();
            m.setClassRoom(c);
            m.setFileName(original);
            m.setFilePath("class-" + classId + "/" + stored);
            m.setUploadedBy(users.getReferenceById(me.getId()));
            m.setUploadedAt(Instant.now());
            repo.save(m);
            m.setUploadedBy(users.findById(me.getId()).orElseThrow());
            return MaterialView.of(m);
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not save file");
        }
    }

    public List<MaterialView> list(AppUserDetails me, Long classId) {
        access.requireClassMember(me, classId);
        return repo.findByClassRoomIdOrderByUploadedAtDesc(classId).stream().map(MaterialView::of).toList();
    }

    public Download load(AppUserDetails me, Long id) {
        Material m = repo.findById(id).orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Access denied"));
        access.requireClassMember(me, m.getClassRoom().getId());
        Path p = root().resolve(m.getFilePath()).normalize();
        if (!p.startsWith(root()) || !Files.isReadable(p)) throw new ApiException(HttpStatus.NOT_FOUND, "File not found");
        try {
            return new Download(new UrlResource(p.toUri()), m.getFileName());
        } catch (java.net.MalformedURLException e) {
            throw new ApiException(HttpStatus.NOT_FOUND, "File not found");
        }
    }

    public void delete(AppUserDetails me, Long id) {
        Material m = repo.findById(id).orElseThrow(() -> new ApiException(HttpStatus.FORBIDDEN, "Access denied"));
        access.requireClassTeacher(me, m.getClassRoom().getId());
        if (!m.getUploadedBy().getId().equals(me.getId()))      // TCH-12: only own uploads
            throw new ApiException(HttpStatus.FORBIDDEN, "You can delete only materials you uploaded");
        deleteFile(m);
        repo.delete(m);
    }

    public void deleteAllForClass(Long classId) {
        repo.findByClassRoomIdOrderByUploadedAtDesc(classId).forEach(this::deleteFile);
        repo.deleteByClassRoomId(classId);
    }

    private void deleteFile(Material m) {
        try {
            Path p = root().resolve(m.getFilePath()).normalize();
            if (p.startsWith(root())) Files.deleteIfExists(p);
        } catch (IOException ignored) { }
    }
}
